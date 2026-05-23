package com.mintynex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

/**
 * Ensures multipart/form-data uploads work for avatar, banner,
 * post images and binder card images.
 * BUG FIX: Without this, @RequestParam MultipartFile fails.
 */
@Configuration
public class MultipartConfig {

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }
}
