package com.example.twofa.controller;

import com.example.twofa.model.Card;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * Sensitive endpoint: all routes under /api/cards/** are protected by the StepUpAuthFilter.
 * Access requires a valid JWT with AMR=otp (or ACR=gold) plus a valid one-time action token.
 */
@Slf4j
@RestController
@RequestMapping("/api/cards")
public class CardController {

    // Simulated card store — in production this would be a database repository
    private static final List<Card> CARDS = List.of(
        Card.builder()
            .id(1L)
            .cardNumber("4532-1234-5678-9012")
            .cardHolder("Test User")
            .expiryDate("12/2027")
            .cvv("123")
            .balance(5000.00)
            .build(),
        Card.builder()
            .id(2L)
            .cardNumber("5412-7512-3456-7890")
            .cardHolder("Test User")
            .expiryDate("06/2026")
            .cvv("456")
            .balance(12500.50)
            .build()
    );

    @GetMapping
    public ResponseEntity<List<Card>> getAllCards(@AuthenticationPrincipal Jwt jwt) {
        log.info("User {} listing cards", jwt.getSubject());
        return ResponseEntity.ok(CARDS);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Card> getCardById(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt) {
        log.info("User {} fetching card id={}", jwt.getSubject(), id);
        Optional<Card> card = CARDS.stream().filter(c -> c.getId().equals(id)).findFirst();
        return card.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
