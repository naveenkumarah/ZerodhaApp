package com.example.journal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findByUserIdOrderByIdDesc(Long userId);
}

