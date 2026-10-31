package com.cabinet.cabinet.config;

import com.cabinet.cabinet.entity.ParametresCabinet;
import com.cabinet.cabinet.entity.Service;
import com.cabinet.cabinet.repository.ParametresCabinetRepository;
import com.cabinet.cabinet.repository.ServiceRepository;
import com.cabinet.cabinet.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Tarifs de départ des services (en Ariary), posés une seule fois sur les services qui n'ont pas encore de prix : le tarif le moins cher relevé
 * en octobre 2026 pour la même prestation à Antananarivo (cliniques et maternités privées, sage-femme libérale ; voir les sources dans la réponse
 * donnée à l'administrateur). Ce ne sont que des points de départ : l'administration les corrige à tout moment dans « Services et tarifs ».
 * Les services sans tarif relevé (vaccination, planning familial, soins après l'accouchement, dépistage, soins…) restent « Non défini » : il n'y a
 * pas de prix public fiable, et le vaccin du programme national est gratuit.
 */
@Component
@RequiredArgsConstructor
public class TarifsDeDepart implements ApplicationRunner {
    /** Un tarif de départ : le premier groupe dont un mot se trouve dans le nom du service l'emporte ; « prix » null = volontairement sans tarif. */
    record Regle(List<String> mots, Integer prix) { }

    static final List<Regle> REGLES = List.of(
            new Regle(List.of("prenatal", "grossesse"), 8_000),                                    // consultation prénatale avec une sage-femme : Marie Stopes Madagascar
            new Regle(List.of("echograph"), 30_000),                                               // échographie 2D : clinique Mère Enfant Antsakaviro ; 3D : maternité Soavimasoandro
            new Regle(List.of("post-natal", "postnatal", "post natal", "apres l accouchement"), null),   // pas de tarif relevé : on ne confond pas avec l'accouchement
            new Regle(List.of("accouchement"), 60_000),                                            // sage-femme libérale d'Ambohimangakely : « cela commence à 60 000 ariary » (L'Express, 2023)
            new Regle(List.of("consultation"), 15_000));                                           // consultation de sage-femme : clinique Antsakaviro et Pavillon Sainte Fleur

    private final ServiceRepository services;
    private final ParametresCabinetRepository parametres;
    private final AuditService audit;

    @Override
    public void run(ApplicationArguments args) {
        appliquer();
    }

    /** Pose les tarifs de départ sur les services sans prix, une seule fois (jusqu'à ce qu'au moins un service en reçoive un). Retourne le nombre de services mis à jour. */
    @Transactional
    public int appliquer() {
        ParametresCabinet p = parametres.findById(1L).orElseGet(ParametresCabinet::new);
        if (Boolean.TRUE.equals(p.getTarifsDepartAjoutes())) return 0;
        int misAJour = 0;
        for (Service service : services.findAll()) {
            if (service.getPrix() != null) continue;
            Integer prix = prixDeDepart(service.getNom());
            if (prix == null) continue;
            service.setPrix(prix);
            services.save(service);
            misAJour++;
        }
        if (misAJour > 0) {
            p.setTarifsDepartAjoutes(true);
            parametres.save(p);
            audit.logAs("système", "SYSTEME", "TARIFS_DE_DEPART", "Service", null, misAJour + " service(s) ont reçu leur tarif de départ");
        }
        return misAJour;
    }

    public static Integer prixDeDepart(String nom) {
        String texte = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replace('\'', ' ').replace('’', ' ');
        for (Regle regle : REGLES) {
            if (regle.mots().stream().anyMatch(texte::contains)) return regle.prix();
        }
        return null;
    }
}
