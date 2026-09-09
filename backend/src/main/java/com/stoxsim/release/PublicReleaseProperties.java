package com.stoxsim.release;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.release")
public class PublicReleaseProperties {

    private boolean publicRegistrationEnabled = true;

    public boolean isPublicRegistrationEnabled() {
        return publicRegistrationEnabled;
    }

    public void setPublicRegistrationEnabled(boolean publicRegistrationEnabled) {
        this.publicRegistrationEnabled = publicRegistrationEnabled;
    }
}
