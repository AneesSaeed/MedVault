package be.he2b.healthsec.medical_records.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration // marks a class that provides Spring configuration.
public class WebConfig {

    @Bean // marks a method whose return value should be managed by Spring’s dependency injection container.
    public WebMvcConfigurer corsConfigurer() { // WebMvcConfigurer is an interface that lets you customize Spring MVC’s default web settings (things like CORS, formatters, interceptors, etc.).
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) { // CorsRegistry is a helper object for defining which origins, methods, or headers are allowed in CORS.
                registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:4200")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
