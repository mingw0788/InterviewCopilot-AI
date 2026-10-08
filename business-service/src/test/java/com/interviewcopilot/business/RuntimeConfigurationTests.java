package com.interviewcopilot.business;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationTests {

    private static final String DATASOURCE_PASSWORD_PROPERTY = "spring.datasource.password";
    private static final String AI_SERVICE_TOKEN_PROPERTY =
            "interviewcopilot.ai-service.service-token";
    private static final String JWT_SIGNING_SECRET_PROPERTY =
            "interviewcopilot.security.jwt.signing-secret";

    @Test
    void datasourcePasswordLoadsFromEnvironmentConfiguration() throws IOException {
        PropertySource<?> applicationConfiguration = loadApplicationConfiguration();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addFirst(new MapPropertySource(
                "test-environment",
                Map.of("MYSQL_PASSWORD", "local-secret")
        ));
        propertySources.addLast(applicationConfiguration);

        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);
        String configuredValue = (String) applicationConfiguration.getProperty(DATASOURCE_PASSWORD_PROPERTY);

        assertEquals("local-secret", resolver.resolveRequiredPlaceholders(configuredValue));
    }

    @Test
    void missingDatasourcePasswordFailsConfigurationResolution() throws IOException {
        PropertySource<?> applicationConfiguration = loadApplicationConfiguration();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(applicationConfiguration);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);
        String configuredValue = (String) applicationConfiguration.getProperty(DATASOURCE_PASSWORD_PROPERTY);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveRequiredPlaceholders(configuredValue)
        );
        assertTrue(exception.getMessage().contains("MYSQL_PASSWORD"));
    }

    @Test
    void missingInternalServiceTokenFailsConfigurationResolution() throws IOException {
        PropertySource<?> applicationConfiguration = loadApplicationConfiguration();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(applicationConfiguration);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);
        String configuredValue = (String) applicationConfiguration.getProperty(AI_SERVICE_TOKEN_PROPERTY);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveRequiredPlaceholders(configuredValue)
        );
        assertTrue(exception.getMessage().contains("INTERNAL_AI_SERVICE_TOKEN"));
    }

    @Test
    void missingJwtSigningSecretFailsConfigurationResolution() throws IOException {
        PropertySource<?> applicationConfiguration = loadApplicationConfiguration();
        MutablePropertySources propertySources = new MutablePropertySources();
        propertySources.addLast(applicationConfiguration);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);
        String configuredValue = (String) applicationConfiguration.getProperty(JWT_SIGNING_SECRET_PROPERTY);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveRequiredPlaceholders(configuredValue)
        );
        assertTrue(exception.getMessage().contains("JWT_SIGNING_SECRET"));
    }

    private PropertySource<?> loadApplicationConfiguration() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(
                "application",
                new ClassPathResource("application.yaml")
        );
        return propertySources.getFirst();
    }
}
