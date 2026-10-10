package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.enums.Role;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Sauvegardes de la base PostgreSQL (pg_dump) et des photos téléversées.
 * Automatique : l'ordinateur du cabinet n'est pas forcément allumé à une heure fixe, donc on vérifie toutes les 30 minutes
 * et on sauvegarde dès que la dernière sauvegarde a plus de 24 heures. Les plus anciennes sont supprimées (14 conservées).
 */
@SuppressWarnings("null") // avertissements de l'éditeur sur des références de méthode : aucun risque réel
@Slf4j
@Service
public class SauvegardeService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Seuls ces noms de fichiers peuvent être téléchargés : aucun chemin ne peut être injecté. */
    public static final Pattern NOM_VALIDE = Pattern.compile("^casafami-\\d{8}-\\d{6}(-photos)?\\.(dump|zip)$");
    private static final Pattern URL_JDBC = Pattern.compile("^jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+).*$");
    private static final Duration MAX_AGE = Duration.ofHours(24);

    public record Fichier(String nom, long taille, LocalDateTime date, String type) { }

    private final Path dossier;
    private final Path uploads;
    private final int garder;
    private final boolean automatique;
    private final String pgDumpConfigure;
    private final String url;
    private final String utilisateurBase;
    private final String motDePasseBase;
    private final AuditService audit;
    private final NotificationService notifications;
    private final UtilisateurRepository utilisateurs;
    private volatile Instant derniereAlerte = Instant.EPOCH;

    public SauvegardeService(@Value("${app.backup.dossier:}") String dossier,
                             @Value("${app.upload-dir:uploads}") String uploads,
                             @Value("${app.backup.garder:14}") int garder,
                             @Value("${app.backup.actif:true}") boolean automatique,
                             @Value("${app.backup.pg-dump:}") String pgDumpConfigure,
                             @Value("${spring.datasource.url}") String url,
                             @Value("${spring.datasource.username:postgres}") String utilisateurBase,
                             @Value("${spring.datasource.password:}") String motDePasseBase,
                             AuditService audit, NotificationService notifications, UtilisateurRepository utilisateurs) {
        this.dossier = dossierDesSauvegardes(dossier);
        this.uploads = Path.of(uploads).toAbsolutePath().normalize();
        this.garder = Math.max(1, garder);
        this.automatique = automatique;
        this.pgDumpConfigure = pgDumpConfigure == null ? "" : pgDumpConfigure.trim();
        this.url = url;
        this.utilisateurBase = utilisateurBase;
        this.motDePasseBase = motDePasseBase == null ? "" : motDePasseBase;
        this.audit = audit;
        this.notifications = notifications;
        this.utilisateurs = utilisateurs;
    }

    /** Dossier configuré ; sinon « backups » à la racine du projet, que le serveur soit lancé depuis la racine ou depuis backend/cabinet. */
    static Path dossierDesSauvegardes(String configure) {
        if (configure != null && !configure.isBlank()) return Path.of(configure.trim()).toAbsolutePath().normalize();
        Path courant = Path.of("").toAbsolutePath();
        if (Files.isRegularFile(courant.resolve("backend").resolve("cabinet").resolve("pom.xml"))) return courant.resolve("backups").normalize();
        if (Files.isRegularFile(courant.resolve("pom.xml")) && courant.getParent() != null && courant.getParent().getParent() != null
                && courant.getParent().getFileName() != null && courant.getParent().getFileName().toString().equals("backend")) {
            return courant.getParent().getParent().resolve("backups").normalize();
        }
        return courant.resolve("backups").normalize();
    }

    public Path getDossier() { return dossier; }
    public int getGarder() { return garder; }
    public boolean isAutomatique() { return automatique; }

    /** Fichiers de sauvegarde, du plus récent au plus ancien. */
    public List<Fichier> lister() {
        if (!Files.isDirectory(dossier)) return List.of();
        List<Fichier> fichiers = new ArrayList<>();
        try (DirectoryStream<Path> flux = Files.newDirectoryStream(dossier, "casafami-*")) {
            for (Path p : flux) {
                String nom = p.getFileName().toString();
                if (!NOM_VALIDE.matcher(nom).matches() || !Files.isRegularFile(p)) continue;
                fichiers.add(new Fichier(nom, Files.size(p),
                        LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZONE), nom.endsWith(".dump") ? "base" : "photos"));
            }
        } catch (IOException e) {
            log.warn("Lecture du dossier de sauvegarde impossible : {}", e.getMessage());
        }
        fichiers.sort(Comparator.comparing(Fichier::nom).reversed());
        return fichiers;
    }

    /** Chemin d'un fichier de sauvegarde à télécharger, ou null s'il n'existe pas (le nom est validé strictement). */
    public Path cheminDe(String nom) {
        if (nom == null || !NOM_VALIDE.matcher(nom).matches()) return null;
        Path chemin = dossier.resolve(nom).normalize();
        return chemin.startsWith(dossier) && Files.isRegularFile(chemin) ? chemin : null;
    }

    public Fichier derniereSauvegardeBase() {
        return lister().stream().filter(f -> f.type().equals("base")).findFirst().orElse(null);
    }

    /** Vrai s'il n'existe aucune sauvegarde ou si la dernière a plus de 36 heures. */
    public boolean enRetard() {
        Fichier derniere = derniereSauvegardeBase();
        return derniere == null || derniere.date().isBefore(LocalDateTime.now(ZONE).minus(MAX_AGE.plusHours(12)));
    }

    public boolean pgDumpDisponible() { return trouverPgDump() != null; }

    /** Sauvegarde immédiate : la base (obligatoire) et les photos (si elles existent). Retourne les fichiers créés. */
    public synchronized List<Fichier> sauvegarder(String declencheur) {
        String pgDump = trouverPgDump();
        if (pgDump == null) {
            throw new IllegalStateException("pg_dump est introuvable. Installez les outils PostgreSQL ou indiquez son chemin (CASAFAMI_PG_DUMP).");
        }
        Matcher m = URL_JDBC.matcher(url == null ? "" : url);
        if (!m.matches()) throw new IllegalStateException("Adresse de la base non reconnue : " + url);
        try {
            Files.createDirectories(dossier);
            String horodatage = LocalDateTime.now(ZONE).format(HORODATAGE);
            Path base = dossier.resolve("casafami-" + horodatage + ".dump");
            executerPgDump(pgDump, m.group(1), m.group(2) == null ? "5432" : m.group(2), m.group(3), base);
            List<Fichier> crees = new ArrayList<>();
            crees.add(decrire(base, "base"));
            Path photos = dossier.resolve("casafami-" + horodatage + "-photos.zip");
            if (archiverPhotos(photos)) crees.add(decrire(photos, "photos"));
            purger();
            String details = String.join(", ", crees.stream().map(f -> f.nom() + " (" + (f.taille() / 1024) + " Ko)").toList());
            if ("auto".equals(declencheur)) audit.logAs("système", "SYSTEME", "SAUVEGARDE", "Base", null, details);
            else audit.log("SAUVEGARDE", "Base", null, details);
            log.info("Sauvegarde terminée ({}) : {}", declencheur, details);
            return crees;
        } catch (IOException e) {
            throw new IllegalStateException("Sauvegarde impossible : " + e.getMessage(), e);
        }
    }

    /** Vérifie toutes les 30 minutes : sauvegarde si la dernière date de plus de 24 h (ou n'existe pas). */
    @Scheduled(initialDelay = 120_000, fixedDelay = 1_800_000)
    public void sauvegardeAutomatique() {
        if (!automatique) return;
        Fichier derniere = derniereSauvegardeBase();
        if (derniere != null && derniere.date().isAfter(LocalDateTime.now(ZONE).minus(MAX_AGE))) return;
        try {
            sauvegarder("auto");
        } catch (RuntimeException e) {
            log.error("Sauvegarde automatique échouée : {}", e.getMessage());
            prevenirDeLEchec(e.getMessage());
        }
    }

    private void prevenirDeLEchec(String raison) {
        if (Duration.between(derniereAlerte, Instant.now()).toHours() < 12) return;
        derniereAlerte = Instant.now();
        for (Utilisateur admin : utilisateurs.findByRoleIn(List.of(Role.SUPER_ADMIN))) {
            notifications.informer(admin, "SAUVEGARDE_ECHEC", "Sauvegarde automatique échouée",
                    "La sauvegarde de la base n'a pas pu être faite : " + raison, "Système", "/admin/sauvegardes");
        }
    }

    private void executerPgDump(String pgDump, String hote, String port, String base, Path sortie) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(pgDump, "-Fc", "-h", hote, "-p", port, "-U", utilisateurBase, "-f", sortie.toString(), base);
        pb.redirectErrorStream(true);
        if (!motDePasseBase.isEmpty()) pb.environment().put("PGPASSWORD", motDePasseBase);
        Process process = pb.start();
        String texte;
        try (InputStream flux = process.getInputStream()) { texte = new String(flux.readAllBytes(), StandardCharsets.UTF_8); }
        try {
            if (!process.waitFor(10, TimeUnit.MINUTES)) { process.destroyForcibly(); throw new IOException("pg_dump a dépassé 10 minutes"); }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Sauvegarde interrompue", e);
        }
        if (process.exitValue() != 0 || !Files.exists(sortie) || Files.size(sortie) == 0) {
            Files.deleteIfExists(sortie);
            String resume = texte.strip();
            throw new IOException("pg_dump a échoué" + (resume.isEmpty() ? "" : " : " + (resume.length() > 300 ? resume.substring(0, 300) : resume)));
        }
    }

    private boolean archiverPhotos(Path zip) throws IOException {
        if (!Files.isDirectory(uploads)) return false;
        try (Stream<Path> fichiers = Files.walk(uploads)) {
            List<Path> liste = fichiers.filter(Files::isRegularFile).toList();
            if (liste.isEmpty()) return false;
            try (ZipOutputStream sortie = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Path p : liste) {
                    sortie.putNextEntry(new ZipEntry(uploads.relativize(p).toString().replace('\\', '/')));
                    Files.copy(p, sortie);
                    sortie.closeEntry();
                }
            }
        }
        return true;
    }

    /** Ne garde que les « garder » sauvegardes les plus récentes de chaque type. */
    private void purger() {
        for (String type : List.of("base", "photos")) {
            List<Fichier> anciens = lister().stream().filter(f -> f.type().equals(type)).skip(garder).toList();
            for (Fichier f : anciens) {
                try { Files.deleteIfExists(dossier.resolve(f.nom())); } catch (IOException e) { log.warn("Suppression de {} impossible : {}", f.nom(), e.getMessage()); }
            }
        }
    }

    private Fichier decrire(Path p, String type) throws IOException {
        return new Fichier(p.getFileName().toString(), Files.size(p), LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), ZONE), type);
    }

    /** pg_dump : chemin configuré, sinon PATH, sinon installation standard de PostgreSQL (Windows / Linux). */
    String trouverPgDump() {
        if (!pgDumpConfigure.isEmpty()) return pgDumpConfigure;
        if (repond("pg_dump")) return "pg_dump";
        Path windows = Path.of("C:\\Program Files\\PostgreSQL");
        if (Files.isDirectory(windows)) {
            try (Stream<Path> versions = Files.list(windows)) {
                return versions.map(v -> v.resolve("bin").resolve("pg_dump.exe")).filter(Files::isRegularFile)
                        .max(Comparator.comparing(Path::toString)).map(Path::toString).orElse(null);
            } catch (IOException ignore) { /* on essaie la suite */ }
        }
        for (String chemin : List.of("/usr/bin/pg_dump", "/usr/local/bin/pg_dump")) if (Files.isExecutable(Path.of(chemin))) return chemin;
        return null;
    }

    private static boolean repond(String commande) {
        try {
            Process p = new ProcessBuilder(commande, "--version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
