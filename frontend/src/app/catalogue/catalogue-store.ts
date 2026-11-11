import { Injectable, inject, signal } from '@angular/core';

import { ApiError } from '../api/api-error';
import type { BookSummary } from '../api/book-api.types';
import { BookCatalogueApi } from '../api/book-catalogue-api';

const PAGE_SIZE = 3;

interface CatalogueState {
  readonly books: readonly BookSummary[];
  readonly totalElements: number;
  readonly nextPage: number;
  readonly last: boolean;
  readonly loading: boolean;
  readonly error: string | null;
}

@Injectable({ providedIn: 'root' })
export class CatalogueStore {
  private readonly api = inject(BookCatalogueApi);
  private readonly stateSignal = signal<CatalogueState>({
    books: [],
    totalElements: 0,
    nextPage: 0,
    last: false,
    loading: false,
    error: null,
  });

  readonly state = this.stateSignal.asReadonly();

  ensureLoaded(): void {
    const { nextPage, error } = this.state();
    if (nextPage === 0 && !error) {
      this.loadNextPage();
    }
  }

  loadNextPage(): void {
    const current = this.state();
    if (current.loading || current.last) {
      return;
    }

    this.stateSignal.set({ ...current, loading: true, error: null });
    this.api.getBooks({ page: current.nextPage, size: PAGE_SIZE }).subscribe({
      next: (page) => {
        const booksById = new Map(current.books.map((book) => [book.id, book]));
        for (const book of page.content) {
          booksById.set(book.id, book);
        }

        this.stateSignal.set({
          books: [...booksById.values()],
          totalElements: page.totalElements,
          nextPage: page.page + 1,
          last: page.last,
          loading: false,
          error: null,
        });
      },
      error: (error: unknown) => {
        this.stateSignal.set({
          ...current,
          loading: false,
          error: error instanceof ApiError ? error.description : 'Could not load adventures.',
        });
      },
    });
  }
}
