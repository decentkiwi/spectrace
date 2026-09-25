package com.spectrace.bank.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Stateless JWT utility: signs and validates HS256 tokens.
 * Subject is the account number; a single "role" claim carries USER or ADMIN.
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiryMillis;

    public JwtUtil(
            @Value("${bank.jwt.secret}") String secret,
            @Value("${bank.jwt.expiry-seconds:3600}") long expirySeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMillis = expirySeconds * 1000L;
    }

    public String generate(String accountNumber) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(accountNumber)
                .claim("role", "USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMillis))
                .signWith(key)
                .compact();
    }

    /**
     * @return the account number (subject) if the token is valid, otherwise throws JwtException.
     */
    public String validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /** Returns true if the token parses and has not expired. */
    public boolean isValid(String token) {
        try {
            validate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
