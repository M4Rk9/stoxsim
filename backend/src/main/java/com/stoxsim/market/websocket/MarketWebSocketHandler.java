package com.stoxsim.market.websocket;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class MarketWebSocketHandler extends WebSocketHandlerDecorator {
    private final MarketWebSocketQuota quota;
    private final MarketWebSocketLimits limits;

    public MarketWebSocketHandler(WebSocketHandler handler, MarketWebSocketQuota quota, MarketWebSocketLimits limits) {
        super(handler); this.quota = quota; this.limits = limits;
    }

    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!quota.open(session)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
        session.setTextMessageSizeLimit(limits.maxFrameBytes());
        session.setBinaryMessageSizeLimit(limits.maxFrameBytes());
        try { super.afterConnectionEstablished(session); }
        catch (Exception failure) { quota.terminate(session.getId(), CloseStatus.SERVER_ERROR, "transport"); throw failure; }
    }

    @Override public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        int bytes = message instanceof TextMessage text ? text.asBytes().length : message.getPayloadLength();
        if (bytes > limits.maxFrameBytes()) {
            quota.terminate(session.getId(), CloseStatus.TOO_BIG_TO_PROCESS, "frame_size"); return;
        }
        // Includes STOMP heartbeats; reject before STOMP parsing or JWT/database work.
        if (!quota.rawFrame(session.getId())) {
            quota.terminate(session.getId(), CloseStatus.POLICY_VIOLATION, "frame_rate"); return;
        }
        super.handleMessage(session, message);
    }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        try { super.afterConnectionClosed(session, status); }
        finally { quota.closed(session.getId()); }
    }
}
