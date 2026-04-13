package com.example.twofa.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {
    private Long id;
    private String cardNumber;
    private String cardHolder;
    private String expiryDate;
    private String cvv;
    private Double balance;
}
