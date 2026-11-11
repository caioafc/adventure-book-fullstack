import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { rethrowApiError } from './api-error';
import type { BookDetails, BookSection } from './book-api.types';

@Injectable({ providedIn: 'root' })
export class AdventureContentApi {
  private readonly http = inject(HttpClient);

  getBook(bookId: number): Observable<BookDetails> {
    return this.http.get<BookDetails>(`/api/books/${bookId}`).pipe(catchError(rethrowApiError));
  }

  getSection(bookId: number, sectionId: number): Observable<BookSection> {
    return this.http
      .get<BookSection>(`/api/books/${bookId}/sections/${sectionId}`)
      .pipe(catchError(rethrowApiError));
  }
}
