package com.example.async.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允許所有路徑
                .allowedOrigins("*") // 允許所有來源 (在生產環境中應指定具體來源)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允許的方法
                .allowedHeaders("*") // 允許所有標頭
                .allowCredentials(false) // 如果您不需要 cookies 或認證，可以設為 false
                .maxAge(3600); // 預檢請求的快取時間 (秒)
    }
}