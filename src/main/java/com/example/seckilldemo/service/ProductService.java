package com.example.seckilldemo.service;

import com.example.seckilldemo.entity.Product;

import java.util.List;

public interface ProductService {
    List<Product> list();

    boolean save(Product product);

    Product getById (Long id);

    boolean update(Product product);

    boolean delete(Long id);
}
