package com.lorevault.api.ingestion.resolution.individual;

import com.lorevault.api.content.association.BookIndividual;
import com.lorevault.api.content.association.BookIndividualGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence boundary for book-level individual aggregate rebuilds.
 *
 * <p>The reducer keeps candidate selection and aggregate construction logic. This service owns
 * the write transactions so propagation rules are applied through a Spring proxy instead of being
 * bypassed by self-invocation inside the reducer.</p>
 */
@Service
@RequiredArgsConstructor
public class BookIndividualPersistenceService {

    private final BookIndividualGraphRepository bookIndividualRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteByBookId(UUID bookId) {
        bookIndividualRepository.deleteByBookId(bookId);
    }

    @Transactional
    public List<BookIndividual> saveAndLinkBookIndividuals(UUID bookId, List<BookIndividual> bookIndividuals) {
        List<BookIndividual> savedIndividuals = new ArrayList<>(bookIndividualRepository.saveAll(bookIndividuals));

        for (BookIndividual bookIndividual : savedIndividuals) {
            bookIndividualRepository.linkBookToIndividual(bookId, bookIndividual.id());
            bookIndividualRepository.linkChapterIndividualsForBookAndNameToBookIndividual(
                    bookId,
                    bookIndividual.normalizedName(),
                    bookIndividual.id()
            );
        }

        return savedIndividuals;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookIndividualRepository.countBookIndividualsByBookId(bookId);
    }
}
