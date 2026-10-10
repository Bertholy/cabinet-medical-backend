package com.cabinet.cabinet.config;

import com.cabinet.cabinet.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class UploadedFilesConfig implements WebMvcConfigurer {
    private final ImageStorageService imageStorageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = imageStorageService.getUploadRoot().toUri().toString();
        if (!location.endsWith("/")) location += "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
