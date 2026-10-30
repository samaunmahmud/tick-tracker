package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/sightings")
public class SightingController {

    private final SightingService sightingService;

    public SightingController(SightingService sightingService) {
        this.sightingService = sightingService;
    }

    @GetMapping
    public PageResponse<SightingResponse> search(
            @ModelAttribute SightingSearchParams params,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sightingService.search(params, page, size);
    }

    @GetMapping("/{id}")
    public SightingResponse findById(@PathVariable UUID id) {
        return sightingService.findById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SightingResponse> report(@Valid @ModelAttribute SightingReportRequest request) {
        SightingResponse created = sightingService.report(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
