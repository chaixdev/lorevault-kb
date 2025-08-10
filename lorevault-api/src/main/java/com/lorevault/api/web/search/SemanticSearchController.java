package com.lorevault.api.web.search;

import com.lorevault.api.dto.search.SemanticSearchDtos;
import com.lorevault.api.service.search.SemanticSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SemanticSearchController {

    private final SemanticSearchService semanticSearchService;

    @PostMapping("/semantic")
    public ResponseEntity<SemanticSearchDtos.Response> semantic(@RequestBody SemanticSearchDtos.Request request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(semanticSearchService.search(request));
    }
}
