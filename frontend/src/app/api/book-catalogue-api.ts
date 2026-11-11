import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { rethrowApiError } from './api-error';
import type { BookPage, BookQuery } from './book-api.types';

@Injectable({ providedIn: 'root' })
export class BookCatalogueApi {
  private readonly http = inject(HttpClient);

  getBooks(query: BookQuery = {}): Observable<BookPage> {
    const params = Object.fromEntries(
      Object.entries(query).filter(([, value]) => value !== undefined && value !== ''),
    );

    return this.http.get<BookPage>('/api/books', { params }).pipe(catchError(rethrowApiError));
  }
}
