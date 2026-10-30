package com.samaun.ticktracker.ingestion;

import java.time.Duration;

@FunctionalInterface
public interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;
}
