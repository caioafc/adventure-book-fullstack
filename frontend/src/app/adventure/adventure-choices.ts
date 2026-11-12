import { Component, input, output } from '@angular/core';

import type { BookChoice } from '../api/book-api.types';

@Component({
  selector: 'app-adventure-choices',
  template: `
    <section aria-labelledby="choices-title">
      <h2 id="choices-title">What do you choose?</h2>
      <ol role="list" aria-labelledby="choices-title">
        @for (choice of options(); track $index) {
          <li>
            <button type="button" [disabled]="loading()" (click)="selected.emit($index)">
              <span class="choice-number" aria-hidden="true">{{ choice.gotoId }}</span>
              {{ choice.description }}
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
  readonly selected = output<number>();
}
