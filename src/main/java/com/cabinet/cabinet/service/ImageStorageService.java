package com.cabinet.cabinet.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

/**
 * Enregistre les photos (services, profils). Toute image lisible est CONVERTIE automatiquement pour le site :
 * JPEG, 1600 pixels au plus sur le grand côté, qualité 85 % (transparence remplacée par du blanc). Une photo de téléphone
 * de plusieurs mégaoctets devient donc un fichier léger, sans que l'utilisateur ait à la retoucher.
 */
@Service
@RequiredArgsConstructor
public class ImageStorageService {
    /** Taille maximale du fichier reçu (avant conversion). */
    private static final long TAILLE_MAX = 20L * 1024 * 1024;
    /** Au-delà, l'image est refusée (protège la mémoire du serveur). */
    private static final long PIXELS_MAX = 150_000_000L;
    static final int COTE_MAX = 1600;
    private static final float QUALITE = 0.85f;

    @Value("${app.upload-dir:uploads}")
    private String uploadDirectory;

    public String storeImage(String section, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une image.");
        if (file.getSize() > TAILLE_MAX) throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Le fichier dépasse 20 Mo : choisissez une photo plus légère.");
        try {
            byte[] converti = convertir(file.getBytes());
            Path directory = sectionDirectory(section);
            Files.createDirectories(directory);
            String name = UUID.randomUUID() + ".jpg";
            Files.write(directory.resolve(name), converti);
            return "/uploads/" + section + "/" + name;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossible d’enregistrer l’image.");
        }
    }

    /** Lit n'importe quelle image prise en charge (JPEG, PNG, GIF, BMP…) et la réécrit en JPEG adapté au site. */
    public static byte[] convertir(byte[] octets) throws IOException {
        BufferedImage source = lire(octets);
        int largeur = source.getWidth(), hauteur = source.getHeight();
        double echelle = Math.min(1.0, (double) COTE_MAX / Math.max(largeur, hauteur));
        int nouvelleLargeur = Math.max(1, (int) Math.round(largeur * echelle)), nouvelleHauteur = Math.max(1, (int) Math.round(hauteur * echelle));

        BufferedImage cible = new BufferedImage(nouvelleLargeur, nouvelleHauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cible.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, nouvelleLargeur, nouvelleHauteur);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, nouvelleLargeur, nouvelleHauteur, null);
        } finally { g.dispose(); }

        Iterator<ImageWriter> ecrivains = ImageIO.getImageWritersByFormatName("jpeg");
        if (!ecrivains.hasNext()) throw new IOException("Aucun encodeur JPEG disponible");
        ImageWriter ecrivain = ecrivains.next();
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream(); MemoryCacheImageOutputStream flux = new MemoryCacheImageOutputStream(sortie)) {
            ImageWriteParam parametres = ecrivain.getDefaultWriteParam();
            parametres.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametres.setCompressionQuality(QUALITE);
            ecrivain.setOutput(flux);
            ecrivain.write(null, new IIOImage(cible, null, null), parametres);
            flux.flush();
            return sortie.toByteArray();
        } finally { ecrivain.dispose(); }
    }

    private static BufferedImage lire(byte[] octets) throws IOException {
        try (ImageInputStream entree = ImageIO.createImageInputStream(new ByteArrayInputStream(octets))) {
            Iterator<ImageReader> lecteurs = entree == null ? null : ImageIO.getImageReaders(entree);
            if (lecteurs == null || !lecteurs.hasNext()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce fichier n’est pas une image lisible. Utilisez une photo JPEG, PNG, GIF ou BMP.");
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(entree, true, true);
                int largeur = lecteur.getWidth(0), hauteur = lecteur.getHeight(0);
                if (largeur <= 0 || hauteur <= 0 || (long) largeur * hauteur > PIXELS_MAX) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette image est démesurée (plus de 150 millions de pixels).");
                }
                // Une très grande image est lue en sautant des pixels : on évite de saturer la mémoire.
                int saut = Math.max(1, Math.max(largeur, hauteur) / (COTE_MAX * 2));
                ImageReadParam parametres = lecteur.getDefaultReadParam();
                if (saut > 1) parametres.setSourceSubsampling(saut, saut, 0, 0);
                return lecteur.read(0, parametres);
            } catch (ResponseStatusException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette image ne peut pas être lue (profil de couleur inhabituel ?). Enregistrez-la en JPEG standard puis réessayez.");
            } finally { lecteur.dispose(); }
        }
    }

    public void deleteStoredImage(String section, String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/" + section + "/")) return;
        String fileName = imageUrl.substring(("/uploads/" + section + "/").length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) return;
        try { Files.deleteIfExists(sectionDirectory(section).resolve(fileName).normalize()); }
        catch (IOException ignored) { /* Keep database operations independent from old file cleanup. */ }
    }

    public Path getUploadRoot() {
        return Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    private Path sectionDirectory(String section) {
        if (!"services".equals(section) && !"profiles".equals(section)) {
            throw new IllegalArgumentException("Unsupported upload section");
        }
        return getUploadRoot().resolve(section).normalize();
    }
}
