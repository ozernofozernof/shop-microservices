package com.shop.orderservice.grpc;

import com.shop.proto.inventory.ProductRequest;
import com.shop.proto.inventory.ProductResponse;
import com.shop.proto.inventory.InventoryServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InventoryClient {

    @GrpcClient("inventory")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public ProductResponse checkAvailability(Long productId, int quantity) {
        try {
            ProductRequest request = ProductRequest.newBuilder()
                    .setProductId(productId)
                    .setRequestedQuantity(quantity)
                    .build();

            return inventoryStub.checkAvailability(request);

        } catch (StatusRuntimeException e) {
            log.error("gRPC error while checking product {}: {}", productId, e.getStatus(), e);
            throw new IllegalStateException("Inventory service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            log.error("Unexpected error in InventoryClient: {}", e.getMessage(), e);
            throw new IllegalStateException("Unexpected error while contacting Inventory service");
        }
    }
}
