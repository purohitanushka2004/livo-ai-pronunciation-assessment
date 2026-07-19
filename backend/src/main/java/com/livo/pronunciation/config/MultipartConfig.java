package com.livo.pronunciation.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

@Configuration
public class MultipartConfig {

    /**
     * Hard cap on upload size as a first line of defence before we even
     * look at audio duration. Raised from 8MB to 20MB: in-browser recordings
     * are usually small webm/opus (well under 2MB for 45s), but some
     * browsers fall back to uncompressed formats, and a 45s 16-bit stereo
     * WAV can already be ~8MB on its own, so 8MB was cutting it too close
     * and was a likely cause of silent upload failures.
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(20));
        factory.setMaxRequestSize(DataSize.ofMegabytes(20));
        return factory.createMultipartConfig();
    }
}
