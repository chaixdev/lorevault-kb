package com.lorevault.api.graph.location.consolidation.book;

import com.lorevault.api.graph.location.persistence.BookLocation;
import com.lorevault.api.graph.location.persistence.BookLocationGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence boundary for book-level location aggregate rebuilds.
 *
 * <p>The reducer keeps clustering logic. This service owns the write transactions so propagation
 * rules are applied through a Spring proxy instead of being bypassed by self-invocation inside the
 * reducer.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookLocationPersistenceService {

    private final BookLocationGraphRepository bookLocationRepository;

    @Transactional
    public List<BookLocation> replaceBookLocations(
            UUID bookId,
            List<BookLocation> bookLocations,
            List<List<UUID>> chapterLocationIdsByBookLocation
    ) {
        log.info("[BOOK_LOCATION_PERSISTENCE] replaceBookLocations start: bookId={}, inputSize={}", bookId, bookLocations.size());
        bookLocationRepository.deleteByBookId(bookId);
        if (bookLocations.isEmpty()) {
            log.warn("[BOOK_LOCATION_PERSISTENCE] replaceBookLocations empty input: bookId={}", bookId);
            return List.of();
        }

        List<BookLocation> savedLocations = new ArrayList<>(bookLocationRepository.saveAll(bookLocations));

        for (int i = 0; i < savedLocations.size(); i++) {
            BookLocation bookLocation = savedLocations.get(i);
            bookLocationRepository.linkBookToLocation(bookId, bookLocation.id());
            bookLocationRepository.linkChapterLocationsToBookLocation(
                    chapterLocationIdsByBookLocation.get(i),
                    bookLocation.id()
            );
        }

        log.info("[BOOK_LOCATION_PERSISTENCE] replaceBookLocations complete: bookId={}, savedCount={}", bookId, savedLocations.size());
        return savedLocations;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookLocationRepository.countBookLocationsByBookId(bookId);
    }
}
