package com.example.seckilldemo.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.seckilldemo.entity.FailedSeckillMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class FailedSeckillMessageMapperTest {

    private static final long ACTIVITY_ID = 9_000_001L;
    private static final long USER_ID = 9_000_002L;

    @Autowired
    private FailedSeckillMessageMapper mapper;

    @AfterEach
    void cleanUp() {
        mapper.delete(query());
    }

    @Test
    void failureUpsertAndReplayClaimAreIdempotent() {
        assertEquals(1, mapper.recordFailure(ACTIVITY_ID, USER_ID, "first failure"));
        assertEquals(2, mapper.recordFailure(ACTIVITY_ID, USER_ID, "latest failure"));

        FailedSeckillMessage failed = mapper.selectOne(query());
        assertNotNull(failed);
        assertEquals("PENDING", failed.getStatus());
        assertEquals("latest failure", failed.getLastError());
        assertEquals(0, failed.getRetryCount());

        assertEquals(1, mapper.claimReplay(failed.getId()));
        assertEquals(0, mapper.claimReplay(failed.getId()));

        assertEquals(1, mapper.markResolved(failed.getId()));
        assertEquals(2, mapper.recordFailure(ACTIVITY_ID, USER_ID, "late duplicate dead letter"));

        FailedSeckillMessage resolved = mapper.selectById(failed.getId());
        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals(1, resolved.getRetryCount());
    }

    private LambdaQueryWrapper<FailedSeckillMessage> query() {
        return new LambdaQueryWrapper<FailedSeckillMessage>()
                .eq(FailedSeckillMessage::getActivityId, ACTIVITY_ID)
                .eq(FailedSeckillMessage::getUserId, USER_ID);
    }
}
