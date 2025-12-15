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

/**
 * gRPC-клиент для взаимодействия с {@code inventory-service}.
 * <p>
 * Основные задачи:
 * <ul>
 *     <li>Запрашивать информацию по товарам (цена, скидка, остатки).</li>
 *     <li>Поддерживать как одиночный запрос по одному продукту,
 *     так и батч-запрос по нескольким позициям.</li>
 * </ul>
 * Используется в {@link com.shop.orderservice.service.OrderService} при создании заказов.
 */
@Component
@Slf4j
public class InventoryClient {

    /**
     * Блокирующий stub gRPC-клиента, создаётся и конфигурируется
     * Spring Boot Starter gRPC по имени канала {@code inventory}.
     */
    @GrpcClient("inventory")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub;

    /**
     * Проверяет наличие одного товара, используя общий batсh-endpoint
     * {@code InventoryService.checkAvailability}.
     *
     * @param productId идентификатор товара
     * @param quantity  запрашиваемое количество (на данный момент здесь
     *                  не используется, но может быть полезно при расширении
     *                  протокола)
     * @return {@link ProductResponse} с информацией о товаре
     * @throws IllegalStateException если сервис вернул пустой ответ
     *                               или произошла gRPC-ошибка
     */
    public ProductResponse checkAvailability(Long productId, int quantity) {
        try {
            // Создаём запрос по одному продукту
            ProductRequest item = ProductRequest.newBuilder()
                    .setProductId(productId)
                    .build();

            // Обёрточный запрос для batch-метода
            CheckAvailabilityRequest request = CheckAvailabilityRequest.newBuilder()
                    .addItems(item)
                    .build();

            // Вызываем gRPC
            CheckAvailabilityResponse response = inventoryStub.checkAvailability(request);

            if (response.getProductsCount() == 0) {
                throw new IllegalStateException(
                        "No product info returned from Inventory service for productId=" + productId
                );
            }

            // Пока берём первый продукт из ответа
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
     * Батч-метод: список позиций заказа → один gRPC-вызов к inventory-service.
     * <p>
     * На основе списка {@link OrderItemRequest} собирает запрос,
     * вызывает {@code checkAvailability} и возвращает карту
     * {@code productId → ProductResponse}.
     *
     * @param items список позиций заказа
     * @return карта с информацией по каждому найденному продукту
     * @throws IllegalStateException при gRPC-ошибках или неожиданных исключениях
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