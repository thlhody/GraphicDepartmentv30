package com.ctgraphdep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.http.HttpSessionListener;
import jakarta.servlet.http.HttpSessionEvent;
import com.ctgraphdep.utils.LoggerUtil;

@Configuration
public class SessionConfig {

    @Bean
    public HttpSessionListener httpSessionListener() {
        return new HttpSessionListener() {
            @Override
            public void sessionCreated(HttpSessionEvent se) {
                LoggerUtil.info(this.getClass(), "Session created: " + se.getSession().getId());
            }

            @Override
            public void sessionDestroyed(HttpSessionEvent se) {
                LoggerUtil.info(this.getClass(), "Session destroyed: " + se.getSession().getId());
            }
        };
    }
}