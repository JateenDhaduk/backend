package com.example.jwtRefreshdemo.security;

import com.example.jwtRefreshdemo.entity.UserEntity;
import io.jsonwebtoken.io.Decoders;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
@Getter
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;
    @Value("${jwt.expiration-ms}")
    private int jwtExpirationMils;

    public String generateAccessToken(UserDetails userDetails){
        Map<String,Object> extraClaims = new HashMap<>();

        String userRole = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("UNASSIGNED");

        extraClaims.put("role" , userRole);
        return buildAccessToken(extraClaims,userDetails);
    }

    public String buildAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date( new Date().getTime() + jwtExpirationMils))
                .signWith(Key())
                .compact();

    }

    public String buildRefreshToken(String username){
        String refreshToken = UUID.randomUUID().toString();
        return refreshToken;
    }

    public SecretKey Key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public <T> T extraClaims(String token, Function<Claims,T> claimsResolver){
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return  Jwts.parser()
                .verifyWith(Key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserName(String accessToken){
        return extraClaims(accessToken,Claims::getSubject);
    }

    public boolean isTokenExpired(String accessToken){
        return extractExpiration(accessToken).before(new Date());
    }

    private Date extractExpiration(String accessToken) {
        return extraClaims(accessToken ,Claims::getExpiration);
    }

    public boolean isTokenValid(String accessToken,UserDetails userDetails)throws Exception{
        final String username = extractUserName(accessToken);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(accessToken);
    }
}
