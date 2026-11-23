package com.bitbox.inventory.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** The service must refuse to start with a missing or weak JWT secret (no silent insecure default). */
class JwtConfigTest {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import(JwtConfig.class)
    static class JwtTestConfiguration {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(JwtTestConfiguration.class)
            .withPropertyValues("security.jwt.expiration=1h", "security.jwt.issuer=test");

    @Test
    void failsToStartWithoutSecret() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWithSecretShorterThan256Bits() {
        runner.withPropertyValues("security.jwt.secret=too-short")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 32 bytes"));
    }

    @Test
    void startsWithStrongSecret() {
        runner.withPropertyValues("security.jwt.secret=0123456789abcdef0123456789abcdef-strong")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(JwtDecoder.class));
    }
}
