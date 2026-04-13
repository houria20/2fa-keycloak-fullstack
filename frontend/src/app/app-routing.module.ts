import { NgModule, inject } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CanActivateFn } from '@angular/router';
import { CardComponent } from './components/card/card.component';
import { KeycloakService } from 'keycloak-angular';

const authGuard: CanActivateFn = (_route, state) => {
  const keycloak = inject(KeycloakService);
  if (keycloak.isLoggedIn()) {
    return true;
  }
  keycloak.login({ redirectUri: window.location.origin + state.url });
  return false;
};

const routes: Routes = [
  { path: '', redirectTo: '/cards', pathMatch: 'full' },
  { path: 'cards', component: CardComponent, canActivate: [authGuard] }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
