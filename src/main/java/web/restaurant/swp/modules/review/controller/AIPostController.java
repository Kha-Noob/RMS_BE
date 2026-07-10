package web.restaurant.swp.modules.review.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.auth.service.S3Service;
import web.restaurant.swp.modules.review.service.AIContentService;
import web.restaurant.swp.modules.review.service.ImageOptimizationService;

import java.io.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AIPostController {

    private final AIContentService aiContentService;
    private final ImageOptimizationService imageOptimizationService;
    private final S3Service s3Service;

    @PostMapping("/articles/generate")
    public ResponseEntity<?> generateArticleText(@RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        if (prompt == null || prompt.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ý tưởng/Từ khóa không được trống."));
        }
        try {
            Map<String, Object> result = aiContentService.generatePostContent(prompt);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi sinh bài viết: " + e.getMessage()));
        }
    }

    @PostMapping("/events/banner/analyze")
    public ResponseEntity<?> analyzeEventBanner(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tệp hình ảnh không được trống."));
        }
        try {
            Map<String, Object> cropBox = aiContentService.analyzeCropBox(file.getBytes(), file.getContentType());
            return ResponseEntity.ok(cropBox);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi phân tích hình ảnh: " + e.getMessage()));
        }
    }

    @PostMapping("/events/banner/optimize")
    public ResponseEntity<?> optimizeEventBanner(
            @RequestParam("file") MultipartFile file,
            @RequestParam("x") int x,
            @RequestParam("y") int y,
            @RequestParam("width") int width,
            @RequestParam("height") int height,
            @RequestParam(value = "targetWidth", defaultValue = "1200") int targetWidth,
            @RequestParam(value = "targetHeight", defaultValue = "675") int targetHeight) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tệp hình ảnh không được trống."));
        }
        try {
            // 1. Process crop/resize/compress
            byte[] optimizedBytes = imageOptimizationService.cropResizeAndCompress(
                    file.getBytes(), x, y, width, height, targetWidth, targetHeight);

            // 2. Wrap as Custom MultipartFile
            String originalFilename = file.getOriginalFilename();
            String cleanedFilename = originalFilename != null ? originalFilename : "banner.jpg";
            if (!cleanedFilename.toLowerCase().endsWith(".jpg") && !cleanedFilename.toLowerCase().endsWith(".jpeg")) {
                cleanedFilename = cleanedFilename + ".jpg"; // Compressed output format is JPEG
            }
            MultipartFile optimizedFile = new CustomMultipartFile(
                    optimizedBytes, "file", cleanedFilename, "image/jpeg");

            // 3. Upload file via S3Service
            String fileUrl = s3Service.uploadFile(optimizedFile, "events");
            
            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (Exception e) {
            log.error("Failed to crop and optimize banner", e);
            return ResponseEntity.badRequest().body(Map.of("message", "Lỗi nén và xử lý hình ảnh: " + e.getMessage()));
        }
    }

    // Static helper class representing custom MultipartFile wrapper for in-memory compressed byte array
    private static class CustomMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String name;
        private final String originalFilename;
        private final String contentType;

        public CustomMultipartFile(byte[] bytes, String name, String originalFilename, String contentType) {
            this.bytes = bytes;
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes == null || bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return bytes;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(bytes);
            }
        }
    }
}
