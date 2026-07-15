package web.restaurant.swp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.local-dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();

        // Legacy floor plan file serving
        registry.addResourceHandler("/api/floor-plans/files/**")
                .addResourceLocations(uploadLocation);

        // New upload serving (used by presigned local flow)
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);

        // Legacy fallback
        registry.addResourceHandler("/api/uploads/files/**")
                .addResourceLocations(uploadLocation);
    }
}
