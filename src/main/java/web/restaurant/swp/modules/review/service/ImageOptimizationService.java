package web.restaurant.swp.modules.review.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
@Slf4j
public class ImageOptimizationService {

    /**
     * Crops, resizes, and compresses an image.
     * @param rawBytes Original image bytes
     * @param x Normalized X start (0 - 1000)
     * @param y Normalized Y start (0 - 1000)
     * @param width Normalized width (0 - 1000)
     * @param height Normalized height (0 - 1000)
     * @param targetWidth Final resized width (e.g., 1200)
     * @param targetHeight Final resized height (e.g., 675)
     * @return Compressed image bytes (JPEG format)
     */
    public byte[] cropResizeAndCompress(byte[] rawBytes, int x, int y, int width, int height, int targetWidth, int targetHeight) throws IOException {
        log.info("Optimizing image: crop({}, {}, {}, {}), resize to {}x{}", x, y, width, height, targetWidth, targetHeight);
        
        BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(rawBytes));
        if (originalImage == null) {
            throw new IOException("Định dạng tệp hình ảnh không hợp lệ.");
        }

        int origW = originalImage.getWidth();
        int origH = originalImage.getHeight();

        // Convert normalized coordinates (0-1000) to actual pixel values
        int actualX = (int) Math.round((x / 1000.0) * origW);
        int actualY = (int) Math.round((y / 1000.0) * origH);
        int actualW = (int) Math.round((width / 1000.0) * origW);
        int actualH = (int) Math.round((height / 1000.0) * origH);

        // Bound-check parameters to avoid out-of-bounds exceptions
        actualX = Math.max(0, Math.min(actualX, origW - 1));
        actualY = Math.max(0, Math.min(actualY, origH - 1));
        actualW = Math.max(1, Math.min(actualW, origW - actualX));
        actualH = Math.max(1, Math.min(actualH, origH - actualY));

        // 1. Crop the image
        BufferedImage croppedImage = originalImage.getSubimage(actualX, actualY, actualW, actualH);

        // 2. Resize the image to target dimensions (using high-quality rendering hints)
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw the cropped image onto the target canvas
        g.drawImage(croppedImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        // 3. Compress the image to JPEG with 75% quality
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream();
        
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("Không tìm thấy bộ ghi ảnh định dạng JPEG.");
        }
        ImageWriter writer = writers.next();

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(compressedStream)) {
            writer.setOutput(ios);
            
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(0.75f); // 75% compression quality
            }
            
            writer.write(null, new IIOImage(resizedImage, null, null), param);
        } finally {
            writer.dispose();
        }

        byte[] compressedBytes = compressedStream.toByteArray();
        log.info("Image compression completed. Original size: {} bytes, Compressed size: {} bytes (Ratio: {}%)", 
                rawBytes.length, compressedBytes.length, (compressedBytes.length * 100 / rawBytes.length));
        
        return compressedBytes;
    }
}
