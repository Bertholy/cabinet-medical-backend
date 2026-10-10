package com.cabinet.cabinet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "theme_configuration")
@Data
public class ThemeConfiguration {
    @Id
    private Long id = 1L;
    @Column(nullable = false, length = 7) private String primaryColor = "#0f766e";
    @Column(nullable = false, length = 7) private String secondaryColor = "#14b8a6";
    @Column(nullable = false, length = 7) private String buttonColor = "#0f766e";
    @Column(nullable = false, length = 7) private String linkColor = "#0f766e";
    @Column(nullable = false, length = 7) private String backgroundColor = "#ffffff";
    @Column(nullable = false, length = 7) private String panelColor = "#f8fafc";
    @Column(nullable = false, length = 7) private String textColor = "#1f2937";
    @Column(nullable = false, length = 7) private String navColor = "#0f172a";
    @Column(nullable = false, length = 7) private String sidebarColor = "#0f172a";
    @Column(nullable = false, length = 7) private String borderColor = "#e2e8f0";
}
