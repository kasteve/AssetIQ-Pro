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

import java.util.Collection;
import java.util.stream.Collectors;

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

        log.info("User found: {}, active: {}, blocked: {}",
                appUser.getUsername(), appUser.isActive(), appUser.isBlocked());

        return new User(
                appUser.getUsername(),
                appUser.getPasswordHash(),
                appUser.isActive(),
                true,
                true,
                !appUser.isBlocked(),
                getAuthorities(appUser)
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(AppUser appUser) {
        return appUser.getPermissions().stream()
                .map(Permission::getPermissionName)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}