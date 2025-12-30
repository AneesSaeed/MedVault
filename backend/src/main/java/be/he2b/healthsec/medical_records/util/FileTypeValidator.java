package be.he2b.healthsec.medical_records.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Validateur de types de fichiers basé sur les magic bytes (signatures de fichiers)
 * 
 * SECURITY: Implémentation de la recommandation du rapport de sécurité (Question 15)
 * Empêche l'upload de fichiers malveillants déguisés avec une fausse extension
 * 
 * Référence: https://en.wikipedia.org/wiki/List_of_file_signatures
 */
public class FileTypeValidator {

    /**
     * Map des magic bytes connus pour les types de fichiers autorisés
     * Format: MIME type -> liste de signatures possibles
     */
    private static final Map<String, byte[][]> MAGIC_BYTES = new HashMap<>();

    static {
        // PDF
        MAGIC_BYTES.put("application/pdf", new byte[][] {
            new byte[] { 0x25, 0x50, 0x44, 0x46 } // %PDF
        });

        // Images JPEG
        MAGIC_BYTES.put("image/jpeg", new byte[][] {
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }, // JFIF
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1 }, // Exif
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE2 }  // Canon
        });

        // Images PNG
        MAGIC_BYTES.put("image/png", new byte[][] {
            new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A } // PNG signature
        });

        // MS Word (.doc)
        MAGIC_BYTES.put("application/msword", new byte[][] {
            new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 } // OLE2
        });

        // MS Word (.docx) et autres Office Open XML
        MAGIC_BYTES.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[][] {
            new byte[] { 0x50, 0x4B, 0x03, 0x04 }, // ZIP (docx est un ZIP)
            new byte[] { 0x50, 0x4B, 0x05, 0x06 }, // ZIP empty
            new byte[] { 0x50, 0x4B, 0x07, 0x08 }  // ZIP spanned
        });

        // Texte brut (pas de magic bytes spécifiques, vérification ASCII)
        MAGIC_BYTES.put("text/plain", new byte[][] {
            // Pas de signature fixe, sera validé séparément
        });
    }

    /**
     * Liste des types MIME autorisés pour les uploads
     */
    private static final String[] ALLOWED_MIME_TYPES = {
        "application/pdf",
        "image/jpeg",
        "image/png",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    };

    /**
     * Valide qu'un fichier correspond bien à son type MIME déclaré
     * 
     * @param contentStream Stream du contenu du fichier
     * @param declaredMimeType Type MIME déclaré par le client
     * @return true si le fichier est valide, false sinon
     * @throws IOException Si erreur de lecture
     */
    public static boolean validateFileType(InputStream contentStream, String declaredMimeType) throws IOException {
        // 1. Vérifier que le type MIME est autorisé
        if (!isAllowedMimeType(declaredMimeType)) {
            return false;
        }

        // 2. Lire les premiers octets du fichier (magic bytes)
        byte[] header = new byte[8]; // 8 octets suffisent pour la plupart des signatures
        int bytesRead = contentStream.read(header);
        
        if (bytesRead < 4) { // Minimum 4 octets pour vérifier une signature
            return false;
        }

        // 3. Cas spécial: texte brut
        if ("text/plain".equals(declaredMimeType)) {
            return validateTextFile(header, bytesRead);
        }

        // 4. Vérifier les magic bytes
        byte[][] signatures = MAGIC_BYTES.get(declaredMimeType);
        if (signatures == null || signatures.length == 0) {
            return false;
        }

        for (byte[] signature : signatures) {
            if (matchesSignature(header, signature)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Vérifie si un type MIME est autorisé
     */
    public static boolean isAllowedMimeType(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        for (String allowed : ALLOWED_MIME_TYPES) {
            if (allowed.equalsIgnoreCase(mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vérifie si les octets lus correspondent à une signature
     */
    private static boolean matchesSignature(byte[] header, byte[] signature) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valide un fichier texte (vérification ASCII/UTF-8)
     * Un fichier texte valide ne devrait contenir que des caractères imprimables
     */
    private static boolean validateTextFile(byte[] header, int length) {
        for (int i = 0; i < length; i++) {
            byte b = header[i];
            // Autoriser: caractères ASCII imprimables (32-126), tab (9), LF (10), CR (13)
            if (!((b >= 32 && b <= 126) || b == 9 || b == 10 || b == 13)) {
                // Tolérance pour UTF-8 (octets > 127)
                if ((b & 0xFF) < 128) {
                    return false; // Caractère de contrôle non autorisé
                }
            }
        }
        return true;
    }

    /**
     * Retourne un message d'erreur descriptif pour un type MIME invalide
     */
    public static String getValidationErrorMessage(String declaredMimeType) {
        if (!isAllowedMimeType(declaredMimeType)) {
            return String.format(
                "Type de fichier non autorisé: %s. Types acceptés: PDF, JPEG, PNG, DOC, DOCX, TXT",
                declaredMimeType
            );
        }
        return String.format(
            "Le contenu du fichier ne correspond pas au type déclaré (%s). Le fichier pourrait être corrompu ou malveillant.",
            declaredMimeType
        );
    }
}
