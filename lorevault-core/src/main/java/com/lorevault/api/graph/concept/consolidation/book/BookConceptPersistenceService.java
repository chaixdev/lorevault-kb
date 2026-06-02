package com.lorevault.api.graph.concept.consolidation.book;

import com.lorevault.api.graph.concept.persistence.BookConcept;
import com.lorevault.api.graph.concept.persistence.BookConceptGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BookConceptPersistenceService {

    private final BookConceptGraphRepository bookConceptRepository;

    public BookConceptPersistenceService(BookConceptGraphRepository bookConceptRepository) {
        this.bookConceptRepository = bookConceptRepository;
    }

    @Transactional
    public List<BookConcept> replaceBookConcepts(
            UUID bookId,
            List<BookConcept> bookConcepts,
            List<List<UUID>> chapterConceptIdsByBookConcept
    ) {
        log.info("[BOOK_CONCEPT_PERSISTENCE] replaceBookConcepts start: bookId={}, inputSize={}", bookId, bookConcepts.size());
        bookConceptRepository.deleteByBookId(bookId);
        if (bookConcepts.isEmpty()) {
            log.warn("[BOOK_CONCEPT_PERSISTENCE] replaceBookConcepts empty input: bookId={}", bookId);
            return List.of();
        }

        List<BookConcept> savedConcepts = new ArrayList<>(bookConceptRepository.saveAll(bookConcepts));

        for (int i = 0; i < savedConcepts.size(); i++) {
            BookConcept bookConcept = savedConcepts.get(i);
            bookConceptRepository.linkBookToConcept(bookId, bookConcept.id());
            bookConceptRepository.linkChapterConceptsToBookConcept(
                    chapterConceptIdsByBookConcept.get(i),
                    bookConcept.id()
            );
        }

        log.info("[BOOK_CONCEPT_PERSISTENCE] replaceBookConcepts complete: bookId={}, savedCount={}", bookId, savedConcepts.size());
        return savedConcepts;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookConceptRepository.countBookConceptsByBookId(bookId);
    }
}
