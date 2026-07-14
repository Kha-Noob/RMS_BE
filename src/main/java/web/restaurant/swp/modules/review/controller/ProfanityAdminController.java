package web.restaurant.swp.modules.review.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.restaurant.swp.modules.review.service.ProfanityFilterService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/profanity")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ProfanityAdminController {

    private final ProfanityFilterService profanityFilterService;

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<?> importProfanityWords(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File tải lên không được để trống."));
        }

        try {
            String content = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            profanityFilterService.importWords(content);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Nhập danh sách từ cấm thành công.",
                    "count", profanityFilterService.getBlacklistedWords().size()
            ));
        } catch (Exception e) {
            log.error("Error importing profanity words", e);
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi nhập danh sách từ cấm: " + e.getMessage()));
        }
    }

    @GetMapping("/words")
    public ResponseEntity<?> getBlacklistedWords() {
        return ResponseEntity.ok(profanityFilterService.getBlacklistedWords());
    }
}
