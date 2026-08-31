package com.example.seckilldemo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {
    private static final String SECRET = "seckill-demo-jwt-secret-key-must-be-long-enougn-1234567890";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static final long EXPIRE_TIME = 2 * 60 * 60 * 1000;

    public String generateToken(Long userId, String username){
        return Jwts.builder()
                .claim("userId",userId)
                .claim("username",username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis()+EXPIRE_TIME))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token){
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
