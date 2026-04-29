package com.lorevault.api.ingestion.resolution.event;

import com.lorevault.api.content.association.ChapterEventGraphRepository;
import com.lorevault.api.content.chapter.Chapter;
import com.lorevault.api.content.chapter.ChapterGraphRepository;
import com.lorevault.api.ingestion.events.ChapterEventsResolvedEvent;
import com.lorevault.api.library.book.Book;
import com.lorevault.api.library.book.BookGraphRepository;
import com.lorevault.api.library.universe.UniverseGraphRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChapterEventAnnRerunService {

    private final UniverseGraphRepository universeGraphRepository;
    private final BookGraphRepository bookGraphRepository;
    private final ChapterGraphRepository chapterGraphRepository;
    private final ChapterEventGraphRepository chapterEventGraphRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ChapterEventAnnRerunService(
            UniverseGraphRepository universeGraphRepository,
            BookGraphRepository bookGraphRepository,
            ChapterGraphRepository chapterGraphRepository,
            ChapterEventGraphRepository chapterEventGraphRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.universeGraphRepository = universeGraphRepository;
        this.bookGraphRepository = bookGraphRepository;
        this.chapterGraphRepository = chapterGraphRepository;
        this.chapterEventGraphRepository = chapterEventGraphRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ChapterEventAnnRerunResult rerun(UUID universeId, UUID bookId, UUID chapterId) {
        UUID jobId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        ScopeSelection scopeSelection = resolveScope(universeId, bookId, chapterId);
        List<ScopedChapter> selectedChapters = scopeSelection.selectedChapters();

        for (ScopedChapter selectedChapter : selectedChapters) {
            Chapter chapter = selectedChapter.chapter();
            UUID resolvedChapterId = chapter.getId();
            UUID resolvedBookId = selectedChapter.bookId();

            eventPublisher.publishEvent(new ChapterEventsResolvedEvent(
                    this,
                    jobId,
                    correlationId,
                    resolvedChapterId,
                    resolvedBookId,
                    true,
                    Math.toIntExact(chapterEventGraphRepository.countMentionsByChapterId(resolvedChapterId)),
                    Math.toIntExact(chapterEventGraphRepository.countChapterEventsByChapterId(resolvedChapterId)),
                    0
            ));
        }

        return new ChapterEventAnnRerunResult(
                true,
                scopeSelection.selectedScope(),
                selectedChapters.size(),
                jobId,
                correlationId,
                buildMessage(scopeSelection.selectedScope(), selectedChapters.size())
        );
    }

    private ScopeSelection resolveScope(UUID universeId, UUID bookId, UUID chapterId) {
        if (chapterId != null) {
            Chapter chapter = chapterGraphRepository.findById(chapterId)
                    .orElseThrow(() -> new NoSuchElementException("Chapter not found: " + chapterId));
            UUID resolvedBookId = resolveChapterBookId(chapter)
                    .orElseThrow(() -> new NoSuchElementException("Chapter missing book relationship: " + chapterId));
            UUID resolvedUniverseId = chapter.getUniverseId();
            ChapterEventAnnRerunResult.SelectedScope selectedScope = new ChapterEventAnnRerunResult.SelectedScope(resolvedUniverseId, resolvedBookId, chapter.getId());
            return new ScopeSelection(selectedScope, List.of(new ScopedChapter(chapter, resolvedBookId)));
        }

        if (bookId != null) {
            Book book = bookGraphRepository.findById(bookId)
                    .orElseThrow(() -> new NoSuchElementException("Book not found: " + bookId));
            List<ScopedChapter> chapters = chapterGraphRepository.findByBookId(bookId).stream()
                    .map(chapter -> new ScopedChapter(chapter, resolveChapterBookId(chapter).orElse(book.getId())))
                    .toList();
            ChapterEventAnnRerunResult.SelectedScope selectedScope = new ChapterEventAnnRerunResult.SelectedScope(book.getUniverseId(), book.getId(), null);
            return new ScopeSelection(selectedScope, chapters);
        }

        if (universeId == null) {
            throw new IllegalArgumentException("universeId is required when chapterId and bookId are not supplied");
        }

        universeGraphRepository.findById(universeId)
                .orElseThrow(() -> new NoSuchElementException("Universe not found: " + universeId));

        List<ScopedChapter> chapters = new ArrayList<>();
        for (Book book : bookGraphRepository.findByUniverseId(universeId)) {
            UUID resolvedBookId = book.getId();
            chapters.addAll(chapterGraphRepository.findByBookId(resolvedBookId).stream()
                    .map(chapter -> new ScopedChapter(chapter, resolveChapterBookId(chapter).orElse(resolvedBookId)))
                    .toList());
        }

        return new ScopeSelection(new ChapterEventAnnRerunResult.SelectedScope(universeId, null, null), chapters);
    }

    private Optional<UUID> resolveChapterBookId(Chapter chapter) {
        if (chapter == null) {
            return Optional.empty();
        }
        if (chapter.getBookId() != null) {
            return Optional.of(chapter.getBookId());
        }
        if (chapter.getBook() != null && chapter.getBook().getId() != null) {
            return Optional.of(chapter.getBook().getId());
        }
        return Optional.empty();
    }

    private String buildMessage(ChapterEventAnnRerunResult.SelectedScope selectedScope, int triggeredChapterCount) {
        String scopeLabel = selectedScope.chapterId() != null ? "chapter"
                : selectedScope.bookId() != null ? "book"
                : "universe";
        return "Triggered " + triggeredChapterCount + " chapter ANN rerun(s) for " + scopeLabel + " scope";
    }

    private record ScopeSelection(ChapterEventAnnRerunResult.SelectedScope selectedScope, List<ScopedChapter> selectedChapters) {
    }

    private record ScopedChapter(Chapter chapter, UUID bookId) {
    }
}
