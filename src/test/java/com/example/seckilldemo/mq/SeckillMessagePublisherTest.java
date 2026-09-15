package com.example.seckilldemo.mq;

import com.example.seckilldemo.config.RabbitMQConfig;
import com.example.seckilldemo.dto.SeckillMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeckillMessagePublisherTest {

    @Test
    void replayMessageCarriesAuditHeadersAndDoesNotReleaseReservationOnPublishFailure() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        SeckillMessagePublisher publisher = new SeckillMessagePublisher(rabbitTemplate);
        SeckillMessage seckillMessage = new SeckillMessage(10L, 20L, 30L);
        ArgumentCaptor<MessagePostProcessor> processorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor =
                ArgumentCaptor.forClass(CorrelationData.class);

        publisher.publish(seckillMessage, false);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.SECKILL_EXCHANGE),
                eq(RabbitMQConfig.SECKILL_ROUTING_KEY),
                eq(seckillMessage),
                processorCaptor.capture(),
                correlationCaptor.capture());

        Message message = processorCaptor.getValue().postProcessMessage(
                new Message(new byte[0], new MessageProperties()));
        assertEquals(Long.valueOf(10L), message.getMessageProperties().getHeader(
                SeckillCorrelationData.HEADER_ACTIVITY_ID));
        assertEquals(Long.valueOf(20L), message.getMessageProperties().getHeader(
                SeckillCorrelationData.HEADER_USER_ID));
        assertEquals(false, message.getMessageProperties().getHeader(
                SeckillCorrelationData.HEADER_COMPENSATE_ON_FAILURE));
        assertFalse(((SeckillCorrelationData) correlationCaptor.getValue()).isCompensateOnFailure());
    }
}
