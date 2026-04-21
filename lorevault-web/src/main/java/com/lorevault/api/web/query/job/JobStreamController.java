package com.lorevault.api.web.query.job;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/query/jobs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Jobs", description = "Ingestion job monitoring and status")
public class JobStreamController {

    private final JobStatusBroadcaster broadcaster;

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream ingestion job status updates via SSE")
    public SseEmitter stream() {
        log.info("New SSE stream connection requested");
        return broadcaster.register();
    }
}
