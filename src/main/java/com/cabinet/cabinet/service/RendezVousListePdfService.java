package com.cabinet.cabinet.service;

import com.cabinet.cabinet.config.AppConstants;
import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** Liste des rendez-vous d'une période au format PDF (A4 paysage, tableau paginé). */
@Service
public class RendezVousListePdfService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final Map<String, String> STATUTS = Map.of("EN_ATTENTE", "En attente", "CONFIRME", "Confirmé", "TERMINE", "Terminé", "ANNULE", "Annulé");
    private static final String[] TITRES = {"N°", "Date", "Heure", "Patient", "Téléphone", "Service", "Praticien", "Statut"};
    private static final float[] LARGEURS = {40, 70, 45, 160, 90, 150, 140, 70};
    private static final float MARGE = 36;
    private static final float HAUTEUR_LIGNE = 18;

    public byte[] generer(List<RendezVousResponseDTO> rendezVous, LocalDate debut, LocalDate fin) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDRectangle format = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            int numeroPage = 0;
            int index = 0;
            do {
                PDPage page = new PDPage(format);
                document.addPage(page);
                numeroPage++;
                try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                    float y = format.getHeight() - MARGE;
                    ecrire(flux, PDType1Font.HELVETICA_BOLD, 16, MARGE, y, AppConstants.APP_NAME + " — Rendez-vous du " + debut + " au " + fin);
                    y -= 16;
                    ecrire(flux, PDType1Font.HELVETICA, 9, MARGE, y, rendezVous.size() + " rendez-vous — édité le " + LocalDate.now(ZONE));
                    y -= 22;
                    y = ligne(flux, y, TITRES, PDType1Font.HELVETICA_BOLD, true);
                    while (index < rendezVous.size() && y > MARGE + 24) {
                        RendezVousResponseDTO r = rendezVous.get(index++);
                        y = ligne(flux, y, new String[]{String.valueOf(r.getId()), String.valueOf(r.getDateRdv()),
                                String.valueOf(r.getHeureRdv()).substring(0, 5), r.getNomClient(), r.getTelephoneClient(),
                                r.getServiceNom() == null ? "—" : r.getServiceNom(),
                                r.getPraticienNom() == null ? "Non attribué" : r.getPraticienNom(),
                                STATUTS.getOrDefault(String.valueOf(r.getStatut()), String.valueOf(r.getStatut()))},
                                PDType1Font.HELVETICA, false);
                    }
                    ecrire(flux, PDType1Font.HELVETICA, 8, format.getWidth() - MARGE - 40, MARGE - 12, "Page " + numeroPage);
                }
            } while (index < rendezVous.size());
            document.save(sortie);
            return sortie.toByteArray();
        }
    }

    private float ligne(PDPageContentStream flux, float y, String[] cellules, PDFont police, boolean entete) throws IOException {
        if (entete) {
            flux.setNonStrokingColor(0.06f, 0.46f, 0.43f);
            flux.addRect(MARGE, y - 5, somme(), HAUTEUR_LIGNE);
            flux.fill();
            flux.setNonStrokingColor(1f, 1f, 1f);
        } else {
            flux.setNonStrokingColor(0.12f, 0.16f, 0.2f);
        }
        float x = MARGE + 3;
        for (int i = 0; i < cellules.length; i++) {
            ecrire(flux, police, 9, x, y, tronquer(cellules[i], police, LARGEURS[i] - 6));
            x += LARGEURS[i];
        }
        flux.setNonStrokingColor(0f, 0f, 0f);
        if (!entete) {
            flux.setStrokingColor(0.85f, 0.87f, 0.9f);
            flux.moveTo(MARGE, y - 5);
            flux.lineTo(MARGE + somme(), y - 5);
            flux.stroke();
        }
        return y - HAUTEUR_LIGNE;
    }

    private float somme() {
        float total = 0;
        for (float l : LARGEURS) total += l;
        return total;
    }

    private String tronquer(String texte, PDFont police, float largeur) throws IOException {
        String propre = nettoyer(police, texte == null ? "" : texte);
        while (propre.length() > 1 && police.getStringWidth(propre) / 1000 * 9 > largeur) propre = propre.substring(0, propre.length() - 1);
        return propre;
    }

    private void ecrire(PDPageContentStream flux, PDFont police, int taille, float x, float y, String texte) throws IOException {
        flux.beginText();
        flux.setFont(police, taille);
        flux.newLineAtOffset(x, y);
        flux.showText(nettoyer(police, texte));
        flux.endText();
    }

    /** Remplace par « ? » les caractères que la police standard ne sait pas dessiner. */
    private String nettoyer(PDFont police, String valeur) {
        StringBuilder resultat = new StringBuilder();
        valeur.replace("’", "'").replace("—", "-").replace("–", "-").codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            try {
                police.getStringWidth(c);
                resultat.append(c);
            } catch (IOException | IllegalArgumentException nonSupporte) {
                resultat.append('?');
            }
        });
        return resultat.toString();
    }
}
