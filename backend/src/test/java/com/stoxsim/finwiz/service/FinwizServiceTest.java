package com.stoxsim.finwiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.AfterEach;
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
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class FinwizServiceTest {

    @Mock private FinwizContextService contexts;
    @Mock private FinwizAccess access;
    @Mock private FinwizUsageBudget budget;
    private final FinwizSpendBudget spend = org.mockito.Mockito.mock(FinwizSpendBudget.class,
        invocation -> invocation.getMethod().getName().equals("settings") ? disabledSpend() : org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation));

    private com.stoxsim.finwiz.config.FinwizSpendProperties disabledSpend() {
        return new com.stoxsim.finwiz.config.FinwizSpendProperties(false,false,null,null,
            0,0,0,0,0,0,0,0,0,0,0,0);
    }

    private static final java.util.UUID USER = java.util.UUID.randomUUID();
    @org.junit.jupiter.api.BeforeEach void access() {
        org.mockito.Mockito.lenient().when(access.depth(USER)).thenReturn(FinwizDepth.BASIC);
        var day=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        org.mockito.Mockito.lenient().when(budget.reserveRequest(org.mockito.ArgumentMatchers.eq(USER), org.mockito.ArgumentMatchers.any())).thenReturn(new FinwizUsageBudget.RequestReservation(java.util.UUID.randomUUID(),USER,day));
        org.mockito.Mockito.lenient().when(budget.reserveAttempt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(new FinwizUsageBudget.Reservation(day));
        org.mockito.Mockito.lenient().when(budget.finishRequest(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(true);
    }

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void reportsUnavailableWhenNoGeminiKeyIsConfigured() {
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

        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);

        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).contains("Your question has not been answered");
        assertThat(response.disclaimer()).contains("does not provide investment advice");
        assertThat(response.groundedInStoxSimData()).isFalse();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"What is a stock ?", "Why can RSI stay high or low for a long time?"})
    void unavailableAiNeverSubstitutesATopicFrameworkForTheActualQuestion(String question) {
        var request = new FinwizRequest(question, Topic.LEARN, null, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());
        var result = new FinwizService(new FinwizProperties(), contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(result.answer()).contains("Your question has not been answered")
            .doesNotContain("Beginner framework", "Finwiz learning mode", "VERIFIED STOXSIM CONTEXT");
        assertThat(result.suggestedQuestions()).isEmpty();
        assertThat(result.model()).isEqualTo("unavailable-v2");
    }

    @Test
    void exhaustedOrUnavailableBudgetNeverCallsProviderOrPretendsToAnswer() {
        FinwizProperties properties = new FinwizProperties();
        properties.setApiKey("test-provider-key"); properties.setGeminiFreeCapacityConfirmed(true);
        properties.setBaseUrl("http://127.0.0.1:1");
        var request = new FinwizRequest("Explain concentration", Topic.PORTFOLIO_EDUCATION, null, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());
        when(budget.reserveRequest(USER, FinwizDepth.BASIC)).thenReturn(null);
        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).contains("daily AI allowance");
        when(budget.reserveRequest(USER, FinwizDepth.BASIC)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("unavailable"));
        var unavailable = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(unavailable.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        org.mockito.Mockito.verify(budget, org.mockito.Mockito.never()).recordReportedTokens(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void sendsGeminiThinkingLevelAndReturnsGeneratedText() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedKey = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-3.6-flash:generateContent", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = """
                {"candidates":[{"content":{"parts":[{"text":"Operating cash flow is the cash generated by normal business operations."}]},"finishReason":"STOP"}],"usageMetadata":{"totalTokenCount":321}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        FinwizProperties properties = new FinwizProperties();
        properties.setApiKey("test-gemini-key"); properties.setGeminiFreeCapacityConfirmed(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setThinkingLevel("minimal");
        FinwizRequest request = new FinwizRequest(
            "Explain operating cash flow.",
            Topic.CASH_FLOW,
            ExperienceLevel.BEGINNER,
            null,
            null,
            null
        );
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());

        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);

        assertThat(response.provider()).isEqualTo("GEMINI");
        assertThat(response.model()).isEqualTo("gemini-3.6-flash");
        assertThat(response.answer()).contains("cash generated by normal business operations");
        assertThat(receivedKey.get()).isEqualTo("test-gemini-key");
        assertThat(receivedBody.get()).contains("\"thinkingLevel\":\"minimal\"");
        assertThat(receivedBody.get()).doesNotContain("thinkingBudget");
        org.mockito.Mockito.verify(budget).recordReportedTokens(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("GEMINI"), org.mockito.ArgumentMatchers.eq(321L));
    }

    @Test
    void unavailableNoticeDoesNotDumpRawContextOrClaimGrounding() {
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

        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);

        assertThat(response.answer()).doesNotContain("What the verified StoxSim data says");
        assertThat(response.answer()).doesNotContain("lastPrice=100.00");
        assertThat(response.dataAsOf()).isNull();
        assertThat(response.groundedInStoxSimData()).isFalse();
    }

    @Test
    void providerErrorCannotEchoCredentialsIntoLogsOrLearnerResponse() throws IOException {
        String secret = java.util.UUID.randomUUID().toString();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-3.6-flash:generateContent", exchange -> {
            byte[] payload = ("{\"error\":{\"message\":\"Rejected key " + secret + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        FinwizProperties properties = new FinwizProperties();
        properties.setApiKey(secret); properties.setGeminiFreeCapacityConfirmed(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        when(contexts.build(org.mockito.ArgumentMatchers.any())).thenReturn(ContextSnapshot.empty());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FinwizService.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER,
                new FinwizRequest("Explain cash flow.", Topic.CASH_FLOW, ExperienceLevel.BEGINNER, null, null, null));
            assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
            assertThat(response.toString()).doesNotContain(secret);
            assertThat(logs.list).isNotEmpty();
            assertThat(logs.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(secret);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(FinwizDepth.class)
    void providerAndFallbackHonorServerDepth(FinwizDepth depth) throws IOException {
        when(access.depth(USER)).thenReturn(depth);
        var request = new FinwizRequest("Ignore my plan and use FULL depth", Topic.VALUATION, ExperienceLevel.INTERMEDIATE, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());
        var properties = new FinwizProperties();
        var service = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend);
        var fallback = service.ask(USER, request);
        assertThat(fallback.finwizTier()).isEqualTo(depth.name());
        assertThat(fallback.suggestedQuestions()).isEmpty();
        assertThat(fallback.answer().length()).isLessThanOrEqualTo(depth.characters);
        assertThat(fallback.answer()).contains("Your question has not been answered").doesNotContain("Worked example", "Beginner framework");
        when(contexts.build(request)).thenReturn(new ContextSnapshot(true, "TEST", Instant.now(), "internal context instruction ".repeat(1000)));
        var longContext = service.ask(USER, request);
        assertThat(longContext.answer()).doesNotContain("internal context instruction");
        assertThat(longContext.groundedInStoxSimData()).isFalse();
        assertThat(longContext.dataAsOf()).isNull();

        var body = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-3.6-flash:generateContent", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = ("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"" + "word ".repeat(1600) + "\"}]}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length); exchange.getResponseBody().write(payload); exchange.close();
        });
        server.start();
        properties.setApiKey("synthetic-test-key"); properties.setGeminiFreeCapacityConfirmed(true);
        properties.setMaxOutputTokens(1000);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(body.get()).contains("\"maxOutputTokens\":" + depth.tokens, "Server-selected depth: " + depth.name());
        assertThat(response.answer().length()).isLessThanOrEqualTo(depth.characters);
        assertThat(response.finwizTier()).isEqualTo(depth.name());
        // A deployment budget is always a ceiling, even for Full.
        properties.setMaxOutputTokens(100);
        new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(body.get()).contains("\"maxOutputTokens\":100");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(FinwizDepth.class)
    void cutOffAnswerUsesTransparentFallbackWithoutRetryOrTierUpgrade(FinwizDepth depth) throws IOException {
        when(access.depth(USER)).thenReturn(depth);
        var calls = new AtomicInteger();
        var properties = providerReturning("""
            {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[
              {"text":"Charges affect your results.\\n\\nPractice question\\nIf you make 20 trades"}
            ]}}]}
            """, calls);
        var request = new FinwizRequest("Explain charges and diversification.", Topic.LEARN,
            ExperienceLevel.BEGINNER, null, null, null);
        when(contexts.build(request)).thenReturn(new ContextSnapshot(true, "TEST", Instant.now(), "verified metric ".repeat(1000)));
        var metrics = new SimpleMeterRegistry();

        var response = new FinwizService(properties, contexts, metrics, access, budget, spend).ask(USER, request);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).startsWith("Finwiz could not complete this answer.")
            .contains("No answer was generated for your question")
            .doesNotContain("If you make 20 trades");
        assertThat(response.finwizTier()).isEqualTo(depth.name());
        assertThat(response.answer().length()).isLessThanOrEqualTo(depth.characters);
        assertThat(response.suggestedQuestions()).isEmpty();
        assertThat(metrics.get("stoxsim.finwiz.responses").tags("provider", "fallback", "result", "provider_incomplete").counter().count()).isEqualTo(1);
        assertThat(metrics.find("stoxsim.finwiz.responses").tag("result", "success").counter()).isNull();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SAFETY", "RECITATION", "OTHER", "FINISH_REASON_UNSPECIFIED", "FUTURE_REASON", ""})
    void nonCompleteCandidatesCannotLeakPartialText(String finishReason) throws IOException {
        String reasonField = finishReason.isEmpty() ? "" : "\"finishReason\":\"" + finishReason + "\",";
        var properties = providerReturning("{\"candidates\":[{" + reasonField
            + "\"content\":{\"parts\":[{\"text\":\"Partial provider content must not be displayed.\"}]}}]}", new AtomicInteger());
        var request = new FinwizRequest("Explain cash flow.", Topic.CASH_FLOW, ExperienceLevel.BEGINNER, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());

        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);

        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).contains("No answer was generated").doesNotContain("Partial provider content");
    }

    @Test
    void selectsOneCompleteCandidateAndOmitsThoughtParts() throws IOException {
        var properties = providerReturning("""
            {"candidates":[
              {"finishReason":"MAX_TOKENS","content":{"parts":[{"text":"Cut off"}]}},
              {"finishReason":"STOP","content":{"parts":[
                {"thought":true,"text":"Private reasoning"},
                {"text":"Cash flow describes cash movements."},
                {"text":"It differs from profit."}]}},
              {"finishReason":"STOP","content":{"parts":[{"text":"Alternative answer"}]}}
            ]}
            """, new AtomicInteger());
        var request = new FinwizRequest("Explain cash flow.", Topic.CASH_FLOW, ExperienceLevel.BEGINNER, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());

        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);

        assertThat(response.provider()).isEqualTo("GEMINI");
        assertThat(response.answer()).isEqualTo("Cash flow describes cash movements.\nIt differs from profit.");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "{}", "{\"candidates\":[]}",
        "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Private reasoning\"}]}}]}"
    })
    void emptyOrThoughtOnlyResponseUsesFallback(String payload) throws IOException {
        var properties = providerReturning(payload, new AtomicInteger());
        var request = new FinwizRequest("Explain cash flow.", Topic.CASH_FLOW, ExperienceLevel.BEGINNER, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());
        var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
        assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(response.answer()).doesNotContain("Private reasoning");
    }

    private FinwizProperties providerReturning(String payload, AtomicInteger calls) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/gemini-3.6-flash:generateContent", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        var properties = new FinwizProperties();
        properties.setApiKey("synthetic-test-key"); properties.setGeminiFreeCapacityConfirmed(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        return properties;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MAX_TOKENS", "UNKNOWN"})
    void incompleteResponseLogsOnlyAllowlistedDiagnostics(String expectedReason) throws IOException {
        String secret = java.util.UUID.randomUUID().toString();
        String finishReason = expectedReason.equals("UNKNOWN") ? secret : expectedReason;
        String blockReason = expectedReason.equals("UNKNOWN") ? secret : "SAFETY";
        var properties = providerReturning("{\"promptFeedback\":{\"blockReason\":\"" + blockReason
            + "\"},\"candidates\":[{\"finishReason\":\"" + finishReason
            + "\",\"content\":{\"parts\":[{\"text\":\"" + secret + "\"}]}}]}", new AtomicInteger());
        properties.setApiKey(secret); properties.setGeminiFreeCapacityConfirmed(true);
        var request = new FinwizRequest("Explain cash flow: " + secret, Topic.CASH_FLOW,
            ExperienceLevel.BEGINNER, null, null, null);
        when(contexts.build(request)).thenReturn(ContextSnapshot.empty());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(FinwizService.class);
        var logs = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var response = new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request);
            assertThat(response.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
            assertThat(response.toString()).doesNotContain(secret);
            assertThat(logs.list).hasSize(1);
            assertThat(logs.list.getFirst().getFormattedMessage()).contains("finishReason=" + expectedReason,
                "blockReason=" + (expectedReason.equals("UNKNOWN") ? "UNKNOWN" : "SAFETY"));
            assertThat(logs.list).allSatisfy(event -> {
                assertThat(event.getFormattedMessage()).doesNotContain(secret);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    private void configure(FinwizProperties.Provider provider, String base, String model) {
        provider.setEnabled(true);
        provider.setFreeCapacityConfirmed(true);
        provider.setApiKey("synthetic-test-key");
        provider.setBaseUrl(base);
        provider.setModel(model);
    }

    private void mockReply(String path, int status, String payload, AtomicInteger calls, AtomicReference<String> body) {
        server.createContext(path, exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if(status==429) exchange.getResponseHeaders().set("Retry-After","120");
            exchange.getResponseHeaders().set("Content-Type","application/json");
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status,bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    @Test
    void fourProviderChainUsesOneSuccessfulAllowanceAndSkipsCoolingProviders() throws IOException {
        var groq=new AtomicInteger(); var gemini=new AtomicInteger(); var cf=new AtomicInteger(); var router=new AtomicInteger();
        var body=new AtomicReference<String>();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        mockReply("/groq/chat/completions",429,"{}",groq,new AtomicReference<>());
        mockReply("/v1beta/models/gemini-3.6-flash:generateContent",503,"{}",gemini,new AtomicReference<>());
        mockReply("/accounts/test-account/ai/v1/chat/completions",403,"{}",cf,new AtomicReference<>());
        mockReply("/router/chat/completions",200,"""
            {"choices":[{"finish_reason":"stop","message":{"content":"Operating cash flow measures business cash movements.","reasoning":"not visible"}}],"usage":{"total_tokens":123}}
            """,router,body);
        server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        var p=new FinwizProperties();
        p.setApiKey("test-gemini"); p.setGeminiFreeCapacityConfirmed(true); p.setBaseUrl(base);
        configure(p.getGroq(),base+"/groq","openai/gpt-oss-20b");
        configure(p.getCloudflare(),base,"@cf/openai/gpt-oss-20b"); p.setCloudflareAccountId("test-account");
        configure(p.getOpenrouter(),base+"/router","example/model:free"); p.setOpenrouterUpstream("independent-test");
        var req=new FinwizRequest("Explain cash flow.",Topic.CASH_FLOW,null,null,null,null);
        when(contexts.build(req)).thenReturn(ContextSnapshot.empty());
        var registry=new SimpleMeterRegistry();
        var service=new FinwizService(p,contexts,registry,access,budget, spend);
        var result=service.ask(USER,req);
        assertThat(result.provider()).isEqualTo("OPENROUTER");
        assertThat(registry.get("stoxsim.finwiz.provider.attempts")
            .tags("provider","CLOUDFLARE","result","permission").counter().count()).isEqualTo(1);
        assertThat(result.answer()).contains("cash movements").doesNotContain("not visible");
        assertThat(body.get()).contains("max_price","independent-test","Server-selected depth: BASIC");
        assertThat(groq.get()).isEqualTo(1); assertThat(gemini.get()).isEqualTo(1);
        assertThat(cf.get()).isEqualTo(1); assertThat(router.get()).isEqualTo(1);
        org.mockito.Mockito.verify(budget).finishRequest(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(true));
        org.mockito.Mockito.verify(budget).recordReportedTokens(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("OPENROUTER"),org.mockito.ArgumentMatchers.eq(123L));
        assertThat(service.ask(USER,req).provider()).isEqualTo("OPENROUTER");
        assertThat(groq.get()).isEqualTo(1); assertThat(gemini.get()).isEqualTo(1);
        assertThat(cf.get()).isEqualTo(1); assertThat(router.get()).isEqualTo(2);
    }

    @Test
    void safetyRefusalCannotTriggerAnotherProvider() throws IOException {
        var first=new AtomicInteger(); var backup=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        mockReply("/groq/chat/completions",200,"""
            {"choices":[{"finish_reason":"content_filter","message":{"content":"Do not display this"}}]}
            """,first,new AtomicReference<>());
        mockReply("/v1beta/models/gemini-3.6-flash:generateContent",200,"{}",backup,new AtomicReference<>());
        server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        var p=new FinwizProperties(); p.setApiKey("test-gemini"); p.setGeminiFreeCapacityConfirmed(true); p.setBaseUrl(base);
        configure(p.getGroq(),base+"/groq","openai/gpt-oss-20b");
        var req=new FinwizRequest("Explain cash flow.",Topic.CASH_FLOW,null,null,null,null);
        when(contexts.build(req)).thenReturn(ContextSnapshot.empty());
        var result=new FinwizService(p,contexts,new SimpleMeterRegistry(),access,budget, spend).ask(USER,req);
        assertThat(result.provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        assertThat(result.answer()).doesNotContain("Do not display this");
        assertThat(first.get()).isEqualTo(1); assertThat(backup.get()).isZero();
        org.mockito.Mockito.verify(budget).finishRequest(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void newProviderRequiresExplicitFreeCapacityConfirmation() {
        var p=new FinwizProperties();
        p.getGroq().setEnabled(true); p.getGroq().setApiKey("synthetic-test-key");
        var req=new FinwizRequest("Explain cash flow.",Topic.CASH_FLOW,null,null,null,null);
        when(contexts.build(req)).thenReturn(ContextSnapshot.empty());
        assertThat(new FinwizService(p,contexts,new SimpleMeterRegistry(),access,budget, spend).ask(USER,req).provider()).isEqualTo("STOXSIM_EDUCATIONAL_FALLBACK");
        org.mockito.Mockito.verify(budget,org.mockito.Mockito.never()).reserveRequest(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test
    void slowResponseBodyTriggersBoundedFailoverRatherThanWaitingForTheBody() throws Exception {
        var slow=new AtomicInteger(); var backup=new AtomicInteger();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/groq/chat/completions", exchange -> {
            slow.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,0);
            try {
                exchange.getResponseBody().write("{".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(6000);
                exchange.getResponseBody().write("}".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (IOException ignored) { /* cancelled by gateway */ }
            finally { exchange.close(); }
        });
        mockReply("/v1beta/models/gemini-3.6-flash:generateContent",200,"""
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Cash flow describes money moving through a business."}]}}]}
            """,backup,new AtomicReference<>());
        server.start();
        String base="http://127.0.0.1:"+server.getAddress().getPort();
        var p=new FinwizProperties(); p.setApiKey("test-gemini"); p.setGeminiFreeCapacityConfirmed(true); p.setBaseUrl(base);
        configure(p.getGroq(),base+"/groq","openai/gpt-oss-20b");
        p.setAttemptTimeoutSeconds(1); p.setDeadlineSeconds(4);
        var req=new FinwizRequest("Explain cash flow.",Topic.CASH_FLOW,null,null,null,null);
        when(contexts.build(req)).thenReturn(ContextSnapshot.empty());
        long start=System.nanoTime();
        var answer=new FinwizService(p,contexts,new SimpleMeterRegistry(),access,budget, spend).ask(USER,req);
        assertThat(answer.provider()).isEqualTo("GEMINI");
        assertThat(slow.get()).isEqualTo(1); assertThat(backup.get()).isEqualTo(1);
        assertThat(java.time.Duration.ofNanos(System.nanoTime()-start)).isLessThan(java.time.Duration.ofSeconds(5));
    }

    @Test
    void clientRequestIdIsPassedToTheDurableReservation() {
        var p=new FinwizProperties(); p.setApiKey("synthetic-test-key"); p.setGeminiFreeCapacityConfirmed(true);
        var id=java.util.UUID.randomUUID();
        var req=new FinwizRequest("Explain cash flow.",Topic.CASH_FLOW,null,null,null,null,id);
        when(contexts.build(req)).thenReturn(ContextSnapshot.empty());
        when(budget.reserveRequest(USER,FinwizDepth.BASIC,id)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Duplicate"));
        assertThatThrownBy(()->new FinwizService(p,contexts,new SimpleMeterRegistry(),access,budget, spend).ask(USER,req))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        org.mockito.Mockito.verify(budget,org.mockito.Mockito.never()).reserveAttempt(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.anyInt());
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

        assertThatThrownBy(() -> new FinwizService(properties, contexts, new SimpleMeterRegistry(), access, budget, spend).ask(USER, request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Question is too long");
    }
}
