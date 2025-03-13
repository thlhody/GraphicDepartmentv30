package com.ctgraphdep.config;

import com.ctgraphdep.service.AuthenticationService;
import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider, AuthenticationService authService) {
        try {
            http
                    .authenticationProvider(authenticationProvider)
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/", "/about", "/css/**", "/images/**", "/icons/**", "/api/system/status", "/autologin", "/update/**").permitAll()
                            .requestMatchers("/admin/**").hasRole("ADMIN")
                            .requestMatchers("/team-lead/**").hasRole("TEAM_LEADER")
                            .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN", "TEAM_LEADER")
                            .requestMatchers("/user/session/resolve/**").hasAnyRole("USER", "ADMIN", "TEAM_LEADER")
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

                                    LoggerUtil.debug(this.getClass(),
                                            String.format("User %s has roles: %s",
                                                    username,
                                                    String.join(", ", roles)));

                                    LoggerUtil.debug(this.getClass(), "Checking roles for redirect...");
                                    if (roles.contains("ROLE_ADMIN")) {
                                        LoggerUtil.debug(this.getClass(), "Redirecting to admin...");
                                        response.sendRedirect("/admin");
                                    } else if (roles.contains("ROLE_TEAM_LEADER")) {
                                        LoggerUtil.debug(this.getClass(), "Redirecting to team lead...");
                                        response.sendRedirect("/team-lead");
                                    } else if (roles.contains("ROLE_USER")) {
                                        LoggerUtil.debug(this.getClass(), "Redirecting to user...");
                                        response.sendRedirect("/user");
                                    } else {
                                        LoggerUtil.debug(this.getClass(), "Redirecting to home...");
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
            LoggerUtil.logAndThrow(this.getClass(), "Failed to configure security", e);
            return null; // This line won't be reached but helps with compiler warnings
        }
    }

    @Bean
    public HttpSessionSecurityContextRepository httpSessionSecurityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        // Ensure web session handling doesn't affect work sessions
        repository.setAllowSessionCreation(true);
        repository.setDisableUrlRewriting(true);
        return repository;
    }
}