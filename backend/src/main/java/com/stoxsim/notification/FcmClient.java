package com.stoxsim.notification;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** OAuth and FCM errors never expose service credentials or device tokens. */
@Component
public class FcmClient {
    public record Outcome(NotificationTransport.Result result, boolean unregistered) {}
    private final boolean enabled;
    private final String project, credentials;
    private final FcmHttp http;
    private final ObjectMapper json;
    private String accessToken;
    private Instant expires=Instant.EPOCH;
    public FcmClient(@Value("${stoxsim.notifications.fcm-enabled:false}") boolean enabled,
        @Value("${stoxsim.notifications.fcm-project:}") String project,
        @Value("${stoxsim.notifications.fcm-credentials-file:}") String credentials,FcmHttp http,ObjectMapper json) {
        this.enabled=enabled;this.project=project;this.credentials=credentials;this.http=http;this.json=json;
        if(enabled&&(!project.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]")||credentials.isBlank()))
            throw new IllegalStateException("FCM requires a configured project and service identity file");
    }
    public boolean configured(){return enabled;}
    public Outcome send(String device,UUID owner,UUID notification) {
        if(!enabled)return new Outcome(NotificationTransport.Result.PERMANENT_REJECTION,false);
        try {
            // Data-only avoids Android displaying stale account messages before our ownership check.
            var message=Map.of("token",device,"data",Map.of("owner",owner.toString(),"notificationId",notification.toString()),
                "android",Map.of("priority","normal","ttl","300s","collapse_key","stoxsim_updates"));
            var response=http.send(HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send"))
                .timeout(Duration.ofSeconds(12)).header("Authorization","Bearer "+token()).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("message",message)))).build());
            return classify(response);
        } catch(Exception failure) {return new Outcome(NotificationTransport.Result.UNKNOWN,false);}
    }
    static Outcome classify(FcmHttp.Reply reply) {
        if(reply.status()>=200&&reply.status()<300)
            return new Outcome(reply.body().path("name").asText().isBlank()?NotificationTransport.Result.UNKNOWN:NotificationTransport.Result.ACCEPTED,false);
        boolean unregistered=false;
        for(var detail:reply.body().path("error").path("details")) {
            if(detail.path("@type").asText().equals("type.googleapis.com/google.firebase.fcm.v1.FcmError")
                &&detail.path("errorCode").asText().equals("UNREGISTERED"))unregistered=true;
        }
        // Only completed, explicit rejections can be retried. No response => UNKNOWN.
        if(reply.status()==429||reply.status()==500||reply.status()==503)return new Outcome(NotificationTransport.Result.RETRYABLE_REJECTION,false);
        return new Outcome(NotificationTransport.Result.PERMANENT_REJECTION,reply.status()==404&&unregistered);
    }
    private synchronized String token()throws Exception {
        if(accessToken!=null&&Instant.now().isBefore(expires.minusSeconds(60)))return accessToken;
        byte[] bytes;
        try(var stream=Files.newInputStream(Path.of(credentials))){bytes=stream.readNBytes(65537);}
        if(bytes.length>65536)throw new IllegalStateException();
        var keyData=json.readTree(bytes);
        String email=keyData.path("client_email").asText();
        if(!keyData.path("type").asText().equals("service_account")||!email.endsWith(".gserviceaccount.com")
            ||!project.equals(keyData.path("project_id").asText()))throw new IllegalStateException();
        long now=Instant.now().getEpochSecond();
        String header=b64(json.writeValueAsBytes(Map.of("alg","RS256","typ","JWT")));
        String claims=b64(json.writeValueAsBytes(Map.of("iss",email,"scope","https://www.googleapis.com/auth/firebase.messaging",
            "aud","https://oauth2.googleapis.com/token","iat",now,"exp",now+3600)));
        String pem=keyData.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        var key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        var signer=Signature.getInstance("SHA256withRSA");signer.initSign(key);signer.update((header+"."+claims).getBytes(StandardCharsets.UTF_8));
        String assertion=header+"."+claims+"."+b64(signer.sign());
        var reply=http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(12))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(
                "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion="+assertion)).build());
        var response=reply.body();int seconds=response.path("expires_in").asInt();
        if(reply.status()!=200||response.path("access_token").asText().isBlank()||seconds<120||!response.path("token_type").asText().equalsIgnoreCase("Bearer"))throw new IllegalStateException();
        accessToken=response.path("access_token").asText();expires=Instant.now().plusSeconds(Math.min(seconds,3600));return accessToken;
    }
    private String b64(byte[] bytes){return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
}
