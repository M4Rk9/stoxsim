package com.stoxsim.notification;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class NotificationTransactions {
 private final NotificationService service;
 public NotificationTransactions(NotificationService service){this.service=service;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public UUID publish(NotificationRequested event){return service.publish(event);}
}
