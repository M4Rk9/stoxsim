package com.stoxsim.auth.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.domain.AppUser;

@Service
public class AccountMailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountMailService.class);

    private final AuthProperties properties;
    private final String frontendUrl;

    public AccountMailService(
        AuthProperties properties,
        @Value("${stoxsim.frontend-url}") String frontendUrl
    ) {
        this.properties = properties;
        this.frontendUrl = frontendUrl;
    }

    @Async
    public void sendVerification(AppUser user, String token) {
        String link = frontendUrl + "/verify-email?token=" + encode(token);
        send(
            user,
            "Verify your StoxSim email",
            "Hello " + user.getDisplayName() + ",\n\n"
                + "Verify your email address by opening this link:\n" + link + "\n\n"
                + "This single-use link expires in 24 hours. If you did not create "
                + "a StoxSim account, you can ignore this message."
        );
    }

    @Async
    public void sendPasswordReset(AppUser user, String token) {
        String link = frontendUrl + "/reset-password?token=" + encode(token);
        send(
            user,
            "Reset your StoxSim password",
            "Hello " + user.getDisplayName() + ",\n\n"
                + "Reset your password by opening this link:\n" + link + "\n\n"
                + "This single-use link expires in 30 minutes. If you did not request "
                + "a reset, no action is required."
        );
    }

    public boolean sendWeeklyPortfolioReport(AppUser user, String subject, String body) {
        return send(user, subject, body);
    }

    private boolean send(AppUser user, String subject, String body) {
        if (!StringUtils.hasText(properties.getMailHost())) {
            LOGGER.warn("Account email not sent because MAIL_HOST is not configured");
            return false;
        }

        var sender = new JavaMailSenderImpl();
        sender.setHost(properties.getMailHost());
        sender.setPort(properties.getMailPort());
        sender.setUsername(properties.getMailUsername());
        sender.setPassword(properties.getMailPassword());

        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(properties.getMailUsername())));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(properties.isMailStartTls()));
        javaMail.put("mail.smtp.connectiontimeout", "10000");
        javaMail.put("mail.smtp.timeout", "10000");
        javaMail.put("mail.smtp.writetimeout", "10000");

        var message = new SimpleMailMessage();
        message.setFrom(properties.getMailFrom());
        message.setTo(user.getEmail());
        message.setSubject(subject);
        message.setText(body);

        try {
            sender.send(message);
            return true;
        } catch (MailException exception) {
            LOGGER.error("Account email delivery failed for user {}", user.getId(), exception);
            return false;
        }
    }

    private String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
