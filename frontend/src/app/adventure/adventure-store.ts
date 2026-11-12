import { Injectable, inject, signal } from '@angular/core';
import { Observable, Subscription, map, switchMap, take } from 'rxjs';

import { AdventureContentApi } from '../api/adventure-content-api';
import { ApiError } from '../api/api-error';
import type { BookDetails, BookSection } from '../api/book-api.types';

const STORAGE_KEY = 'adventure-book.progress.v1';
export const MAX_HEALTH = 10;
const MAX_HINTS = 3;

type AdventureStatus = 'playing' | 'completed' | 'dead';

interface Progress {
  readonly bookId: number;
  readonly sectionId: number;
  readonly health: number;
  readonly status: AdventureStatus;
  readonly consequenceMessage: string | null;
  readonly consequenceHealthChange: number | null;
  readonly hintsRemaining: number;
  readonly hintsVisible: boolean;
}

interface Adventure {
  readonly book: BookDetails;
  readonly section: BookSection;
  readonly progress: Progress;
}

export function isAdventureId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

type StoredProgress = Omit<
  Progress,
  'status' | 'consequenceMessage' | 'consequenceHealthChange' | 'hintsRemaining' | 'hintsVisible'
> & {
  readonly status: AdventureStatus | 'paused';
  readonly consequenceMessage?: string | null;
  readonly consequenceHealthChange?: number | null;
  readonly hintsRemaining?: number;
  readonly hintsVisible?: boolean;
};

function isProgress(value: unknown): value is StoredProgress {
  if (typeof value !== 'object' || value === null) return false;

  const {
    bookId,
    sectionId,
    health,
    status,
    consequenceMessage,
    consequenceHealthChange,
    hintsRemaining,
    hintsVisible,
  } = value as Partial<StoredProgress>;
  return (
    isAdventureId(bookId) &&
    isAdventureId(sectionId) &&
    typeof health === 'number' &&
    Number.isInteger(health) &&
    health >= 0 &&
    health <= MAX_HEALTH &&
    (status === 'playing' || status === 'paused' || status === 'completed' || status === 'dead') &&
    (health === 0) === (status === 'dead') &&
    (consequenceMessage == null || typeof consequenceMessage === 'string') &&
    (consequenceHealthChange == null ||
      (Number.isInteger(consequenceHealthChange) &&
        Math.abs(consequenceHealthChange) <= MAX_HEALTH)) &&
    (hintsRemaining === undefined ||
      (Number.isInteger(hintsRemaining) && hintsRemaining >= 0 && hintsRemaining <= MAX_HINTS)) &&
    (hintsVisible === undefined || typeof hintsVisible === 'boolean')
  );
}

@Injectable({ providedIn: 'root' })
export class AdventureStore {
  private readonly api = inject(AdventureContentApi);
  private readonly current = signal<Adventure | null>(null);
  private readonly pending = signal(false);
  private readonly failure = signal<string | null>(null);
  private readonly notice = signal<string | null>(null);
  private readonly savedStatuses = signal(this.checkpointStatuses(this.readSavedProgress()));
  private request?: Subscription;

  readonly adventure = this.current.asReadonly();
  readonly loading = this.pending.asReadonly();
  readonly error = this.failure.asReadonly();
  readonly storageNotice = this.notice.asReadonly();
  readonly savedStatusByBook = this.savedStatuses.asReadonly();

  open(bookId: number): void {
    this.clearAdventure();
    this.load(bookId, this.readSavedProgress().get(bookId));
  }

  restart(bookId: number): void {
    this.load(bookId, undefined, true);
  }

  persist(): boolean {
    const progress = this.current()?.progress;
    if (!progress) return false;

    const saved = this.readSavedProgress();
    saved.set(progress.bookId, progress);
    return this.writeSavedProgress(saved);
  }

  discard(bookId: number): void {
    this.clearAdventure();
    this.removeSavedProgress(bookId);
  }

  choose(index: number): void {
    const adventure = this.current();
    if (!adventure || adventure.progress.status !== 'playing' || this.pending()) return;

    const choice = adventure.section.options[index];
    if (!choice) return;

    this.fetch(this.api.getSection(adventure.book.id, choice.gotoId), (section) => {
      const consequence = choice.consequence;
      let health = adventure.progress.health;
      if (consequence) {
        const change = consequence.type === 'GAIN_HEALTH' ? consequence.value : -consequence.value;
        health = Math.max(0, Math.min(MAX_HEALTH, health + change));
      }
      this.enterSection(
        adventure.book,
        section,
        health,
        consequence
          ? { message: consequence.text, healthChange: health - adventure.progress.health }
          : null,
        adventure.progress.hintsRemaining,
      );
    });
  }

  showHint(): void {
    const adventure = this.current();
    if (!adventure || adventure.progress.status !== 'playing' || this.pending()) return;

    const { book, section, progress } = adventure;
    if (progress.hintsVisible || progress.hintsRemaining === 0) return;
    if (!section.options.some((choice) => choice.consequence)) return;

    this.showAdventure(book, section, {
      ...progress,
      hintsVisible: true,
      hintsRemaining: progress.hintsRemaining - 1,
    });
  }

  clearAdventure(): void {
    this.cancel();
    this.current.set(null);
    this.failure.set(null);
  }

  private cancel(): void {
    this.request?.unsubscribe();
    this.request = undefined;
    this.pending.set(false);
  }

  private load(bookId: number, progress?: Progress, restarting = false): void {
    const content = this.api
      .getBook(bookId)
      .pipe(
        switchMap((book) =>
          this.api
            .getSection(book.id, progress?.sectionId ?? book.beginningSectionId)
            .pipe(map((section) => ({ book, section }))),
        ),
      );

    this.fetch(
      content,
      ({ book, section }) => {
        if (progress) {
          this.showAdventure(book, section, progress);
        } else {
          this.enterSection(book, section, MAX_HEALTH, null);
          if (restarting) this.removeSavedProgress(bookId);
        }
      },
      (error) => {
        if (progress && error instanceof ApiError && error.status === 404) {
          this.removeSavedProgress(bookId);
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

  private enterSection(
    book: BookDetails,
    section: BookSection,
    health: number,
    consequence: { message: string; healthChange: number } | null,
    hintsRemaining = MAX_HINTS,
  ): void {
    let status: AdventureStatus = 'playing';
    if (health === 0) {
      status = 'dead';
    } else if (section.type === 'END') {
      status = 'completed';
    }
    this.showAdventure(book, section, {
      bookId: book.id,
      sectionId: section.id,
      health,
      status,
      consequenceMessage: consequence?.message ?? null,
      consequenceHealthChange: consequence?.healthChange ?? null,
      hintsRemaining,
      hintsVisible: false,
    });
    if (status !== 'playing') this.persist();
  }

  private showAdventure(book: BookDetails, section: BookSection, progress: Progress): void {
    this.current.set({ book, section, progress });
  }

  private readSavedProgress(): Map<number, Progress> {
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
          progressByBook.set(entry.bookId, {
            ...entry,
            status,
            consequenceMessage: entry.consequenceMessage ?? null,
            consequenceHealthChange: entry.consequenceHealthChange ?? null,
            hintsRemaining: entry.hintsRemaining ?? MAX_HINTS,
            hintsVisible: entry.hintsVisible ?? false,
          });
        } else {
          invalid = true;
        }
      }
    } catch {
      invalid = true;
    }

    if (invalid) {
      this.notice.set('Some saved progress could not be restored.');
    }
    return progressByBook;
  }

  private removeSavedProgress(bookId: number): void {
    const saved = this.readSavedProgress();
    if (saved.delete(bookId) && !this.writeSavedProgress(saved)) {
      this.notice.set('Saved progress could not be removed from this device.');
    }
  }

  private checkpointStatuses(
    saved: ReadonlyMap<number, Progress>,
  ): ReadonlyMap<number, AdventureStatus> {
    return new Map(Array.from(saved, ([id, progress]) => [id, progress.status]));
  }

  private writeSavedProgress(progressByBook: ReadonlyMap<number, Progress>): boolean {
    try {
      if (progressByBook.size > 0) {
        localStorage.setItem(STORAGE_KEY, JSON.stringify([...progressByBook.values()]));
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
      this.savedStatuses.set(this.checkpointStatuses(progressByBook));
      this.notice.set(null);
      return true;
    } catch {
      this.notice.set('Progress cannot be saved on this device.');
      return false;
    }
  }
}
