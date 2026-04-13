import { Component, OnInit } from '@angular/core';
import { CardService, Card } from '../../services/card.service';
import { ActionTokenService } from '../../services/action-token.service';

@Component({
  selector: 'app-card',
  templateUrl: './card.component.html',
  styleUrls: ['./card.component.scss']
})
export class CardComponent implements OnInit {
  cards: Card[] = [];
  selectedCard: Card | null = null;
  loading = false;
  error: string | null = null;

  constructor(
    private readonly cardService: CardService,
    private readonly actionTokenService: ActionTokenService
  ) {}

  ngOnInit(): void {
    this.loadCards();
  }

  loadCards(): void {
    this.loading = true;
    this.error = null;
    this.selectedCard = null;

    this.cardService.getCards().subscribe({
      next: cards => {
        this.cards = cards;
        this.loading = false;
      },
      error: err => {
        this.loading = false;
        if (err.status === 403 && err.error?.error === 'step_up_required') {
          this.error = 'Strong authentication required. Redirecting to 2FA challenge…';
        } else {
          this.error = 'Failed to load cards. Please try again.';
        }
      }
    });
  }

  viewCard(id: number): void {
    this.loading = true;
    this.error = null;
    this.selectedCard = null;

    this.cardService.getCard(id).subscribe({
      next: card => {
        this.selectedCard = card;
        this.loading = false;
        this.actionTokenService.clearActionToken();
      },
      error: err => {
        this.loading = false;
        if (err.status === 403 && err.error?.error === 'step_up_required') {
          this.error = 'Strong authentication required. Redirecting to 2FA challenge…';
        } else {
          this.error = 'Failed to load card details. Please try again.';
        }
      }
    });
  }

  maskCardNumber(cardNumber: string): string {
    return cardNumber.replace(/\d(?=\d{4})/g, '*');
  }

  formatBalance(balance: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR'
    }).format(balance);
  }
}
