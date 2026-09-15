package com.example.seckilldemo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.entity.FailedSeckillMessage;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.exception.BusinessException;
import com.example.seckilldemo.mapper.FailedSeckillMessageMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import com.example.seckilldemo.mq.SeckillMessagePublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeckillFailureService {
    public static final String PENDING = "PENDING";

    private final FailedSeckillMessageMapper failedMessageMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillMessagePublisher messagePublisher;

    public SeckillFailureService(FailedSeckillMessageMapper failedMessageMapper,
                                 SeckillOrderMapper seckillOrderMapper,
                                 SeckillMessagePublisher messagePublisher) {
        this.failedMessageMapper = failedMessageMapper;
        this.seckillOrderMapper = seckillOrderMapper;
        this.messagePublisher = messagePublisher;
    }

    public void recordDeadLetter(SeckillMessage message, String lastError) {
        failedMessageMapper.recordFailure(message.getActivityId(), message.getUserId(), lastError);
    }

    public List<FailedSeckillMessage> listPending() {
        return failedMessageMapper.selectList(
                new LambdaQueryWrapper<FailedSeckillMessage>()
                        .eq(FailedSeckillMessage::getStatus, PENDING)
                        .orderByAsc(FailedSeckillMessage::getCreatedTime));
    }

    public void replay(Long failedMessageId) {
        FailedSeckillMessage failed = failedMessageMapper.selectById(failedMessageId);
        if (failed == null) {
            throw new BusinessException("死信记录不存在");
        }
        if (!PENDING.equals(failed.getStatus())) {
            throw new BusinessException("该死信记录已经处理");
        }
        if (orderExists(failed.getActivityId(), failed.getUserId())) {
            failedMessageMapper.markResolved(failedMessageId);
            return;
        }
        if (failedMessageMapper.claimReplay(failedMessageId) == 0) {
            throw new BusinessException("重放过于频繁，请30秒后重试");
        }

        // 重放失败时保留 PENDING 记录，不能恢复 Redis 预约，否则旧消息与新请求可能并发下单。
        try {
            messagePublisher.publish(
                    new SeckillMessage(failed.getActivityId(), failed.getUserId(), failedMessageId),
                    false);
        } catch (RuntimeException publishFailure) {
            throw new BusinessException("重放发送失败，记录仍保留为待处理", publishFailure);
        }
    }

    public ReconciliationResult reconcile() {
        List<FailedSeckillMessage> pending = listPending();
        int resolved = 0;
        for (FailedSeckillMessage failed : pending) {
            if (orderExists(failed.getActivityId(), failed.getUserId())) {
                resolved += failedMessageMapper.markResolved(failed.getId());
            }
        }
        return new ReconciliationResult(pending.size(), resolved, pending.size() - resolved);
    }

    public void markResolved(SeckillMessage message) {
        if (message.getFailedMessageId() != null) {
            failedMessageMapper.markResolved(message.getFailedMessageId());
        }
    }

    private boolean orderExists(Long activityId, Long userId) {
        return seckillOrderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getSeckillActivityId, activityId)
                        .eq(SeckillOrder::getUserId, userId)) > 0;
    }

    public record ReconciliationResult(int scanned, int resolved, int remaining) {
    }
}
