package com.example.seckilldemo.service;

import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.entity.FailedSeckillMessage;
import com.example.seckilldemo.entity.SeckillOrder;
import com.example.seckilldemo.exception.BusinessException;
import com.example.seckilldemo.mapper.FailedSeckillMessageMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import com.example.seckilldemo.mq.SeckillMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeckillFailureServiceTest {
    private FailedSeckillMessageMapper failedMessageMapper;
    private SeckillOrderMapper orderMapper;
    private SeckillMessagePublisher publisher;
    private SeckillFailureService service;

    @BeforeEach
    void setUp() {
        failedMessageMapper = mock(FailedSeckillMessageMapper.class);
        orderMapper = mock(SeckillOrderMapper.class);
        publisher = mock(SeckillMessagePublisher.class);
        service = new SeckillFailureService(failedMessageMapper, orderMapper, publisher);
    }

    @Test
    void deadLetterIsPersistedByBusinessKey() {
        service.recordDeadLetter(new SeckillMessage(1L, 2L), "consumer failed");

        verify(failedMessageMapper).recordFailure(1L, 2L, "consumer failed");
    }

    @Test
    void pendingFailureCanBeClaimedAndReplayed() {
        FailedSeckillMessage failed = failure(5L, 1L, 2L);
        when(failedMessageMapper.selectById(5L)).thenReturn(failed);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(failedMessageMapper.claimReplay(5L)).thenReturn(1);
        ArgumentCaptor<SeckillMessage> messageCaptor = ArgumentCaptor.forClass(SeckillMessage.class);

        service.replay(5L);

        verify(publisher).publish(messageCaptor.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertEquals(1L, messageCaptor.getValue().getActivityId());
        assertEquals(2L, messageCaptor.getValue().getUserId());
        assertEquals(5L, messageCaptor.getValue().getFailedMessageId());
    }

    @Test
    void replayIsRateLimitedByAtomicClaim() {
        FailedSeckillMessage failed = failure(5L, 1L, 2L);
        when(failedMessageMapper.selectById(5L)).thenReturn(failed);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(failedMessageMapper.claimReplay(5L)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.replay(5L));

        assertEquals("重放过于频繁，请30秒后重试", exception.getMessage());
        verify(publisher, never()).publish(any(), anyBoolean());
    }

    @Test
    void existingOrderIsReconciledInsteadOfReplayed() {
        FailedSeckillMessage failed = failure(5L, 1L, 2L);
        when(failedMessageMapper.selectById(5L)).thenReturn(failed);
        when(orderMapper.selectCount(any())).thenReturn(1L);

        service.replay(5L);

        verify(failedMessageMapper).markResolved(5L);
        verify(publisher, never()).publish(any(), anyBoolean());
    }

    @Test
    void reconciliationClosesFailuresWhoseOrdersAlreadyExist() {
        FailedSeckillMessage first = failure(5L, 1L, 2L);
        FailedSeckillMessage second = failure(6L, 1L, 3L);
        when(failedMessageMapper.selectList(any())).thenReturn(List.of(first, second));
        when(orderMapper.selectCount(any())).thenReturn(1L, 0L);
        when(failedMessageMapper.markResolved(5L)).thenReturn(1);

        SeckillFailureService.ReconciliationResult result = service.reconcile();

        assertEquals(new SeckillFailureService.ReconciliationResult(2, 1, 1), result);
        verify(failedMessageMapper).markResolved(5L);
        verify(failedMessageMapper, never()).markResolved(6L);
    }

    private FailedSeckillMessage failure(Long id, Long activityId, Long userId) {
        FailedSeckillMessage failed = new FailedSeckillMessage();
        failed.setId(id);
        failed.setActivityId(activityId);
        failed.setUserId(userId);
        failed.setStatus(SeckillFailureService.PENDING);
        return failed;
    }
}
