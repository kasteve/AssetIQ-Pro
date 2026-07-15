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
    private String staffId;
    private String username;
    private String email;
    private String password;
    private String passwordHash;
    private String fullName;
    private String department;
    private Integer departmentId;
    private Long employeeId;
    private Long lineManagerId;
    private String phoneNumber;
    private String role = "EMPLOYEE";
    private boolean active = true;
    private boolean blocked = false;
    private boolean firstLogin = true;
    private boolean mustChangePassword = true;
    private String userType;
    private List<String> permissions = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime lastPasswordChanged;
    private Long createdBy;
    private String resetPasswordUrl;
}