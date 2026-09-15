package com.example.seckilldemo.mq;

import com.example.seckilldemo.service.SeckillReservationCompensator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SeckillPublishFailureHandlerTest {
    private final SeckillReservationCompensator compensator = mock(SeckillReservationCompensator.class);
    private final SeckillPublishFailureHandler handler = new SeckillPublishFailureHandler(compensator);

    @Test
    void brokerNackTriggersCompensation() {
        SeckillCorrelationData correlationData = new SeckillCorrelationData(1L, 2L);

        handler.confirm(correlationData, false, "broker unavailable");

        verify(compensator).compensate(1L, 2L, "broker-nack: broker unavailable");
    }

    @Test
    void brokerAckDoesNotCompensate() {
        handler.confirm(new SeckillCorrelationData(1L, 2L), true, null);

        verify(compensator, never()).compensate(1L, 2L, "broker-nack: null");
    }

    @Test
    void replayNackKeepsPendingRecordWithoutReleasingReservation() {
        handler.confirm(new SeckillCorrelationData(1L, 2L, false), false, "broker unavailable");

        verify(compensator, never()).compensate(1L, 2L, "broker-nack: broker unavailable");
    }

    @Test
    void unroutableMessageTriggersCompensationFromHeaders() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(SeckillCorrelationData.HEADER_ACTIVITY_ID, 3L);
        properties.setHeader(SeckillCorrelationData.HEADER_USER_ID, 4L);
        ReturnedMessage returned = new ReturnedMessage(
                new Message(new byte[0], properties),
                312,
                "NO_ROUTE",
                "seckill.exchange",
                "wrong.key");

        handler.returnedMessage(returned);

        verify(compensator).compensate(3L, 4L, "unroutable: NO_ROUTE");
    }

    @Test
    void unroutableReplayKeepsPendingRecordWithoutReleasingReservation() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(SeckillCorrelationData.HEADER_ACTIVITY_ID, 3L);
        properties.setHeader(SeckillCorrelationData.HEADER_USER_ID, 4L);
        properties.setHeader(SeckillCorrelationData.HEADER_COMPENSATE_ON_FAILURE, false);
        ReturnedMessage returned = new ReturnedMessage(
                new Message(new byte[0], properties),
                312,
                "NO_ROUTE",
                "seckill.exchange",
                "wrong.key");

        handler.returnedMessage(returned);

        verify(compensator, never()).compensate(3L, 4L, "unroutable: NO_ROUTE");
    }
}
