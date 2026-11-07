package com.stoxsim.billing;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.stereotype.Component;

/** Purchase credentials are encrypted at rest; only hashes are used for unique ownership. */
@Component
public class PurchaseTokenVault {
    private final BillingConfig config;
    public PurchaseTokenVault(BillingConfig config){this.config=config;}
    public String hash(String value){validate(value);try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("Token hashing unavailable");}}
    public String encrypt(String value){validate(value);try{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(cipher(Cipher.ENCRYPT_MODE,iv).doFinal(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("Token encryption unavailable");}}
    public String decrypt(String value){try{String[] parts=value.split(":",2);return new String(cipher(Cipher.DECRYPT_MODE,Base64.getDecoder().decode(parts[0])).doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);}catch(Exception ex){throw new IllegalStateException("Token decryption unavailable");}}
    private Cipher cipher(int mode,byte[] iv)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(HexFormat.of().parseHex(config.tokenEncryptionKey),"AES"),new GCMParameterSpec(128,iv));cipher.updateAAD(config.packageName.getBytes(StandardCharsets.UTF_8));return cipher;}
    private void validate(String value){if(value==null||value.isBlank()||value.length()>4096||value.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid purchase token");}
}
