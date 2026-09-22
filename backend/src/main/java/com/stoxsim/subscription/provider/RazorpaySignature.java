package com.stoxsim.subscription.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class RazorpaySignature {
    private RazorpaySignature() {}
    public static boolean valid(byte[] body,String signature,String secret) {
        if(signature==null || !signature.matches("[a-fA-F0-9]{64}") || secret.isBlank()) return false;
        try {
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature));
        } catch(Exception ex) { return false; }
    }
}
