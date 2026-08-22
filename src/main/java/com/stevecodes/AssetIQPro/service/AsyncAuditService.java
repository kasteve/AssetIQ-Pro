package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.AuditLog;
import com.stevecodes.AssetIQPro.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncAuditService {

    private final AuditLogRepository auditLogRepository;

    @Async("auditTaskExecutor")
    public void logActionAsync(String action, String details, Long userId) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setUserId(userId);
            log.setTimestamp(LocalDateTime.now());
            log.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(log);
            log.debug("✅ Audit log saved: {}", action);
        } catch (Exception e) {
            log.error("❌ Failed to save audit log: {}", e.getMessage());
        }
    }

    @Async("auditTaskExecutor")
    public void logActionAsync(String action, String details, Long userId, String ipAddress) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setUserId(userId);
            log.setIpAddress(ipAddress);
            log.setTimestamp(LocalDateTime.now());
            log.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(log);
            log.debug("✅ Audit log saved with IP: {}", action);
        } catch (Exception e) {
            log.error("❌ Failed to save audit log: {}", e.getMessage());
        }
    }
}