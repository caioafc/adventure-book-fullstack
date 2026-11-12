import {
  Component,
  DestroyRef,
  OnDestroy,
  OnInit,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AdventureChoices } from './adventure-choices';
import { AdventureStore, MAX_HEALTH, isAdventureId } from './adventure-store';

type AdventureAction = 'restart' | 'stop' | 'pause';

@Component({
  selector: 'app-adventure-page',
  imports: [RouterLink, AdventureChoices],
  templateUrl: './adventure-page.html',
  styleUrl: './adventure-page.css',
})
export class AdventurePage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(AdventureStore);
  protected readonly maxHealth = MAX_HEALTH;
  protected readonly bookId = signal<number | null>(null);
  protected readonly confirmation = signal<AdventureAction>('restart');
  protected readonly consequenceHidden = linkedSignal({
    source: () => this.store.adventure()?.section,
    computation: (section, previous) => previous?.source === section && previous?.value === true,
  });
  protected readonly savedFeedback = linkedSignal({
    source: this.store.adventure,
    computation: () => false,
  });

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const value = params.get('bookId') ?? '';
      const id = /^\d+$/.test(value) ? Number(value) : null;
      if (isAdventureId(id)) {
        this.bookId.set(id);
        this.store.open(id);
      } else {
        this.bookId.set(null);
        this.store.clearAdventure();
      }
    });
  }

  ngOnDestroy(): void {
    this.store.clearAdventure();
  }

  protected confirm(dialog: HTMLDialogElement, action: AdventureAction): void {
    this.confirmation.set(action);
    dialog.showModal();
  }

  protected saveProgress(): boolean {
    const saved = this.store.persist();
    this.savedFeedback.set(saved);
    return saved;
  }

  protected saveAndLeave(): void {
    if (this.saveProgress()) {
      void this.router.navigate(['/']);
    }
  }

  protected stop(bookId: number): void {
    this.store.discard(bookId);
    void this.router.navigate(['/']);
  }
}
