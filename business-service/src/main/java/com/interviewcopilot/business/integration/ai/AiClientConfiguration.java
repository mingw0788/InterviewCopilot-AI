package com.interviewcopilot.business.integration.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.validation.Validator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiServiceProperties.class)
class AiClientConfiguration {

    @Bean
    AiQuestionClient aiQuestionClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Validator validator,
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = restClientBuilder.clone()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
        ObjectMapper strictObjectMapper = strictContractObjectMapper(objectMapper);

        return new AiQuestionClient(
                restClient,
                strictObjectMapper,
                validator,
                properties.serviceToken()
        );
    }

    @Bean
    AiEvaluationClient aiEvaluationClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Validator validator,
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = restClientBuilder.clone().baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory).build();
        return new AiEvaluationClient(restClient, strictContractObjectMapper(objectMapper), validator, properties.serviceToken());
    }

    @Bean
    AiReportClient aiReportClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Validator validator,
            AiServiceProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = restClientBuilder.clone().baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory).build();
        return new AiReportClient(restClient, strictContractObjectMapper(objectMapper), validator, properties.serviceToken());
    }

    static ObjectMapper strictContractObjectMapper(ObjectMapper source) {
        ObjectMapper strictMapper = source.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strictMapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strictMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return strictMapper;
    }
}
