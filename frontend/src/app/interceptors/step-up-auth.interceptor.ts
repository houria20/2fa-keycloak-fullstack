import { Injectable } from '@angular/core';
import {
  HttpInterceptor,
  HttpRequest,
  HttpHandler,
  HttpEvent,
  HttpErrorResponse
} from '@angular/common/http';
import { Observable, throwError, from } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { KeycloakService } from 'keycloak-angular';
import { ActionTokenService } from '../services/action-token.service';

interface StepUpErrorBody {
  error: string;
  error_description: string;
  action_token: string;
}

/**
 * HTTP interceptor that handles 403 step_up_required responses.
 *
 * Flow:
 *  1. Detects a 403 whose body contains `{ error: "step_up_required", action_token: "..." }`.
 *  2. Stores the action token in memory.
 *  3. Triggers Keycloak step-up login (acr=gold, prompt=login) so the user performs OTP.
 *  4. After the new token is obtained, retries the original request with the action token
 *     in the X-Action-Token header.
 */
@Injectable()
export class StepUpAuthInterceptor implements HttpInterceptor {
  constructor(
    private readonly keycloak: KeycloakService,
    private readonly actionTokenService: ActionTokenService
  ) {}

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 403 && this.isStepUpRequired(error)) {
          return this.handleStepUp(error, request, next);
        }
        return throwError(() => error);
      })
    );
  }

  private isStepUpRequired(error: HttpErrorResponse): boolean {
    try {
      const body = error.error as StepUpErrorBody;
      return body?.error === 'step_up_required';
    } catch {
      return false;
    }
  }

  private handleStepUp(
    error: HttpErrorResponse,
    original: HttpRequest<unknown>,
    next: HttpHandler
  ): Observable<HttpEvent<unknown>> {
    const body = error.error as StepUpErrorBody;
    const actionToken = body.action_token;

    if (!actionToken) {
      return throwError(() => error);
    }

    this.actionTokenService.setActionToken(actionToken);

    return from(this.performStepUpAuth()).pipe(
      switchMap(() => {
        // Retry with the action token attached
        const retried = original.clone({
          setHeaders: { 'X-Action-Token': actionToken }
        });
        return next.handle(retried);
      }),
      catchError(stepUpError => {
        this.actionTokenService.clearActionToken();
        return throwError(() => stepUpError);
      })
    );
  }

  private async performStepUpAuth(): Promise<void> {
    const kc = this.keycloak.getKeycloakInstance();

    // Request step-up with ACR level "gold" (mapped to OTP LoA in Keycloak)
    await kc.login({
      acr: { values: ['gold'], essential: true },
      prompt: 'login'
    });

    // Refresh the local token after login completes
    await this.keycloak.updateToken(30);
  }
}
