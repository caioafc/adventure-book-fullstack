import { Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AdventureChoices } from './adventure-choices';
import { AdventureStore, isAdventureId } from './adventure-store';

@Component({
  selector: 'app-adventure-page',
  imports: [RouterLink, AdventureChoices],
  templateUrl: './adventure-page.html',
  styleUrl: './adventure-page.css',
})
export class AdventurePage implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(AdventureStore);
  protected readonly bookId = signal<number | null>(null);

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const value = params.get('bookId') ?? '';
      const id = /^\d+$/.test(value) ? Number(value) : null;
      if (isAdventureId(id)) {
        this.bookId.set(id);
        this.store.open(id);
      } else {
        this.bookId.set(null);
        this.store.cancel();
      }
    });
  }

  ngOnDestroy(): void {
    this.store.cancel();
  }
}
