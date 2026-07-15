package web.restaurant.swp.modules.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import web.restaurant.swp.modules.upload.service.LocalUploadService;
import web.restaurant.swp.modules.upload.service.UploadService;

@Configuration
public class UploadConfig {

    // TODO: When adding S3 support:
    // 1. Add AWS SDK dependencies to pom.xml
    // 2. Create S3StorageService implementing UploadService
    // 3. Use @ConditionalOnProperty(name = "app.upload.provider", havingValue = "s3") on S3StorageService
    // 4. Use @ConditionalOnProperty(name = "app.upload.provider", havingValue = "local", matchIfMissing = true) on LocalUploadService
    // 5. Remove this @Bean method and let Spring auto-wire via @ConditionalOnProperty

    @Bean
    public UploadService uploadService(LocalUploadService localUploadService) {
        return localUploadService;
    }
}
