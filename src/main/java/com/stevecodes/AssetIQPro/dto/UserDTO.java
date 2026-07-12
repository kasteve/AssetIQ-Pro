package com.stevecodes.AssetIQPro.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class UserDTO {

    private Long userId;
    private String username;
    private String email;
    private String password;
    private String fullName;
    private String department;
    private boolean active = true;
    private boolean blocked = false;
    private boolean firstLogin = true;
    private boolean mustChangePassword = true;
    private String userType;
    private List<String> permissions = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime lastPasswordChanged;

    // For creation
    private Long createdBy;
    private String resetPasswordUrl;
}