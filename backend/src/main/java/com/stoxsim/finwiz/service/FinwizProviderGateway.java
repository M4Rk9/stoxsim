package com.stoxsim.finwiz.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import tools.jackson.databind.json.JsonMapper;
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
import com.stoxsim.finwiz.config.FinwizProperties;
import tools.jackson.databind.JsonNode;

/** One bounded attempt per distinct provider. Never logs request/response bodies or credentials. */
final class FinwizProviderGateway {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(FinwizService.class);
    private final FinwizProperties properties;
    private final FinwizUsageBudget budget;
    private final MeterRegistry metrics;
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, FinwizCircuit> circuits = new ConcurrentHashMap<>();
    private final FinwizSpendBudget spend;
    private final Map<String, Semaphore> slots = new ConcurrentHashMap<>();
    record Candidate(String name, String base, String model, String key, int dailyLimit, int rpm, int tpm, int tpd) {
        boolean paid() { return name.equals("GEMINI_PAID"); }
        boolean gemini() { return paid() || name.equals("GEMINI"); }
        @Override public String toString() { return name; } }
    record Result(String answer, String provider, String model, String reason) {
        static Result failure(String reason) { return new Result(null, null, null, reason); }
    }
    FinwizProviderGateway(FinwizProperties properties, FinwizUsageBudget budget, MeterRegistry metrics, FinwizSpendBudget spend) {
        this.properties=properties; this.budget=budget; this.metrics=metrics; this.spend=spend;
    }
    boolean configured() { return !candidates(FinwizDepth.FULL).isEmpty(); }
    private List<Candidate> candidates(FinwizDepth depth) {
        List<Candidate> result = new ArrayList<>();
        add(result, "GROQ", properties.getGroq(), properties.getGroq().getBaseUrl());
        if (properties.hasApiKey() && properties.isGeminiFreeCapacityConfirmed() && properties.getGeminiDailyLimit() > 0)
            result.add(new Candidate("GEMINI", properties.getBaseUrl(), properties.getModel(), properties.getApiKey(), properties.getGeminiDailyLimit(), properties.getGeminiRequestsPerMinute(), properties.getGeminiTokensPerMinute(), properties.getGeminiTokensPerDay()));
        String account = properties.getCloudflareAccountId();
        if (account != null && account.matches("[A-Za-z0-9_-]{1,128}"))
            add(result, "CLOUDFLARE", properties.getCloudflare(), properties.getCloudflare().getBaseUrl() + "/accounts/" + account + "/ai/v1");
        var router = properties.getOpenrouter();
        // Explicit free model and independent upstream required. No unrestricted auto-router.
        String upstream = properties.getOpenrouterUpstream();
        if (router.getModel() != null && router.getModel().endsWith(":free") && upstream != null
            && upstream.matches("[A-Za-z0-9_-]{1,80}") && !List.of("groq","google","google-ai-studio","google-vertex","cloudflare").contains(upstream.toLowerCase(java.util.Locale.ROOT)))
            add(result, "OPENROUTER", router, router.getBaseUrl());
        if (depth != FinwizDepth.BASIC) {
            result.sort(java.util.Comparator.comparingInt(Candidate::tpm).reversed());
            var paid = spend.settings();
            if (paid.enabled()) {
                result.add(new Candidate("GEMINI_PAID", "https://generativelanguage.googleapis.com", paid.model(), paid.apiKey(),
                    paid.dailyAttempts(), paid.requestsPerMinute(), paid.tokensPerMinute(), paid.tokensPerDay()));
            }
        }
        return result;
    }
    private void add(List<Candidate> list, String name, FinwizProperties.Provider p, String base) {
        if (p.configured()) list.add(new Candidate(name, base, p.getModel(), p.getApiKey(), p.getDailyLimit(), p.getRequestsPerMinute(), p.getTokensPerMinute(), p.getTokensPerDay()));
    }
    Result generate(UUID user, FinwizDepth depth, UUID requestId, Map<String,Object> geminiBody,
        java.util.function.Predicate<String> validate) {
        FinwizUsageBudget.RequestReservation request;
        try { request=requestId==null?budget.reserveRequest(user,depth):budget.reserveRequest(user,depth,requestId); }
        catch(org.springframework.dao.DataAccessException e) { LOGGER.warn("Finwiz usage budget unavailable; returning an unavailable notice"); return Result.failure("budget_limited"); }
        if(request==null) return Result.failure("budget_limited");
        boolean settled=false;
        try {
            Result result=dispatch(request,depth,geminiBody);
            if (result.answer()!=null && !validate.test(result.answer())) result=Result.failure("output_rejected");
            settled=budget.finishRequest(request,result.answer()!=null);
            return settled?result:Result.failure("budget_limited");
        } catch(org.springframework.dao.DataAccessException e) {
            LOGGER.warn("Finwiz request settlement unavailable");
            return Result.failure("budget_limited");
        } finally {
            if(!settled) {
                try { budget.finishRequest(request,false); }
                catch(org.springframework.dao.DataAccessException e) { LOGGER.warn("Finwiz request lease will expire"); }
            }
        }
    }
    private Result dispatch(FinwizUsageBudget.RequestReservation request, FinwizDepth depth, Map<String,Object> geminiBody) {
        long deadline = System.nanoTime() + Duration.ofSeconds(Math.max(1, Math.min(30,properties.getDeadlineSeconds()))).toNanos();
        String reason = "provider_error";
        for (Candidate p : candidates(depth)) {
            long remaining = deadline-System.nanoTime();
            if (remaining <= 0) break;
            var circuit = circuits.computeIfAbsent(p.name(), name -> {
                var entry = new FinwizCircuit(System::nanoTime);
                metrics.gauge("stoxsim.finwiz.provider.circuit", List.of(io.micrometer.core.instrument.Tag.of("provider",name)),
                    entry, FinwizCircuit::state);
                return entry;
            });
            int concurrency = FinwizCapacity.providerShare(properties, Math.clamp(properties.getProviderConcurrency(),1,32), depth, p.paid());
            if (concurrency == 0) { reason = "budget_limited"; continue; }
            Semaphore slot = slots.computeIfAbsent(p.name() + ":" + depth, ignored -> new Semaphore(concurrency,true));
            try {
                if (!slot.tryAcquire(Math.min(remaining,TimeUnit.MILLISECONDS.toNanos(100)),TimeUnit.NANOSECONDS)) {
                    record(p,"concurrency_limited");
                    reason = "budget_limited";
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failure("provider_error");
            }
            int permit = circuit.enter();
            if (permit < 0) {
                slot.release();
                continue;
            }
            UUID spendId = null;
            JsonNode paidUsage = null;
            try {
                boolean gemini=p.gemini();
                URI base=URI.create(p.base());
                if (!"https".equals(base.getScheme()) && !("http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost()))) {
                    LOGGER.warn("Finwiz provider configuration rejected"); circuit.open(300); continue;
                }
                if (base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null || base.getHost()==null) continue;
                if(gemini && !p.model().matches("[A-Za-z0-9._-]{1,128}")) continue;
                byte[] payload=json.writeValueAsBytes(gemini?geminiBody:chatBody(p,geminiBody));
                // Conservative admission estimate, not an exact tokenizer or invoice. Count UTF-8 bytes,
                // requested completion and extra reasoning headroom. Never refund a failed attempt.
                int output=((Number)((Map<?,?>)geminiBody.get("generationConfig")).get("maxOutputTokens")).intValue();
                int estimate=Math.toIntExact((long)payload.length+2L*output+256);
                FinwizUsageBudget.Reservation allowance;
                try { allowance=budget.reserveAttempt(request,depth,p.name(),p.dailyLimit(),p.rpm(),p.tpm(),p.tpd(),estimate); }
                catch (org.springframework.dao.DataAccessException e) {
                    LOGGER.warn("Finwiz usage budget unavailable; returning an unavailable notice");
                    return Result.failure("budget_limited");
                }
                if (allowance==null) { record(p,"admission_limited"); reason="budget_limited"; continue; }
                if (p.paid()) {
                    spendId = spend.reserve(request, depth, payload.length + 256, output);
                    if (spendId == null) { reason = "budget_limited"; continue; }
                }
                remaining=deadline-System.nanoTime();
                if (remaining<=0) break;
                long nanos=Math.max(1,Math.min(remaining,Duration.ofSeconds(Math.max(1,Math.min(20,properties.getAttemptTimeoutSeconds()))).toNanos()));
                URI endpoint=URI.create(p.base().replaceAll("/+$","")+(gemini?"/v1beta/models/"+p.model()+":generateContent":"/chat/completions"));
                var outgoing=HttpRequest.newBuilder(endpoint).timeout(Duration.ofNanos(nanos))
                    .header("Content-Type","application/json")
                    .header(gemini?"x-goog-api-key":"Authorization",gemini?p.key():"Bearer "+p.key())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                var pending=http.sendAsync(outgoing, info -> new BoundedBody());
                HttpResponse<byte[]> reply;
                try { reply=pending.get(nanos,TimeUnit.NANOSECONDS); }
                catch (TimeoutException | ExecutionException e) { pending.cancel(true); throw new IllegalStateException("Provider transport incomplete"); }
                catch (InterruptedException e) { pending.cancel(true); Thread.currentThread().interrupt(); return Result.failure("provider_error"); }
                int status=reply.statusCode();
                if(status<200||status>=300) {
                    String category = httpFailure(p, status);
                    reason = "provider_http_error";
                    record(p, category);
                    LOGGER.warn("Finwiz provider {} failed: {} (HTTP {})",p.name(),category,status);
                    switch (category) {
                        case "rate_limited" -> circuit.open(retryAfter(reply.headers().firstValue("Retry-After").orElse(null)));
                        case "transient" -> circuit.open(15);
                        case "authentication", "permission" -> circuit.open(300);
                        default -> { circuit.healthy(permit); return Result.failure(reason); }
                    }
                    continue;
                }
                JsonNode root=json.readTree(reply.body());
                if (p.paid()) paidUsage = root == null ? null : root.path("usageMetadata");
                JsonNode tokens=root==null?null:gemini?root.path("usageMetadata").path("totalTokenCount"):root.path("usage").path("total_tokens");
                if (tokens!=null && tokens.isIntegralNumber()) {
                    try { budget.recordReportedTokens(allowance.day(),p.name(),tokens.asLong()); }
                    catch (org.springframework.dao.DataAccessException e) { LOGGER.warn("Finwiz aggregate token usage could not be recorded"); }
                }
                String answer=gemini?extractAnswer(root):extractChat(root);
                if (answer!=null && !answer.isBlank() && !refused(p,root)) {
                    circuit.healthy(permit);
                    record(p, "success");
                    return new Result(answer,gemini?"GEMINI":p.name(),p.model(),"success");
                }
                reason="provider_incomplete";
                record(p, "incomplete");
                if (gemini) {
                    JsonNode candidates=root==null?null:root.get("candidates");
                    JsonNode first=candidates!=null&&candidates.isArray()&&!candidates.isEmpty()?candidates.get(0):null;
                    LOGGER.warn("Finwiz provider returned no complete answer; finishReason={}, blockReason={}; returning an unavailable notice",
                        diagnosticReason(first==null?null:first.get("finishReason")),diagnosticReason(root==null?null:root.path("promptFeedback").get("blockReason")));
                } else LOGGER.warn("Finwiz provider {} returned no complete answer",p.name());
                // Explicit refusals are terminal. Only known truncation or missing output can fail over.
                if (refused(p,root) || !retryableIncomplete(p,root)) { circuit.healthy(permit); return Result.failure(reason); }
                circuit.open(15);
            } catch (org.springframework.dao.DataAccessException e) {
                LOGGER.warn("Finwiz spending admission unavailable");
                return Result.failure("budget_limited");
            } catch (RuntimeException e) {
                reason="provider_error"; circuit.open(15);
                record(p, "error");
                LOGGER.warn("Finwiz provider {} request failed ({})",p.name(),e.getClass().getSimpleName());
            } finally {
                circuit.release(permit);
                slot.release();
                if (spendId != null) {
                    try { spend.settle(spendId, paidUsage); }
                    catch (org.springframework.dao.DataAccessException e) { LOGGER.warn("Finwiz paid usage reconciliation deferred"); }
                }
            }
        }
        return Result.failure(reason);
    }
    private void record(Candidate provider, String category) {
        metrics.counter("stoxsim.finwiz.provider.attempts","provider",provider.name(),"result",category).increment();
    }

    private String httpFailure(Candidate provider, int status) {
        if (status == 429) return "rate_limited";
        if (status >= 500 || status == 408) return "transient";
        if (status == 401) return "authentication";
        if (status == 404 || (status == 403 && !provider.name().equals("OPENROUTER"))) return "permission";
        // A router/guardrail denial and unclassified client errors are terminal.
        return "request_rejected";
    }

    private long retryAfter(String header) {
        if(header!=null) {
            try { return Math.max(1,Long.parseLong(header.trim())); } catch (RuntimeException ignored) {}
            try { return Math.max(1,Duration.between(Instant.now(),ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); } catch (RuntimeException ignored) {}
        }
        return 60;
    }
    /** Limit even a hostile/incorrect upstream response; completion waits for the entire body. */
    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(Flow.Subscription s) { subscription=s; delegate.onSubscribe(s); }
        public void onNext(List<ByteBuffer> buffers) {
            for(ByteBuffer b:buffers) received+=b.remaining();
            if(received>262144) { subscription.cancel(); delegate.onError(new IllegalStateException("Provider body exceeds limit")); }
            else delegate.onNext(buffers);
        }
        public void onError(Throwable error) { delegate.onError(error); }
        public void onComplete() { delegate.onComplete(); }
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
        body.put(p.name().equals("GROQ") ? "max_completion_tokens" : "max_tokens",config.get("maxOutputTokens"));
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
        if(p.gemini()) {
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
        if (root == null) return true;
        if (root.path("error").isObject() || root.path("errors").isArray() && !root.path("errors").isEmpty()) return false;
        boolean gemini=p.gemini();
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
