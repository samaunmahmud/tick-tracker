package com.samaun.ticktracker.ingestion;

import com.samaun.ticktracker.common.SyncInProgressException;
import com.samaun.ticktracker.config.ElancoApiProperties;
import com.samaun.ticktracker.sync.TriggerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final IngestionService ingestionService;
    private final ElancoApiProperties properties;

    public SyncScheduler(IngestionService ingestionService, ElancoApiProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (properties.syncOnStartup()) {
            runSync(TriggerType.STARTUP);
        }
    }

    @Scheduled(cron = "${app.elanco.sync-cron}")
    public void syncOnSchedule() {
        runSync(TriggerType.SCHEDULED);
    }

    private void runSync(TriggerType triggerType) {
        try {
            ingestionService.runSync(triggerType);
        } catch (SyncInProgressException e) {
            log.info("Skipped {} sync because another sync is still running", triggerType);
        }
    }
}
