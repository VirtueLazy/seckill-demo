package com.example.seckilldemo.controller;

import com.example.seckilldemo.mapper.SeckillActivityMapper;
import com.example.seckilldemo.mapper.SeckillOrderMapper;
import com.example.seckilldemo.security.AuthenticatedUser;
import com.example.seckilldemo.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;

    @PostMapping("/{activityId}")
    public boolean doSeckill(@PathVariable Long activityId, @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return seckillService.doSeckill(activityId, authenticatedUser.id());
    }

    @PostMapping("/preload/{activityId}")
    public String preload(@PathVariable Long activityId) {
        seckillService.preloadStock(activityId);
        return "库存预热完成";
    }

}
