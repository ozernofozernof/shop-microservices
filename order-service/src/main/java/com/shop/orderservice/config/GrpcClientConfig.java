package com.shop.orderservice.config;

import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(GrpcClientAutoConfiguration.class)
public class GrpcClientConfig {
}