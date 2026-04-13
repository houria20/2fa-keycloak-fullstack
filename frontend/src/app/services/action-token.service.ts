import { Injectable } from '@angular/core';

/**
 * Holds the one-time action token received in a 403 step_up_required response.
 * The token is kept in memory only (not in localStorage) to avoid leakage.
 */
@Injectable({ providedIn: 'root' })
export class ActionTokenService {
  private actionToken: string | null = null;

  setActionToken(token: string): void {
    this.actionToken = token;
  }

  getActionToken(): string | null {
    return this.actionToken;
  }

  clearActionToken(): void {
    this.actionToken = null;
  }

  hasActionToken(): boolean {
    return this.actionToken !== null;
  }
}
