import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { rethrowApiError } from './api-error';
import type { BookFilter, BookPage, BookQuery } from './book-api.types';

@Injectable({ providedIn: 'root' })
export class BookCatalogueApi {
  private readonly http = inject(HttpClient);

  getFilters(): Observable<readonly BookFilter[]> {
    return this.http
      .get<readonly BookFilter[]>('/api/books/filters')
      .pipe(catchError(rethrowApiError));
  }

  getBooks(query: BookQuery, filters: ReadonlyMap<string, string>): Observable<BookPage> {
    const params = Object.fromEntries(
      Object.entries({ ...Object.fromEntries(filters), ...query }).filter(
        ([, value]) => value !== undefined && value !== '',
      ),
    );

    return this.http.get<BookPage>('/api/books', { params }).pipe(catchError(rethrowApiError));
  }
}
