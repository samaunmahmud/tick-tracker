package com.samaun.ticktracker.reference;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum UkCity {

    BIRMINGHAM("Birmingham", "West Midlands", 52.4862, -1.8904),
    BRISTOL("Bristol", "South West", 51.4545, -2.5879),
    CARDIFF("Cardiff", "Wales", 51.4816, -3.1791),
    EDINBURGH("Edinburgh", "Scotland", 55.9533, -3.1883),
    GLASGOW("Glasgow", "Scotland", 55.8642, -4.2518),
    LEEDS("Leeds", "Yorkshire and the Humber", 53.8008, -1.5491),
    LEICESTER("Leicester", "East Midlands", 52.6369, -1.1398),
    LIVERPOOL("Liverpool", "North West", 53.4084, -2.9916),
    LONDON("London", "London", 51.5072, -0.1276),
    MANCHESTER("Manchester", "North West", 53.4808, -2.2426),
    NEWCASTLE("Newcastle", "North East", 54.9783, -1.6178, "Newcastle upon Tyne"),
    NOTTINGHAM("Nottingham", "East Midlands", 52.9548, -1.1581),
    SHEFFIELD("Sheffield", "Yorkshire and the Humber", 53.3811, -1.4701),
    SOUTHAMPTON("Southampton", "South East", 50.9097, -1.4044);

    private static final Map<String, UkCity> BY_NAME = new HashMap<>();

    static {
        for (UkCity city : values()) {
            BY_NAME.put(normalize(city.displayName), city);
            for (String alias : city.aliases) {
                BY_NAME.put(normalize(alias), city);
            }
        }
    }

    private final String displayName;
    private final String region;
    private final double latitude;
    private final double longitude;
    private final String[] aliases;

    UkCity(String displayName, String region, double latitude, double longitude, String... aliases) {
        this.displayName = displayName;
        this.region = region;
        this.latitude = latitude;
        this.longitude = longitude;
        this.aliases = aliases;
    }

    public static Optional<UkCity> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_NAME.get(normalize(name)));
    }

    public static Optional<String> findRegion(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        return Arrays.stream(values())
                .map(UkCity::region)
                .filter(region -> normalize(region).equals(wanted))
                .findFirst();
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String region() {
        return region;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }
}
