package com.stoxsim.billing;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PlayNotificationVerifier {
    private final BillingConfig config;private final JwtDecoder decoder;
    @org.springframework.beans.factory.annotation.Autowired
    public PlayNotificationVerifier(BillingConfig config){this(config,NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());}
    PlayNotificationVerifier(BillingConfig config,JwtDecoder decoder){this.config=config;this.decoder=decoder;}
    public void verify(String authorization){
        if(!config.playEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Play billing is disabled");
        try{
            if(authorization==null||!authorization.startsWith("Bearer ")||authorization.length()>16384)throw new IllegalArgumentException();
            Jwt jwt=decoder.decode(authorization.substring(7));
            if(!List.of("https://accounts.google.com","accounts.google.com").contains(jwt.getClaimAsString("iss"))
                ||jwt.getAudience()==null||!jwt.getAudience().contains(config.notificationAudience)
                ||!config.notificationEmail.equals(jwt.getClaimAsString("email"))||!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                ||jwt.getExpiresAt()==null||!jwt.getExpiresAt().isAfter(Instant.now())||jwt.getIssuedAt()==null||jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60)))throw new IllegalArgumentException();
        }catch(RuntimeException ex){throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Unverified Play notification");}
    }
}
