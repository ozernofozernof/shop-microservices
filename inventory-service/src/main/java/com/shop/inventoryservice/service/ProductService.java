package com.shop.inventoryservice.service;

import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<Product> getAll() {
        return productRepository.findAll();
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public Product create(Product product) {
        product.setCreatedAt(OffsetDateTime.now());
        return productRepository.save(product);
    }

    public Product update(Long id, Product updated) {
        Product p = get(id);
        p.setName(updated.getName());
        p.setDescription(updated.getDescription());
        p.setPrice(updated.getPrice());
        p.setSale(updated.getSale());
        p.setQuantity(updated.getQuantity());
        return productRepository.save(p);
    }

    public void delete(Long id) {
        productRepository.deleteById(id);
    }
}

