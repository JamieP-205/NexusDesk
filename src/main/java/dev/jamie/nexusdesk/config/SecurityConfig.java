package dev.jamie.nexusdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import java.util.Locale;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwords() { return new Pbkdf2PasswordEncoder("", 16, 600_000, 256); }

    @Bean
    UserDetailsService userDetails(JdbcTemplate db) {
        return email -> db.query("SELECT * FROM users WHERE email=?", (rs, row) ->
                User.withUsername(rs.getString("email")).password(rs.getString("password_hash"))
                        .roles(rs.getString("role")).disabled(!rs.getBoolean("enabled")).build(),
                email.strip().toLowerCase(Locale.ROOT)).stream().findFirst()
                .orElseThrow(() -> new UsernameNotFoundException("Invalid login"));
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, JdbcTemplate db) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/style.css", "/error").permitAll()
                    .requestMatchers("/users/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
                .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy
                    .policyDirectives("default-src 'self'; style-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .addFilterBefore(new AccountStatusFilter(db), AuthorizationFilter.class)
                .build();
    }
}
