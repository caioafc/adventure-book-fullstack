import { Component, computed, input, output } from '@angular/core';

import type { BookChoice } from '../api/book-api.types';

@Component({
  selector: 'app-adventure-choices',
  template: `
    <section aria-labelledby="choices-title">
      <div class="choices-heading">
        <h2 id="choices-title">What do you choose?</h2>
        @if (hasConsequences()) {
          <button
            type="button"
            class="hint-button"
            [disabled]="loading() || hintsVisible() || hintsRemaining() === 0"
            (click)="hintRequested.emit()"
          >
            {{ hintsVisible() ? 'Hint shown' : 'Show hint' }} ({{ hintsRemaining() }} left)
          </button>
        }
      </div>
      <ol role="list" aria-labelledby="choices-title">
        @for (choice of options(); track $index) {
          <li>
            <button
              type="button"
              [attr.aria-labelledby]="'choice-label-' + $index"
              [attr.aria-describedby]="
                hintsVisible() && choice.consequence ? 'choice-effect-' + $index : null
              "
              [disabled]="loading()"
              (click)="selected.emit($index)"
            >
              <span class="choice-number" aria-hidden="true">{{ choice.gotoId }}</span>
              <span class="choice-content">
                <span class="choice-description" [id]="'choice-label-' + $index">
                  {{ choice.description }}
                </span>
                @if (hintsVisible() && choice.consequence; as consequence) {
                  <span class="choice-consequence" [id]="'choice-effect-' + $index">
                    {{ consequence.text }}
                  </span>
                }
              </span>
            </button>
          </li>
        }
      </ol>
    </section>
  `,
  styleUrl: './adventure-choices.css',
})
export class AdventureChoices {
  readonly options = input.required<readonly BookChoice[]>();
  readonly loading = input.required<boolean>();
  readonly hintsRemaining = input.required<number>();
  readonly hintsVisible = input.required<boolean>();
  readonly selected = output<number>();
  readonly hintRequested = output<void>();
  protected readonly hasConsequences = computed(() =>
    this.options().some((choice) => choice.consequence),
  );
}
