import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ActionTokenService } from './action-token.service';

export interface Card {
  id: number;
  cardNumber: string;
  cardHolder: string;
  expiryDate: string;
  cvv: string;
  balance: number;
}

@Injectable({ providedIn: 'root' })
export class CardService {
  private readonly apiUrl = `${environment.apiUrl}/api/cards`;

  constructor(
    private readonly http: HttpClient,
    private readonly actionTokenService: ActionTokenService
  ) {}

  getCards(): Observable<Card[]> {
    return this.http.get<Card[]>(this.apiUrl, { headers: this.buildHeaders() });
  }

  getCard(id: number): Observable<Card> {
    return this.http.get<Card>(`${this.apiUrl}/${id}`, { headers: this.buildHeaders() });
  }

  private buildHeaders(): HttpHeaders {
    let headers = new HttpHeaders();
    const token = this.actionTokenService.getActionToken();
    if (token) {
      headers = headers.set('X-Action-Token', token);
    }
    return headers;
  }
}
