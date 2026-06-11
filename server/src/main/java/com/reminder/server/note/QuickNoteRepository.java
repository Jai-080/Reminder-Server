package com.reminder.server.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface QuickNoteRepository extends JpaRepository<QuickNote, Long> {
    List<QuickNote> findByUserIdAndDeletedFalseOrderByPositionAsc(Long userId);
    Page<QuickNote> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
    Optional<QuickNote> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
}
