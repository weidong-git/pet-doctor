package com.petdoctor.controller;

import com.petdoctor.dto.FileUploadResponse;
import com.petdoctor.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 文件上传与访问接口
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    /**
     * 上传图片（供小程序选择患处照片后多模态问诊）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<FileUploadResponse> upload(@RequestPart("file") FilePart file) {
        return fileStorageService.upload(file);
    }

    /**
     * 获取已上传图片（Dify remote_url 拉取或前端预览）
     */
    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<byte[]>> getFile(@PathVariable String fileId) {
        return fileStorageService.read(fileId);
    }
}
