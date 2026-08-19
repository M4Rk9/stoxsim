package com.stoxsim.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.auth")
public class AuthProperties {

    private String jwtSecret;
    private long accessTokenMinutes = 15;
    private long refreshTokenDays = 30;
    private boolean cookieSecure;
    private long emailVerificationMinutes = 1440;
    private long passwordResetMinutes = 30;
    private String mailHost;
    private int mailPort = 587;
    private String mailUsername;
    private String mailPassword;
    private String mailFrom = "no-reply@stoxsim.com";
    private boolean mailStartTls = true;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(long accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public long getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(long refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public long getEmailVerificationMinutes() {
        return emailVerificationMinutes;
    }

    public void setEmailVerificationMinutes(long emailVerificationMinutes) {
        this.emailVerificationMinutes = emailVerificationMinutes;
    }

    public long getPasswordResetMinutes() {
        return passwordResetMinutes;
    }

    public void setPasswordResetMinutes(long passwordResetMinutes) {
        this.passwordResetMinutes = passwordResetMinutes;
    }

    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public int getMailPort() {
        return mailPort;
    }

    public void setMailPort(int mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public boolean isMailStartTls() {
        return mailStartTls;
    }

    public void setMailStartTls(boolean mailStartTls) {
        this.mailStartTls = mailStartTls;
    }
}
