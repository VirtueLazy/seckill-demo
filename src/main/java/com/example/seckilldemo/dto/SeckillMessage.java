package com.example.seckilldemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillMessage implements Serializable {
    private Long activityId;
    private Long userId;
    private Long failedMessageId;

    public SeckillMessage(Long activityId, Long userId) {
        this(activityId, userId, null);
    }
}
