package com.example.seckilldemo.controller;

import com.example.seckilldemo.dto.LoginRequest;
import com.example.seckilldemo.dto.RegisterRequest;
import com.example.seckilldemo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public boolean register(@RequestBody RegisterRequest request){
        return userService.register(request.getUsername(),request.getPassword(),request.getNickname());

    }

    @PostMapping("/login")
    public String login(@RequestBody LoginRequest loginRequest){
        return userService.login(loginRequest.getUsername(),loginRequest.getPassword());
    }

}
