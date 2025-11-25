package com.shop.orderservice.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}