package com.lorevault.api.web.command.catalog;

import com.lorevault.api.dto.catalog.*;
import com.lorevault.api.service.catalog.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CQRS Command controller for publication hierarchy management
 */
@RestController
@RequestMapping("/api/command/catalog")
@RequiredArgsConstructor
@Slf4j
public class CatalogCommandController {

    private final CatalogService catalogService;

    /**
     * Create a universe for organizing publication content
     */
    @PostMapping("/create-universe")
    public ResponseEntity<CreateUniverseResponse> createUniverse(@Valid @RequestBody CreateUniverseRequest request) {
        log.info("[CMD] Create universe: {}", request.getName());
        
        try {
            CreateUniverseResponse response = catalogService.createUniverse(request);
            
            log.info("[CMD] Universe {}: {} with id: {}", 
                    response.isCreated() ? "created" : "exists", 
                    response.getName(), 
                    response.getUniverseId());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("[CMD] Invalid universe creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[CMD] Unexpected error creating universe: {}", request.getName(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create a series within a universe
     */
    @PostMapping("/create-series")
    public ResponseEntity<CreateSeriesResponse> createSeries(@Valid @RequestBody CreateSeriesRequest request) {
        log.info("[CMD] Create series: {} in universe: {}", request.getName(), request.getUniverseId());
        
        try {
            CreateSeriesResponse response = catalogService.createSeries(request);
            
            log.info("[CMD] Series {}: {} with id: {} in universe: {}", 
                    response.isCreated() ? "created" : "exists", 
                    response.getName(), 
                    response.getSeriesId(),
                    response.getUniverseId());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("[CMD] Invalid series creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[CMD] Unexpected error creating series: {} in universe: {}", 
                    request.getName(), request.getUniverseId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Create a book, either standalone in a universe or within a series
     */
    @PostMapping("/create-book")
    public ResponseEntity<CreateBookResponse> createBook(@Valid @RequestBody CreateBookRequest request) {
        log.info("[CMD] Create book: {} in universe: {}, series: {}", 
                request.getTitle(), request.getUniverseId(), request.getSeriesId());
        
        try {
            CreateBookResponse response = catalogService.createBook(request);
            
            log.info("[CMD] Book {}: {} with id: {} in universe: {}, series: {}", 
                    response.isCreated() ? "created" : "exists", 
                    response.getTitle(), 
                    response.getBookId(),
                    response.getUniverseId(),
                    response.getSeriesId());
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("[CMD] Invalid book creation request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("[CMD] Unexpected error creating book: {} in universe: {}, series: {}", 
                    request.getTitle(), request.getUniverseId(), request.getSeriesId(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}