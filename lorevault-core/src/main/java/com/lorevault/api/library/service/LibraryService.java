package com.lorevault.api.library.service;

import com.lorevault.api.library.universe.Universe;
import com.lorevault.api.library.series.Series;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.library.series.SeriesGraphRepository;
import com.lorevault.api.library.universe.UniverseGraphRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing publication hierarchy (Universe/Series/Book) creation.
 * Provides idempotent commands for establishing the library structure before chapter ingestion.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final UniverseGraphRepository universeRepo;
    private final SeriesGraphRepository seriesRepo;
    private final BookGraphRepository bookRepo;

    /**
     * Create a universe, returning the existing one if it already exists
     */
    public LibraryResult<Universe> createUniverse(String name) {
        validateUniverseName(name);

        log.info("Creating universe: {}", name);
        
        // Check if universe already exists
        Optional<Universe> existingUniverse = universeRepo.findByName(name);
        if (existingUniverse.isPresent()) {
            log.info("Universe already exists: {}", name);
            return new LibraryResult<>(existingUniverse.get(), false);
        }

        // Create new universe
        Universe newUniverse = Universe.ofName(name);
        Universe savedUniverse = universeRepo.save(newUniverse);
        
        log.info("Created universe: {} with id: {}", savedUniverse.getName(), savedUniverse.getId());
        
        return new LibraryResult<>(savedUniverse, true);
    }

    /**
     * Create a series within a universe, returning the existing one if it already exists
     */
    public LibraryResult<Series> createSeries(UUID universeId, String name) {
        validateSeriesName(name);

        log.info("Creating series: {} in universe: {}", name, universeId);
        
        // Validate universe exists
        Universe universe = universeRepo.findById(universeId)
                .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + universeId));

        // Check if series already exists in this universe
        Optional<Series> existingSeries = seriesRepo.findByNameAndUniverseId(name, universeId);
        if (existingSeries.isPresent()) {
            log.info("Series already exists: {} in universe: {}", name, universeId);
            return new LibraryResult<>(existingSeries.get(), false);
        }

        // Create new series
        Series newSeries = Series.create(universeId, universe.getName(), name);
        Series savedSeries = seriesRepo.save(newSeries);
        
        log.info("Created series: {} with id: {} in universe: {}", 
                savedSeries.getName(), savedSeries.getId(), savedSeries.getUniverseId());
        
        return new LibraryResult<>(savedSeries, true);
    }

    /**
     * Create a book, either standalone in a universe or within a series.
     * Returns the existing one if it already exists.
     */
    public LibraryResult<Book> createBook(UUID universeId, UUID seriesId, String title, Integer bookNumber) {
        validateBookTitle(title);

        log.info("Creating book: {} in universe: {}, series: {}", 
                title, universeId, seriesId);
        
        // Validate universe exists
        Universe universe = universeRepo.findById(universeId)
                .orElseThrow(() -> new IllegalArgumentException("Universe not found: " + universeId));

        Series series = null;
        if (seriesId != null) {
            // Validate series exists and belongs to universe
            series = seriesRepo.findById(seriesId)
                    .orElseThrow(() -> new IllegalArgumentException("Series not found: " + seriesId));
            
            if (!series.getUniverseId().equals(universeId)) {
                throw new IllegalArgumentException("Series does not belong to the specified universe");
            }
        }

        // Check if book already exists
        Optional<Book> existingBook = findExistingBook(universeId, seriesId, title);
        if (existingBook.isPresent()) {
            log.info("Book already exists: {} in universe: {}, series: {}", 
                    title, universeId, seriesId);
            return new LibraryResult<>(existingBook.get(), false);
        }

        // Create new book
        Book newBook = createNewBook(universeId, seriesId, title, bookNumber, universe, series);
        Book savedBook = bookRepo.save(newBook);
        
        log.info("Created book: {} with id: {} in universe: {}, series: {}", 
                savedBook.getTitle(), savedBook.getId(), savedBook.getUniverseId(), savedBook.getSeriesId());
        
        return new LibraryResult<>(savedBook, true);
    }

    private Optional<Book> findExistingBook(UUID universeId, UUID seriesId, String title) {
        if (seriesId != null) {
            // Book in series
            return bookRepo.findByTitleAndSeriesId(title, seriesId);
        } else {
            // Standalone book
            return bookRepo.findStandaloneByTitleAndUniverseId(title, universeId);
        }
    }

    private Book createNewBook(UUID universeId, UUID seriesId, String title, Integer bookNumber, Universe universe, Series series) {
        if (series != null) {
            return Book.createInSeries(
                universe.getId(),
                universe.getName(),
                series.getId(),
                series.getName(),
                bookNumber,
                title
            );
        } else {
            return Book.createStandalone(universe.getId(), universe.getName(), title);
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
