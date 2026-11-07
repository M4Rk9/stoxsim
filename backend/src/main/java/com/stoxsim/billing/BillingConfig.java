package com.stoxsim.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BillingConfig {
    public final boolean razorpayEnabled,playEnabled;
    public final String keyId,keySecret,webhookSecret,plusPlan,proPlan,packageName,plusProduct,proProduct,plusBasePlan,proBasePlan;
    public final String googleCredentialsFile,tokenEncryptionKey,notificationAudience,notificationEmail;
    public BillingConfig(
        @Value("${stoxsim.billing.live.approved:${STOXSIM_LIVE_PAYMENTS_APPROVED:false}}") boolean approved,
        @Value("${stoxsim.billing.live.enabled:${STOXSIM_BILLING_LIVE_ENABLED:false}}") boolean razorpay,
        @Value("${stoxsim.billing.live.key-id:${STOXSIM_BILLING_LIVE_KEY_ID:}}") String keyId,
        @Value("${stoxsim.billing.live.key-secret:${STOXSIM_BILLING_LIVE_KEY_SECRET:}}") String keySecret,
        @Value("${stoxsim.billing.live.webhook-secret:${STOXSIM_BILLING_LIVE_WEBHOOK_SECRET:}}") String webhookSecret,
        @Value("${stoxsim.billing.live.plus-plan:${STOXSIM_BILLING_LIVE_PLUS_PLAN:}}") String plusPlan,
        @Value("${stoxsim.billing.live.pro-plan:${STOXSIM_BILLING_LIVE_PRO_PLAN:}}") String proPlan,
        @Value("${stoxsim.billing.play.enabled:${STOXSIM_PLAY_BILLING_ENABLED:false}}") boolean play,
        @Value("${stoxsim.billing.play.package-name:com.stoxsim.app}") String packageName,
        @Value("${stoxsim.billing.play.plus-product:${STOXSIM_PLAY_PLUS_PRODUCT:}}") String plusProduct,
        @Value("${stoxsim.billing.play.pro-product:${STOXSIM_PLAY_PRO_PRODUCT:}}") String proProduct,
        @Value("${stoxsim.billing.play.plus-base-plan:${STOXSIM_PLAY_PLUS_BASE_PLAN:}}") String plusBasePlan,
        @Value("${stoxsim.billing.play.pro-base-plan:${STOXSIM_PLAY_PRO_BASE_PLAN:}}") String proBasePlan,
        @Value("${stoxsim.billing.play.credentials-file:${STOXSIM_PLAY_CREDENTIALS_FILE:}}") String credentials,
        @Value("${stoxsim.billing.play.token-encryption-key:${STOXSIM_PLAY_TOKEN_ENCRYPTION_KEY:}}") String encryption,
        @Value("${stoxsim.billing.play.notification-audience:${STOXSIM_PLAY_NOTIFICATION_AUDIENCE:}}") String audience,
        @Value("${stoxsim.billing.play.notification-email:${STOXSIM_PLAY_NOTIFICATION_EMAIL:}}") String email) {
        this.razorpayEnabled=approved&&razorpay;this.playEnabled=approved&&play;
        this.keyId=keyId;this.keySecret=keySecret;this.webhookSecret=webhookSecret;this.plusPlan=plusPlan;this.proPlan=proPlan;
        this.packageName=packageName;this.plusProduct=plusProduct;this.proProduct=proProduct;this.plusBasePlan=plusBasePlan;this.proBasePlan=proBasePlan;
        googleCredentialsFile=credentials;tokenEncryptionKey=encryption;notificationAudience=audience;notificationEmail=email;
        if((razorpay||play)&&!approved) throw new IllegalStateException("Live billing requires explicit owner activation approval");
        if(razorpayEnabled&&(!keyId.matches("rzp_live_[A-Za-z0-9]+")||keySecret.isBlank()||webhookSecret.length()<32
            ||!plusPlan.matches("plan_[A-Za-z0-9]+")||!proPlan.matches("plan_[A-Za-z0-9]+")||plusPlan.equals(proPlan)))
            throw new IllegalStateException("Live Razorpay requires live credentials and distinct configured plans");
        if(playEnabled&&(!packageName.equals("com.stoxsim.app")||!product(plusProduct)||!product(proProduct)||plusProduct.equals(proProduct)
            ||!product(plusBasePlan)||!product(proBasePlan)||credentials.isBlank()||!encryption.matches("[a-fA-F0-9]{64}")
            ||!audience.startsWith("https://")||!email.endsWith(".gserviceaccount.com")))
            throw new IllegalStateException("Play billing requires catalog, service credentials, encryption and authenticated notification configuration");
    }
    private boolean product(String value) {return value.matches("[a-z0-9][a-z0-9_.-]{0,149}");}
    public String razorpayPlan(String plan) {return switch(plan){case "PLUS"->plusPlan;case "PRO"->proPlan;default->throw new IllegalArgumentException("Unsupported plan");};}
    public String playBasePlan(String plan) {return "PLUS".equals(plan)?plusBasePlan:proBasePlan;}
}
