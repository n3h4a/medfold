package com.medfold.search;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medfold.auth.CurrentUserId;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** Documents containing every word of {@code q}, in timeline order, at most 50. */
    @GetMapping
    public List<SearchResult> search(@CurrentUserId UUID userId, @RequestParam(defaultValue = "") String q) {
        return searchService.search(userId, q);
    }
}
