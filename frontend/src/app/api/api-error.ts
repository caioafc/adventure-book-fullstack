import { HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';

export interface ApiErrorBody {
  readonly code: string;
  readonly description: string;
}

export class ApiError extends Error implements ApiErrorBody {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly description: string,
  ) {
    super(description);
    this.name = 'ApiError';
  }
}

export function rethrowApiError(error: unknown): Observable<never> {
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  const body: unknown = error instanceof HttpErrorResponse ? error.error : undefined;

  if (status === 0) {
    return throwError(
      () => new ApiError(status, 'NETWORK_ERROR', 'Could not reach the service. Please try again.'),
    );
  }

  if (isApiErrorBody(body)) {
    return throwError(() => new ApiError(status, body.code, body.description));
  }

  return throwError(
    () => new ApiError(status, 'REQUEST_FAILED', 'The request could not be completed. Please try again.'),
  );
}

function isApiErrorBody(body: unknown): body is ApiErrorBody {
  return (
    typeof body === 'object' &&
    body !== null &&
    'code' in body &&
    typeof body.code === 'string' &&
    'description' in body &&
    typeof body.description === 'string'
  );
}
