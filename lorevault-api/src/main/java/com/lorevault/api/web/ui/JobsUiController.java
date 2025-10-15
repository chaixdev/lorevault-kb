package com.lorevault.api.web.ui;

import com.lorevault.api.dto.ingestion.JobListResponse;
import com.lorevault.api.service.ingestion.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        JobListResponse response = ingestionService.listJobs(null, null, limit, offset);
        model.addAttribute("jobs", response.getJobs());
        model.addAttribute("pagination", response.getPagination());
        return "ui/jobs :: jobTable";
    }
}
