package com.example.seckilldemo.mq;

import com.example.seckilldemo.dto.SeckillMessage;
import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import com.example.seckilldemo.service.SeckillFailureService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeckillConsumerTest {

    private SeckillFailureService failureService;
    private Channel channel;
    private SeckillConsumer consumer;

    @BeforeEach
    void setUp() {
        failureService = mock(SeckillFailureService.class);
        channel = mock(Channel.class);
        consumer = new SeckillConsumer(
                mock(SeckillActivityMapper.class),
                mock(SeckillOrderMapper.class),
                mock(PlatformTransactionManager.class),
                failureService);
    }

    @Test
    void deadLetterIsAcknowledgedOnlyAfterPersistence() throws Exception {
        SeckillMessage message = new SeckillMessage(1L, 2L);

        consumer.handleDeadLetter(message, channel, 7L);

        var ordered = inOrder(failureService, channel);
        ordered.verify(failureService).recordDeadLetter(
                message, "消费者处理失败，消息被拒绝进入死信队列");
        ordered.verify(channel).basicAck(7L, false);
    }

    @Test
    void persistenceFailureRequeuesDeadLetter() throws Exception {
        SeckillMessage message = new SeckillMessage(1L, 2L);
        doThrow(new RuntimeException("database unavailable"))
                .when(failureService)
                .recordDeadLetter(message, "消费者处理失败，消息被拒绝进入死信队列");

        consumer.handleDeadLetter(message, channel, 8L);

        verify(channel).basicNack(8L, false, true);
    }
}
