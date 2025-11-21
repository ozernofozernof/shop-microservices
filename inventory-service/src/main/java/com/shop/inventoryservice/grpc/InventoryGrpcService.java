package com.shop.inventoryservice.grpc;

import com.shop.inventoryservice.product.Product;
import com.shop.inventoryservice.product.ProductService;
import com.shop.proto.inventory.InventoryServiceGrpc;
import com.shop.proto.inventory.ProductRequest;
import com.shop.proto.inventory.ProductResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductService productService;

    @Override
    public void checkProduct(
            ProductRequest request,
            StreamObserver<ProductResponse> responseObserver
    ) {

        Product p = productService.get(request.getProductId());

        ProductResponse response = ProductResponse.newBuilder()
                .setProductId(p.getId())
                .setName(p.getName())
                .setAvailableQuantity(p.getQuantity())
                .setPrice(p.getPrice().doubleValue())
                .setSale(p.getSale() != null ? p.getSale().doubleValue() : 0.0)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}

