package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.RendezVous;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.cabinet.cabinet.service.PdfCabinet.ACCENT;
import static com.cabinet.cabinet.service.PdfCabinet.BLANC;
import static com.cabinet.cabinet.service.PdfCabinet.ENCRE;
import static com.cabinet.cabinet.service.PdfCabinet.FILET;
import static com.cabinet.cabinet.service.PdfCabinet.FOND;
import static com.cabinet.cabinet.service.PdfCabinet.GRAS;
import static com.cabinet.cabinet.service.PdfCabinet.GRIS;
import static com.cabinet.cabinet.service.PdfCabinet.MARGE;
import static com.cabinet.cabinet.service.PdfCabinet.NORMAL;
import static com.cabinet.cabinet.service.PdfCabinet.ecrire;
import static com.cabinet.cabinet.service.PdfCabinet.ecrireADroite;
import static com.cabinet.cabinet.service.PdfCabinet.remplir;

/**
 * Reçu de rendez-vous en PDF (GET /api/rdv/{id}/pdf), à la même mise en page que la facture proforma et que le reçu dessiné par le site :
 * bandeau du cabinet, bloc patient et bloc rendez-vous, tableau des informations, clé de suivi. Aucune information médicale (le motif) n'y figure.
 */
@Service
public class RendezVousPdfService {
    private static final ZoneId CABINET_ZONE = ZoneId.of("Indian/Antananarivo");
    private static final DateTimeFormatter JOUR_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter JOUR_COURT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generate(RendezVous rdv) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float largeur = page.getMediaBox().getWidth(), haut = page.getMediaBox().getHeight();
            try (PDPageContentStream flux = new PDPageContentStream(document, page)) {
                String annee = rdv.getDateRdv() == null ? String.valueOf(LocalDate.now(CABINET_ZONE).getYear()) : String.valueOf(rdv.getDateRdv().getYear());
                String numero = "RV-" + annee + "-" + String.format("%05d", rdv.getId() == null ? 0 : rdv.getId());
                PdfCabinet.entete(document, flux, largeur, haut, "REÇU DE RENDEZ-VOUS", "N° " + numero, "Émis le " + LocalDate.now(CABINET_ZONE).format(JOUR_COURT), libelleEtat(rdv));

                String dateLongue = rdv.getDateRdv() == null ? "" : majuscule(rdv.getDateRdv().format(JOUR_LONG));
                String heure = rdv.getHeureRdv() == null ? "" : rdv.getHeureRdv().toString().substring(0, 5);
                float y = haut - 150;
                ecrire(flux, GRAS, 9.75f, MARGE, y, GRIS, "PATIENT");
                ecrire(flux, GRAS, 15, MARGE, y - 21, ENCRE, rdv.getNomClient());
                float yGauche = y - 37.5f;
                if (rdv.getTelephoneClient() != null && !rdv.getTelephoneClient().isBlank()) { ecrire(flux, NORMAL, 11.25f, MARGE, yGauche, GRIS, "Tél. " + rdv.getTelephoneClient()); yGauche -= 15; }
                float xDroit = largeur / 2 + 15;
                ecrire(flux, GRAS, 9.75f, xDroit, y, GRIS, "RENDEZ-VOUS");
                ecrire(flux, GRAS, 13.5f, xDroit, y - 21, ENCRE, dateLongue);
                ecrire(flux, NORMAL, 11.25f, xDroit, y - 37.5f, GRIS, "à " + heure);
                y = Math.min(yGauche, y - 52.5f) - 20;

                // tableau des informations
                float largeurTableau = largeur - 2 * MARGE, xDetail = MARGE + 172;
                remplir(flux, ACCENT, MARGE, y - 19, largeurTableau, 25.5f);
                ecrire(flux, GRAS, 10.5f, MARGE + 7.5f, y - 12, BLANC, "Information");
                ecrire(flux, GRAS, 10.5f, xDetail, y - 12, BLANC, "Détail");
                y -= 25.5f;
                String[][] lignes = {
                        {"Service", rdv.getService() == null ? "Non précisé" : rdv.getService().getNom()},
                        {"Date du rendez-vous", dateLongue},
                        {"Heure", heure},
                        {"Reçu par", rdv.getPraticien() == null ? "À préciser par le cabinet" : rdv.getPraticien().getNom()},
                        {"Statut", libelleStatut(rdv)},
                };
                for (int i = 0; i < lignes.length; i++) {
                    float hauteurLigne = 24;
                    if (i % 2 == 1) remplir(flux, new Color(249, 250, 251), MARGE, y - hauteurLigne + 6, largeurTableau, hauteurLigne);
                    ecrire(flux, NORMAL, 11.25f, MARGE + 7.5f, y - 11, GRIS, lignes[i][0]);
                    ecrire(flux, i == 4 ? GRAS : NORMAL, 12, xDetail, y - 11, i == 4 ? ACCENT : ENCRE, lignes[i][1]);
                    remplir(flux, FILET, MARGE, y - hauteurLigne + 6, largeurTableau, 0.75f);
                    y -= hauteurLigne;
                }
                y -= 30;

                // clé de suivi : la référence du patient pour retrouver, annuler ou télécharger ce reçu dans « Mes rendez-vous »
                if (rdv.getCleSuivi() != null) {
                    float hauteurCle = 92;
                    remplir(flux, FOND, MARGE, y - hauteurCle, largeurTableau, hauteurCle);
                    remplir(flux, ACCENT, MARGE, y - hauteurCle, 3, hauteurCle);
                    ecrire(flux, GRAS, 9.75f, MARGE + 20, y - 26, GRIS, "CLÉ DE SUIVI (À GARDER)");
                    ecrire(flux, PDType1Font.COURIER_BOLD, 24, MARGE + 20, y - 56, ACCENT, rdv.getCleSuivi());
                    ecrire(flux, NORMAL, 10, MARGE + 20, y - 78, GRIS, "Saisissez cette clé dans « Mes rendez-vous » du site pour retrouver ou annuler ce rendez-vous.");
                    y -= hauteurCle + 30;
                }

                ecrire(flux, NORMAL, 9.75f, MARGE, y, GRIS, "Présentez ce reçu à l'accueil du cabinet si nécessaire.");
                ecrire(flux, NORMAL, 9.75f, MARGE, y - 15, GRIS, "Document administratif : il ne contient aucune information médicale.");
                ecrireADroite(flux, GRAS, 11.25f, largeur - MARGE, y - 3, ENCRE, "Le cabinet");
                remplir(flux, FILET, largeur - MARGE - 135, y - 58, 135, 0.75f);
                PdfCabinet.pied(flux, largeur);
            }
            document.save(sortie);
            return sortie.toByteArray();
        }
    }

    private static String libelleStatut(RendezVous rdv) {
        if (rdv.getStatut() == null) return "";
        return switch (rdv.getStatut().name()) {
            case "CONFIRME" -> "Confirmé";
            case "TERMINE" -> "Terminé";
            case "ANNULE" -> "Annulé";
            default -> "En cours";
        };
    }

    private static String libelleEtat(RendezVous rdv) {
        if (rdv.getStatut() == null) return "Rendez-vous enregistré";
        return switch (rdv.getStatut().name()) {
            case "CONFIRME" -> "Rendez-vous confirmé";
            case "TERMINE" -> "Consultation terminée";
            case "ANNULE" -> "Rendez-vous annulé";
            default -> "Rendez-vous enregistré";
        };
    }

    private static String majuscule(String texte) {
        return texte.isEmpty() ? texte : Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }
}
