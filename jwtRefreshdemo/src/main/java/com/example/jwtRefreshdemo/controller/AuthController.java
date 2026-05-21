package com.example.jwtRefreshdemo.controller;

import com.example.jwtRefreshdemo.dto.*;
import com.example.jwtRefreshdemo.entity.RefreshToken;
import com.example.jwtRefreshdemo.service.RefreshTokenService;
import com.example.jwtRefreshdemo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth/user")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    @Operation(summary = "create a new user" , description = "Endpoint for user register")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Email or username already exists")
    })
    public ResponseEntity<ApiUserResponse<UserRegisterResponse>> registerUser(@Valid @RequestBody UserRegisterRequest request){
        UserRegisterResponse response = userService.registerUser(request);
        return ResponseEntity.ok(ApiUserResponse.success(200,"Account created Successfully",response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login a user", description = "Endpoint to login a user with email and password.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User logged in successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Invalid credentials")
    })
    public ResponseEntity<ApiUserResponse<UserLoginResponse>> loginUser(@Valid @RequestBody UserLoginRequest request) {
        // Placeholder for user login logic
        LoginResult result = userService.loginUser(request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,buildRefreshCookie(result.getRawRefreshToken()).toString())
                .body(ApiUserResponse.success("Login successful" , result.getUserLoginResponse()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Rotates refresh token and issues new access token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<ApiUserResponse<UserLoginResponse>> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null) {
            throw new RuntimeException("Refresh token cookie is missing");
        }

        LoginResult result = refreshTokenService.rotateToken(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.getRawRefreshToken()).toString())
                .body(ApiUserResponse.success("Token refreshed successfully", result.getUserLoginResponse()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes all refresh tokens for the user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<ApiUserResponse<Void>> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        if (refreshToken != null) {
            // resolve userId from token and revoke
            RefreshToken token = refreshTokenService.findByRawToken(refreshToken);
            refreshTokenService.revokeAllForUser(token.getUser().getId());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiUserResponse.success("Logged out successfully", null));
    }
    private ResponseCookie buildRefreshCookie(String rawToken) {
        return ResponseCookie.from("refreshToken", rawToken)
                .httpOnly(true)
                .secure(true)
                .path("/auth/user/refresh")
                .maxAge(Duration.ofDays(30))
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/auth/user/refresh")
                .maxAge(Duration.ZERO)          // immediately expire
                .sameSite("Strict")
                .build();
    }

}
