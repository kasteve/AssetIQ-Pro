package com.stevecodes.AssetIQPro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.internet.AddressException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class EmailRetryConfig {

    @Bean
    public RetryTemplate emailRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = customEmailRetryPolicy();
        retryTemplate.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000L);
        backOffPolicy.setMultiplier(2.0D);
        backOffPolicy.setMaxInterval(10000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    public SimpleRetryPolicy customEmailRetryPolicy() {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(org.springframework.mail.MailSendException.class, true);
        retryableExceptions.put(jakarta.mail.MessagingException.class, true);
        retryableExceptions.put(SocketTimeoutException.class, true);
        retryableExceptions.put(ConnectException.class, true);
        retryableExceptions.put(AuthenticationFailedException.class, false);
        retryableExceptions.put(AddressException.class, false);
        return new SimpleRetryPolicy(3, retryableExceptions);
    }
}