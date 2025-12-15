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

/**
 * gRPC-сервис склада (inventory), отвечающий за проверку доступности товаров.
 * <p>
 * Реализует метод {@code checkAvailability}, который:
 * <ul>
 *     <li>принимает список productId;</li>
 *     <li>для каждого пытается получить товар из БД;</li>
 *     <li>возвращает список {@link ProductResponse} с текущими остатками и ценой;</li>
 *     <li>если товара нет — возвращает заглушку с количеством 0.</li>
 * </ul>
 * Используется order-service через gRPC-клиент.
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductService productService;

    /**
     * Проверка доступности товаров по списку productId.
     *
     * @param request           входной запрос с набором {@link ProductRequest}
     * @param responseObserver  gRPC-observer для отправки ответа
     */
    @Override
    public void checkAvailability(
            CheckAvailabilityRequest request,
            StreamObserver<CheckAvailabilityResponse> responseObserver
    ) {
        log.info("InventoryGrpcService: checkAvailability called, itemsCount={}",
                request.getItemsCount());

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
                // Логируем, но не рвём весь batch — возвращаем "пустой" продукт.
                log.warn("InventoryGrpcService: product {} not found in inventory: {}",
                        productId, ex.getMessage());

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

        CheckAvailabilityResponse response = responseBuilder.build();
        log.info("InventoryGrpcService: checkAvailability finished, productsInResponse={}",
                response.getProductsCount());

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}