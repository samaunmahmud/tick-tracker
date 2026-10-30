package com.samaun.ticktracker.common;

public class DuplicateSightingException extends RuntimeException {
    public DuplicateSightingException() {
        super("A sighting of this species in this city at this time already exists");
    }
}
