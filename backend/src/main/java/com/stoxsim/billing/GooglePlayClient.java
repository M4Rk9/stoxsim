package com.stoxsim.billing;

import java.net.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Android Publisher server API; tokens and provider error bodies never leave this boundary. */
@Component
public class GooglePlayClient {
    private final BillingConfig config;private final ObjectMapper json;private final BillingHttp http;
    private String accessToken;private Instant tokenExpires=Instant.EPOCH;
    public GooglePlayClient(BillingConfig config,ObjectMapper json,BillingHttp http){this.config=config;this.json=json;this.http=http;}
    public JsonNode fetch(String token){return request("GET","/purchases/subscriptionsv2/tokens/"+segment(token),null);}
    public void acknowledge(String product,String token){request("POST","/purchases/subscriptions/"+segment(product)+"/tokens/"+segment(token)+":acknowledge",Map.of());}
    public boolean wasVoided(String purchaseToken,String orderId){
        if(orderId==null||orderId.isBlank())return false;
        String page="";
        for(int i=0;i<3;i++){
            JsonNode response=request("GET","/purchases/voidedpurchases?type=1&includeQuantityBasedPartialRefund=true&maxResults=100"+(page.isEmpty()?"":"&token="+segment(page)),null);
            for(JsonNode item:response.path("voidedPurchases"))if(purchaseToken.equals(item.path("purchaseToken").asText())&&orderId.equals(item.path("orderId").asText()))return true;
            page=response.path("tokenPagination").path("nextPageToken").asText();if(page.isBlank())return false;
        }
        throw BillingHttp.unavailable(); // Incomplete bounded scans cannot assert that an order was not voided.
    }
    private String segment(String value){if(value==null||value.isBlank()||value.length()>4096)throw BillingHttp.unavailable();return URLEncoder.encode(value,StandardCharsets.UTF_8).replace("+","%20");}
    private JsonNode request(String method,String path,Object body){
        if(!config.playEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Play billing is disabled");
        try{return http.send(HttpRequest.newBuilder(URI.create("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"+segment(config.packageName)+path)).timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+token()).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build());}
        catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw BillingHttp.unavailable();}}
    private synchronized String token()throws Exception{
        if(accessToken!=null&&Instant.now().isBefore(tokenExpires.minusSeconds(60)))return accessToken;
        JsonNode credentials;
        try(var input=Files.newInputStream(Path.of(config.googleCredentialsFile))){byte[] bytes=input.readNBytes(65537);if(bytes.length>65536)throw BillingHttp.unavailable();credentials=json.readTree(bytes);}
        String email=credentials.path("client_email").asText();
        if(!credentials.path("type").asText().equals("service_account")||!email.endsWith(".gserviceaccount.com"))throw BillingHttp.unavailable();
        long now=Instant.now().getEpochSecond();String header=b64(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
        String claims=b64(json.writeValueAsBytes(Map.of("iss",email,"scope","https://www.googleapis.com/auth/androidpublisher","aud","https://oauth2.googleapis.com/token","iat",now,"exp",now+3600)));
        String pem=credentials.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        var key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Signature signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update((header+"."+claims).getBytes(StandardCharsets.UTF_8));
        String assertion=header+"."+claims+"."+b64(signer.sign());
        var response=http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(12)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="+assertion)).build());
        String token=response.path("access_token").asText();int seconds=response.path("expires_in").asInt();
        if(token.isBlank()||seconds<120||!response.path("token_type").asText().equalsIgnoreCase("Bearer"))throw BillingHttp.unavailable();
        accessToken=token;tokenExpires=Instant.now().plusSeconds(Math.min(seconds,3600));return token;
    }
    private String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
}
