package com.stoxsim.billing;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class RazorpayLiveClient {
    private final BillingConfig config;private final ObjectMapper json;private final BillingHttp http;
    public RazorpayLiveClient(BillingConfig config,ObjectMapper json,BillingHttp http){this.config=config;this.json=json;this.http=http;}
    public void validatePlan(String plan){var p=request("GET","/plans/"+id(config.razorpayPlan(plan),"plan"),null);
        if(!p.path("id").asText().equals(config.razorpayPlan(plan))||!p.path("period").asText().equals("monthly")||p.path("interval").asInt()!=1
            ||!p.path("item").path("currency").asText().equals("INR")||p.path("item").path("amount").asInt()!=amount(plan))throw BillingHttp.unavailable();}
    public JsonNode create(String plan,UUID reference){return request("POST","/subscriptions",Map.of("plan_id",config.razorpayPlan(plan),"quantity",1,"total_count",12,"customer_notify",true,"notes",Map.of("stoxsim_billing_reference",reference.toString())));}
    public JsonNode fetch(String id){return request("GET","/subscriptions/"+id(id,"sub"),null);}
    public JsonNode cancel(String id,boolean atEnd){return request("POST","/subscriptions/"+id(id,"sub")+"/cancel",Map.of("cancel_at_cycle_end",atEnd));}
    public JsonNode invoices(String id){return request("GET","/invoices?subscription_id="+id(id,"sub")+"&count=100",null);}
    public JsonNode invoice(String id){return request("GET","/invoices/"+id(id,"inv"),null);}
    public JsonNode payment(String id){return request("GET","/payments/"+id(id,"pay"),null);}
    public JsonNode dispute(String id){return request("GET","/disputes/"+id(id,"disp"),null);}
    public static int amount(String plan){return "PLUS".equals(plan)?9900:19900;}
    private String id(String value,String prefix){if(value==null||!value.matches(prefix+"_[A-Za-z0-9]+"))throw BillingHttp.unavailable();return value;}
    private JsonNode request(String method,String path,Object body){
        if(!config.razorpayEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Website billing is disabled");
        return http.send(HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1"+path)).timeout(Duration.ofSeconds(12))
            .header("Content-Type","application/json").header("Authorization","Basic "+Base64.getEncoder().encodeToString((config.keyId+":"+config.keySecret).getBytes(StandardCharsets.UTF_8)))
            .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());}
}
