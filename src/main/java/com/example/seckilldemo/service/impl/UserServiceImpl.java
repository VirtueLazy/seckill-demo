package com.example.seckilldemo.service.impl;

import com.example.seckilldemo.entity.User;
import com.example.seckilldemo.mapper.UserMapper;
import com.example.seckilldemo.service.UserService;
import com.example.seckilldemo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean register(String username, String password, String nickname) {
        User existuser = userMapper.selectByUsername(username);
        if (existuser != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);

        return userMapper.insert(user) > 0;
    }

    @Override
    public String login(String username, String password) {
        User user = userMapper.selectByUsername(username);
        if(user == null){
            throw new RuntimeException("用户不存在");
        }

        if(!passwordEncoder.matches(password,user.getPassword())){
            throw new RuntimeException("密码错误");
        }

        return jwtUtil.generateToken(user.getId(),user.getUsername());
    }
}
