package com.cabinet.cabinet.service;

import com.cabinet.cabinet.config.AppConstants;
import com.cabinet.cabinet.entity.RendezVous;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class RendezVousPdfService {
    private static final ZoneId CABINET_ZONE = ZoneId.of("Indian/Antananarivo");

    public byte[] generate(RendezVous rdv) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage(PDRectangle.A4));
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                try {
                    ClassPathResource logo = new ClassPathResource("static/logo_casafami.png");
                    if (logo.exists()) {
                        PDImageXObject image = PDImageXObject.createFromByteArray(document, logo.getContentAsByteArray(), "casafami-logo");
                        stream.drawImage(image, 54, 755, 60, 60);
                    }
                } catch (IOException logoError) {
                    // Keep the document usable when the optional logo cannot be read.
                }
                write(stream, PDType1Font.HELVETICA_BOLD, 22, 130, 785, AppConstants.APP_NAME);
                write(stream, PDType1Font.HELVETICA, 11, 130, 765, "Cabinet m\u00e9dical");
                stream.setStrokingColor(new java.awt.Color(15, 118, 110));
                stream.setLineWidth(2);
                stream.moveTo(54, 735);
                stream.lineTo(541, 735);
                stream.stroke();
                write(stream, PDType1Font.HELVETICA_BOLD, 18, 54, 700, "Confirmation de rendez-vous");

                int y = 650;
                y = field(stream, y, "Num\u00e9ro du rendez-vous", String.valueOf(rdv.getId()));
                y = field(stream, y, "Patient", rdv.getNomClient());
                y = field(stream, y, "T\u00e9l\u00e9phone", rdv.getTelephoneClient());
                y = field(stream, y, "Service", rdv.getService() == null ? "Non pr\u00e9cis\u00e9" : rdv.getService().getNom());
                y = field(stream, y, "Praticien", rdv.getPraticien() == null ? "Non pr\u00e9cis\u00e9" : rdv.getPraticien().getNom());
                y = field(stream, y, "Date", String.valueOf(rdv.getDateRdv()));
                y = field(stream, y, "Heure", String.valueOf(rdv.getHeureRdv()));
                field(stream, y, "Date d'\u00e9mission", LocalDate.now(CABINET_ZONE).toString());
                write(stream, PDType1Font.HELVETICA, 9, 54, 55,
                        "Document administratif de confirmation - aucune information m\u00e9dicale n'est incluse.");
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private int field(PDPageContentStream stream, int y, String label, String value) throws IOException {
        write(stream, PDType1Font.HELVETICA_BOLD, 11, 58, y, label);
        List<String> lines = wrap(value == null ? "" : value, 73);
        for (int i = 0; i < lines.size(); i++) {
            write(stream, PDType1Font.HELVETICA, 11, 220, y - i * 15, lines.get(i));
        }
        return y - Math.max(34, lines.size() * 15 + 12);
    }

    private List<String> wrap(String value, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            if (line.length() > 0 && line.length() + word.length() + 1 > maxChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }

    private void write(PDPageContentStream stream, PDFont font, int size, float x, float y, String value) throws IOException {
        String safe = safeForFont(font, value.replace("\u2019", "'").replace("\u2014", "-").replace("\u2013", "-"));
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(safe);
        stream.endText();
    }

    private String safeForFont(PDFont font, String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            try {
                font.getStringWidth(character);
                result.append(character);
            } catch (IOException | IllegalArgumentException unsupported) {
                result.append('?');
            }
        });
        return result.toString();
    }
}
