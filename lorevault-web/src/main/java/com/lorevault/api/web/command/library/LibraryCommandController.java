package com.lorevault.api.web.command.library;

import com.lorevault.api.library.domain.Universe;
import com.lorevault.api.library.domain.Series;
import com.lorevault.api.library.domain.Book;
import com.lorevault.api.library.application.LibraryResult;
import com.lorevault.api.library.application.LibraryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * CQRS Command controller for publication hierarchy management
 */
@RestController
@RequestMapping("/api/command/library")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Library", description = "Publication hierarchy management")
public class LibraryCommandController {

    private final LibraryService catalogService;

    /**
     * Create a universe for organizing publication content
     */
    @PostMapping("/create-universe")
    public ResponseEntity<CreateUniverseResponse> createUniverse(@Valid @RequestBody CreateUniverseRequest request) {
        log.info("[CMD] Create universe: {}", request.getName());
        
        try {
            LibraryResult<Universe> result = catalogService.createUniverse(request.getName());
            Universe universe = result.entity();
            
            CreateUniverseResponse response = result.isNew() ?
                CreateUniverseResponse.newlyCreated(
                    universe.getId(),
                    universe.getName(),
                    universe.getSlug(),
                    universe.getCreatedAt(),
                    universe.getUpdatedAt()
                ) :
                CreateUniverseResponse.existing(
                    universe.getId(),
                    universe.getName(),
                    universe.getSlug(),
                    universe.getCreatedAt(),
                    universe.getUpdatedAt()
                );
            
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
            LibraryResult<Series> result = catalogService.createSeries(request.getUniverseId(), request.getName());
            Series series = result.entity();
            
            CreateSeriesResponse response = result.isNew() ?
                CreateSeriesResponse.newlyCreated(
                    series.getId(),
                    series.getUniverseId(),
                    series.getUniverseName(),
                    series.getName(),
                    series.getCreatedAt(),
                    series.getUpdatedAt()
                ) :
                CreateSeriesResponse.existing(
                    series.getId(),
                    series.getUniverseId(),
                    series.getUniverseName(),
                    series.getName(),
                    series.getCreatedAt(),
                    series.getUpdatedAt()
                );
            
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
            LibraryResult<Book> result = catalogService.createBook(
                request.getUniverseId(), 
                request.getSeriesId(), 
                request.getTitle(), 
                request.getBookNumber()
            );
            Book book = result.entity();
            
            CreateBookResponse response = result.isNew() ?
                CreateBookResponse.newlyCreated(
                    book.getId(),
                    book.getUniverseId(),
                    book.getUniverse(),
                    book.getSeriesId(),
                    book.getSeries(),
                    book.getTitle(),
                    book.getBookNumber(),
                    book.getCreatedAt(),
                    book.getUpdatedAt()
                ) :
                CreateBookResponse.existing(
                    book.getId(),
                    book.getUniverseId(),
                    book.getUniverse(),
                    book.getSeriesId(),
                    book.getSeries(),
                    book.getTitle(),
                    book.getBookNumber(),
                    book.getCreatedAt(),
                    book.getUpdatedAt()
                );
            
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
