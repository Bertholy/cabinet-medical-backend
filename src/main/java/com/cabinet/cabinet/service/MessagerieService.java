package com.cabinet.cabinet.service;

import com.cabinet.cabinet.entity.Message;
import com.cabinet.cabinet.entity.Utilisateur;
import com.cabinet.cabinet.repository.MessageRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Messagerie interne : conversations privées entre membres du personnel. */
@Service
@RequiredArgsConstructor
public class MessagerieService {
    private static final ZoneId ZONE = ZoneId.of("Indian/Antananarivo");
    private static final int MAX_CARACTERES = 2000;
    private static final int MESSAGES_AFFICHES = 150;
    private static final int MAX_PAR_MINUTE = 30;

    private final MessageRepository messages;
    private final UtilisateurRepository utilisateurs;

    public record DernierMessage(String extrait, LocalDateTime date, boolean moi) { }

    public record Contact(Long id, String nom, String role, String photoUrl, long nonLus, DernierMessage dernier) { }

    public record MessageDto(Long id, boolean deMoi, String contenu, LocalDateTime creeLe, boolean lu) { }

    public record Conversation(Contact contact, List<MessageDto> messages) { }

    @Transactional(readOnly = true)
    public Map<String, Object> contacts(Utilisateur moi) {
        Map<Long, Long> nonLus = new HashMap<>();
        for (Object[] ligne : messages.nonLusParExpediteur(moi.getId())) nonLus.put((Long) ligne[0], (Long) ligne[1]);
        Map<Long, DernierMessage> derniers = new HashMap<>();
        for (Message m : messages.recentsDe(moi.getId(), PageRequest.of(0, 500))) {
            boolean envoye = m.getExpediteur().getId().equals(moi.getId());
            Long autre = envoye ? m.getDestinataire().getId() : m.getExpediteur().getId();
            derniers.putIfAbsent(autre, new DernierMessage(extrait(m.getContenu()), m.getCreeLe(), envoye));
        }
        List<Contact> liste = new ArrayList<>();
        for (Utilisateur u : utilisateurs.findAll()) {
            if (u.getId().equals(moi.getId())) continue;
            liste.add(new Contact(u.getId(), u.getNom(), u.getRole().name(), u.getPhotoUrl(), nonLus.getOrDefault(u.getId(), 0L), derniers.get(u.getId())));
        }
        // conversations récentes d'abord, puis les autres par ordre alphabétique
        liste.sort(Comparator.comparing((Contact c) -> c.dernier() == null ? LocalDateTime.MIN : c.dernier().date()).reversed()
                .thenComparing(c -> c.nom() == null ? "" : c.nom().toLowerCase()));
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("nonLus", messages.countByDestinataireIdAndLuFalse(moi.getId()));
        reponse.put("contacts", liste);
        return reponse;
    }

    @Transactional(readOnly = true)
    public long nonLus(Utilisateur moi) {
        return messages.countByDestinataireIdAndLuFalse(moi.getId());
    }

    /** Ouvre la conversation avec une personne : les messages reçus sont marqués comme lus. */
    @Transactional
    public Conversation conversation(Utilisateur moi, Long autreId) {
        Utilisateur autre = autre(moi, autreId);
        messages.marquerLus(moi.getId(), autre.getId());
        List<Message> recents = new ArrayList<>(messages.conversation(moi.getId(), autre.getId(), PageRequest.of(0, MESSAGES_AFFICHES)));
        Collections.reverse(recents);
        List<MessageDto> dtos = recents.stream().map(m -> versDto(m, moi)).toList();
        return new Conversation(new Contact(autre.getId(), autre.getNom(), autre.getRole().name(), autre.getPhotoUrl(), 0, null), dtos);
    }

    @Transactional
    public MessageDto envoyer(Utilisateur moi, Long destinataireId, String contenu) {
        Utilisateur destinataire = autre(moi, destinataireId);
        String texte = contenu == null ? "" : contenu.strip();
        if (texte.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Écrivez un message avant d'envoyer.");
        if (texte.length() > MAX_CARACTERES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne peut pas dépasser " + MAX_CARACTERES + " caractères.");
        LocalDateTime maintenant = LocalDateTime.now(ZONE);
        if (messages.countByExpediteurIdAndCreeLeAfter(moi.getId(), maintenant.minusMinutes(1)) >= MAX_PAR_MINUTE) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Vous envoyez trop de messages à la suite. Patientez un instant.");
        }
        Message m = new Message();
        m.setExpediteur(moi);
        m.setDestinataire(destinataire);
        m.setContenu(texte);
        m.setCreeLe(maintenant);
        return versDto(messages.save(m), moi);
    }

    /** Ménage quotidien : les messages lus depuis plus d'un an sont supprimés. */
    @Scheduled(cron = "0 15 4 * * *", zone = "Indian/Antananarivo")
    @Transactional
    public void purger() {
        messages.supprimerLusAvant(LocalDateTime.now(ZONE).minusYears(1));
    }

    private Utilisateur autre(Utilisateur moi, Long id) {
        if (id == null || id.equals(moi.getId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez un collègue.");
        return utilisateurs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cette personne n'existe plus."));
    }

    private MessageDto versDto(Message m, Utilisateur moi) {
        return new MessageDto(m.getId(), m.getExpediteur().getId().equals(moi.getId()), m.getContenu(), m.getCreeLe(), m.isLu());
    }

    private static String extrait(String contenu) {
        String ligne = contenu.replace('\n', ' ').strip();
        return ligne.length() <= 70 ? ligne : ligne.substring(0, 69) + "…";
    }
}
