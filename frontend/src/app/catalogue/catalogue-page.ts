import { TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AdventureStore } from '../adventure/adventure-store';
import { CatalogueStore } from './catalogue-store';

@Component({
  selector: 'app-catalogue-page',
  imports: [TitleCasePipe],
  templateUrl: './catalogue-page.html',
  styleUrl: './catalogue-page.css',
})
export class CataloguePage implements OnInit {
  private readonly router = inject(Router);
  protected readonly adventure = inject(AdventureStore);
  protected readonly catalogue = inject(CatalogueStore);

  ngOnInit(): void {
    this.catalogue.ensureLoaded();
  }

  protected openAdventure(bookId: number): void {
    void this.router.navigate(['/books', bookId, 'play']);
  }
}
