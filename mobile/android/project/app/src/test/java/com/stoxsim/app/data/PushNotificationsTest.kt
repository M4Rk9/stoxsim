package com.stoxsim.app.data
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
class PushNotificationsTest {
    @Test fun delayedMessagesNeverDisplayForAnotherOrSignedOutAccount(){
        val owner=UUID.randomUUID().toString();val notification=UUID.randomUUID().toString()
        assertTrue(PushNotifications.accepts(owner,owner,notification))
        assertFalse(PushNotifications.accepts(null,owner,notification))
        assertFalse(PushNotifications.accepts(UUID.randomUUID().toString(),owner,notification))
        assertFalse(PushNotifications.accepts(owner,null,notification))
        assertFalse(PushNotifications.accepts(owner,owner,"https://untrusted.example"))
        assertFalse(PushNotifications.accepts(owner,owner,null))
    }
}
