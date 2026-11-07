package com.stoxsim.notification;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
class FcmClientTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void disabledClientNeverLoadsCredentialsOrSends(){
        var http=mock(FcmHttp.class);
        var client=new FcmClient(false,"","",http,json);
        assertThat(client.configured()).isFalse();
        assertThat(client.send("private-token",UUID.randomUUID(),UUID.randomUUID()).result()).isEqualTo(NotificationTransport.Result.PERMANENT_REJECTION);
        verifyNoInteractions(http);
        assertThatThrownBy(()->new FcmClient(true,"","",http,json)).isInstanceOf(IllegalStateException.class);
    }
    @Test void onlyAuthoritativeUnregisteredEvidenceInvalidatesAToken(){
        var error=json.readTree("{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\"UNREGISTERED\"}]}}");
        assertThat(FcmClient.classify(new FcmHttp.Reply(404,error)).unregistered()).isTrue();
        assertThat(FcmClient.classify(new FcmHttp.Reply(400,error)).unregistered()).isFalse();
        assertThat(FcmClient.classify(new FcmHttp.Reply(404,json.createObjectNode())).unregistered()).isFalse();
        assertThat(FcmClient.classify(new FcmHttp.Reply(503,error)).result()).isEqualTo(NotificationTransport.Result.RETRYABLE_REJECTION);
        assertThat(FcmClient.classify(new FcmHttp.Reply(200,json.createObjectNode())).result()).isEqualTo(NotificationTransport.Result.UNKNOWN);
        assertThat(FcmClient.classify(new FcmHttp.Reply(200,json.createObjectNode().put("name","projects/p/messages/id"))).result()).isEqualTo(NotificationTransport.Result.ACCEPTED);
    }
    @Test void partialOrAmbiguousDeliveryCannotReplayAlreadyAcceptedDevices(){
        assertThat(FcmNotificationTransport.aggregate(List.of(NotificationTransport.Result.ACCEPTED,NotificationTransport.Result.RETRYABLE_REJECTION))).isEqualTo(NotificationTransport.Result.UNKNOWN);
        assertThat(FcmNotificationTransport.aggregate(List.of(NotificationTransport.Result.UNKNOWN,NotificationTransport.Result.PERMANENT_REJECTION))).isEqualTo(NotificationTransport.Result.UNKNOWN);
        assertThat(FcmNotificationTransport.aggregate(List.of(NotificationTransport.Result.ACCEPTED,NotificationTransport.Result.PERMANENT_REJECTION))).isEqualTo(NotificationTransport.Result.ACCEPTED);
        assertThat(FcmNotificationTransport.aggregate(List.of(NotificationTransport.Result.RETRYABLE_REJECTION))).isEqualTo(NotificationTransport.Result.RETRYABLE_REJECTION);
    }
}
