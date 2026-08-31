package com.example.seckilldemo.service.impl;

import com.example.seckilldemo.entity.Product;
import com.example.seckilldemo.mapper.ProductMapper;
import com.example.seckilldemo.service.ProductService;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<Product> list(){
        return productMapper.selectList(null);
    }

    @Override
    public boolean save(Product product){
        return productMapper.insert(product) > 0;
    }

    @Override
    public Product getById(Long id){
        String cacheKey = "product:"+id;

        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            System.out.println("命中缓存: " + cacheKey);
            try {
                return objectMapper.readValue(cachedJson, Product.class);
            } catch (Exception e){
                throw new RuntimeException("缓存数据解析失败",e);
            }
        }

        System.out.println("未命中缓存，查数据库："+cacheKey);
        Product product = productMapper.selectById(id);

        if (product!=null){
            try {
                String json = objectMapper.writeValueAsString(product);
                stringRedisTemplate.opsForValue().set(cacheKey,json,10, TimeUnit.MINUTES);
            } catch (Exception e) {
                throw new RuntimeException("缓存数据写入失败",e);
            }
        }
        return product;
    }

    @Override
    public boolean update(Product product){
        boolean success = productMapper.updateById(product) > 0;

        if (success) {
            String cacheKey = "product:"+product.getId();
            stringRedisTemplate.delete(cacheKey);
            System.out.println("已删除缓存："+cacheKey);
        }

        return success;
    }

    @Override
    public boolean delete(Long id){
        boolean success = productMapper.deleteById(id) > 0;

        if (success) {
            String cacheKey = "product:"+id;
            stringRedisTemplate.delete(cacheKey);
            System.out.println("已删除缓存："+cacheKey);
        }

        return success;
    }
}
