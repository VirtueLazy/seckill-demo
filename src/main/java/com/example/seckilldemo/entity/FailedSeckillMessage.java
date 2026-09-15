package com.example.seckilldemo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seckill_failed_message")
public class FailedSeckillMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long userId;
    private String status;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime lastReplayTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
