package com.lorevault.api.service.library;

import com.lorevault.api.domain.content.Universe;
import com.lorevault.api.domain.content.Series;
import com.lorevault.api.domain.content.Book;
import com.lorevault.api.dto.library.*;
import com.lorevault.api.infrastructure.persistence.neo4j.adapter.Neo4jContentPersistenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for managing publication hierarchy (Universe/Series/Book) creation.
 * Provides idempotent commands for establishing the library structure before chapter ingestion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final Neo4jContentPersistenceAdapter contentPersistencePort;

    /**
     * Create a universe, returning the existing one if it already exists
     */
    public CreateUniverseResponse createUniverse(CreateUniverseRequest request) {
        validateUniverseName(request.getName());

        log.info("Creating universe: {}", request.getName());
        
        // Check if universe already exists
        Optional<Universe> existingUniverse = contentPersistencePort.findUniverseByName(request.getName());
        if (existingUniverse.isPresent()) {
            log.info("Universe already exists: {}", request.getName());
            Universe universe = existingUniverse.get();
            return CreateUniverseResponse.existing(
                universe.getId(),
                universe.getName(),
                universe.getSlug(),
                universe.getCreatedAt(),
                universe.getUpdatedAt()
            );
        }

        // Create new universe
        Universe newUniverse = Universe.ofName(request.getName());
        Universe savedUniverse = contentPersistencePort.createUniverse(newUniverse);
        
        log.info("Created universe: {} with id: {}", savedUniverse.getName(), savedUniverse.getId());
        
        return CreateUniverseResponse.newlyCreated(
            savedUniverse.getId(),
            savedUniverse.getName(),
            savedUniverse.getSlug(),
            savedUniverse.getCreatedAt(),
            savedUniverse.getUpdatedAt()
        );
    }

    /**
     * Create a series within a universe, returning the existing one if it already exists
     */
    public CreateSeriesResponse createSeries(CreateSeriesRequest request) {
        validateSeriesName(request.getName());

        log.info("Creating series: {} in universe: {}", request.getName(), request.getUniverseId());
        
        // Validate universe exists
        Universe universe = contentPersistencePort.findUniverseById(request.getUniverseId())
                .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + request.getUniverseId()));

        // Check if series already exists in this universe
        Optional<Series> existingSeries = contentPersistencePort.findSeriesByNameAndUniverseId(
                request.getName(), request.getUniverseId());
        if (existingSeries.isPresent()) {
            log.info("Series already exists: {} in universe: {}", request.getName(), request.getUniverseId());
            Series series = existingSeries.get();
            return CreateSeriesResponse.existing(
                series.getId(),
                series.getUniverseId(),
                series.getUniverseName(),
                series.getName(),
                series.getCreatedAt(),
                series.getUpdatedAt()
            );
        }

        // Create new series
        Series newSeries = Series.create(request.getUniverseId(), universe.getName(), request.getName());
        Series savedSeries = contentPersistencePort.createSeries(newSeries);
        
        log.info("Created series: {} with id: {} in universe: {}", 
                savedSeries.getName(), savedSeries.getId(), savedSeries.getUniverseId());
        
        return CreateSeriesResponse.newlyCreated(
            savedSeries.getId(),
            savedSeries.getUniverseId(),
            savedSeries.getUniverseName(),
            savedSeries.getName(),
            savedSeries.getCreatedAt(),
            savedSeries.getUpdatedAt()
        );
    }

    /**
     * Create a book, either standalone in a universe or within a series.
     * Returns the existing one if it already exists.
     */
    public CreateBookResponse createBook(CreateBookRequest request) {
        validateBookTitle(request.getTitle());

        log.info("Creating book: {} in universe: {}, series: {}", 
                request.getTitle(), request.getUniverseId(), request.getSeriesId());
        
        // Validate universe exists
        Universe universe = contentPersistencePort.findUniverseById(request.getUniverseId())
                .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + request.getUniverseId()));

        Series series = null;
        if (request.getSeriesId() != null) {
            // Validate series exists and belongs to universe
            series = contentPersistencePort.findSeriesById(request.getSeriesId())
                    .orElseThrow(() -> new IllegalArgumentException("Series not found: " + request.getSeriesId()));
            
            if (!series.getUniverseId().equals(request.getUniverseId())) {
                throw new IllegalArgumentException("Series does not belong to the specified universe");
            }
        }

        // Check if book already exists
        Optional<Book> existingBook = findExistingBook(request);
        if (existingBook.isPresent()) {
            log.info("Book already exists: {} in universe: {}, series: {}", 
                    request.getTitle(), request.getUniverseId(), request.getSeriesId());
            Book book = existingBook.get();
            return CreateBookResponse.existing(
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
        }

        // Create new book
        Book newBook = createNewBook(request, universe, series);
        Book savedBook = contentPersistencePort.createBook(newBook);
        
        log.info("Created book: {} with id: {} in universe: {}, series: {}", 
                savedBook.getTitle(), savedBook.getId(), savedBook.getUniverseId(), savedBook.getSeriesId());
        
        return CreateBookResponse.newlyCreated(
            savedBook.getId(),
            savedBook.getUniverseId(),
            savedBook.getUniverse(),
            savedBook.getSeriesId(),
            savedBook.getSeries(),
            savedBook.getTitle(),
            savedBook.getBookNumber(),
            savedBook.getCreatedAt(),
            savedBook.getUpdatedAt()
        );
    }

    private Optional<Book> findExistingBook(CreateBookRequest request) {
        if (request.getSeriesId() != null) {
            // Book in series
            return contentPersistencePort.findBookByTitleAndSeriesId(request.getTitle(), request.getSeriesId());
        } else {
            // Standalone book
            return contentPersistencePort.findStandaloneBookByTitleAndUniverseId(
                    request.getTitle(), request.getUniverseId());
        }
    }

    private Book createNewBook(CreateBookRequest request, Universe universe, Series series) {
        if (series != null) {
            return Book.createInSeries(
                universe.getId(),
                universe.getName(),
                series.getId(),
                series.getName(),
                request.getBookNumber(),
                request.getTitle()
            );
        } else {
            return Book.createStandalone(universe.getId(), universe.getName(), request.getTitle());
        }
    }

    private void validateUniverseName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Universe name cannot be null or blank");
        }
    }

    private void validateSeriesName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Series name cannot be null or blank");
        }
    }

    private void validateBookTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Book title cannot be null or blank");
        }
    }
}
