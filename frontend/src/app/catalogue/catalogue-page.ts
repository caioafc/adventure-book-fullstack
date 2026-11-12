import { TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';

import type { Difficulty } from '../api/book-api.types';
import { CatalogueStore } from './catalogue-store';

@Component({
  selector: 'app-catalogue-page',
  imports: [TitleCasePipe],
  templateUrl: './catalogue-page.html',
  styleUrl: './catalogue-page.css',
})
export class CataloguePage implements OnInit {
  protected readonly catalogue = inject(CatalogueStore);
  protected readonly difficulties = ['EASY', 'MEDIUM', 'HARD'] as const;

  ngOnInit(): void {
    this.catalogue.ensureLoaded();
  }

  protected toggleDifficulty(level: Difficulty): void {
    const selected = this.catalogue.state().filters.difficulty;
    this.catalogue.setFilters({ difficulty: selected === level ? '' : level }, false);
  }
}
