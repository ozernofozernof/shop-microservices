package com.shop.inventoryservice.service;

import com.shop.inventoryservice.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> getAll();

    Product get(Long id);

    Product create(Product product);

    Product update(Long id, Product updated);

    void delete(Long id);
}
