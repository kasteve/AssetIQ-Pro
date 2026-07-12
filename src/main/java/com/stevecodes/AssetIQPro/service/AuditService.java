package com.stevecodes.AssetIQPro.service;

import com.stevecodes.AssetIQPro.entity.AuditLog;
import com.stevecodes.AssetIQPro.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logAction(String action, String details, Long userId) {
        try {
            AuditLog log = new AuditLog();
            log.setAction(action);
            log.setDetails(details);
            log.setUserId(userId);
            log.setTimestamp(LocalDateTime.now());
            log.setCreatedAt(LocalDateTime.now());
            auditLogRepository.save(log);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    public List<AuditLog> getLast100Logs() {
        Pageable pageable = PageRequest.of(0, 100);
        return auditLogRepository.findLast100Logs(pageable);
    }
}