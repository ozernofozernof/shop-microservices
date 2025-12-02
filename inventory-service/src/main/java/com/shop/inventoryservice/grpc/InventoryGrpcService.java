package com.shop.inventoryservice.grpc;

import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.service.ProductService;
import com.shop.proto.inventory.CheckAvailabilityRequest;
import com.shop.proto.inventory.CheckAvailabilityResponse;
import com.shop.proto.inventory.InventoryServiceGrpc;
import com.shop.proto.inventory.ProductRequest;
import com.shop.proto.inventory.ProductResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductService productService;

    @Override
    public void checkAvailability(
            CheckAvailabilityRequest request,
            StreamObserver<CheckAvailabilityResponse> responseObserver
    ) {

        CheckAvailabilityResponse.Builder responseBuilder = CheckAvailabilityResponse.newBuilder();

        for (ProductRequest item : request.getItemsList()) {
            long productId = item.getProductId();

            try {
                Product product = productService.get(productId);

                int availableQty = product.getQuantity();

                ProductResponse productResponse = ProductResponse.newBuilder()
                        .setProductId(product.getId())
                        .setName(product.getName())
                        .setAvailableQuantity(availableQty)
                        .setPrice(product.getPrice().doubleValue())
                        .setSale(product.getSale() != null ? product.getSale().doubleValue() : 0.0)
                        .build();

                responseBuilder.addProducts(productResponse);
            } catch (RuntimeException ex) {
                log.warn("Product {} not found in inventory: {}", productId, ex.getMessage());
                ProductResponse productResponse = ProductResponse.newBuilder()
                        .setProductId(productId)
                        .setName("")
                        .setAvailableQuantity(0)
                        .setPrice(0.0)
                        .setSale(0.0)
                        .build();

                responseBuilder.addProducts(productResponse);
            }
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}