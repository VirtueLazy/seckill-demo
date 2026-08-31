package com.example.seckilldemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SeckillActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private BigDecimal seckillPrice;
    private Integer seckillStock;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
