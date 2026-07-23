package com.stevecodes.AssetIQPro.security;

import com.stevecodes.AssetIQPro.entity.AppUser;
import com.stevecodes.AssetIQPro.entity.Permission;
import com.stevecodes.AssetIQPro.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("Loading user by username: {}", username);

        AppUser appUser = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        log.info("User found: {}, active: {}, blocked: {}, role: {}, permissions: {}",
                appUser.getUsername(), appUser.isActive(), appUser.isBlocked(),
                appUser.getRole(), appUser.getPermissions().size());

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // Add permissions as authorities
        for (Permission permission : appUser.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.getPermissionName()));
            log.debug("Added permission authority: {}", permission.getPermissionName());
        }

        // Add role as authority (ROLE_ prefix for Spring Security)
        if (appUser.getRole() != null && !appUser.getRole().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + appUser.getRole()));
            log.debug("Added role authority: ROLE_{}", appUser.getRole());
        }

        // Add default ROLE_USER for all authenticated users
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        log.info("User {} has {} authorities", username, authorities.size());

        return new User(
                appUser.getUsername(),
                appUser.getPasswordHash(),
                appUser.isActive(),
                true,
                true,
                !appUser.isBlocked(),
                authorities
        );
    }
}