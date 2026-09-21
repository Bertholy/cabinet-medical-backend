package com.cabinet.cabinet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    // ============ GÉNÉRER UN TOKEN ============
    public String genererToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ============ EXTRAIRE L'EMAIL DU TOKEN ============
   public String extraireEmail(String token) {
    return extraireClaim(token, claims -> claims.getSubject());   // ← lambda
}

    // ============ VÉRIFIER SI LE TOKEN EST VALIDE ============
    public boolean estValide(String token, UserDetails userDetails) {
        final String email = extraireEmail(token);
        return email.equals(userDetails.getUsername()) && !estExpire(token);
    }

    // ============ VÉRIFIER EXPIRATION ============
    private boolean estExpire(String token) {
        return extraireExpiration(token).before(new Date());
    }

  private Date extraireExpiration(String token) {
    return extraireClaim(token, claims -> claims.getExpiration());  // ← lambda
}
    // ============ EXTRAIRE UN CLAIM GÉNÉRIQUE ============
    private <T> T extraireClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extraireTousLesClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extraireTousLesClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ============ CLÉ DE SIGNATURE ============
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}