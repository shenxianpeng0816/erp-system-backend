package com.erp.service;

import com.erp.entity.Product;

import java.util.List;

public interface ProductService {

    List<Product> listProducts(Long warehouseId, String countryCode);

    Product create(Product product);

    Product update(Long id, Product body);

    void delete(Long id);
}
