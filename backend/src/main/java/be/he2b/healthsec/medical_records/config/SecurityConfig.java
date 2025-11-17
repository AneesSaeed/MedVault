package be.he2b.healthsec.medical_records.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // tell Spring that this class defines the security rules for the app
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // CSRF is disabled because the application is a stateless REST API secured via Authorization headers rather than cookies.
            .authorizeHttpRequests(auth -> auth // This defines which endpoints require which permissions.
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/patient/**").hasRole("PATIENT")
                .requestMatchers("/api/doctor/**").hasRole("DOCTOR")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2 // This tells Spring that your server is a resource server using JWTs for authentication
                .jwt(jwt -> {})
            );

        return http.build();
    }
}
