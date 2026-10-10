package com.cabinet.cabinet.util;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Répare les textes « cassés » par un double encodage : « mÃ©dicale » → « médicale », « Ã‰chographie » → « Échographie ».
 *
 * Cause habituelle : un texte UTF-8 relu comme du Windows-1252 (script SQL ouvert avec le mauvais encodage, copier-coller
 * depuis un fichier mal enregistré…) puis enregistré à nouveau. La réparation fait le chemin inverse : chaque caractère est
 * remis en octet Windows-1252, puis l'ensemble est relu en UTF-8. Elle n'agit que si le résultat est du UTF-8 strictement valide,
 * donc un texte correct n'est jamais modifié.
 */
public final class TextRepair {
    private TextRepair() { }

    private static final Charset CP1252 = Charset.forName("windows-1252");

    /** Un texte est suspect s'il contient un caractère qui introduit un octet UTF-8 mal lu (Ã, Â, â) ; la décision finale est prise par le décodage strict. */
    public static boolean suspect(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == 'Ã' || c == 'Â' || c == 'â') return true;
        }
        return false;
    }

    /** @return le texte réparé, ou le texte d'origine s'il n'est pas cassé (ou s'il ne peut pas être réparé sans risque). */
    public static String repair(String text) {
        if (!suspect(text)) return text;
        String current = text;
        for (int pass = 0; pass < 3; pass++) { // jusqu'à un triple encodage
            String decoded = decodeOnce(current);
            if (decoded == null || decoded.equals(current) || decoded.length() >= current.length()) break;
            current = decoded;
            if (!suspect(current)) break;
        }
        return current;
    }

    private static String decodeOnce(String text) {
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c <= 0x7F) { bytes[i] = (byte) c; continue; }
            byte[] mapped = String.valueOf(c).getBytes(CP1252);
            // Windows-1252 n'a pas de caractère pour 0x81, 0x8D, 0x8F, 0x90, 0x9D : ils restent tels quels (ISO-8859-1).
            if (mapped.length == 1 && !(mapped[0] == '?' && c != '?')) bytes[i] = mapped[0];
            else if (c <= 0xFF) bytes[i] = (byte) c;
            else return null; // un caractère qui ne vient pas d'une mauvaise lecture : le texte n'est pas du double encodage
        }
        try {
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return decoded.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
