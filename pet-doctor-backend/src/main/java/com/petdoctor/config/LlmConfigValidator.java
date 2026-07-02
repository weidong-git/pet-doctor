package com.petdoctor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时校验大模型配置
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmConfigValidator implements ApplicationRunner {

    private final LlmProperties llmProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final DifyProperties difyProperties;

    @Override
    public void run(ApplicationArguments args) {
        String provider = llmProperties.getProvider();
        if ("deepseek".equalsIgnoreCase(provider)) {
            validateDeepSeek();
            return;
        }
        if ("dify".equalsIgnoreCase(provider)) {
            validateDify();
            return;
        }
        log.error("【LLM 配置错误】未知 provider={}，请设置为 deepseek 或 dify", provider);
    }

    private void validateDeepSeek() {
        if (!deepSeekProperties.isConfigured()) {
            log.error("【DeepSeek 配置错误】未设置 API Key。请在 application-local.yml 设置 deepseek.api-key，"
                    + "或设置环境变量 DEEPSEEK_API_KEY（https://platform.deepseek.com/api_keys）");
            return;
        }
        String apiKey = deepSeekProperties.getApiKey().trim();
        if (!apiKey.startsWith("sk-") || apiKey.contains("在此粘贴") || apiKey.contains("your-deepseek")) {
            log.error("【DeepSeek 配置错误】api-key 格式无效或未替换占位符，请到 platform.deepseek.com 创建新密钥");
            return;
        }
        String maskedKey = apiKey.substring(0, Math.min(8, apiKey.length())) + "...";
        log.info("DeepSeek 配置已加载: model={}, baseUrl={}, apiKey={}",
                deepSeekProperties.getModel(), deepSeekProperties.getBaseUrl(), maskedKey);
    }

    private void validateDify() {
        String apiKey = difyProperties.getApiKey();
        if (!StringUtils.hasText(apiKey)) {
            log.error("【Dify 配置错误】未设置 API Key，请配置环境变量 DIFY_API_KEY");
            return;
        }
        if (apiKey.startsWith("dataset-")) {
            log.error("【Dify 配置错误】当前 API Key 为 dataset-（知识库密钥），聊天接口必须使用 app- 开头的应用密钥");
            return;
        }
        String maskedKey = apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "***";
        log.info("Dify 配置已加载: baseUrl={}, apiKey={}", difyProperties.getBaseUrl(), maskedKey);
    }
}
