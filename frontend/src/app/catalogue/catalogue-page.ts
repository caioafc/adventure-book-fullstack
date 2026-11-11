import { TitleCasePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';

import { CatalogueStore } from './catalogue-store';

@Component({
  selector: 'app-catalogue-page',
  imports: [TitleCasePipe],
  templateUrl: './catalogue-page.html',
  styleUrl: './catalogue-page.css',
})
export class CataloguePage implements OnInit {
  protected readonly catalogue = inject(CatalogueStore);

  ngOnInit(): void {
    this.catalogue.ensureLoaded();
  }
}
