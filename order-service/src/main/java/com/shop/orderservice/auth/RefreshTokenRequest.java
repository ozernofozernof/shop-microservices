package com.shop.orderservice.auth;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}