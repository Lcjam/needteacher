package kr.needteacher.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    public SecurityConfig() {}

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http.formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/ping")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/tutor-profiles",
                                "/api/v1/tutor-profiles/{id:[0-9a-fA-F-]{36}}")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/tutor-profile")
                        .hasRole("TEACHER")
                        .requestMatchers("/api/v1/tutor-profiles/**")
                        .hasRole("TEACHER")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex ->
                        ex.authenticationEntryPoint(authenticationEntryPoint).accessDeniedHandler(accessDeniedHandler))
                .csrf(csrf -> csrf.spa());

        return http.build();
    }
}
