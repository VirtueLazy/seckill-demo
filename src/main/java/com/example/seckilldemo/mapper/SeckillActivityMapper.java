package com.example.seckilldemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.seckilldemo.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    @Update("update seckill_activity set seckill_stock = seckill_stock - 1 where id = #{id} and seckill_stock > 0")
    int deductStock(@Param("id") Long activityId);
}
