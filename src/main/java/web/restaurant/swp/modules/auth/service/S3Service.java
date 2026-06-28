package web.restaurant.swp.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Slf4j
public class S3Service {

    @Value("${aws.s3.bucket-name:rms-bucket}")
    private String bucketName;

    @Value("${aws.s3.region:ap-southeast-1}")
    private String region;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    @Value("${app.upload.dir:./uploads}")
    private String localUploadDir;

    private S3Client s3Client;
    private boolean isMockMode = true;

    @PostConstruct
    public void init() {
        if (accessKey != null && !accessKey.trim().isEmpty() &&
            secretKey != null && !secretKey.trim().isEmpty()) {
            try {
                this.s3Client = S3Client.builder()
                        .region(Region.of(region))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        ))
                        .build();
                this.isMockMode = false;
                log.info("AWS S3 Client successfully initialized for bucket: {}", bucketName);
            } catch (Exception e) {
                log.error("Failed to initialize AWS S3 Client. Falling back to local storage mock mode.", e);
                this.isMockMode = true;
            }
        } else {
            log.info("AWS S3 credentials not provided. Running in local storage S3 simulation mode.");
            this.isMockMode = true;
        }
    }

    public String uploadAvatar(MultipartFile file) throws IOException {
        String url = uploadFile(file, "avatars");
        if (url.startsWith("/")) {
            return "http://localhost:8080" + url;
        }
        return url;
    }

    public String uploadFile(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = UUID.randomUUID().toString() + extension;
        String s3Key = folder + "/" + filename;

        if (isMockMode) {
            Path targetPath = Paths.get(localUploadDir, folder, filename).toAbsolutePath();
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
            log.info("Mock Mode: Saved file locally to {}", targetPath);
            return String.format("/api/floor-plans/files/%s/%s", folder, filename);
        } else {
            try {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(file.getContentType())
                        .build();

                s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
                log.info("Successfully uploaded file {} to S3 key {}", originalFilename, s3Key);
                return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, s3Key);
            } catch (Exception e) {
                log.error("Failed to upload to AWS S3. Falling back to local copy.", e);
                Path targetPath = Paths.get(localUploadDir, folder, filename).toAbsolutePath();
                Files.createDirectories(targetPath.getParent());
                file.transferTo(targetPath.toFile());
                return String.format("/api/floor-plans/files/%s/%s", folder, filename);
            }
        }
    }
}
