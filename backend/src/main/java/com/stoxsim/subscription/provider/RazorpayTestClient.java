package com.stoxsim.subscription.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RazorpayTestClient {
    private final RazorpayTestConfig config;
    private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    public RazorpayTestClient(RazorpayTestConfig config,ObjectMapper json) { this.config=config;this.json=json; }
    private String id(String value,String prefix) {
        if(value==null || !value.matches(prefix+"_[A-Za-z0-9]+")) throw unavailable();
        return value;
    }
    public void validatePlan(String plan) {
        var price=request("GET","/plans/"+id(config.planId(plan),"plan"),null);
        if (!price.path("period").asText().equals("monthly") || price.path("interval").asInt()!=1
            || !price.path("item").path("currency").asText().equals("INR")
            || price.path("item").path("amount").asInt()!=(plan.equals("PLUS")?9900:19900))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Configured test plan must match the monthly INR catalog");
    }
    public JsonNode create(String plan,UUID reference) {
        return request("POST","/subscriptions",Map.of("plan_id",config.planId(plan),"quantity",1,
            "total_count",12,"customer_notify",false,"notes",Map.of("stoxsim_test_reference",reference.toString())));
    }
    public JsonNode fetch(String subscription) { return request("GET","/subscriptions/"+id(subscription,"sub"),null); }
    public JsonNode cancel(String subscription) {
        return request("POST","/subscriptions/"+id(subscription,"sub")+"/cancel",Map.of("cancel_at_cycle_end",0));
    }
    private JsonNode request(String method,String path,Object body) {
        if(!config.enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Test billing is disabled");
        try {
            var request=HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1"+path))
                .timeout(Duration.ofSeconds(12)).header("Content-Type","application/json")
                .header("Authorization","Basic "+Base64.getEncoder().encodeToString(
                    (config.keyId+":"+config.keySecret).getBytes(StandardCharsets.UTF_8)))
                .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                byte[] bytes=stream.readNBytes(65537);
                if(response.statusCode()<200 || response.statusCode()>=300 || bytes.length>65536) throw unavailable();
                JsonNode result=json.readTree(bytes);
                if(result==null || !result.isObject()) throw unavailable();
                return result;
            }
        } catch(InterruptedException ex) { Thread.currentThread().interrupt();throw unavailable(); }
          catch(ResponseStatusException ex) { throw ex; }
          catch(Exception ex) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Razorpay test request could not be confirmed; refresh before trying again");
    }
}
