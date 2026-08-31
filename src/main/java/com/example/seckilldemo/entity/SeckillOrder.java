package com.example.seckilldemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long seckillActivityId;
    private Long userId;
    private Long productId;
    private BigDecimal seckillPrice;
    private LocalDateTime createTime;

}
