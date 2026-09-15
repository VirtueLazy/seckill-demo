package com.example.seckilldemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckilldemo.entity.FailedSeckillMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FailedSeckillMessageMapper extends BaseMapper<FailedSeckillMessage> {

    @Insert("""
            INSERT INTO seckill_failed_message
                (activity_id, user_id, status, retry_count, last_error)
            VALUES
                (#{activityId}, #{userId}, 'PENDING', 0, #{lastError})
            ON DUPLICATE KEY UPDATE
                status = IF(status = 'RESOLVED', status, 'PENDING'),
                last_error = VALUES(last_error),
                updated_time = CURRENT_TIMESTAMP
            """)
    int recordFailure(@Param("activityId") Long activityId,
                      @Param("userId") Long userId,
                      @Param("lastError") String lastError);

    @Update("""
            UPDATE seckill_failed_message
            SET retry_count = retry_count + 1,
                last_replay_time = CURRENT_TIMESTAMP,
                updated_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status = 'PENDING'
              AND (last_replay_time IS NULL OR last_replay_time < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 SECOND))
            """)
    int claimReplay(@Param("id") Long id);

    @Update("""
            UPDATE seckill_failed_message
            SET status = 'RESOLVED', updated_time = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status <> 'RESOLVED'
            """)
    int markResolved(@Param("id") Long id);
}
