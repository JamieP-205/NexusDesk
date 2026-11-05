package dev.jamie.nexusdesk.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwords() { return PasswordHashing.encoder(); }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    /**
     * The usual username/password check, wrapped by the sign-in throttle. Passing the account service as
     * the password service lets Spring rehash old SHA-1 hashes after a successful sign-in.
     */
    @Bean
    AuthenticationProvider authenticationProvider(AccountDetailsService accounts, PasswordEncoder passwords, LoginThrottle throttle) {
        var dao = new DaoAuthenticationProvider(accounts);
        dao.setPasswordEncoder(passwords);
        dao.setUserDetailsPasswordService(accounts);
        return new ThrottledAuthenticationProvider(dao, throttle);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JdbcTemplate db) throws Exception {
        // Same generic message for a wrong email, wrong password or disabled account, so the page
        // doesn't reveal which accounts exist. Throttled attempts get their own message.
        AuthenticationFailureHandler failure = (request, response, exception) -> response.sendRedirect(
                exception instanceof ThrottledAuthenticationProvider.Throttled throttled
                        ? "/login?locked=" + throttled.minutes() : "/login?error");
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/style.css", "/app.js", "/error").permitAll()
                    .requestMatchers("/users/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).failureHandler(failure).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                // Spring Security's defaults already give a new session ID at sign-in (fixation protection),
                // CSRF tokens on every POST, and no-store caching for authenticated pages.
                .headers(headers -> headers
                    .contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'"))
                    .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .addFilterBefore(new AccountStatusFilter(db), AuthorizationFilter.class)
                .build();
    }
}
