package com.stoxsim.finwiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.finwiz.api.FinwizRequest;
import com.stoxsim.finwiz.api.FinwizRequest.ExperienceLevel;
import com.stoxsim.finwiz.api.FinwizRequest.Topic;
import com.stoxsim.finwiz.config.FinwizProperties;
import com.stoxsim.finwiz.service.FinwizContextService.ContextSnapshot;

@ExtendWith(MockitoExtension.class)
class FinwizServiceTest {

    @Mock private FinwizContextService contexts;

    @Test
    void usesEducationalFallbackWhenNoOpenAiKeyIsConfigured() {
        FinwizProperties properties = new FinwizProperties();
        properties.setApiKey("");
        FinwizRequest request = new FinwizRequest(
            "Why can profit rise while operating cash flow falls?",
            Topic.CASH_FLOW,
            ExperienceLevel.BEGINNER,
            null,
            null,
            null
        );
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());

        var response = new FinwizService(properties, contexts).ask(request);

        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).contains("Operating cash flow");
        assertThat(response.disclaimer()).contains("does not provide investment advice");
        assertThat(response.groundedInStoxSimData()).isFalse();
    }

    @Test
    void preservesVerifiedContextInFallbackMode() {
        FinwizProperties properties = new FinwizProperties();
        FinwizRequest request = new FinwizRequest(
            "Explain the available data without recommending a trade.",
            Topic.STOCK_FUNDAMENTALS,
            ExperienceLevel.BEGINNER,
            null,
            null,
            null
        );
        var context = new ContextSnapshot(
            true,
            "TEST",
            Instant.parse("2026-08-01T12:00:00Z"),
            "Quote: lastPrice=100.00, previousClose=98.00."
        );
        when(contexts.build(request)).thenReturn(context);

        var response = new FinwizService(properties, contexts).ask(request);

        assertThat(response.answer()).contains("Verified StoxSim data");
        assertThat(response.answer()).contains("lastPrice=100.00");
        assertThat(response.dataAsOf()).isEqualTo(context.dataAsOf());
    }

    @Test
    void rejectsQuestionsAboveTheConfiguredLimit() {
        FinwizProperties properties = new FinwizProperties();
        properties.setMaxQuestionCharacters(10);
        FinwizRequest request = new FinwizRequest(
            "This question is longer than ten characters.",
            Topic.LEARN,
            ExperienceLevel.BEGINNER,
            null,
            null,
            null
        );

        assertThatThrownBy(() -> new FinwizService(properties, contexts).ask(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Question is too long");
    }
}
