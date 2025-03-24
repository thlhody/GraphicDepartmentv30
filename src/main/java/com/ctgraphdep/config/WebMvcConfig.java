package com.ctgraphdep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));

        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}