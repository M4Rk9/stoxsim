package com.stoxsim.instrument.provider.upstox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Component;

import com.stoxsim.instrument.config.UpstoxInstrumentProperties;

@Component
public class UpstoxInstrumentMasterClient {

    private final HttpClient httpClient;
    private final UpstoxInstrumentProperties properties;

    public UpstoxInstrumentMasterClient(UpstoxInstrumentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public List<String> sources() {
        return properties.instrumentMasterUrls();
    }

    public InputStream download(String source) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(source))
            .timeout(Duration.ofMinutes(2))
            .header("User-Agent", "StoxSim/0.1")
            .GET()
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException(
                "Upstox instrument download returned HTTP "
                    + response.statusCode()
                    + " for "
                    + source
            );
        }
        return new GZIPInputStream(response.body());
    }
}
