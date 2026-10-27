package com.example.noteworkshop.verification;

import com.example.noteworkshop.exception.BusinessException;
import com.example.noteworkshop.service.FileStorageService;
import com.example.noteworkshop.service.FileStorageService.StoredNoteImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    @TempDir
    Path tempDirectory;

    private Path uploadRoot;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        uploadRoot = tempDirectory.resolve("uploads");
        fileStorageService = new FileStorageService(
                uploadRoot.toString(),
                MAX_FILE_SIZE,
                MAX_FILE_SIZE
        );
    }

    @Test
    void shouldStoreNoteImageUnderGeneratedSafePathAndDeleteIt() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "files",
                "../../unsafe-name.png",
                MediaType.IMAGE_PNG_VALUE,
                createPng()
        );

        StoredNoteImage storedImage = fileStorageService.storeNoteImage(42L, image);

        Path expectedRoot = uploadRoot.resolve("notes").resolve("42").normalize();
        assertThat(storedImage.imageUrl())
                .startsWith("/uploads/notes/42/")
                .endsWith(".png");
        assertThat(storedImage.absolutePath()).isRegularFile();
        assertThat(storedImage.absolutePath().normalize()).startsWith(expectedRoot);
        assertThat(storedImage.absolutePath().getFileName().toString()).doesNotContain("unsafe-name");

        fileStorageService.deleteStoredNoteImage(storedImage);

        assertThat(storedImage.absolutePath()).doesNotExist();
    }

    @Test
    void shouldRejectFileWhoseContentIsNotARealImage() {
        MockMultipartFile fakeImage = new MockMultipartFile(
                "files",
                "fake.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> fileStorageService.storeNoteImage(7L, fakeImage))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("不是有效图片");
                });
        assertThat(uploadRoot.resolve("notes")).isEmptyDirectory();
    }

    @Test
    void shouldStillRejectAvatarWhoseContentIsNotARealImage() {
        MockMultipartFile fakeAvatar = new MockMultipartFile(
                "file",
                "avatar.png",
                MediaType.IMAGE_PNG_VALUE,
                "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> fileStorageService.storeAvatar(1L, fakeAvatar))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("不是有效图片");
                });
    }

    @Test
    void shouldRefuseToDeleteFileOutsideNoteImageRoot() throws Exception {
        Path outsideFile = tempDirectory.resolve("outside.png");
        Files.writeString(outsideFile, "keep-me");
        StoredNoteImage unsafeStoredImage = new StoredNoteImage(
                "/uploads/notes/1/outside.png",
                outsideFile
        );

        fileStorageService.deleteStoredNoteImage(unsafeStoredImage);

        assertThat(outsideFile).exists().hasContent("keep-me");
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }
}