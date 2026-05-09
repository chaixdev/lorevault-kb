package com.lorevault.api.web.command.ingestion;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Provides 307 Temporary Redirects from the old book-level {@code resolve-*} URLs
 * to the new {@code reduce-*} URLs.
 *
 * <p>The book-level endpoints were renamed from "resolve" to "reduce" to match
 * the domain vocabulary (chapter-level entities are "resolved", book-level
 * entities are "reduced"). These redirects will be maintained for one release
 * cycle, then removed.
 *
 * <p>Clients should update their URLs to use {@code /reduce-*} directly.
 */
@RestController
@RequestMapping("/api/command/ingest")
@Tag(name = "Ingestion", description = "Content ingestion operations")
public class BookReductionRedirectController {

    @PostMapping("/books/{bookId}/resolve-individuals")
    public RedirectView resolveToReduceIndividuals(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/reduce-individuals", request);
    }

    @PostMapping("/books/{bookId}/resolve-collectives")
    public RedirectView resolveToReduceCollectives(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/reduce-collectives", request);
    }

    @PostMapping("/books/{bookId}/resolve-locations")
    public RedirectView resolveToReduceLocations(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/reduce-locations", request);
    }

    @PostMapping("/books/{bookId}/resolve-objects")
    public RedirectView resolveToReduceObjects(@PathVariable String bookId, HttpServletRequest request) {
        return temporaryRedirect("/api/command/ingest/books/" + bookId + "/reduce-objects", request);
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