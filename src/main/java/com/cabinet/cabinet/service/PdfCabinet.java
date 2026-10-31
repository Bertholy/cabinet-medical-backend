package com.cabinet.cabinet.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;

import java.awt.Color;
import java.io.IOException;

/**
 * Mise en page commune des PDF du cabinet : le même bandeau (logo et coordonnées à gauche, titre, numéro et informations à droite) et le même pied de page
 * que la facture proforma et le reçu de rendez-vous dessinés par le site (frontend/src/utils/enteteCabinet.js), pour que tous les documents se ressemblent.
 * Dimensions en points PDF (1 px du site = 0,75 pt).
 */
final class PdfCabinet {
    private PdfCabinet() { }

    static final Color ENCRE = new Color(17, 24, 39);
    static final Color GRIS = new Color(107, 114, 128);
    static final Color FILET = new Color(209, 213, 219);
    static final Color ACCENT = new Color(14, 116, 144);
    static final Color FOND = new Color(236, 254, 255);
    static final Color BLANC = Color.WHITE;

    static final float MARGE = 42;
    static final float HAUTEUR_BANDEAU = 116;

    private static final String NOM = "CASAFAMI";
    private static final String SOUS_TITRE = "Cabinet d'accouchement Sainte Famille";
    private static final String[] ADRESSE = {"Chez Rasazy VERO", "Andongona - ANTSOHIHY", "Madagascar"};
    private static final String TELEPHONES = "Tél. 034 06 759 28 / 032 43 035 07";

    static final PDFont NORMAL = PDType1Font.HELVETICA;
    static final PDFont GRAS = PDType1Font.HELVETICA_BOLD;

    /**
     * Bandeau du haut de page. « premiereLigne » : le numéro du document (« N° RV-2026-00047 ») ou une période ; « lignes » : jusqu'à deux lignes grises dessous.
     * Le titre prend 20 pt et rétrécit si besoin pour ne jamais passer sur les coordonnées du cabinet.
     */
    static void entete(PDDocument document, PDPageContentStream flux, float largeurPage, float hautPage, String titre, String premiereLigne, String... lignes) throws IOException {
        remplir(flux, FOND, 0, hautPage - 112.5f, largeurPage, 112.5f);
        remplir(flux, ACCENT, 0, hautPage - 115.5f, largeurPage, 3);
        float xTexte = MARGE + 97.5f;
        try {
            ClassPathResource logo = new ClassPathResource("static/logo_casafami.png");
            if (logo.exists()) {
                PDImageXObject image = PDImageXObject.createFromByteArray(document, logo.getContentAsByteArray(), "casafami-logo");
                float hauteur = 63, largeur = image.getWidth() * hauteur / image.getHeight();
                flux.drawImage(image, MARGE, hautPage - 25.5f - hauteur, largeur, hauteur);
            }
        } catch (IOException logoIllisible) {
            // le logo est facultatif : le document reste utilisable sans lui
        }
        ecrire(flux, GRAS, 18, xTexte, hautPage - 40.5f, ENCRE, NOM);
        ecrire(flux, NORMAL, 9.75f, xTexte, hautPage - 55.5f, GRIS, SOUS_TITRE);
        float y = hautPage - 70.5f;
        float limite = xTexte + largeurTexte(NORMAL, 9.75f, SOUS_TITRE) + 18;
        for (String ligne : ADRESSE) {
            ecrire(flux, NORMAL, 9.75f, xTexte, y, GRIS, ligne);
            limite = Math.max(limite, xTexte + largeurTexte(NORMAL, 9.75f, ligne) + 18);
            y -= 12.75f;
        }
        ecrire(flux, NORMAL, 9.75f, xTexte, y, GRIS, TELEPHONES);
        limite = Math.max(limite, xTexte + largeurTexte(NORMAL, 9.75f, TELEPHONES) + 18);

        float taille = 20;
        while (taille > 12 && largeurPage - MARGE - largeurTexte(GRAS, taille, titre) < limite) taille -= 0.5f;
        ecrireADroite(flux, GRAS, taille, largeurPage - MARGE, hautPage - 46.5f, ACCENT, titre);
        ecrireADroite(flux, GRAS, 12.75f, largeurPage - MARGE, hautPage - 70.5f, ENCRE, premiereLigne);
        for (int i = 0; i < Math.min(2, lignes.length); i++) {
            ecrireADroite(flux, NORMAL, 10.5f, largeurPage - MARGE, hautPage - 87 - i * 15, GRIS, lignes[i]);
        }
    }

    /** Ligne du bas de page, centrée : nom, sous-titre et adresse du cabinet. */
    static void pied(PDPageContentStream flux, float largeurPage) throws IOException {
        String texte = NOM + " · " + SOUS_TITRE + " · " + String.join(", ", ADRESSE);
        ecrire(flux, NORMAL, 9.75f, (largeurPage - largeurTexte(NORMAL, 9.75f, texte)) / 2, 26, GRIS, texte);
    }

    static void remplir(PDPageContentStream flux, Color couleur, float x, float y, float largeur, float hauteur) throws IOException {
        flux.setNonStrokingColor(couleur);
        flux.addRect(x, y, largeur, hauteur);
        flux.fill();
    }

    static void ecrire(PDPageContentStream flux, PDFont police, float taille, float x, float y, Color couleur, String texte) throws IOException {
        flux.setNonStrokingColor(couleur);
        flux.beginText();
        flux.setFont(police, taille);
        flux.newLineAtOffset(x, y);
        flux.showText(nettoyer(police, texte));
        flux.endText();
    }

    static void ecrireADroite(PDPageContentStream flux, PDFont police, float taille, float xDroite, float y, Color couleur, String texte) throws IOException {
        ecrire(flux, police, taille, xDroite - largeurTexte(police, taille, texte), y, couleur, texte);
    }

    static float largeurTexte(PDFont police, float taille, String texte) throws IOException {
        return police.getStringWidth(nettoyer(police, texte)) / 1000 * taille;
    }

    /** Coupe un texte en lignes qui tiennent dans la largeur donnée. */
    static java.util.List<String> couper(PDFont police, float taille, String texte, float largeurMax) throws IOException {
        java.util.List<String> lignes = new java.util.ArrayList<>();
        String courante = "";
        for (String mot : (texte == null ? "" : texte).split("\\s+")) {
            String essai = courante.isEmpty() ? mot : courante + " " + mot;
            if (!courante.isEmpty() && largeurTexte(police, taille, essai) > largeurMax) { lignes.add(courante); courante = mot; } else courante = essai;
        }
        lignes.add(courante);
        return lignes;
    }

    /** Remplace les caractères que la police standard ne sait pas dessiner (apostrophe courbe, tirets longs…). */
    static String nettoyer(PDFont police, String valeur) {
        StringBuilder resultat = new StringBuilder();
        String propre = (valeur == null ? "" : valeur).replace("’", "'").replace("—", "-").replace("–", "-");
        propre.codePoints().forEach(cp -> {
            String caractere = new String(Character.toChars(cp));
            try {
                police.getStringWidth(caractere);
                resultat.append(caractere);
            } catch (IOException | IllegalArgumentException nonSupporte) {
                resultat.append('?');
            }
        });
        return resultat.toString();
    }
}
