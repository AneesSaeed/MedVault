package be.he2b.healthsec.medical_records.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the REST API.
 *
 * <p>Applies CORS rules to all /api/** endpoints so a browser-based frontend hosted on
 * https://localhost can call the backend with credentials.</p>
 */
@Configuration
public class WebConfig {

    /**
     * Registers CORS mappings for Spring MVC.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("https://localhost")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .exposedHeaders("*")
                    .allowCredentials(true);
            }
        };
    }
}
