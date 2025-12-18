package com.shop.inventoryservice.service;

import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Реализация сервиса работы с товарами на стороне inventory-service.
 * <p>
 * Отвечает за:
 * <ul>
 *     <li>CRUD-операции над сущностью {@link Product};</li>
 *     <li>установку {@code createdAt} при создании товара;</li>
 *     <li>обновление основных полей товара при редактировании.</li>
 * </ul>
 * Используется REST-контроллером и gRPC-сервисом склада.
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    /**
     * Возвращает полный список товаров.
     *
     * @return список всех товаров из БД
     */
    @Override
    public List<Product> getAll() {
        return productRepository.findAll();
    }

    /**
     * Получает товар по идентификатору.
     *
     * @param id идентификатор товара
     * @return найденный товар
     * @throws IllegalStateException если товар с таким id не найден
     */
    @Override
    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Product not found: " + id));
    }

    /**
     * Создаёт новый товар.
     * <p>
     * Устанавливает поле {@code createdAt} в текущее время перед сохранением.
     *
     * @param product товар для создания
     * @return сохранённый товар с выставленным id и createdAt
     */
    @Override
    public Product create(Product product) {
        product.setCreatedAt(OffsetDateTime.now());
        return productRepository.save(product);
    }

    /**
     * Обновляет существующий товар.
     * <p>
     * Обновляются основные бизнес-поля: имя, описание, цена, скидка, количество.
     *
     * @param id      идентификатор обновляемого товара
     * @param updated объект с новыми значениями полей
     * @return обновлённый и сохранённый товар
     * @throws IllegalStateException если товар с таким id не найден
     */
    @Override
    public Product update(Long id, Product updated) {
        Product product = get(id);
        product.setName(updated.getName());
        product.setDescription(updated.getDescription());
        product.setPrice(updated.getPrice());
        product.setSale(updated.getSale());
        product.setQuantity(updated.getQuantity());
        return productRepository.save(product);
    }

    /**
     * Удаляет товар по идентификатору.
     *
     * @param id идентификатор товара для удаления
     */
    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }
}

