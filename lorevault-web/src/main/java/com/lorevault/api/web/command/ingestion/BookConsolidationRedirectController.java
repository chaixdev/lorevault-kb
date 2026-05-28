package com.lorevault.api.web.command.ingestion;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Provides 307 Temporary Redirects from legacy book-level URLs
 * ({@code resolve-*} and {@code reduce-*}) to the current {@code book-consolidate-*} URLs.
 *
 * <p>Book-level endpoints were renamed across releases: first from "resolve" to "reduce",
 * then from "reduce" to "book-consolidate" to align with the unified consolidation vocabulary.
 * These redirects will be maintained for one release cycle after the final rename, then removed.
 *
 * <p>Clients should update their URLs to use {@code /book-consolidate-*} directly.
 */
@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class BookConsolidationRedirectController {

    // ── Legacy resolve-* → book-consolidate-* ──────────────────────────

    @PostMapping("/books/{bookId}/resolve-individuals")
    public RedirectView resolveToConsolidateIndividuals(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-individuals", request);
    }

    @PostMapping("/books/{bookId}/resolve-collectives")
    public RedirectView resolveToConsolidateCollectives(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-collectives", request);
    }

    @PostMapping("/books/{bookId}/resolve-locations")
    public RedirectView resolveToConsolidateLocations(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-locations", request);
    }

    @PostMapping("/books/{bookId}/resolve-objects")
    public RedirectView resolveToConsolidateObjects(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-objects", request);
    }

    // ── Legacy reduce-* → book-consolidate-* ───────────────────────────

    @PostMapping("/books/{bookId}/reduce-individuals")
    public RedirectView reduceToConsolidateIndividuals(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-individuals", request);
    }

    @PostMapping("/books/{bookId}/reduce-collectives")
    public RedirectView reduceToConsolidateCollectives(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-collectives", request);
    }

    @PostMapping("/books/{bookId}/reduce-locations")
    public RedirectView reduceToConsolidateLocations(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-locations", request);
    }

    @PostMapping("/books/{bookId}/reduce-objects")
    public RedirectView reduceToConsolidateObjects(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/book-consolidate-objects", request);
    }

    private RedirectView temporaryRedirect(String url, HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            url = url + "?" + queryString;
        }
        RedirectView redirectView = new RedirectView(url);
        redirectView.setStatusCode(HttpStatus.TEMPORARY_REDIRECT);
        return redirectView;
    }
}
