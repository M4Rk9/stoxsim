package com.stoxsim.analytics.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.server.ResponseStatusException;

/** Bound parsing even for chunked requests without a Content-Length header. */
@ControllerAdvice(assignableTypes = ProductActivityController.class)
public class ActivityBodyLimit extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return parameter.getContainingClass() == ProductActivityController.class;
    }
    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter,
        Type type, Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        byte[] body = input.getBody().readNBytes(1025);
        if (body.length > 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Activity payload exceeds 1024 bytes");
        return new HttpInputMessage() {
            public HttpHeaders getHeaders() { return input.getHeaders(); }
            public InputStream getBody() { return new ByteArrayInputStream(body); }
        };
    }
}
