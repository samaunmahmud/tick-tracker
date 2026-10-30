package com.samaun.ticktracker.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop20ByOrderByStartedAtDesc();

    Optional<SyncRun> findFirstByOrderByStartedAtDesc();
}
