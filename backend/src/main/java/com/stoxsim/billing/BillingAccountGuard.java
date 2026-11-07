package com.stoxsim.billing;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BillingAccountGuard {
    private final JdbcTemplate db;
    private final LiveBillingService billing;
    public BillingAccountGuard(JdbcTemplate db,LiveBillingService billing){this.db=db;this.billing=billing;}
    /** Caller holds the user row lock. Revocation alone is not proof that renewal stopped. */
    public void requireDeletionAllowed(UUID actor){
        // A canceled Play subscription may restart before its paid period ends,
        // independently of our webhook/reconciliation schedule. Recheck it now.
        var restartable=db.queryForList("SELECT id FROM billing_purchase WHERE user_id=? AND (renewal_stopped=false OR paid_through>now() OR access_until>now()) ORDER BY id LIMIT 21",UUID.class,actor);
        if(restartable.size()>20)throw new ResponseStatusException(HttpStatus.CONFLICT,"Reconcile billing with support before deleting this account");
        for(UUID purchase:restartable)billing.refresh(actor,purchase);
        Integer pending=db.queryForObject("SELECT count(*) FROM billing_purchase WHERE user_id=? AND renewal_stopped=false",Integer.class,actor);
        if(pending>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Cancel renewal with your billing provider and refresh billing before deleting this account");}
    public List<Map<String,Object>> export(UUID actor){return db.queryForList("SELECT id,provider,plan,status,paid_through,access_until,cancellation_status,created_at FROM billing_purchase WHERE user_id=? ORDER BY created_at",actor);}
}
