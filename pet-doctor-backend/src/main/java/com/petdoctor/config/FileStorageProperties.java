package com.petdoctor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件上传配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileStorageProperties {

    /** 本地上传目录 */
    private String uploadDir = "./uploads";

    /** 对外访问基础地址（供 Dify remote_url 拉取图片） */
    private String publicBaseUrl = "http://127.0.0.1:8080";

    /** 单文件最大体积（MB） */
    private int maxSizeMb = 10;
}
