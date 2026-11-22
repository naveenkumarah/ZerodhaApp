package com.example.journal;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final JournalService journal;

    public JournalController(JournalService journal) {
        this.journal = journal;
    }

    @PostMapping("/add")
    public JournalEntry add(@AuthenticationPrincipal Long userId, @Valid @RequestBody JournalService.AddRequest req) {
        return journal.add(userId, req);
    }

    @GetMapping("/history")
    public List<JournalEntry> history(@AuthenticationPrincipal Long userId) {
        return journal.history(userId);
    }

    @GetMapping("/analysis")
    public JournalAnalyzer.Report analysis(@AuthenticationPrincipal Long userId) {
        return journal.analysis(userId);
    }
}

