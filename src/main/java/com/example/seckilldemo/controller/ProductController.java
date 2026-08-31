package com.example.seckilldemo.controller;

import com.example.seckilldemo.entity.Product;
import com.example.seckilldemo.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping("/list")
    public List<Product> list(){
        return productService.list();
    }

    @PostMapping("/add")
    public boolean add(@RequestBody Product product){
        return productService.save(product);
    }

    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id){
        return productService.getById(id);
    }

    @PutMapping("/{id}")
    public boolean update(@RequestBody Product product){
        return productService.update(product);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id){
        return productService.delete(id);
    }
}
