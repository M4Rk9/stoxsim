package com.stoxsim.market.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.service.IndexQuoteService;

@RestController
@RequestMapping("/api/v1/market/indices")
public class IndexController {

    private final IndexQuoteService indices;

    public IndexController(IndexQuoteService indices) {
        this.indices = indices;
    }

    @GetMapping
    public List<IndexQuoteResponse> current() {
        return indices.current();
    }
}
