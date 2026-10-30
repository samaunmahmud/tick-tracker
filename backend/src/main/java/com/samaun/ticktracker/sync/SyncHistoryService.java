package com.samaun.ticktracker.sync;

import com.samaun.ticktracker.common.NotFoundException;
import com.samaun.ticktracker.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncHistoryService {

    private final SyncRunRepository syncRunRepository;
    private final RejectedRecordRepository rejectedRecordRepository;

    public SyncHistoryService(SyncRunRepository syncRunRepository, RejectedRecordRepository rejectedRecordRepository) {
        this.syncRunRepository = syncRunRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
    }

    public List<SyncRunResponse> latestRuns() {
        return syncRunRepository.findTop20ByOrderByStartedAtDesc().stream()
                .map(SyncRunResponse::from)
                .toList();
    }

    public SyncRunResponse findRun(Long id) {
        return syncRunRepository.findById(id)
                .map(SyncRunResponse::from)
                .orElseThrow(() -> new NotFoundException("No sync run with id " + id));
    }

    public PageResponse<RejectedRecordResponse> rejectedRecords(int page, int size) {
        PageRequest newestFirst = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return PageResponse.from(rejectedRecordRepository.findAll(newestFirst).map(RejectedRecordResponse::from));
    }
}
