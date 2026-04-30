package com.lorevault.api.ingestion.resolution.object;

import com.lorevault.api.content.association.BookObject;
import com.lorevault.api.content.association.BookObjectGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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
        bookObjectRepository.deleteByBookId(bookId);
        if (bookObjects.isEmpty()) {
            return List.of();
        }

        List<BookObject> savedObjects = new ArrayList<>(bookObjectRepository.saveAll(bookObjects));

        for (int i = 0; i < savedObjects.size(); i++) {
            BookObject bookObject = savedObjects.get(i);
            bookObjectRepository.linkBookToObject(bookId, bookObject.id());
            bookObjectRepository.linkChapterObjectsToBookObject(chapterObjectIdsByBookObject.get(i), bookObject.id());
        }

        return savedObjects;
    }

    @Transactional(readOnly = true)
    public long countByBookId(UUID bookId) {
        return bookObjectRepository.countBookObjectsByBookId(bookId);
    }
}
