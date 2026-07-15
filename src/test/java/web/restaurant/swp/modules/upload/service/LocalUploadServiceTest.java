package web.restaurant.swp.modules.upload.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalUploadServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void saveLocalUploadCreatesMissingParentDirectories() throws Exception {
        LocalUploadService service = new LocalUploadService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "publicBaseUrl", "http://localhost:8080/uploads");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "diagram.png",
                "image/png",
                "image-bytes".getBytes()
        );
        String fileKey = "floor-plans/1/diagram/test-image.png";

        String publicUrl = service.saveLocalUpload(fileKey, file);

        Path savedFile = tempDir.resolve(fileKey);
        assertThat(Files.exists(savedFile)).isTrue();
        assertThat(Files.readString(savedFile)).isEqualTo("image-bytes");
        assertThat(publicUrl).isEqualTo("http://localhost:8080/uploads/floor-plans/1/diagram/test-image.png");
    }
}
