import { Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, of, switchMap, tap, timer } from 'rxjs';

import { ApiError } from '../api/api-error';
import type { BookFilter, BookPage, BookSummary } from '../api/book-api.types';
import { BookCatalogueApi } from '../api/book-catalogue-api';

const PAGE_SIZE = 3;
const FILTER_DELAY_MS = 300;

interface CatalogueFilters {
  readonly search: string;
  readonly selected: ReadonlyMap<string, string>;
}

type FilterOptionsState =
  | { readonly status: 'idle' | 'loading' | 'error' }
  | { readonly status: 'ready'; readonly filters: readonly BookFilter[] };

interface CatalogueRequest {
  readonly page: number;
  readonly size: number;
  readonly append: boolean;
  readonly delayMs: number;
}

interface CatalogueState {
  readonly books: readonly BookSummary[];
  readonly filters: CatalogueFilters;
  readonly catalogueTotal: number | null;
  readonly totalElements: number;
  readonly targetCount: number;
  readonly nextPage: number;
  readonly last: boolean;
  readonly loading: boolean;
  readonly error: string | null;
}

@Injectable({ providedIn: 'root' })
export class CatalogueStore {
  private readonly api = inject(BookCatalogueApi);
  private readonly requests = new Subject<CatalogueRequest>();
  private readonly filterOptionsSignal = signal<FilterOptionsState>({ status: 'idle' });
  private failedRequest: CatalogueRequest | null = null;
  private readonly stateSignal = signal<CatalogueState>({
    books: [],
    filters: { search: '', selected: new Map() },
    catalogueTotal: null,
    totalElements: 0,
    targetCount: PAGE_SIZE,
    nextPage: 0,
    last: false,
    loading: false,
    error: null,
  });

  readonly state = this.stateSignal.asReadonly();
  readonly filterOptions = this.filterOptionsSignal.asReadonly();
  readonly hasFilters = computed(() => {
    const { search, selected } = this.state().filters;
    return search.trim().length > 0 || selected.size > 0;
  });

  constructor() {
    this.requests
      .pipe(
        switchMap((request) => {
          const { search, selected } = this.state().filters;
          const ready = request.delayMs ? timer(request.delayMs) : of(0);

          return ready.pipe(
            switchMap(() =>
              this.api.getBooks(
                { search: search.trim(), page: request.page, size: request.size },
                selected,
              ),
            ),
            tap((page) => this.applyPage(request, page)),
            catchError((error: unknown) => {
              this.failedRequest = request;
              this.stateSignal.update((state) => ({
                ...state,
                loading: false,
                error: error instanceof ApiError ? error.description : 'Could not load adventures.',
              }));
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  ensureLoaded(): void {
    if (this.filterOptions().status === 'idle') {
      this.loadFilterOptions();
    }
    const { nextPage, loading, error, targetCount } = this.state();
    if (nextPage === 0 && !loading && !error) {
      this.load({ page: 0, size: targetCount, append: false, delayMs: 0 });
    }
  }

  loadFilterOptions(): void {
    const { status } = this.filterOptions();
    if (status === 'loading' || status === 'ready') return;

    this.filterOptionsSignal.set({ status: 'loading' });
    this.api.getFilters().subscribe({
      next: (filters) => this.filterOptionsSignal.set({ status: 'ready', filters }),
      error: () => this.filterOptionsSignal.set({ status: 'error' }),
    });
  }

  setSearch(search: string): void {
    this.setFilters({ search }, FILTER_DELAY_MS);
  }

  toggleFilter(key: string, value: string): void {
    const options = this.filterOptions();
    if (
      options.status !== 'ready' ||
      !options.filters.some(
        (filter) =>
          filter.key === key && filter.type === 'select' && filter.options.includes(value),
      )
    ) {
      return;
    }

    const selected = new Map(this.state().filters.selected);
    if (selected.get(key) === value) {
      selected.delete(key);
    } else {
      selected.set(key, value);
    }
    this.setFilters({ selected });
  }

  private setFilters(changes: Partial<CatalogueFilters>, delayMs = 0): void {
    const current = this.state();
    const filters = { ...current.filters, ...changes };
    this.stateSignal.update((state) => ({ ...state, filters }));
    this.load({
      page: 0,
      size: current.targetCount,
      append: false,
      delayMs,
    });
  }

  clearFilters(): void {
    this.setFilters({ search: '', selected: new Map() });
  }

  loadNextPage(): void {
    const current = this.state();
    if (current.loading || current.last || current.error) {
      return;
    }

    this.load({ page: current.nextPage, size: PAGE_SIZE, append: true, delayMs: 0 });
  }

  retry(): void {
    if (this.failedRequest) {
      this.load({ ...this.failedRequest, delayMs: 0 });
    }
  }

  private load(request: CatalogueRequest): void {
    this.failedRequest = null;
    this.stateSignal.update((state) => ({ ...state, loading: true, error: null }));
    this.requests.next(request);
  }

  private applyPage(request: CatalogueRequest, page: BookPage): void {
    const current = this.state();
    const booksById = new Map((request.append ? current.books : []).map((book) => [book.id, book]));
    for (const book of page.content) {
      booksById.set(book.id, book);
    }

    this.stateSignal.set({
      ...current,
      books: [...booksById.values()],
      catalogueTotal: this.hasFilters() ? current.catalogueTotal : page.totalElements,
      totalElements: page.totalElements,
      targetCount: request.append ? current.targetCount + PAGE_SIZE : current.targetCount,
      nextPage: request.append ? page.page + 1 : request.size / PAGE_SIZE,
      last: page.last,
      loading: false,
    });
  }
}
