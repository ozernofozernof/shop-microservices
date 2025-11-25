package com.shop.orderservice.inventory;

import com.shop.proto.inventory.ProductRequest;
import com.shop.proto.inventory.ProductResponse;
import com.shop.proto.inventory.InventoryServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class InventoryClient {

    @GrpcClient("inventory")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public ProductResponse checkAvailability(Long productId, int quantity) {
        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(productId)
                .setRequestedQuantity(quantity)
                .build();

        return inventoryStub.checkProduct(request);
    }
}
