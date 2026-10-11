package com.cabinet.cabinet;

import com.cabinet.cabinet.service.ImageStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Les photos sont converties automatiquement pour le site : plus aucune image lisible n'est refusée pour sa taille ou son format. */
class ImagesTests {

    private static BufferedImage image(int largeur, int hauteur, int type) {
        BufferedImage img = new BufferedImage(largeur, hauteur, type);
        Graphics2D g = img.createGraphics();
        Random hasard = new Random(7);
        for (int i = 0; i < 60; i++) { g.setColor(new Color(hasard.nextInt(256), hasard.nextInt(256), hasard.nextInt(256))); g.fillRect(hasard.nextInt(largeur), hasard.nextInt(hauteur), largeur / 5, hauteur / 5); }
        g.dispose();
        return img;
    }

    private static byte[] encoder(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, sortie), "format " + format + " pris en charge");
        return sortie.toByteArray();
    }

    @Test
    void uneGrandePhotoEstRedimensionneeEtAlegee() throws Exception {
        byte[] original = encoder(image(4000, 3000, BufferedImage.TYPE_INT_RGB), "png");   // 12 mégapixels, plusieurs Mo
        byte[] converti = ImageStorageService.convertir(original);
        BufferedImage resultat = ImageIO.read(new ByteArrayInputStream(converti));
        assertEquals(0xFF, converti[0] & 0xFF);
        assertEquals(0xD8, converti[1] & 0xFF);                                              // signature JPEG
        assertEquals(1600, Math.max(resultat.getWidth(), resultat.getHeight()), "le grand côté est ramené à 1600 px");
        assertEquals(1200, resultat.getHeight(), "les proportions sont conservées (4:3)");
        assertTrue(converti.length < original.length, "le fichier est plus léger");
    }

    @Test
    void uneImageDejaPetiteNEstPasAgrandie() throws Exception {
        BufferedImage resultat = ImageIO.read(new ByteArrayInputStream(ImageStorageService.convertir(encoder(image(320, 200, BufferedImage.TYPE_INT_RGB), "png"))));
        assertEquals(320, resultat.getWidth());
        assertEquals(200, resultat.getHeight());
    }

    @Test
    void laTransparenceDevientDuBlanc() throws Exception {
        BufferedImage transparent = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);   // entièrement transparent
        BufferedImage resultat = ImageIO.read(new ByteArrayInputStream(ImageStorageService.convertir(encoder(transparent, "png"))));
        Color coin = new Color(resultat.getRGB(2, 2));
        assertTrue(coin.getRed() > 240 && coin.getGreen() > 240 && coin.getBlue() > 240, "pixel transparent → blanc");
    }

    @Test
    void lesAutresFormatsCourantsSontAcceptes() throws Exception {
        for (String format : new String[]{"gif", "bmp", "jpeg"}) {
            byte[] converti = ImageStorageService.convertir(encoder(image(900, 600, BufferedImage.TYPE_INT_RGB), format));
            assertNotNull(ImageIO.read(new ByteArrayInputStream(converti)), format + " converti en JPEG lisible");
        }
    }

    @Test
    void unFichierQuiNEstPasUneImageEstRefuseAvecUnMessageClair() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> ImageStorageService.convertir("ceci n'est pas une image".getBytes()));
        assertEquals(400, e.getStatusCode().value());
        assertTrue(e.getReason().contains("pas une image"));
        assertThrows(ResponseStatusException.class, () -> ImageStorageService.convertir(new byte[0]));
    }
}
