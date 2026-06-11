package com.reminder.server.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuickNoteRepository extends JpaRepository<QuickNote, Long> {
}
