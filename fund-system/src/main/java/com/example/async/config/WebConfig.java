package com.example.async.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 允許所有路徑
                .allowedOriginPatterns("*") // 允許所有來源模式 (支援通配符和更靈活的匹配)
                .allowedOrigins("*") // 允許所有來源
                .allowedMethods("*") // 允許所有HTTP方法
                .allowedHeaders("*") // 允許所有標頭
                .exposedHeaders("*") // 暴露所有響應標頭
                .allowCredentials(false) // 測試環境設為false避免衝突
                .maxAge(86400); // 預檢請求快取24小時
    }

    /**
     * 額外的CORS配置源 - 確保完全開放
     * 測試環境專用，生產環境應移除或限制
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowCredentials(false); // 測試環境避免cookie衝突
        configuration.addAllowedOriginPattern("*"); // 支持通配符
        configuration.addAllowedOrigin("*"); // 允許所有來源
        configuration.addAllowedHeader("*"); // 允許所有標頭
        configuration.addAllowedMethod("*"); // 允許所有方法
        configuration.addExposedHeader("*"); // 暴露所有響應標頭
        configuration.setMaxAge(86400L); // 24小時緩存

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}