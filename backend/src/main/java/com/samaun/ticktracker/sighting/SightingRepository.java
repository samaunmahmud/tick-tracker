package com.samaun.ticktracker.sighting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface SightingRepository extends JpaRepository<Sighting, UUID>, JpaSpecificationExecutor<Sighting> {

    @Query("select s.externalId from Sighting s where s.externalId in :externalIds")
    Set<String> findExistingExternalIds(@Param("externalIds") Collection<String> externalIds);

    @Query("select s.contentHash from Sighting s where s.contentHash in :contentHashes")
    Set<String> findExistingContentHashes(@Param("contentHashes") Collection<String> contentHashes);

    boolean existsByContentHash(String contentHash);
}
