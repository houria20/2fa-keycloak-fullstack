import { Component, OnInit } from '@angular/core';
import { KeycloakService } from 'keycloak-angular';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  username = '';

  constructor(private readonly keycloak: KeycloakService) {}

  ngOnInit(): void {
    this.keycloak.loadUserProfile().then(profile => {
      this.username = `${profile.firstName ?? ''} ${profile.lastName ?? ''}`.trim();
    });
  }

  logout(): void {
    this.keycloak.logout(window.location.origin);
  }
}
