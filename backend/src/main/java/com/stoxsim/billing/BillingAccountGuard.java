package com.stoxsim.billing;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingAccountGuard {
    private final JdbcTemplate db;
    public BillingAccountGuard(JdbcTemplate db){this.db=db;}
    /** Caller holds the user row lock. Revocation alone is not proof that renewal stopped. */
    public void requireDeletionAllowed(UUID actor){
        Integer pending=db.queryForObject("SELECT count(*) FROM billing_purchase WHERE user_id=? AND cancellation_status<>'CONFIRMED' AND renewal_stopped=false",Integer.class,actor);
        if(pending>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancel renewal with your billing provider and refresh billing before deleting this account");}
    public List<Map<String,Object>> export(UUID actor){return db.queryForList("SELECT id,provider,plan,status,paid_through,access_until,cancellation_status,created_at FROM billing_purchase WHERE user_id=? ORDER BY created_at",actor);}
}
