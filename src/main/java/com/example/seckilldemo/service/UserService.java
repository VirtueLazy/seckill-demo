package com.example.seckilldemo.service;

public interface UserService {

    boolean register(String username, String password, String nickname);

    String login(String username,String password);
}
