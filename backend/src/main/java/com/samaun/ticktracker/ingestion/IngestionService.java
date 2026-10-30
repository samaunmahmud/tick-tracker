package com.samaun.ticktracker.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.samaun.ticktracker.common.SyncInProgressException;
import com.samaun.ticktracker.ingestion.NormalizationResult.Accepted;
import com.samaun.ticktracker.ingestion.NormalizationResult.Rejected;
import com.samaun.ticktracker.sighting.Sighting;
import com.samaun.ticktracker.sighting.SightingRepository;
import com.samaun.ticktracker.sync.RejectedRecord;
import com.samaun.ticktracker.sync.RejectedRecordRepository;
import com.samaun.ticktracker.sync.SyncRun;
import com.samaun.ticktracker.sync.SyncRunRepository;
import com.samaun.ticktracker.sync.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Service
public class IngestionService {

    static final int CHUNK_SIZE = 500;

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final ElancoClient elancoClient;
    private final SightingNormalizer normalizer;
    private final SightingRepository sightingRepository;
    private final SyncRunRepository syncRunRepository;
    private final RejectedRecordRepository rejectedRecordRepository;
    private final Clock clock;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    public IngestionService(ElancoClient elancoClient,
                            SightingNormalizer normalizer,
                            SightingRepository sightingRepository,
                            SyncRunRepository syncRunRepository,
                            RejectedRecordRepository rejectedRecordRepository,
                            Clock clock,
                            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.elancoClient = elancoClient;
        this.normalizer = normalizer;
        this.sightingRepository = sightingRepository;
        this.syncRunRepository = syncRunRepository;
        this.rejectedRecordRepository = rejectedRecordRepository;
        this.clock = clock;
        this.taskExecutor = taskExecutor;
    }

    public SyncRun runSync(TriggerType triggerType) {
        claimSyncLock();
        try {
            return complete(startRun(triggerType));
        } finally {
            syncRunning.set(false);
        }
    }

    public Long startSyncInBackground(TriggerType triggerType) {
        claimSyncLock();
        try {
            SyncRun run = startRun(triggerType);
            Long runId = run.getId();
            taskExecutor.execute(() -> {
                try {
                    complete(run);
                } finally {
                    syncRunning.set(false);
                }
            });
            return runId;
        } catch (RuntimeException e) {
            syncRunning.set(false);
            throw e;
        }
    }

    private void claimSyncLock() {
        if (!syncRunning.compareAndSet(false, true)) {
            throw new SyncInProgressException();
        }
    }

    private SyncRun startRun(TriggerType triggerType) {
        SyncRun run = syncRunRepository.save(SyncRun.start(triggerType, clock.instant()));
        log.info("Sync {} started ({})", run.getId(), triggerType);
        return run;
    }

    private SyncRun complete(SyncRun run) {
        try {
            importRecords(run, elancoClient.fetchAllSightings());
            run.succeed(clock.instant());
            log.info("Sync {} finished: fetched={} inserted={} duplicates={} rejected={} corrected={}",
                    run.getId(), run.getFetched(), run.getInserted(), run.getDuplicates(),
                    run.getRejected(), run.getCorrected());
        } catch (ExternalApiException e) {
            log.warn("Sync {} failed: {}", run.getId(), e.getMessage());
            run.fail(e.getMessage(), clock.instant());
        } catch (RuntimeException e) {
            log.error("Sync {} failed unexpectedly", run.getId(), e);
            run.fail("Unexpected error: " + e.getMessage(), clock.instant());
        }
        return syncRunRepository.save(run);
    }

    private void importRecords(SyncRun run, List<JsonNode> records) {
        Instant now = clock.instant();
        List<RejectedRecord> rejects = new ArrayList<>();
        List<NormalizedSighting> uniqueInBatch = new ArrayList<>();
        Set<String> seenExternalIds = new HashSet<>();
        Set<String> seenContentHashes = new HashSet<>();
        int corrected = 0;
        int duplicates = 0;

        for (JsonNode raw : records) {
            switch (normalizer.normalize(raw)) {
                case Rejected rejected -> rejects.add(
                        new RejectedRecord(run.getId(), externalIdOf(raw), raw.toString(), rejected.reason(), now));
                case Accepted accepted -> {
                    if (accepted.wasCorrected()) {
                        corrected++;
                    }
                    NormalizedSighting sighting = accepted.sighting();
                    if (alreadySeen(sighting, seenExternalIds, seenContentHashes)) {
                        duplicates++;
                    } else {
                        uniqueInBatch.add(sighting);
                    }
                }
            }
        }

        List<NormalizedSighting> newSightings = withoutExistingInDatabase(uniqueInBatch);
        duplicates += uniqueInBatch.size() - newSightings.size();

        run.recordProcessed(records.size(), duplicates, rejects.size(), corrected);
        rejectedRecordRepository.saveAll(rejects);

        for (List<NormalizedSighting> chunk : chunks(newSightings)) {
            List<Sighting> entities = chunk.stream()
                    .map(sighting -> Sighting.fromElanco(sighting, now))
                    .toList();
            sightingRepository.saveAll(entities);
            run.addInserted(entities.size());
        }
    }

    private boolean alreadySeen(NormalizedSighting sighting, Set<String> seenExternalIds, Set<String> seenContentHashes) {
        boolean idSeen = sighting.externalId() != null && !seenExternalIds.add(sighting.externalId());
        boolean contentSeen = !seenContentHashes.add(sighting.contentHash());
        return idSeen || contentSeen;
    }

    private List<NormalizedSighting> withoutExistingInDatabase(List<NormalizedSighting> sightings) {
        List<String> externalIds = sightings.stream()
                .map(NormalizedSighting::externalId)
                .filter(id -> id != null)
                .toList();
        List<String> contentHashes = sightings.stream()
                .map(NormalizedSighting::contentHash)
                .toList();

        Set<String> existingExternalIds = findExisting(externalIds, sightingRepository::findExistingExternalIds);
        Set<String> existingContentHashes = findExisting(contentHashes, sightingRepository::findExistingContentHashes);

        return sightings.stream()
                .filter(s -> s.externalId() == null || !existingExternalIds.contains(s.externalId()))
                .filter(s -> !existingContentHashes.contains(s.contentHash()))
                .toList();
    }

    private Set<String> findExisting(List<String> values, Function<List<String>, Set<String>> lookup) {
        Set<String> existing = new HashSet<>();
        for (List<String> chunk : chunks(values)) {
            existing.addAll(lookup.apply(chunk));
        }
        return existing;
    }

    private static <T> List<List<T>> chunks(List<T> items) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < items.size(); start += CHUNK_SIZE) {
            chunks.add(items.subList(start, Math.min(start + CHUNK_SIZE, items.size())));
        }
        return chunks;
    }

    private static String externalIdOf(JsonNode raw) {
        JsonNode id = raw.path("id");
        if (!id.isTextual() || id.asText().isBlank()) {
            return null;
        }
        String value = id.asText().trim();
        return value.length() > SightingNormalizer.MAX_EXTERNAL_ID_LENGTH
                ? value.substring(0, SightingNormalizer.MAX_EXTERNAL_ID_LENGTH)
                : value;
    }
}
