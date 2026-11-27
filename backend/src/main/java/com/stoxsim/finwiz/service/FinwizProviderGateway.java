package com.stoxsim.finwiz.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import com.stoxsim.finwiz.config.FinwizProperties;
import tools.jackson.databind.JsonNode;

/** One bounded attempt per distinct provider. Never logs request/response bodies or credentials. */
final class FinwizProviderGateway {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FinwizService.class);
    private final FinwizProperties properties;
    private final FinwizUsageBudget budget;
    private final MeterRegistry metrics;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, Long> cooldown = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> slots = new ConcurrentHashMap<>();
    record Candidate(String name, String base, String model, String key, int dailyLimit) {}
    record Result(String answer, String provider, String model, String reason) {
        static Result failure(String reason) { return new Result(null, null, null, reason); }
    }
    FinwizProviderGateway(FinwizProperties properties, FinwizUsageBudget budget, MeterRegistry metrics) {
        this.properties=properties; this.budget=budget; this.metrics=metrics;
    }
    boolean configured() { return !candidates().isEmpty(); }
    private List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        add(result, "GROQ", properties.getGroq(), properties.getGroq().getBaseUrl());
        if (properties.hasApiKey() && properties.getGeminiDailyLimit() > 0)
            result.add(new Candidate("GEMINI", properties.getBaseUrl(), properties.getModel(), properties.getApiKey(), properties.getGeminiDailyLimit()));
        String account = properties.getCloudflareAccountId();
        if (account != null && account.matches("[A-Za-z0-9_-]{1,128}"))
            add(result, "CLOUDFLARE", properties.getCloudflare(), properties.getCloudflare().getBaseUrl() + "/accounts/" + account + "/ai/v1");
        var router = properties.getOpenrouter();
        // Explicit free model and independent upstream required. No unrestricted auto-router.
        String upstream = properties.getOpenrouterUpstream();
        if (router.getModel() != null && router.getModel().endsWith(":free") && upstream != null
            && upstream.matches("[A-Za-z0-9_-]{1,80}") && !List.of("groq","google","google-ai-studio","google-vertex","cloudflare").contains(upstream.toLowerCase(java.util.Locale.ROOT)))
            add(result, "OPENROUTER", router, router.getBaseUrl());
        return result;
    }
    private void add(List<Candidate> list, String name, FinwizProperties.Provider p, String base) {
        if (p.configured()) list.add(new Candidate(name, base, p.getModel(), p.getApiKey(), p.getDailyLimit()));
    }
    Result generate(UUID user, FinwizDepth depth, Map<String,Object> geminiBody) {
        long deadline = System.nanoTime() + Duration.ofSeconds(Math.max(1, Math.min(30,properties.getDeadlineSeconds()))).toNanos();
        String reason = "provider_error";
        for (Candidate p : candidates()) {
            long remaining = deadline-System.nanoTime();
            if (remaining <= 0) break;
            if (cooldown.getOrDefault(p.name(),0L)>System.currentTimeMillis()) continue;
            Semaphore slot = slots.computeIfAbsent(p.name(), ignored -> new Semaphore(Math.max(1,Math.min(32,properties.getProviderConcurrency()))));
            if (!slot.tryAcquire()) continue;
            try {
                if (cooldown.getOrDefault(p.name(),0L)>System.currentTimeMillis()) continue;
                FinwizUsageBudget.Reservation allowance;
                try { allowance=budget.reserve(user,depth,p.name(),p.dailyLimit()); }
                catch (org.springframework.dao.DataAccessException e) {
                    LOGGER.warn("Finwiz usage budget unavailable; returning an unavailable notice");
                    return Result.failure("budget_limited");
                }
                if (allowance==null) { reason="budget_limited"; continue; }
                remaining=deadline-System.nanoTime();
                if (remaining<=0) break;
                var factory=new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofNanos(Math.max(1, Math.min(remaining,Duration.ofSeconds(Math.max(1,Math.min(20,properties.getAttemptTimeoutSeconds()))).toNanos()))));
                // Only operator-configured HTTPS bases (loopback HTTP for test fixtures).
                URI base=URI.create(p.base());
                if (!"https".equals(base.getScheme()) && !("http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost()))) {
                    LOGGER.warn("Finwiz provider configuration rejected");
                    cool(p.name(),300);
                    continue;
                }
                if (base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null || base.getHost()==null) continue;
                var client=RestClient.builder().requestFactory(factory).baseUrl(p.base()).build();
                JsonNode root;
                boolean gemini=p.name().equals("GEMINI");
                if (gemini) root=client.post().uri("/v1beta/models/{model}:generateContent",p.model())
                    .header("x-goog-api-key",p.key()).header("Content-Type","application/json").body(geminiBody).retrieve().body(JsonNode.class);
                else root=client.post().uri("/chat/completions").header("Authorization","Bearer "+p.key())
                    .header("Content-Type","application/json").body(chatBody(p,geminiBody)).retrieve().body(JsonNode.class);
                JsonNode tokens=root==null?null:gemini?root.path("usageMetadata").path("totalTokenCount"):root.path("usage").path("total_tokens");
                if (tokens!=null && tokens.isIntegralNumber()) {
                    try { budget.recordReportedTokens(allowance.day(),tokens.asLong()); }
                    catch (org.springframework.dao.DataAccessException e) { LOGGER.warn("Finwiz aggregate token usage could not be recorded"); }
                }
                String answer=gemini?extractAnswer(root):extractChat(root);
                if (answer!=null && !answer.isBlank() && !refused(p,root)) {
                    metrics.counter("stoxsim.finwiz.provider.attempts","provider",p.name(),"result","success").increment();
                    return new Result(answer,p.name(),p.model(),"success");
                }
                reason="provider_incomplete";
                metrics.counter("stoxsim.finwiz.provider.attempts","provider",p.name(),"result","incomplete").increment();
                if (gemini) {
                    JsonNode candidates=root==null?null:root.get("candidates");
                    JsonNode first=candidates!=null&&candidates.isArray()&&!candidates.isEmpty()?candidates.get(0):null;
                    LOGGER.warn("Finwiz provider returned no complete answer; finishReason={}, blockReason={}; returning an unavailable notice",
                        diagnosticReason(first==null?null:first.get("finishReason")),diagnosticReason(root==null?null:root.path("promptFeedback").get("blockReason")));
                } else LOGGER.warn("Finwiz provider {} returned no complete answer",p.name());
                // Explicit refusals are terminal. Only known truncation or missing output can fail over.
                if (refused(p,root) || !retryableIncomplete(p,root)) return Result.failure(reason);
                cool(p.name(),15);
            } catch (RestClientResponseException e) {
                int status=e.getStatusCode().value();
                reason="provider_http_error";
                metrics.counter("stoxsim.finwiz.provider.attempts","provider",p.name(),"result",status==429?"rate_limited":"http_error").increment();
                LOGGER.warn("Finwiz provider {} request failed with HTTP {}",p.name(),status);
                if(status==429) cool(p.name(),retryAfter(e));
                else if(status>=500 || status==408) cool(p.name(),15);
                else { cool(p.name(),300); return Result.failure(reason); }
            } catch (RestClientException | IllegalStateException | IllegalArgumentException e) {
                reason="provider_error"; cool(p.name(),15);
                metrics.counter("stoxsim.finwiz.provider.attempts","provider",p.name(),"result","error").increment();
                LOGGER.warn("Finwiz provider {} request failed ({})",p.name(),e.getClass().getSimpleName());
            } finally { slot.release(); }
        }
        return Result.failure(reason);
    }
    private void cool(String provider,long seconds) {
        cooldown.merge(provider,System.currentTimeMillis()+Math.max(1,Math.min(86400,seconds))*1000,Math::max);
    }
    private long retryAfter(RestClientResponseException e) {
        String header=e.getResponseHeaders()==null?null:e.getResponseHeaders().getFirst("Retry-After");
        if(header!=null) {
            try { return Math.max(1,Long.parseLong(header.trim())); } catch (RuntimeException ignored) {}
            try { return Math.max(1,Duration.between(Instant.now(),ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); } catch (RuntimeException ignored) {}
        }
        return 60;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> chatBody(Candidate p,Map<String,Object> original) {
        var system=(Map<String,Object>)original.get("systemInstruction");
        var systemPart=((List<Map<String,Object>>)system.get("parts")).get(0);
        var user=((List<Map<String,Object>>)original.get("contents")).get(0);
        var userPart=((List<Map<String,Object>>)user.get("parts")).get(0);
        var config=(Map<String,Object>)original.get("generationConfig");
        var body=new java.util.LinkedHashMap<String,Object>();
        body.put("model",p.model());
        body.put("messages",List.of(Map.of("role","system","content",systemPart.get("text")),Map.of("role","user","content",userPart.get("text"))));
        body.put("max_tokens",config.get("maxOutputTokens"));
        body.put("stream",false);
        if (p.name().equals("GROQ") && p.model().startsWith("openai/gpt-oss")) body.put("reasoning_effort","low");
        if (p.name().equals("OPENROUTER")) body.put("provider",Map.of("only",List.of(properties.getOpenrouterUpstream()),"allow_fallbacks",false,"data_collection","deny","max_price",Map.of("prompt",0,"completion",0)));
        return body;
    }
    private String extractChat(JsonNode root) {
        if(root==null || !root.path("choices").isArray()) return null;
        for(JsonNode choice:root.path("choices")) {
            if (!"stop".equals(choice.path("finish_reason").asText())) continue;
            var content=choice.path("message").path("content");
            if(content.isTextual()&&!content.asText().isBlank()) return content.asText();
        }
        return null;
    }
    private boolean refused(Candidate p,JsonNode root) {
        if(root==null) return false;
        if(p.name().equals("GEMINI")) {
            String block=root.path("promptFeedback").path("blockReason").asText();
            if (!block.isBlank() && !"BLOCK_REASON_UNSPECIFIED".equals(block)) return true;
            for(JsonNode candidate:root.path("candidates")) {
                if(List.of("SAFETY","RECITATION","BLOCKLIST","PROHIBITED_CONTENT","SPII","IMAGE_SAFETY").contains(candidate.path("finishReason").asText())) return true;
            }
        } else for(JsonNode choice:root.path("choices")) {
            if("content_filter".equals(choice.path("finish_reason").asText()) || "safety".equals(choice.path("finish_reason").asText())) return true;
            var refusal=choice.path("message").path("refusal");
            if(!refusal.isMissingNode()&&!refusal.isNull()&&!refusal.asText().isBlank()) return true;
        }
        return false;
    }
    private boolean retryableIncomplete(Candidate p,JsonNode root) {
        if(root==null) return true;
        boolean gemini=p.name().equals("GEMINI");
        JsonNode choices=root.path(gemini?"candidates":"choices");
        if(!choices.isArray()||choices.isEmpty()) return true;
        for(JsonNode choice:choices) {
            String finish=choice.path(gemini?"finishReason":"finish_reason").asText();
            if(!List.of(gemini?"STOP":"stop",gemini?"MAX_TOKENS":"length").contains(finish)) return false;
        }
        return true;
    }

    private String extractAnswer(JsonNode root) {
        if (root == null) return null;
        JsonNode candidates = root.get("candidates");
        if (candidates == null || !candidates.isArray()) return null;

        for (JsonNode candidate : candidates) {
            // This is a non-streaming request: only STOP confirms a completed
            // candidate. Missing/unknown reasons must not count as success.
            if (!"STOP".equals(candidate.path("finishReason").asText())) continue;
            JsonNode content = candidate.get("content");
            if (content == null) continue;
            JsonNode parts = content.get("parts");
            if (parts == null || !parts.isArray()) continue;
            StringBuilder answer = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) continue;
                String text = part.path("text").asText();
                if (!text.isBlank()) {
                    if (!answer.isEmpty()) answer.append('\n');
                    answer.append(text);
                }
            }
            // Candidates are alternatives, not consecutive pieces of one answer.
            if (!answer.isEmpty()) return answer.toString();
        }
        return null;
    }

    private String diagnosticReason(JsonNode reason) {
        if (reason == null || reason.isNull()) return "MISSING";
        // Provider fields are untrusted. Log only known enums, never free text,
        // response bodies, learner prompts, or exception messages.
        return switch (reason.asText()) {
            case "STOP", "MAX_TOKENS", "SAFETY", "RECITATION", "LANGUAGE", "OTHER",
                 "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "MALFORMED_FUNCTION_CALL",
                 "IMAGE_SAFETY", "FINISH_REASON_UNSPECIFIED", "BLOCK_REASON_UNSPECIFIED" -> reason.asText();
            default -> "UNKNOWN";
        };
    }


}
