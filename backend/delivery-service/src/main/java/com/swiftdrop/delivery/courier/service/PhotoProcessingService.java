package com.swiftdrop.delivery.courier.service;

import com.swiftdrop.common.error.DomainException;
import com.swiftdrop.common.error.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoProcessingService {

    private static final int MAX_DIMENSION_PX = 1600;
    private static final long MAX_PIXEL_COUNT = 40_000_000L; // Decompression bomb guard (e.g. 40 Megapixels)
    private static final float JPEG_QUALITY = 0.8f;

    private final long maxUploadBytes;
    private final long maxStoredBytes;

    public PhotoProcessingService(
            @Value("${swiftdrop.max-upload-mb:10}") long maxUploadMb,
            @Value("${swiftdrop.max-stored-photo-mb:5}") long maxStoredPhotoMb) {
        this.maxUploadBytes = maxUploadMb * 1024L * 1024L;
        this.maxStoredBytes = maxStoredPhotoMb * 1024L * 1024L;
    }

    /**
     * Inspects, validates, guards against bombs, and re-encodes the photo to JPEG at 0.8 quality <= 1600px.
     */
    public byte[] processPhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Photo file is empty");
        }

        if (file.getSize() > maxUploadBytes) {
            throw new DomainException(ErrorCode.PAYLOAD_TOO_LARGE, "Uploaded photo exceeds maximum size limit");
        }

        byte[] rawBytes;
        try {
            rawBytes = file.getBytes();
        } catch (IOException e) {
            throw new DomainException(ErrorCode.VALIDATION_ERROR, "Could not read photo bytes");
        }

        // 1. Content inspection via magic bytes
        String format = detectFormat(rawBytes);
        if (format == null) {
            throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Only JPEG and PNG formats are supported");
        }

        // 2. Guard against decompression bombs by checking dimensions before decoding raster
        checkDecompressionBomb(rawBytes, format);

        // 3. Decode image
        BufferedImage sourceImage;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(rawBytes)) {
            sourceImage = ImageIO.read(bais);
        } catch (IOException e) {
            throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Failed to read image data");
        }

        if (sourceImage == null) {
            throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Image could not be decoded");
        }

        // 4. Calculate dimensions (longest side at most 1600 px, scale down only)
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();
        int targetWidth = width;
        int targetHeight = height;

        int longest = Math.max(width, height);
        if (longest > MAX_DIMENSION_PX) {
            double ratio = (double) MAX_DIMENSION_PX / longest;
            targetWidth = Math.max(1, (int) Math.round(width * ratio));
            targetHeight = Math.max(1, (int) Math.round(height * ratio));
        }

        // 5. Draw onto clean RGB buffer (composite transparency on white, strip metadata)
        BufferedImage normalizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = normalizedImage.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        // 6. Encode to JPEG with quality 0.8
        byte[] processedJpeg = encodeToJpeg(normalizedImage);

        // 7. Verify stored size limit
        if (processedJpeg.length > maxStoredBytes) {
            throw new DomainException(ErrorCode.PAYLOAD_TOO_LARGE, "Processed photo exceeds stored limit");
        }

        return processedJpeg;
    }

    private String detectFormat(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return null;
        }
        // JPEG magic bytes: FF D8 FF
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "JPEG";
        }
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47
                && (bytes[4] & 0xFF) == 0x0D && (bytes[5] & 0xFF) == 0x0A && (bytes[6] & 0xFF) == 0x1A && (bytes[7] & 0xFF) == 0x0A) {
            return "PNG";
        }
        return null;
    }

    private void checkDecompressionBomb(byte[] bytes, String format) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported image reader for format: " + format);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, false);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Invalid image dimensions");
                }
                long pixelCount = (long) width * (long) height;
                if (pixelCount > MAX_PIXEL_COUNT || width > 10_000 || height > 10_000) {
                    throw new DomainException(ErrorCode.PAYLOAD_TOO_LARGE, "Image dimensions exceed safety limits");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new DomainException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Could not inspect image headers");
        }
    }

    private byte[] encodeToJpeg(BufferedImage image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("JPEG");
        if (!writers.hasNext()) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } catch (IOException e) {
            throw new DomainException(ErrorCode.INTERNAL_ERROR, "Failed to encode photo to JPEG");
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }
}

