package org.example.cloud.configservice.logging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import java.util.List;

@Slf4j
@Configuration
public class LoggingConfiguration {
    @Bean
    public FilterRegistrationBean<Filter> registerHttpRequestResponseLoggingFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(httpRequestResponseLoggingFilter());
        registration.setUrlPatterns(List.of("/*"));
        registration.setName("httpRequestResponseLoggingFilter");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public Filter httpRequestResponseLoggingFilter() {
        return new HTTPRequestResponseLoggingFilter();
    }
}
