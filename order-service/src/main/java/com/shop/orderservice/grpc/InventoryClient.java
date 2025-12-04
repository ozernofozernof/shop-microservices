package com.shop.orderservice.grpc;

import com.shop.orderservice.dto.OrderItemRequest;
import com.shop.proto.inventory.CheckAvailabilityRequest;
import com.shop.proto.inventory.CheckAvailabilityResponse;
import com.shop.proto.inventory.ProductRequest;
import com.shop.proto.inventory.ProductResponse;
import com.shop.proto.inventory.InventoryServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class InventoryClient {

    @GrpcClient("inventory")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    public ProductResponse checkAvailability(Long productId, int quantity) {
        try {
            //Создаём запрос по одному продукту
            ProductRequest item = ProductRequest.newBuilder()
                    .setProductId(productId)
                    .build();

            //Обёрточный запрос для batсh-метода
            CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                    .addItems(item)
                    .build();

            //Вызываем gRPC с новым типом
            CheckAvailabilityResponse response = inventoryStub.checkAvailability(request);

            if (response.getProductsCount() == 0) {
                throw new IllegalStateException(
                        "No product info returned from Inventory service for productId=" + productId
                );
            }

            //Пока берём первый продукт из ответа
            return response.getProducts(0);

        } catch (StatusRuntimeException e) {
            log.error("gRPC error while checking product {}: {}", productId, e.getStatus(), e);
            throw new IllegalStateException("Inventory service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            log.error("Unexpected error in InventoryClient: {}", e.getMessage(), e);
            throw new IllegalStateException("Unexpected error while contacting Inventory service");
        }
    }

    /**
     * батч-метод – список позиций → одна gRPC-вызов.
     */
    public Map<Long, ProductResponse> checkAvailabilityBatch(List<OrderItemRequest> items) {
        try {
            CheckAvailabilityRequest.Builder requestBuilder = CheckAvailabilityRequest.newBuilder();

            for (OrderItemRequest itemRequest : items) {
                requestBuilder.addItems(
                        ProductRequest.newBuilder()
                                .setProductId(itemRequest.getProductId())
                                .build()
                );
            }

            CheckAvailabilityRequest request = requestBuilder.build();

            log.info("Calling InventoryService.checkAvailability for {} items", items.size());
            CheckAvailabilityResponse response = inventoryStub.checkAvailability(request);
            log.info("Received inventory response with {} products", response.getProductsCount());

            Map<Long, ProductResponse> result = new HashMap<>();
            for (ProductResponse productResponse : response.getProductsList()) {
                result.put(productResponse.getProductId(), productResponse);
            }

            return result;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error while checking products batch: {}", e.getStatus(), e);
            throw new IllegalStateException("Inventory service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            log.error("Unexpected error in InventoryClient (batch): {}", e.getMessage(), e);
            throw new IllegalStateException("Unexpected error while contacting Inventory service (batch)");
        }
    }
}