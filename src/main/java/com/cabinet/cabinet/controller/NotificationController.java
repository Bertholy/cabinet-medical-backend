package com.cabinet.cabinet.controller;

import com.cabinet.cabinet.entity.Notification;
import com.cabinet.cabinet.repository.NotificationRepository;
import com.cabinet.cabinet.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cloche de notifications du membre du personnel connecté : chacun ne voit et ne modifie que les siennes. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationRepository notifications;
    private final UtilisateurRepository utilisateurs;

    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> lister(Authentication authentication) {
        Long moi = moi(authentication);
        List<Notification> liste = notifications.findByDestinataireIdOrderByCreeLeDescIdDesc(moi, PageRequest.of(0, 30));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nonLues", notifications.countByDestinataireIdAndLueFalse(moi));
        body.put("notifications", liste);
        return body;
    }

    @PutMapping("/{id}/lue")
    @Transactional
    public ResponseEntity<Void> marquerLue(@PathVariable Long id, Authentication authentication) {
        Notification n = notifications.findById(id).filter(x -> x.getDestinataire().getId().equals(moi(authentication)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable."));
        n.setLue(true);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/lues")
    @Transactional
    public ResponseEntity<Void> toutMarquerLu(Authentication authentication) {
        notifications.marquerToutesLues(moi(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long moi(Authentication authentication) {
        return utilisateurs.findByEmail(authentication.getName()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte introuvable.")).getId();
    }
}
