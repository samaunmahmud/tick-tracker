package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.reference.TickSpecies;
import com.samaun.ticktracker.reference.UkCity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

public final class ContentHash {

    private static final DateTimeFormatter TO_THE_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private ContentHash() {
    }

    public static String of(LocalDateTime sightedAt, UkCity city, TickSpecies species) {
        String key = String.join("|",
                sightedAt.format(TO_THE_SECOND),
                city.displayName(),
                species.commonName()
        ).toLowerCase(Locale.ROOT);
        return sha256Hex(key);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
