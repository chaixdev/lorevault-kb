package com.lorevault.api.ingestion.resolution.collective;

import com.lorevault.api.content.association.BookCollective;
import com.lorevault.api.content.association.BookCollectiveGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
        bookCollectiveRepository.deleteByBookId(bookId);
        if (bookCollectives.isEmpty()) {
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

        return savedCollectives;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookCollectiveRepository.countBookCollectivesByBookId(bookId);
    }
}
