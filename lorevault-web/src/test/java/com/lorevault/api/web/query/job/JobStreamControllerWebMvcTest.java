package com.lorevault.api.web.query.job;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.application.CoreSearchRecords.*;
import com.lorevault.api.ingestion.application.*;
import com.lorevault.api.ingestion.domain.*;
import com.lorevault.api.ingestion.infrastructure.*;
import com.lorevault.api.search.application.*;
import com.lorevault.api.search.domain.*;
import com.lorevault.api.search.infrastructure.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobStreamController.class)
@DisplayName("JobStreamController WebMvc Tests")
class JobStreamControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobStatusBroadcaster broadcaster;

    @Test
    @DisplayName("GET /api/query/jobs/stream returns 200 and registers SSE client")
    void streamEndpointRegistersClient() throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        when(broadcaster.register()).thenReturn(emitter);

        mockMvc.perform(get("/api/query/jobs/stream").accept("text/event-stream"))
                .andExpect(status().isOk());

        verify(broadcaster).register();
        emitter.complete();
    }
}
