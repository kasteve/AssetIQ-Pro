package com.stevecodes.AssetIQPro.repository;

import com.stevecodes.AssetIQPro.entity.PasswordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByUserIdOrderByChangedAtDesc(Long userId);

    void deleteByUserIdAndHistoryIdNotIn(Long userId, List<Long> historyIdsToKeep);
}