package com.lorevault.api.web.ui;

import com.lorevault.api.orchestration.submission.IngestionService;
import com.lorevault.api.orchestration.job.JobStatusDetails;
import com.lorevault.api.orchestration.job.PaginatedJobSummaries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/ui/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobsUiController {

    private final IngestionService ingestionService;

    @GetMapping
    public String listJobs(@RequestParam(value = "limit", defaultValue = "20") int limit,
                           @RequestParam(value = "offset", defaultValue = "0") int offset,
                           Model model) {
        PaginatedJobSummaries response = ingestionService.listJobs(null, null, limit, offset);
        model.addAttribute("jobs", response.jobs());
        model.addAttribute("pagination", response.pagination());
        return "ui/jobs :: jobTable";
    }

    @GetMapping("/{jobId}")
    public String jobDetail(@PathVariable UUID jobId, Model model) {
        JobStatusDetails status = ingestionService.getJobStatus(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        model.addAttribute("status", status);
        return "ui/jobs :: jobDetail";
    }

    @GetMapping("/{jobId}/expand")
    public String expandedJobDetail(@PathVariable UUID jobId, Model model) {
        JobStatusDetails status = ingestionService.getJobStatus(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        model.addAttribute("status", status);
        return "ui/jobs :: jobExpandedRow";
    }

    @GetMapping("/{jobId}/collapse")
    public String collapsedJobDetail(@PathVariable UUID jobId, Model model) {
        model.addAttribute("jobId", jobId);
        return "ui/jobs :: jobCollapsedPlaceholder";
    }
}
