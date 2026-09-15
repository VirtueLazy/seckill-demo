package com.example.seckilldemo.controller;

import com.example.seckilldemo.entity.FailedSeckillMessage;
import com.example.seckilldemo.service.SeckillFailureService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seckill/failures")
public class SeckillFailureController {
    private final SeckillFailureService failureService;

    public SeckillFailureController(SeckillFailureService failureService) {
        this.failureService = failureService;
    }

    @GetMapping
    public List<FailedSeckillMessage> listPending() {
        return failureService.listPending();
    }

    @PostMapping("/{failedMessageId}/replay")
    public String replay(@PathVariable Long failedMessageId) {
        failureService.replay(failedMessageId);
        return "重放请求已提交";
    }

    @PostMapping("/reconcile")
    public SeckillFailureService.ReconciliationResult reconcile() {
        return failureService.reconcile();
    }
}
