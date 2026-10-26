package com.example.noteworkshop.service;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final Path avatarRoot;
    private final long maxAvatarSizeBytes;

    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.max-avatar-size-bytes}") long maxAvatarSizeBytes
    ) {
        if (maxAvatarSizeBytes <= 0) {
            throw new IllegalArgumentException("Max avatar size must be greater than 0");
        }
        this.avatarRoot = Path.of(uploadDir).toAbsolutePath().normalize().resolve("avatars");
        this.maxAvatarSizeBytes = maxAvatarSizeBytes;
        try {
            Files.createDirectories(this.avatarRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create avatar upload directory", exception);
        }
    }

    public String storeAvatar(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "头像文件不能为空");
        }
        if (file.getSize() > maxAvatarSizeBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "头像文件不能超过 5MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "头像仅支持 jpg、jpeg、png 格式");
        }

        validateImageContent(file);

        Path userDirectory = avatarRoot.resolve(String.valueOf(userId)).normalize();
        if (!userDirectory.startsWith(avatarRoot)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法上传路径");
        }

        String storedFilename = UUID.randomUUID() + "." + extension;
        Path targetFile = userDirectory.resolve(storedFilename).normalize();
        if (!targetFile.startsWith(userDirectory)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法上传路径");
        }

        try {
            Files.createDirectories(userDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }

        return "/uploads/avatars/" + userId + "/" + storedFilename;
    }

    private void validateImageContent(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            if (ImageIO.read(inputStream) == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "头像文件不是有效图片");
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "头像文件读取失败");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}