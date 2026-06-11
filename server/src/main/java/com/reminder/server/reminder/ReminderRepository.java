package com.reminder.server.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByUserIdAndDeletedFalse(Long userId);
    List<Reminder> findByUserIdAndDeletedFalseAndIsExpired(Long userId, Boolean isExpired);
    Page<Reminder> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
    Page<Reminder> findByUserIdAndDeletedFalseAndIsExpired(Long userId, Boolean isExpired, Pageable pageable);
    Optional<Reminder> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
}
