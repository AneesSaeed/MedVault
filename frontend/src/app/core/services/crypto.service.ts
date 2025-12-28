import { Injectable } from '@angular/core';
import { LoggingService } from './logging.service';

/**
 * Service de chiffrement utilisant Web Crypto API
 *
 * Architecture:
 * - Chaque utilisateur a une paire de clés RSA (publique/privée)
 * - Les patients ont une clé symétrique AES pour chiffrer leurs dossiers médicaux
 * - La clé privée RSA reste côté client (jamais envoyée au serveur)
 * - La clé publique RSA est envoyée au serveur
 * - La clé symétrique AES est chiffrée avec la clé publique RSA avant d'être stockée
 */
@Injectable({
  providedIn: 'root'
})
export class CryptoService {

  constructor(private logger: LoggingService) {}

  /**
   * Génère une paire de clés RSA (2048 bits)
   * @returns Promise avec {publicKey, privateKey} en format CryptoKey
   */
  async generateRSAKeyPair(): Promise<{ publicKey: CryptoKey; privateKey: CryptoKey }> {
    this.logger.debug('Generating RSA-2048 key pair...', { algorithm: 'RSA-OAEP', keySize: 2048 }, 'CryptoService');
    const startTime = performance.now();
    
    try {
      const keyPair = await window.crypto.subtle.generateKey(
        {
          name: 'RSA-OAEP',
          modulusLength: 2048,
          publicExponent: new Uint8Array([1, 0, 1]),
          hash: 'SHA-256',
        },
        false,
        ['encrypt', 'decrypt']
      );

      const duration = Math.round(performance.now() - startTime);
      this.logger.logSecurityEvent('RSA_KEY_PAIR_GENERATED', 'client', 'LOW', {
        algorithm: 'RSA-OAEP',
        modulusLength: 2048,
        hash: 'SHA-256',
        durationMs: duration
      });

      return {
        publicKey: keyPair.publicKey,
        privateKey: keyPair.privateKey
      };
    } catch (error) {
      this.logger.error('Failed to generate RSA key pair', error, {}, 'CryptoService');
      throw error;
    }
  }


  /**
   * Génère une clé symétrique AES-GCM (256 bits)
   * @returns Promise avec la clé CryptoKey
   */
  async generateAESKey(): Promise<CryptoKey> {
    this.logger.debug('Generating AES-256 symmetric key...', { algorithm: 'AES-GCM', keyLength: 256 }, 'CryptoService');
    
    try {
      const key = await window.crypto.subtle.generateKey(
        {
          name: 'AES-GCM',
          length: 256,
        },
        true, // extractable
        ['encrypt', 'decrypt']
      );

      this.logger.logSecurityEvent('AES_KEY_GENERATED', 'client', 'LOW', {
        algorithm: 'AES-GCM',
        keyLength: 256,
        extractable: true
      });

      return key;
    } catch (error) {
      this.logger.error('Failed to generate AES key', error, {}, 'CryptoService');
      throw error;
    }
  }

  /**
   * Génère un IV unique pour AES-GCM (par défaut 12 octets)
   */
  generateIV(length = 12): Uint8Array {
    const iv = new Uint8Array(length);
    window.crypto.getRandomValues(iv);
    return iv;
  }

  /**
   * Exporte une clé publique RSA au format PEM (base64)
   * @param publicKey Clé publique CryptoKey
   * @returns Promise avec la clé publique en format PEM
   */
  async exportPublicKey(publicKey: CryptoKey): Promise<string> {
    this.logger.debug('Exporting RSA public key to PEM format...', {}, 'CryptoService');
    
    try {
      const exported = await window.crypto.subtle.exportKey('spki', publicKey);
      const exportedAsBase64 = this.arrayBufferToBase64(exported);
      const pem = `-----BEGIN PUBLIC KEY-----\n${exportedAsBase64}\n-----END PUBLIC KEY-----`;
      
      this.logger.info('RSA public key exported successfully', { format: 'PEM', size: pem.length }, 'CryptoService');
      return pem;
    } catch (error) {
      this.logger.error('Failed to export public key', error, {}, 'CryptoService');
      throw error;
    }
  }

  /**
   * Importe une clé publique RSA depuis le format PEM
   * @param pem Clé publique en format PEM
   * @returns Promise avec la clé publique CryptoKey
   */
  async importPublicKey(pem: string): Promise<CryptoKey> {
    this.logger.debug('Importing RSA public key from PEM format...', { pemLength: pem.length }, 'CryptoService');
    
    try {
      const pemHeader = '-----BEGIN PUBLIC KEY-----';
      const pemFooter = '-----END PUBLIC KEY-----';
      const pemContents = pem
        .replace(pemHeader, '')
        .replace(pemFooter, '')
        .replace(/\s/g, '');

      const binaryDer = this.base64ToArrayBuffer(pemContents);

      const key = await window.crypto.subtle.importKey(
        'spki',
        binaryDer,
        {
          name: 'RSA-OAEP',
          hash: 'SHA-256',
        },
        true,
        ['encrypt']
      );

      this.logger.logSecurityEvent('RSA_PUBLIC_KEY_IMPORTED', 'client', 'MEDIUM', {
        format: 'PEM',
        algorithm: 'RSA-OAEP',
        hash: 'SHA-256'
      });

      return key;
    } catch (error) {
      this.logger.error('Failed to import public key', error, { pemLength: pem?.length }, 'CryptoService');
      throw error;
    }
  }

  /**
   * Importe une clé AES depuis le format base64
   * @param base64Key Clé en base64
   * @returns Promise avec la clé AES CryptoKey
   */
  async importAESKey(base64Key: string): Promise<CryptoKey> {
    const keyData = this.base64ToArrayBuffer(base64Key);
    return await window.crypto.subtle.importKey(
      'raw',
      keyData,
      {
        name: 'AES-GCM',
        length: 256,
      },
      true,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Chiffre une clé AES avec une clé publique RSA
   * @param aesKey Clé AES à chiffrer
   * @param publicKey Clé publique RSA
   * @returns Promise avec la clé AES chiffrée en base64
   */
  async encryptAESKeyWithRSA(aesKey: CryptoKey, publicKey: CryptoKey): Promise<string> {
    // Exporte la clé AES en raw
    const exportedAES = await window.crypto.subtle.exportKey('raw', aesKey);
    const aesKeyArray = new Uint8Array(exportedAES);

    // Chiffre avec RSA-OAEP
    const encrypted = await window.crypto.subtle.encrypt(
      {
        name: 'RSA-OAEP',
      },
      publicKey,
      aesKeyArray
    );

    return this.arrayBufferToBase64(encrypted);
  }

  /**
   * Déchiffre une clé AES chiffrée avec RSA
   * @param encryptedAESKeyBase64 Clé AES chiffrée en base64
   * @param privateKey Clé privée RSA
   * @returns Promise avec la clé AES déchiffrée
   */
  async decryptAESKeyWithRSA(encryptedAESKeyBase64: string, privateKey: CryptoKey): Promise<CryptoKey> {
    const encryptedData = this.base64ToArrayBuffer(encryptedAESKeyBase64);

    // Déchiffre avec RSA-OAEP
    const decrypted = await window.crypto.subtle.decrypt(
      {
        name: 'RSA-OAEP',
      },
      privateKey,
      encryptedData
    );

    // Importe la clé AES déchiffrée
    return await this.importAESKey(this.arrayBufferToBase64(decrypted));
  }

  /**
   * Chiffre des données avec AES-GCM
   * @param data Données à chiffrer (string)
   * @param key Clé AES
   * @returns Promise avec {encrypted: base64, iv: base64}
   */
  async encryptWithAES(data: string, key: CryptoKey): Promise<{ encrypted: string; iv: string }> {
    const encoder = new TextEncoder();
    const dataBuffer = encoder.encode(data);

    // Génère un IV (Initialization Vector) aléatoire
    const iv = window.crypto.getRandomValues(new Uint8Array(12)); // 12 bytes pour AES-GCM

    const encrypted = await window.crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv: iv as unknown as BufferSource,
      },
      key,
      dataBuffer
    );

    return {
      encrypted: this.arrayBufferToBase64(encrypted),
      iv: this.arrayBufferToBase64(iv.buffer)
    };
  }

  /**
   * Chiffre des données binaires avec AES-GCM
   * @param data ArrayBuffer (bytes)
   * @param key Clé AES
   * @returns Promise avec {encrypted: base64, iv: base64}
   */
  async encryptBytesWithAES(data: ArrayBuffer, key: CryptoKey): Promise<{ encrypted: string; iv: string }> {
    const iv = window.crypto.getRandomValues(new Uint8Array(12));

    const encrypted = await window.crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      data
    );

    return {
      encrypted: this.arrayBufferToBase64(encrypted),
      iv: this.arrayBufferToBase64(iv.buffer),
    };
  }


  /**
   * Pack iv + ciphertext into one Uint8Array:
   * [ 12 bytes IV | ciphertext bytes ]
   */
  packIvAndCiphertext(ivBase64: string, encryptedBase64: string): Uint8Array {
    const iv = new Uint8Array(this.base64ToArrayBuffer(ivBase64));
    const ct = new Uint8Array(this.base64ToArrayBuffer(encryptedBase64));

    const out = new Uint8Array(iv.length + ct.length);
    out.set(iv, 0);
    out.set(ct, iv.length);
    return out;
  }

  unpackIvCipherFromBase64(packedBase64: string): { ivBase64: string; encryptedBase64: string } {
    const combined = this.base64ToUint8Array(packedBase64);
    const iv = combined.slice(0, 12);
    const enc = combined.slice(12);

    return {
      ivBase64: this.uint8ArrayToBase64(iv),
      encryptedBase64: this.uint8ArrayToBase64(enc),
    };
  }


  async decryptBytesWithAES(encryptedBase64: string, ivBase64: string, key: CryptoKey): Promise<ArrayBuffer> {
    const encrypted = this.base64ToArrayBuffer(encryptedBase64);
    const ivArray = this.base64ToUint8Array(ivBase64);

    return await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: ivArray as unknown as BufferSource },
      key,
      encrypted
    );
  }



  async decryptWithAES(encryptedBase64: string, ivBase64: string, key: CryptoKey): Promise<string> {
    const encrypted = this.base64ToArrayBuffer(encryptedBase64);
    const ivArray = this.base64ToUint8Array(ivBase64);

    const decrypted = await window.crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: ivArray as unknown as BufferSource },
      key,
      encrypted
    );

    return new TextDecoder().decode(decrypted);
  }


  // ========== Helpers ==========

  private arrayBufferToBase64(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  private base64ToArrayBuffer(base64: string): ArrayBuffer {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
  }


  packIvCipherToBase64(ivBase64: string, encryptedBase64: string): string {
    const packed = this.packIvAndCiphertext(ivBase64, encryptedBase64); // Uint8Array
    return this.uint8ArrayToBase64(packed);
  }

  private uint8ArrayToBase64(bytes: Uint8Array): string {
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
    return btoa(binary);
  }

  private base64ToUint8Array(base64: string): Uint8Array {
    const binary = atob(base64);
    const buffer = new ArrayBuffer(binary.length);
    const bytes = new Uint8Array(buffer);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return bytes;
  }

}

