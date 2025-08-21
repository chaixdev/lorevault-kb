package com.lorevault.api.infrastructure.adapter;

import com.lorevault.api.application.port.JobContextPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thread-local implementation of JobContextPort.
 * Manages job ID context for the current processing thread.
 */
@Component
@Slf4j
public class ThreadLocalJobContextAdapter implements JobContextPort {

    private static final ThreadLocal<UUID> CURRENT_JOB_ID = new ThreadLocal<>();

    @Override
    public void setCurrentJobId(UUID jobId) {
        CURRENT_JOB_ID.set(jobId);
        log.debug("Set current job ID: {}", jobId);
    }

    @Override
    public void clearCurrentJobId() {
        UUID jobId = CURRENT_JOB_ID.get();
        CURRENT_JOB_ID.remove();
        log.debug("Cleared current job ID: {}", jobId);
    }

    @Override
    public UUID getCurrentJobId() {
        return CURRENT_JOB_ID.get();
    }
}