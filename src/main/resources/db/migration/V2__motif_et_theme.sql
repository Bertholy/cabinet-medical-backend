-- Casafami: ajoute le motif aux rendez-vous existants sans modifier leurs données.
ALTER TABLE rendez_vous ADD COLUMN IF NOT EXISTS motif VARCHAR(2000);

-- Thème global, limité à une ligne (id = 1). Les couleurs initiales reprennent la palette Casafami.
CREATE TABLE IF NOT EXISTS theme_configuration (
    id BIGINT PRIMARY KEY CHECK (id = 1),
    primary_color VARCHAR(7) NOT NULL DEFAULT '#0f766e',
    secondary_color VARCHAR(7) NOT NULL DEFAULT '#14b8a6',
    button_color VARCHAR(7) NOT NULL DEFAULT '#0f766e',
    link_color VARCHAR(7) NOT NULL DEFAULT '#0f766e',
    background_color VARCHAR(7) NOT NULL DEFAULT '#ffffff',
    panel_color VARCHAR(7) NOT NULL DEFAULT '#f8fafc',
    text_color VARCHAR(7) NOT NULL DEFAULT '#1f2937',
    nav_color VARCHAR(7) NOT NULL DEFAULT '#0f172a',
    sidebar_color VARCHAR(7) NOT NULL DEFAULT '#0f172a',
    border_color VARCHAR(7) NOT NULL DEFAULT '#e2e8f0'
);
