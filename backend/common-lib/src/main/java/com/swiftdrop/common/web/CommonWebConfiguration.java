package com.swiftdrop.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.client.InternalRestClientFactory;
import com.swiftdrop.common.security.InternalApiKey;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Beans of the web part: request id, problem writer, global exception handler, internal client factory. */
@Configuration(proxyBeanMethods = false)
public class CommonWebConfiguration {

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>(new RequestIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    public ProblemWriter problemWriter(ObjectMapper mapper) {
        return new ProblemWriter(mapper);
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public InternalRestClientFactory internalRestClientFactory(InternalApiKey key, ObjectMapper mapper) {
        return new InternalRestClientFactory(key, mapper);
    }
}
