package com.lorevault.api.graph.collective.consolidation.book;

import com.lorevault.api.graph.collective.persistence.BookCollective;
import com.lorevault.api.graph.collective.persistence.BookCollectiveGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BookCollectivePersistenceService {

    private final BookCollectiveGraphRepository bookCollectiveRepository;

    public BookCollectivePersistenceService(BookCollectiveGraphRepository bookCollectiveRepository) {
        this.bookCollectiveRepository = bookCollectiveRepository;
    }

    @Transactional
    public List<BookCollective> replaceBookCollectives(
            UUID bookId,
            List<BookCollective> bookCollectives,
            List<List<UUID>> chapterCollectiveIdsByBookCollective
    ) {
        log.info("[BOOK_COLLECTIVE_PERSISTENCE] replaceBookCollectives start: bookId={}, inputSize={}", bookId, bookCollectives.size());
        bookCollectiveRepository.deleteByBookId(bookId);
        if (bookCollectives.isEmpty()) {
            log.warn("[BOOK_COLLECTIVE_PERSISTENCE] replaceBookCollectives empty input: bookId={}", bookId);
            return List.of();
        }

        List<BookCollective> savedCollectives = new ArrayList<>(bookCollectiveRepository.saveAll(bookCollectives));

        for (int i = 0; i < savedCollectives.size(); i++) {
            BookCollective bookCollective = savedCollectives.get(i);
            bookCollectiveRepository.linkBookToCollective(bookId, bookCollective.id());
            bookCollectiveRepository.linkChapterCollectivesToBookCollective(
                    chapterCollectiveIdsByBookCollective.get(i),
                    bookCollective.id()
            );
        }

        log.info("[BOOK_COLLECTIVE_PERSISTENCE] replaceBookCollectives complete: bookId={}, savedCount={}", bookId, savedCollectives.size());
        return savedCollectives;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookCollectiveRepository.countBookCollectivesByBookId(bookId);
    }
}
