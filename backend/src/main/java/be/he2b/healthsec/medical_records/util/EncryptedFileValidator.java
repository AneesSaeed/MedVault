package be.he2b.healthsec.medical_records.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 * Lightweight validation for uploaded encrypted files.
 *
 * <p>Expected structure: AES-GCM output where the first 12 bytes are the IV/nonce,
 * followed by ciphertext. This validator does not decrypt; it only rejects obvious
 * non-encrypted uploads (e.g., PDF/JPEG/PNG/ZIP headers).</p>
 */
public final class EncryptedFileValidator {

    private static final Logger logger = Logger.getLogger(EncryptedFileValidator.class.getName());

    private EncryptedFileValidator() {
        // Utility class
    }

    /** AES-GCM IV size in bytes. */
    private static final int IV_SIZE = 12;

    /** Minimum: IV + at least 1 byte of ciphertext. */
    private static final int MIN_ENCRYPTED_FILE_SIZE = 13;

    /** How many bytes to read to check for known file signatures. */
    private static final int VALIDATION_HEADER_SIZE = 64;

    /**
     * Returns true if the stream looks like an encrypted file.
     *
     * <p>Checks:
     * <ul>
     *   <li>minimum length (IV + ciphertext)</li>
     *   <li>does not start with known "magic bytes" of common cleartext formats</li>
     * </ul>
     * </p>
     */
    public static boolean validateEncryptedFile(InputStream contentStream) throws IOException {
        byte[] header = new byte[VALIDATION_HEADER_SIZE];
        int bytesRead = contentStream.read(header);
        
        if (bytesRead < MIN_ENCRYPTED_FILE_SIZE || bytesRead < IV_SIZE + 1) {
            logger.warning("File too small: " + bytesRead + " bytes (minimum: " + MIN_ENCRYPTED_FILE_SIZE + ")");
            return false;
        }

        int magicBytesCheckLength = Math.min(bytesRead, 8);
        if (startsWithKnownMagicBytes(header, magicBytesCheckLength)) {
            logger.warning("File starts with known magic bytes (likely not encrypted)");
            return false;
        }

        return true;
    }

    /**
     * Detects common cleartext file signatures in the first bytes.
     */
    private static boolean startsWithKnownMagicBytes(byte[] header, int length) {
        // PDF
        if (length >= 4 && header[0] == 0x25 && header[1] == 0x50 && 
            header[2] == 0x44 && header[3] == 0x46) {
            return true;
        }

        // JPEG
        if (length >= 4 && header[0] == (byte)0xFF && header[1] == (byte)0xD8 && 
            header[2] == (byte)0xFF) {
            return true; 
        }

        // PNG
        if (length >= 8 && header[0] == (byte)0x89 && header[1] == 0x50 && 
            header[2] == 0x4E && header[3] == 0x47) {
            return true; // PNG
        }

        // ZIP/DOCX
        if (length >= 4 && header[0] == 0x50 && header[1] == 0x4B && 
            header[2] == 0x03 && header[3] == 0x04) {
            return true; // ZIP
        }

        // OLE2/DOC
        if (length >= 8 && header[0] == (byte)0xD0 && header[1] == (byte)0xCF && 
            header[2] == 0x11 && header[3] == (byte)0xE0) {
            return true; // OLE2
        }

        return false;
    }

    /**
     * User-facing error message for invalid encrypted uploads.
     */
    public static String getValidationErrorMessage() {
        return "Le fichier chiffré ne correspond pas à la structure attendue (IV + ciphertext). " +
               "Le fichier pourrait être corrompu ou malveillant.";
    }
}

