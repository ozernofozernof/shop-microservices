package com.shop.orderservice.inventory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Здесь мог быть ваш импорт реальных классов из сгенерированного gRPC кода
// import com.shop.proto.InventoryServiceGrpc;
// import com.shop.proto.ProductRequest;
// import com.shop.proto.ProductResponse;

@Component
@RequiredArgsConstructor
public class InventoryClient {

    // private final InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public ProductInfo checkAvailability(Long productId, int quantity) {

        // Здесь мог быть ваш реальный gRPC вызов.
        // Типа:

        /*
        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(productId)
                .setRequestedQuantity(quantity)
                .build();

        ProductResponse response = inventoryStub.checkAvailability(request);

        return new ProductInfo(
                response.getProductId(),
                response.getName(),
                response.getAvailableQuantity(),
                BigDecimal.valueOf(response.getPrice()),
                BigDecimal.valueOf(response.getSale())
        );
        */

        // Пока заглушка для компиляции:
        throw new UnsupportedOperationException("gRPC client not implemented yet");
    }
}

