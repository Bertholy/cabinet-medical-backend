package com.cabinet.cabinet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageStorageService {
    @Value("${app.upload-dir:uploads}")
    private String uploadDirectory;

    public String storeImage(String section, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une image.");
        if (file.getSize() > 5L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "L’image ne doit pas dépasser 5 Mo.");

        String contentType = file.getContentType();
        String extension = "image/png".equalsIgnoreCase(contentType) ? ".png"
                : "image/jpeg".equalsIgnoreCase(contentType) ? ".jpg" : null;
        if (extension == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formats acceptés : JPEG et PNG.");

        try {
            byte[] bytes = file.getBytes();
            validateImage(bytes);
            Path directory = sectionDirectory(section);
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + extension;
            Files.write(directory.resolve(name), bytes);
            return "/uploads/" + section + "/" + name;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d’enregistrer l’image.");
        }
    }

    public void deleteStoredImage(String section, String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/" + section + "/")) return;
        String fileName = imageUrl.substring(("/uploads/" + section + "/").length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) return;
        try { Files.deleteIfExists(sectionDirectory(section).resolve(fileName).normalize()); }
        catch (IOException ignored) { /* Keep database operations independent from old file cleanup. */ }
    }

    public Path getUploadRoot() {
        return Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    private Path sectionDirectory(String section) {
        if (!"services".equals(section) && !"profiles".equals(section)) {
            throw new IllegalArgumentException("Unsupported upload section");
        }
        return getUploadRoot().resolve(section).normalize();
    }

    private void validateImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier n’est pas une image valide.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier n’est pas une image valide.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (!(format.equals("png") || format.equals("jpeg") || format.equals("jpg")) || pixels > 16_000_000L) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image invalide ou trop grande en dimensions.");
                }
            } finally { reader.dispose(); }
        }
    }
}
