package com.ctgraphdep.config;

import com.ctgraphdep.service.AuthenticationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.authentication.AuthenticationProvider;
import com.ctgraphdep.security.CustomAuthenticationProvider;

import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final AuthenticationService authService;

    public SecurityConfig(AuthenticationService authService) {
        this.authService = authService;
    }

    private static final int REMEMBER_ME_VALIDITY_SECONDS = 2592000; // 30 days

    @Bean
    public AuthenticationProvider authenticationProvider(AuthenticationService authService) {
        return new CustomAuthenticationProvider(authService);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            AuthenticationService authService) {
        try {
            http
                    .authenticationProvider(authenticationProvider)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/", "/about", "/css/**", "/images/**", "/icons/**", "/api/system/status").permitAll()
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                            .anyRequest().authenticated()
                    )
                    .formLogin(form -> form
                            .loginPage("/login")
                            .permitAll()
                            .successHandler((request, response, authentication) -> {
                                try {
                                    boolean rememberMe = "on".equals(request.getParameter("rememberMe"));
                                    String username = authentication.getName();
                                    authService.handleSuccessfulLogin(username, rememberMe);

                                    var authorities = authentication.getAuthorities();
                                    var roles = authorities.stream()
                                            .map(GrantedAuthority::getAuthority)
                                            .collect(Collectors.toSet());

                                    if (roles.contains("ROLE_ADMIN")) {
                                        response.sendRedirect("/admin");
                                    } else if (roles.contains("ROLE_USER")) {
                                        response.sendRedirect("/user");
                                    } else {
                                        response.sendRedirect("/");
                                    }
                                } catch (Exception e) {
                                    LoggerUtil.error(this.getClass(),
                                            "Failed to handle login success: " + e.getMessage());
                                    response.sendRedirect("/login?error");
                                }
                            })
                    )
                    .rememberMe(remember -> remember
                            .key("uniqueAndSecureKey")
                            .tokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS)
                            .rememberMeParameter("rememberMe")
                            .useSecureCookie(true)
                            .alwaysRemember(false)
                    )
                    .logout(logout -> logout
                            .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                            .logoutSuccessUrl("/")
                            .deleteCookies("remember-me")
                            .permitAll()
                    )
                    .csrf(AbstractHttpConfigurer::disable);

            return http.build();
        } catch (Exception e) {
            LoggerUtil.error(this.getClass(), "Failed to configure security: " + e.getMessage());
            throw new SecurityException("Failed to configure security", e);
        }
    }

    @Bean
    public HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}