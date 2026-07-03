package com.petdoctor.service;

import com.petdoctor.config.FileStorageProperties;
import com.petdoctor.dto.FileUploadResponse;
import com.petdoctor.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储服务（供小程序上传患处图片，供 AI 多模态检索）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final Map<String, String> EXT_CONTENT_TYPE = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif"
    );

    private final FileStorageProperties properties;

    /**
     * 上传图片并返回可访问 URL
     */
    public Mono<FileUploadResponse> upload(FilePart filePart) {
        String originalFilename = filePart.filename();
        String extension = resolveExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Mono.error(new BusinessException(400, "仅支持 jpg、png、webp、gif 图片"));
        }

        String fileId = UUID.randomUUID() + "." + extension;
        String contentType = EXT_CONTENT_TYPE.getOrDefault(extension, "application/octet-stream");
        long maxBytes = (long) properties.getMaxSizeMb() * 1024 * 1024;

        return DataBufferUtils.join(filePart.content())
                .flatMap(buffer -> {
                    int size = buffer.readableByteCount();
                    if (size > maxBytes) {
                        DataBufferUtils.release(buffer);
                        return Mono.error(new BusinessException(400,
                                "图片不能超过 " + properties.getMaxSizeMb() + "MB"));
                    }

                    byte[] bytes = new byte[size];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);

                    return Mono.fromCallable(() -> saveToDisk(fileId, bytes))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(savedId -> buildResponse(savedId, contentType));
                });
    }

    /**
     * 读取已上传文件
     */
    public Mono<ResponseEntity<byte[]>> read(String fileId) {
        if (!isSafeFileId(fileId)) {
            return Mono.error(new BusinessException(400, "非法文件路径"));
        }

        return Mono.fromCallable(() -> {
                    Path path = resolveUploadPath(fileId);
                    if (!Files.exists(path)) {
                        throw new BusinessException(404, "文件不存在");
                    }
                    byte[] bytes = Files.readAllBytes(path);
                    String extension = resolveExtension(fileId);
                    String contentType = EXT_CONTENT_TYPE.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_TYPE, contentType)
                            .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                            .body(bytes);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String saveToDisk(String fileId, byte[] bytes) throws IOException {
        Path dir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(fileId).normalize();
        if (!target.startsWith(dir)) {
            throw new BusinessException(400, "非法文件路径");
        }
        Files.write(target, bytes);
        log.info("文件已保存: {}", target);
        return fileId;
    }

    private FileUploadResponse buildResponse(String fileId, String contentType) {
        String baseUrl = properties.getPublicBaseUrl().replaceAll("/+$", "");
        String url = baseUrl + "/api/v1/files/" + fileId;
        return new FileUploadResponse(fileId, url, contentType);
    }

    private Path resolveUploadPath(String fileId) {
        Path dir = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(fileId).normalize();
        if (!target.startsWith(dir)) {
            throw new BusinessException(400, "非法文件路径");
        }
        return target;
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename) || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isSafeFileId(String fileId) {
        if (!StringUtils.hasText(fileId) || fileId.contains("..") || fileId.contains("/") || fileId.contains("\\")) {
            return false;
        }
        String extension = resolveExtension(fileId);
        return ALLOWED_EXTENSIONS.contains(extension);
    }
}
