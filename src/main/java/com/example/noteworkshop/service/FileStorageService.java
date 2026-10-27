package com.example.noteworkshop.service;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final String NOTE_IMAGE_URL_PREFIX = "/uploads/notes/";

    private final Path uploadRoot;
    private final Path avatarRoot;
    private final Path noteImageRoot;
    private final long maxAvatarSizeBytes;
    private final long maxNoteImageSizeBytes;

    public FileStorageService(
            @Value("${app.upload.dir}") String uploadDir,
            @Value("${app.upload.max-avatar-size-bytes}") long maxAvatarSizeBytes,
            @Value("${app.upload.max-note-image-size-bytes}") long maxNoteImageSizeBytes
    ) {
        if (maxAvatarSizeBytes <= 0 || maxNoteImageSizeBytes <= 0) {
            throw new IllegalArgumentException("Upload size limits must be greater than 0");
        }

        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.avatarRoot = uploadRoot.resolve("avatars").normalize();
        this.noteImageRoot = uploadRoot.resolve("notes").normalize();
        this.maxAvatarSizeBytes = maxAvatarSizeBytes;
        this.maxNoteImageSizeBytes = maxNoteImageSizeBytes;

        try {
            Files.createDirectories(avatarRoot);
            Files.createDirectories(noteImageRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create upload directories", exception);
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
        validateImageMetadata(file, extension, "头像");
        validateImageContent(file, "头像");

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
            deleteQuietly(targetFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "头像保存失败");
        }

        return "/uploads/avatars/" + userId + "/" + storedFilename;
    }

    public StoredNoteImage storeNoteImage(Long noteId, MultipartFile file) {
        validateNoteId(noteId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "笔记图片文件不能为空");
        }
        if (file.getSize() > maxNoteImageSizeBytes) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "笔记图片超过大小限制");
        }

        String extension = getExtension(file.getOriginalFilename());
        validateImageMetadata(file, extension, "笔记图片");
        validateImageContent(file, "笔记图片");

        Path noteDirectory = noteImageRoot.resolve(String.valueOf(noteId)).normalize();
        if (!noteDirectory.startsWith(noteImageRoot)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法上传路径");
        }

        String storedFilename = UUID.randomUUID() + "." + extension;
        Path targetFile = noteDirectory.resolve(storedFilename).normalize();
        if (!targetFile.startsWith(noteDirectory)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "非法上传路径");
        }

        try {
            Files.createDirectories(noteDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteQuietly(targetFile);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "笔记图片保存失败");
        }

        String imageUrl = "/uploads/notes/" + noteId + "/" + storedFilename;
        return new StoredNoteImage(imageUrl, targetFile);
    }

    public void deleteStoredNoteImages(List<StoredNoteImage> storedImages) {
        if (storedImages == null || storedImages.isEmpty()) {
            return;
        }
        storedImages.forEach(this::deleteStoredNoteImage);
    }

    public void deleteStoredNoteImage(StoredNoteImage storedImage) {
        if (storedImage == null || storedImage.absolutePath() == null) {
            return;
        }

        Path targetFile = storedImage.absolutePath().toAbsolutePath().normalize();
        if (!targetFile.startsWith(noteImageRoot)) {
            log.warn("Refused to delete a note image outside the upload root");
            return;
        }

        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException exception) {
            log.warn("Failed to delete note image during compensation: {}", targetFile, exception);
        }
    }

    public void deleteNoteImageByUrl(String imageUrl) {
        if (imageUrl == null
                || !imageUrl.startsWith(NOTE_IMAGE_URL_PREFIX)
                || imageUrl.length() == NOTE_IMAGE_URL_PREFIX.length()) {
            log.warn("Refused to delete note image with invalid URL");
            return;
        }

        String relativePath = imageUrl.substring(NOTE_IMAGE_URL_PREFIX.length());
        Path targetFile = noteImageRoot.resolve(relativePath).normalize();
        if (!targetFile.startsWith(noteImageRoot)) {
            log.warn("Refused to delete note image outside the upload root");
            return;
        }

        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException exception) {
            log.warn("Failed to delete note image by URL: {}", imageUrl, exception);
        }
    }

    private void validateNoteId(Long noteId) {
        if (noteId == null || noteId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "笔记 ID 不合法");
        }
    }

    private void validateImageMetadata(MultipartFile file, String extension, String imageLabel) {
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    imageLabel + "仅支持 jpg、jpeg、png 格式"
            );
        }
    }

    private void validateImageContent(MultipartFile file, String imageLabel) {
        try (InputStream inputStream = file.getInputStream()) {
            if (ImageIO.read(inputStream) == null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        imageLabel + "文件不是有效图片"
                );
            }
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    imageLabel + "文件读取失败"
            );
        }
    }

    private void deleteQuietly(Path targetFile) {
        try {
            Files.deleteIfExists(targetFile);
        } catch (IOException exception) {
            log.warn("Failed to clean up an incomplete upload: {}", targetFile, exception);
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

    public record StoredNoteImage(String imageUrl, Path absolutePath) {
    }
}