package dev.jamie.nexusdesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwords() { return PasswordHashing.encoder(); }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JdbcTemplate db, LoginThrottle throttle) throws Exception {
        var failure = new SimpleUrlAuthenticationFailureHandler("/login?error") {
            @Override
            public void onAuthenticationFailure(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.AuthenticationException exception)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throttle.failed(request.getParameter("username"), request.getRemoteAddr());
                super.onAuthenticationFailure(request, response, exception);
            }
        };
        var success = new SavedRequestAwareAuthenticationSuccessHandler() {
            @Override
            public void onAuthenticationSuccess(jakarta.servlet.http.HttpServletRequest request,
                                                jakarta.servlet.http.HttpServletResponse response,
                                                org.springframework.security.core.Authentication authentication)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                throttle.succeeded(request.getParameter("username"), request.getRemoteAddr());
                super.onAuthenticationSuccess(request, response, authentication);
            }
        };
        success.setDefaultTargetUrl("/");
        success.setAlwaysUseDefaultTargetUrl(true);
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/style.css", "/app.js", "/error").permitAll()
                    .requestMatchers("/users/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").successHandler(success).failureHandler(failure).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // Spring Security's defaults already give a new session ID at sign-in (fixation protection),
                // CSRF tokens on every POST, and no-store caching for authenticated pages.
                .headers(headers -> headers
                    .contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'"))
                    .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .addFilterBefore(new LoginThrottleFilter(throttle), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AccountStatusFilter(db), AuthorizationFilter.class)
                .build();
    }
}
