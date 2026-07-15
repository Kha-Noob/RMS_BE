package web.restaurant.swp.modules.upload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.upload.model.PresignRequest;
import web.restaurant.swp.modules.upload.model.PresignResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class LocalUploadService implements UploadService {

    @Value("${app.upload.local-dir:./uploads}")
    private String uploadDir;

    @Value("${app.upload.public-base-url:http://localhost:8080/uploads}")
    private String publicBaseUrl;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "svg");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public PresignResponse createPresignedUrl(PresignRequest request) {
        validateImageFile(request);

        String fileKey = buildObjectKey(
                request.getModule(),
                request.getPurpose(),
                request.getFloorPlanId(),
                request.getFileName()
        );

        // Encode fileKey into the token so the upload endpoint knows where to save
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(fileKey.getBytes(StandardCharsets.UTF_8));

        // Must be a relative path — matches the controller mapping @RequestMapping("/api/uploads") + @PostMapping("/local/{token}")
        String uploadUrl = "/api/uploads/local/" + token;
        String publicUrl = buildPublicUrl(fileKey);

        log.info("Presign created: token={}, fileKey={}, publicUrl={}", token, fileKey, publicUrl);

        return PresignResponse.builder()
                .uploadUrl(uploadUrl)
                .fileKey(fileKey)
                .publicUrl(publicUrl)
                .method("POST")
                .headers(Map.of("Content-Type", request.getContentType()))
                .build();
    }

    /**
     * Decode a Base64url token back to the original fileKey.
     */
    public String decodeToken(String token) {
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    @Override
    public String saveLocalUpload(String fileKey, MultipartFile file) {
        Path uploadRoot = getUploadRoot();
        Path uploadPath = resolveUploadPath(uploadRoot, fileKey);
        Path parent = uploadPath.getParent();

        try {
            log.info("Saving upload. root={}, destination={}, fileKey={}, size={}, contentType={}",
                    uploadRoot, uploadPath, fileKey, file.getSize(), file.getContentType());

            if (parent == null) {
                throw new UploadStorageException("Upload destination has no parent directory");
            }

            log.debug("Ensuring upload directory exists: {}", parent);
            Files.createDirectories(parent);

            if (!Files.isDirectory(parent)) {
                throw new UploadStorageException("Upload directory is not a directory: " + parent);
            }
            if (!Files.isWritable(parent)) {
                throw new UploadStorageException("Upload directory is not writable: " + parent);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, uploadPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("File saved locally. root={}, destination={}, publicUrl={}",
                    uploadRoot, uploadPath, buildPublicUrl(fileKey));
            return buildPublicUrl(fileKey);
        } catch (IOException e) {
            log.error("Failed to save upload. root={}, destination={}, parent={}", uploadRoot, uploadPath, parent, e);
            throw new UploadStorageException("Unable to save uploaded file. Please check upload directory permissions.", e);
        }
    }

    @Override
    public String buildPublicUrl(String fileKey) {
        return publicBaseUrl.replaceAll("/+$", "") + "/" + normalizeFileKey(fileKey);
    }

    @Override
    public String buildObjectKey(String module, String purpose, Long entityId, String fileName) {
        String ext = getExtension(fileName);
        String uuid = UUID.randomUUID().toString();
        return module + "/" + entityId + "/" + purpose + "/" + uuid + "." + ext;
    }

    @Override
    public void validateImageFile(PresignRequest request) {
        if (request.getFileName() == null || request.getFileName().isBlank()) {
            throw new IllegalArgumentException("File name is required");
        }
        if (request.getContentType() == null || request.getContentType().isBlank()) {
            throw new IllegalArgumentException("Content type is required");
        }

        String ext = getExtension(request.getFileName()).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Allowed file types: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        if (request.getSize() != null && request.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("File size must be <= 10MB");
        }

        String contentType = request.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        Set<String> allowedPurposes = Set.of("diagram", "panorama");
        if (request.getPurpose() == null || !allowedPurposes.contains(request.getPurpose())) {
            throw new IllegalArgumentException("Purpose must be 'diagram' or 'panorama'");
        }

        if (request.getFloorPlanId() == null) {
            throw new IllegalArgumentException("Floor plan ID is required");
        }
    }

    private String getExtension(String fileName) {
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx < 0) return "";
        return fileName.substring(dotIdx + 1);
    }

    private Path getUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private Path resolveUploadPath(Path uploadRoot, String fileKey) {
        String normalizedKey = normalizeFileKey(fileKey);
        Path destination = uploadRoot.resolve(normalizedKey).normalize();

        if (!destination.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid upload path");
        }

        return destination;
    }

    private String normalizeFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("File key is required");
        }
        return fileKey.replace('\\', '/');
    }

    public static class UploadStorageException extends RuntimeException {
        public UploadStorageException(String message) {
            super(message);
        }

        public UploadStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
