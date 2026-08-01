package com.stoxsim.finwiz.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.finwiz.service.FinwizService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/finwiz")
public class FinwizController {

    private final FinwizService finwiz;

    public FinwizController(FinwizService finwiz) {
        this.finwiz = finwiz;
    }

    @PostMapping("/ask")
    public FinwizResponse ask(@Valid @RequestBody FinwizRequest request) {
        return finwiz.ask(request);
    }
}
