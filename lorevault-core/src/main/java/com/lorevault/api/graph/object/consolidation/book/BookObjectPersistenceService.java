package com.lorevault.api.graph.object.consolidation.book;

import com.lorevault.api.graph.object.persistence.BookObject;
import com.lorevault.api.graph.object.persistence.BookObjectGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class BookObjectPersistenceService {

    private final BookObjectGraphRepository bookObjectRepository;

    public BookObjectPersistenceService(BookObjectGraphRepository bookObjectRepository) {
        this.bookObjectRepository = bookObjectRepository;
    }

    @Transactional
    public List<BookObject> replaceBookObjects(
            UUID bookId,
            List<BookObject> bookObjects,
            List<List<UUID>> chapterObjectIdsByBookObject
    ) {
        log.info("[BOOK_OBJECT_PERSISTENCE] replaceBookObjects start: bookId={}, inputSize={}", bookId, bookObjects.size());
        bookObjectRepository.deleteByBookId(bookId);
        if (bookObjects.isEmpty()) {
            log.warn("[BOOK_OBJECT_PERSISTENCE] replaceBookObjects empty input: bookId={}", bookId);
            return List.of();
        }

        List<BookObject> savedObjects = new ArrayList<>(bookObjectRepository.saveAll(bookObjects));

        for (int i = 0; i < savedObjects.size(); i++) {
            BookObject bookObject = savedObjects.get(i);
            bookObjectRepository.linkBookToObject(bookId, bookObject.id());
            bookObjectRepository.linkChapterObjectsToBookObject(chapterObjectIdsByBookObject.get(i), bookObject.id());
        }

        log.info("[BOOK_OBJECT_PERSISTENCE] replaceBookObjects complete: bookId={}, savedCount={}", bookId, savedObjects.size());
        return savedObjects;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookObjectRepository.countBookObjectsByBookId(bookId);
    }
}
