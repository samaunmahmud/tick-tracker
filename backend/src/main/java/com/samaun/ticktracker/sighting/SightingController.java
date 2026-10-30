package com.samaun.ticktracker.sighting;

import com.samaun.ticktracker.common.ApiError;
import com.samaun.ticktracker.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
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

@Tag(name = "Sightings", description = "Search stored sightings and report new ones")
@RestController
@RequestMapping("/api/sightings")
public class SightingController {

    private final SightingService sightingService;

    public SightingController(SightingService sightingService) {
        this.sightingService = sightingService;
    }

    @Operation(summary = "Search sightings",
            description = "Filter by time range, city, region and species. All filters are optional and combine "
                    + "with AND.")
    @ApiResponse(responseCode = "200", description = "A page of matching sightings")
    @ApiResponse(responseCode = "400", description = "Unknown city, region, species or sort, a bad date, "
            + "or 'from' after 'to'",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping
    public PageResponse<SightingResponse> search(
            @ParameterObject @ModelAttribute SightingFilterParams params,
            @Parameter(description = "newest (default) or oldest")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Page number, starting at 0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, 1 to 100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return sightingService.search(params, sort, page, size);
    }

    @Operation(summary = "Get one sighting")
    @ApiResponse(responseCode = "200", description = "The sighting")
    @ApiResponse(responseCode = "404", description = "No sighting with this id",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}")
    public SightingResponse findById(@PathVariable UUID id) {
        return sightingService.findById(id);
    }

    @Operation(summary = "Report a new sighting",
            description = "Region, coordinates and latin name are filled in by the server. The Location header "
                    + "points at the new sighting.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "400", description = "Missing or invalid fields, unknown city or species, "
            + "or a date in the future",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "A sighting with the same time, city and species already exists",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
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
