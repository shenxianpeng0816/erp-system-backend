package com.erp.controller;

import com.erp.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/files")
public class FileUploadController {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    /** Allowed subfolders under the upload root (caller sends as {@code scope}). */
    private static final Set<String> UPLOAD_SCOPES = Set.of("customers", "inbound");

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','INBOUND','WAREHOUSE')")
    public Result<String> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scope", required = false, defaultValue = "defaults") String scope) {
        if (file == null || file.isEmpty()) {
            return Result.fail("File is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            return Result.fail("Only JPEG, PNG, WebP or GIF images are allowed");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        String folder = StringUtils.hasText(scope) ? scope.trim().toLowerCase(Locale.ROOT) : "defaults";
        if (!UPLOAD_SCOPES.contains(folder)) {
            return Result.fail("Invalid upload scope. Allowed: " + String.join(", ", UPLOAD_SCOPES));
        }

        if (!Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(ext)) {
            ext = switch (contentType.toLowerCase(Locale.ROOT)) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".jpg";
            };
        }
        String filename = folder + "/" + UUID.randomUUID() + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(filename);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String urlPath = "/uploads/" + filename.replace('\\', '/');
        return Result.success(urlPath);
    }
}
