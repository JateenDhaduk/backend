package com.example.jwtRefreshdemo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UserLoginRequest {

    @NotBlank(message  = "Username is mandatory")
    private String username;

    @NotBlank(message = "Password is mandatory")
    private String password;

}
