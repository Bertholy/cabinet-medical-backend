package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.ThemeConfiguration;
import com.cabinet.cabinet.repository.ThemeConfigurationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/theme")
@RequiredArgsConstructor
public class ThemeController {
    private final ThemeConfigurationRepository repository;
    private final com.cabinet.cabinet.service.AuditService audit;

    @GetMapping
    public ThemeConfiguration get() {
        return repository.findById(1L).orElseGet(ThemeConfiguration::new);
    }

    @PutMapping
    public ResponseEntity<ThemeConfiguration> save(@Valid @RequestBody ThemeRequest request) {
        ThemeConfiguration theme = repository.findById(1L).orElseGet(ThemeConfiguration::new);
        theme.setId(1L);
        theme.setPrimaryColor(request.primaryColor);
        theme.setSecondaryColor(request.secondaryColor);
        theme.setButtonColor(request.buttonColor);
        theme.setLinkColor(request.linkColor);
        theme.setBackgroundColor(request.backgroundColor);
        theme.setPanelColor(request.panelColor);
        theme.setTextColor(request.textColor);
        theme.setNavColor(request.navColor);
        theme.setSidebarColor(request.sidebarColor);
        theme.setBorderColor(request.borderColor);
        ThemeConfiguration saved = repository.save(theme);
        audit.log("THEME_MODIFIE", "Theme", 1, "Couleurs du site modifiées");
        return ResponseEntity.ok(saved);
    }

    @Data
    public static class ThemeRequest {
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String primaryColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String secondaryColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String buttonColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String linkColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String backgroundColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String panelColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String textColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String navColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String sidebarColor;
        @NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}$") public String borderColor;
    }
}
