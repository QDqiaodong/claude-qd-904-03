package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tea.factory.domain.CleaningHandoverLog;

public interface CleaningHandoverLogRepository extends JpaRepository<CleaningHandoverLog, Long> {

    List<CleaningHandoverLog> findByHandoverNoOrderByCreatedAtAsc(String handoverNo);
}
