package com.stoxsim.notification;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component
public class NotificationEvents {
 private final NotificationTransactions transactions;
 public NotificationEvents(NotificationTransactions transactions){this.transactions=transactions;}
 @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT,fallbackExecution=true)
 public void published(NotificationRequested event){
  try{transactions.publish(event);}
  catch(RuntimeException error){LoggerFactory.getLogger(getClass()).warn("Optional notification creation failed ({})",error.getClass().getSimpleName());}
 }
}
