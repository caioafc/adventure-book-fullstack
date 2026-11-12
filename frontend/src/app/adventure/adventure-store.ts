import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subscription, map, switchMap, take } from 'rxjs';

import { AdventureContentApi } from '../api/adventure-content-api';
import { ApiError } from '../api/api-error';
import type { BookDetails, BookSection } from '../api/book-api.types';

const STORAGE_KEY = 'adventure-book.progress.v1';
const STARTING_HEALTH = 10;

type AdventureStatus = 'playing' | 'completed';

interface Progress {
  readonly bookId: number;
  readonly sectionId: number;
  readonly health: number;
  readonly status: AdventureStatus;
}

interface Adventure {
  readonly book: BookDetails;
  readonly section: BookSection;
  readonly progress: Progress;
}

export function isAdventureId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

type StoredProgress = Omit<Progress, 'status'> & {
  readonly status: AdventureStatus | 'paused';
};

function isProgress(value: unknown): value is StoredProgress {
  if (typeof value !== 'object' || value === null) return false;

  const { bookId, sectionId, health, status } = value as Partial<StoredProgress>;
  return (
    isAdventureId(bookId) &&
    isAdventureId(sectionId) &&
    typeof health === 'number' &&
    Number.isInteger(health) &&
    health > 0 &&
    health <= STARTING_HEALTH &&
    (status === 'playing' || status === 'paused' || status === 'completed')
  );
}

@Injectable({ providedIn: 'root' })
export class AdventureStore {
  private readonly api = inject(AdventureContentApi);
  private readonly current = signal<Adventure | null>(null);
  private readonly pending = signal(false);
  private readonly failure = signal<string | null>(null);
  private readonly notice = signal<string | null>(null);
  private readonly saved = signal<ReadonlyMap<number, Progress>>(this.readSavedProgress());
  private request?: Subscription;

  readonly adventure = this.current.asReadonly();
  readonly loading = this.pending.asReadonly();
  readonly error = this.failure.asReadonly();
  readonly storageNotice = this.notice.asReadonly();
  readonly savedProgress = this.saved.asReadonly();

  open(bookId: number): void {
    this.current.set(null);
    this.load(bookId, this.saved().get(bookId));
  }

  restart(bookId: number): void {
    this.load(bookId);
  }

  choose(index: number): void {
    const adventure = this.current();
    if (!adventure || adventure.progress.status !== 'playing' || this.pending()) return;

    const choice = adventure.section.options[index];
    if (!choice) return;

    this.fetch(this.api.getSection(adventure.book.id, choice.gotoId), (section) =>
      this.enterSection(adventure.book, section, adventure.progress.health),
    );
  }

  cancel(): void {
    this.request?.unsubscribe();
    this.request = undefined;
    this.pending.set(false);
  }

  private load(bookId: number, saved?: Progress): void {
    const content = this.api
      .getBook(bookId)
      .pipe(
        switchMap((book) =>
          this.api
            .getSection(book.id, saved?.sectionId ?? book.beginningSectionId)
            .pipe(map((section) => ({ book, section }))),
        ),
      );

    this.fetch(
      content,
      ({ book, section }) => this.enterSection(book, section, saved?.health ?? STARTING_HEALTH),
      (error) => {
        if (saved && error instanceof ApiError && error.status === 404) {
          const remaining = new Map(this.saved());
          remaining.delete(bookId);
          this.saveProgress(remaining);
          this.failure.set(
            'This saved adventure is no longer available. You can try starting again.',
          );
        }
      },
    );
  }

  private fetch<T>(
    source: Observable<T>,
    accept: (value: T) => void,
    handleError?: (error: unknown) => void,
  ): void {
    this.cancel();
    this.failure.set(null);
    this.pending.set(true);
    this.request = source.pipe(take(1)).subscribe({
      next: accept,
      complete: () => this.pending.set(false),
      error: (error: unknown) => {
        this.pending.set(false);
        this.failure.set(
          error instanceof ApiError
            ? error.description
            : 'Could not load the adventure. Please try again.',
        );
        handleError?.(error);
      },
    });
  }

  private enterSection(book: BookDetails, section: BookSection, health: number): void {
    const status = section.type === 'END' ? 'completed' : 'playing';
    const progress: Progress = { bookId: book.id, sectionId: section.id, health, status };
    this.current.set({ book, section, progress });
    this.saveProgress(new Map(this.saved()).set(book.id, progress));
  }

  private readSavedProgress(): ReadonlyMap<number, Progress> {
    const progressByBook = new Map<number, Progress>();
    let raw: string | null;
    try {
      raw = localStorage.getItem(STORAGE_KEY);
    } catch {
      this.notice.set('Progress cannot be saved on this device.');
      return progressByBook;
    }

    if (raw === null) return progressByBook;

    let invalid = false;
    try {
      const stored: unknown = JSON.parse(raw);
      const entries: readonly unknown[] = Array.isArray(stored) ? stored : [stored];
      for (const entry of entries) {
        if (isProgress(entry)) {
          const status = entry.status === 'paused' ? 'playing' : entry.status;
          progressByBook.set(entry.bookId, { ...entry, status });
        } else {
          invalid = true;
        }
      }
    } catch {
      invalid = true;
    }

    if (invalid) {
      this.writeSavedProgress(progressByBook);
      if (!this.notice()) this.notice.set('Some saved progress could not be restored.');
    }
    return progressByBook;
  }

  private saveProgress(progressByBook: ReadonlyMap<number, Progress>): void {
    this.saved.set(progressByBook);
    this.writeSavedProgress(progressByBook);
  }

  private writeSavedProgress(progressByBook: ReadonlyMap<number, Progress>): void {
    try {
      if (progressByBook.size > 0) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([...progressByBook.values()]));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
      this.notice.set(null);
    } catch {
      this.notice.set('Progress cannot be saved on this device.');
    }
  }
}
