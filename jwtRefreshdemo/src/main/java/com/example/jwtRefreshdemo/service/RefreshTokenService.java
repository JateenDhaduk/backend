package com.example.jwtRefreshdemo.service;

import com.example.jwtRefreshdemo.dto.LoginResult;
import com.example.jwtRefreshdemo.dto.UserLoginResponse;
import com.example.jwtRefreshdemo.entity.RefreshToken;
import com.example.jwtRefreshdemo.entity.UserEntity;
import com.example.jwtRefreshdemo.repoitory.RefreshTokenRepository;
import com.example.jwtRefreshdemo.security.CustomUserDetailsService;
import com.example.jwtRefreshdemo.security.JwtRefreshTokenUtils;
import com.example.jwtRefreshdemo.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtRefreshTokenUtils jwtRefreshTokenUtils;
    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshTokenExpiryMs;


    public String createRefreshToken(UserEntity user){
        String rawToken = jwtRefreshTokenUtils.generateRawToken();
        String hashToken = jwtRefreshTokenUtils.hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashToken)
                .user(user)
                .revoked(false)
                .familyId(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plus(refreshTokenExpiryMs, ChronoUnit.MILLIS))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public LoginResult rotateToken(String incomingRawToken){
        String hashToken = jwtRefreshTokenUtils.hashToken(incomingRawToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hashToken)
                .orElseThrow(() -> new RuntimeException("Invalid Token"));

        if(existing.isRevoked()){
            refreshTokenRepository.revokeAllByFamilyId(existing.getFamilyId());
        }
        if(existing.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new RuntimeException("Refresh token expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

       String newRawToken = jwtRefreshTokenUtils.generateRawToken();
       RefreshToken newToken = RefreshToken.builder()
               .tokenHash(newRawToken)
               .revoked(false)
               .familyId(existing.getFamilyId())
               .expiresAt(LocalDateTime.now().plus(refreshTokenExpiryMs,ChronoUnit.MILLIS))
               .user(existing.getUser())
               .build();

       refreshTokenRepository.save(newToken);

       UserEntity user  = existing.getUser();
       UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
       String newAccessToken = jwtUtils.generateAccessToken(userDetails);

        UserLoginResponse loginResponse = UserLoginResponse.builder()
                .userName(user.getUsername())
                .accessToken(newAccessToken)
                .role(user.getRole().toString())
                .email(user.getEmail())
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getJwtExpirationMils() / 1000)
                .build();

        return new LoginResult(loginResponse,newRawToken);

    }
    @Transactional
    public void revokeAllForUser(Long userId){
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    public RefreshToken findByRawToken(String refreshToken) {

        String hashToken = jwtRefreshTokenUtils.hashToken(refreshToken);
        return  refreshTokenRepository.findByTokenHash(hashToken)
                .orElseThrow(() -> new RuntimeException("Invalid Token"));

    }


}
