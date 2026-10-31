package com.cabinet.cabinet.service;

import com.cabinet.cabinet.dto.RendezVousResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static com.cabinet.cabinet.service.PdfCabinet.ACCENT;
import static com.cabinet.cabinet.service.PdfCabinet.BLANC;
import static com.cabinet.cabinet.service.PdfCabinet.ENCRE;
import static com.cabinet.cabinet.service.PdfCabinet.FILET;
import static com.cabinet.cabinet.service.PdfCabinet.GRAS;
import static com.cabinet.cabinet.service.PdfCabinet.GRIS;
import static com.cabinet.cabinet.service.PdfCabinet.HAUTEUR_BANDEAU;
import static com.cabinet.cabinet.service.PdfCabinet.MARGE;
import static com.cabinet.cabinet.service.PdfCabinet.NORMAL;
import static com.cabinet.cabinet.service.PdfCabinet.ecrire;
import static com.cabinet.cabinet.service.PdfCabinet.ecrireADroite;
import static com.cabinet.cabinet.service.PdfCabinet.remplir;

/** Liste des rendez-vous d'une période au format PDF (A4 paysage, tableau paginé), sous le même bandeau que la facture proforma et le reçu de rendez-vous. */
@Service
public class RendezVousListePdfService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> STATUTS = Map.of("EN_ATTENTE", "En cours", "CONFIRME", "Confirmé", "TERMINE", "Terminé", "ANNULE", "Annulé");
    private static final String[] TITRES = {"N°", "Date", "Heure", "Patient", "Téléphone", "Service", "Praticien", "Statut"};
    private static final float[] LARGEURS = {40, 70, 45, 160, 90, 150, 140, 70};
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
                    PdfCabinet.entete(document, flux, format.getWidth(), format.getHeight(), "LISTE DES RENDEZ-VOUS",
                            "Du " + debut.format(JOUR) + " au " + fin.format(JOUR), rendezVous.size() + " rendez-vous", "Édité le " + LocalDate.now(ZONE).format(JOUR));
                    float y = format.getHeight() - HAUTEUR_BANDEAU - 34;
                    y = ligne(flux, y, TITRES, GRAS, true);
                    int rang = 0;
                    while (index < rendezVous.size() && y > MARGE + 36) {
                        RendezVousResponseDTO r = rendezVous.get(index++);
                        y = ligne(flux, y, new String[]{String.valueOf(r.getId()), String.valueOf(r.getDateRdv()),
                                String.valueOf(r.getHeureRdv()).substring(0, 5), r.getNomClient(), r.getTelephoneClient(),
                                r.getServiceNom() == null ? "—" : r.getServiceNom(),
                                r.getPraticienNom() == null ? "Non attribué" : r.getPraticienNom(),
                                STATUTS.getOrDefault(String.valueOf(r.getStatut()), String.valueOf(r.getStatut()))},
                                NORMAL, false, rang++ % 2 == 1);
                    }
                    PdfCabinet.pied(flux, format.getWidth());
                    ecrireADroite(flux, NORMAL, 8, format.getWidth() - MARGE, 26, GRIS, "Page " + numeroPage);
                }
            } while (index < rendezVous.size());
            document.save(sortie);
            return sortie.toByteArray();
        }
    }

    private float ligne(PDPageContentStream flux, float y, String[] cellules, PDFont police, boolean entete) throws IOException {
        return ligne(flux, y, cellules, police, entete, false);
    }

    private float ligne(PDPageContentStream flux, float y, String[] cellules, PDFont police, boolean entete, boolean bande) throws IOException {
        if (entete) remplir(flux, ACCENT, MARGE, y - 5, somme(), HAUTEUR_LIGNE + 2);
        else if (bande) remplir(flux, new Color(249, 250, 251), MARGE, y - 5, somme(), HAUTEUR_LIGNE);
        float x = MARGE + 3;
        for (int i = 0; i < cellules.length; i++) {
            ecrire(flux, police, 9, x, y, entete ? BLANC : ENCRE, tronquer(cellules[i], police, LARGEURS[i] - 6));
            x += LARGEURS[i];
        }
        if (!entete) remplir(flux, FILET, MARGE, y - 5, somme(), 0.5f);
        return y - HAUTEUR_LIGNE;
    }

    private float somme() {
        float total = 0;
        for (float l : LARGEURS) total += l;
        return total;
    }

    private String tronquer(String texte, PDFont police, float largeur) throws IOException {
        String propre = PdfCabinet.nettoyer(police, texte == null ? "" : texte);
        while (propre.length() > 1 && police.getStringWidth(propre) / 1000 * 9 > largeur) propre = propre.substring(0, propre.length() - 1);
        return propre;
    }
}
