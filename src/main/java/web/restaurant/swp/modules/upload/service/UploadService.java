package web.restaurant.swp.modules.upload.service;

import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.upload.model.PresignRequest;
import web.restaurant.swp.modules.upload.model.PresignResponse;

/**
 * Storage abstraction for file uploads.
 *
 * Current: LocalStorageService for development.
 * Future:  S3StorageService for production (AWS SDK not yet added).
 */
public interface UploadService {

    /**
     * Create a presigned upload URL.
     * Local mode: returns a URL pointing to /api/uploads/local/{token}.
     */
    PresignResponse createPresignedUrl(PresignRequest request);

    /**
     * Save a file to local storage. Used by direct multipart upload endpoints.
     * Returns the public URL of the saved file.
     */
    String saveLocalUpload(String fileKey, MultipartFile file);

    /**
     * Build the public URL for a given file key.
     */
    String buildPublicUrl(String fileKey);

    /**
     * Build a deterministic storage key for a file.
     * Pattern: {module}/{entityId}/{purpose}/{uuid}.{ext}
     */
    String buildObjectKey(String module, String purpose, Long entityId, String fileName);

    /**
     * Validate an upload request (mime type, size, purpose, etc.).
     */
    void validateImageFile(PresignRequest request);
}
