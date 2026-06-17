package hr.algebra.shop.configuration;

import hr.algebra.shop.filter.JwtRequestFilter;
import hr.algebra.shop.service.MyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final MyUserDetailsService userDetailsService;
    private final JwtRequestFilter jwtRequestFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        try {
            return config.getAuthenticationManager();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build AuthenticationManager", e);
        }
    }

    // Order(1),  checked first, only matches /api/** requests
    @Bean
    @Order(1)
    public SecurityFilterChain jwtFilterChain(HttpSecurity http) {
        try {
            http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable()) // stateless REST uses tokens, not cookies, CSRF not applicable
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWT carries identity, no server session needed
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/products/**").permitAll()
                    .anyRequest().authenticated()
                )
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);
            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure JWT filter chain", e);
        }
    }

    // Order(2), catches everything else, browser sessions and form login
    @Bean
    @Order(2)
    public SecurityFilterChain mvcFilterChain(HttpSecurity http) {
        try {
            http
                .authenticationProvider(authenticationProvider())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/categories/**", "/products/**", "/cart/**",
                            "/h2-console/**", "/login").permitAll()
                    .requestMatchers("/checkout/**", "/orders/**").hasRole("CUSTOMER")
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
                )
                .formLogin(form -> form
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .permitAll()
                )
                .logout(logout -> logout
                    .logoutSuccessUrl("/login?logout")
                    .permitAll()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())); // h2-console uses frames — sameOrigin allows it while blocking cross-origin framing
            return http.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure MVC filter chain", e);
        }
    }
}
