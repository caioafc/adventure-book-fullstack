import { Routes } from '@angular/router';
import { CataloguePage } from './catalogue/catalogue-page';

export const routes: Routes = [
  { path: '', pathMatch: 'full', component: CataloguePage },
  {
    path: 'books/:bookId/play',
    loadComponent: () =>
      import('./adventure/adventure-page').then((module) => module.AdventurePage),
  },
];
