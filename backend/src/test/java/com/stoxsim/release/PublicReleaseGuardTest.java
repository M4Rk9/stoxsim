package com.stoxsim.release;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

class PublicReleaseGuardTest {

    @Test
    void permitsRegistrationOnlyWhenReleaseAndBothProvidersAreEnabled() {
        var release = new PublicReleaseProperties();
        release.setPublicRegistrationEnabled(true);
        var upstox = new UpstoxMarketDataProperties();
        upstox.setPublicServingEnabled(true);
        var alpaca = new AlpacaMarketDataProperties();
        alpaca.setPublicServingEnabled(true);

        var guard = new PublicReleaseGuard(release, upstox, alpaca);

        assertThatCode(guard::requirePublicRegistration).doesNotThrowAnyException();
    }

    @Test
    void blocksRegistrationWhenOperatorHasNotOpenedTheRelease() {
        var release = new PublicReleaseProperties();
        release.setPublicRegistrationEnabled(false);
        var upstox = new UpstoxMarketDataProperties();
        var alpaca = new AlpacaMarketDataProperties();

        var guard = new PublicReleaseGuard(release, upstox, alpaca);

        assertThatThrownBy(guard::requirePublicRegistration)
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            );
    }

    @Test
    void blocksRegistrationWhenEitherProviderServingSwitchIsOff() {
        var release = new PublicReleaseProperties();
        release.setPublicRegistrationEnabled(true);
        var upstox = new UpstoxMarketDataProperties();
        upstox.setPublicServingEnabled(false);
        var alpaca = new AlpacaMarketDataProperties();
        alpaca.setPublicServingEnabled(true);

        var guard = new PublicReleaseGuard(release, upstox, alpaca);

        assertThatThrownBy(guard::requirePublicRegistration)
            .isInstanceOf(ResponseStatusException.class);
    }
}
