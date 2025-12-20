import { Injectable } from '@angular/core';

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

  /**
   * Génère une paire de clés RSA (2048 bits)
   * @returns Promise avec {publicKey, privateKey} en format CryptoKey
   */
  async generateRSAKeyPair(): Promise<{ publicKey: CryptoKey; privateKey: CryptoKey }> {
    const keyPair = await window.crypto.subtle.generateKey(
      {
        name: 'RSA-OAEP',
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]), // 65537
        hash: 'SHA-256',
      },
      true, // extractable
      ['encrypt', 'decrypt']
    );

    return {
      publicKey: keyPair.publicKey,
      privateKey: keyPair.privateKey
    };
  }

  /**
   * Génère une clé symétrique AES-GCM (256 bits)
   * @returns Promise avec la clé CryptoKey
   */
  async generateAESKey(): Promise<CryptoKey> {
    return await window.crypto.subtle.generateKey(
      {
        name: 'AES-GCM',
        length: 256,
      },
      true, // extractable
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Exporte une clé publique RSA au format PEM (base64)
   * @param publicKey Clé publique CryptoKey
   * @returns Promise avec la clé publique en format PEM
   */
  async exportPublicKey(publicKey: CryptoKey): Promise<string> {
    const exported = await window.crypto.subtle.exportKey('spki', publicKey);
    const exportedAsBase64 = this.arrayBufferToBase64(exported);
    return `-----BEGIN PUBLIC KEY-----\n${exportedAsBase64}\n-----END PUBLIC KEY-----`;
  }

  /**
   * Exporte une clé privée RSA au format PEM (base64)
   * @param privateKey Clé privée CryptoKey
   * @returns Promise avec la clé privée en format PEM
   */
  async exportPrivateKey(privateKey: CryptoKey): Promise<string> {
    const exported = await window.crypto.subtle.exportKey('pkcs8', privateKey);
    const exportedAsBase64 = this.arrayBufferToBase64(exported);
    return `-----BEGIN PRIVATE KEY-----\n${exportedAsBase64}\n-----END PRIVATE KEY-----`;
  }

  /**
   * Importe une clé publique RSA depuis le format PEM
   * @param pem Clé publique en format PEM
   * @returns Promise avec la clé publique CryptoKey
   */
  async importPublicKey(pem: string): Promise<CryptoKey> {
    const pemHeader = '-----BEGIN PUBLIC KEY-----';
    const pemFooter = '-----END PUBLIC KEY-----';
    const pemContents = pem
      .replace(pemHeader, '')
      .replace(pemFooter, '')
      .replace(/\s/g, '');
    
    const binaryDer = this.base64ToArrayBuffer(pemContents);
    
    return await window.crypto.subtle.importKey(
      'spki',
      binaryDer,
      {
        name: 'RSA-OAEP',
        hash: 'SHA-256',
      },
      true,
      ['encrypt']
    );
  }

  /**
   * Importe une clé privée RSA depuis le format PEM
   * @param pem Clé privée en format PEM
   * @returns Promise avec la clé privée CryptoKey
   */
  async importPrivateKey(pem: string): Promise<CryptoKey> {
    const pemHeader = '-----BEGIN PRIVATE KEY-----';
    const pemFooter = '-----END PRIVATE KEY-----';
    const pemContents = pem
      .replace(pemHeader, '')
      .replace(pemFooter, '')
      .replace(/\s/g, '');
    
    const binaryDer = this.base64ToArrayBuffer(pemContents);
    
    return await window.crypto.subtle.importKey(
      'pkcs8',
      binaryDer,
      {
        name: 'RSA-OAEP',
        hash: 'SHA-256',
      },
      true,
      ['decrypt']
    );
  }

  /**
   * Exporte une clé AES en format base64 (pour stockage)
   * @param key Clé AES CryptoKey
   * @returns Promise avec la clé en base64
   */
  async exportAESKey(key: CryptoKey): Promise<string> {
    const exported = await window.crypto.subtle.exportKey('raw', key);
    return this.arrayBufferToBase64(exported);
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
        iv: iv,
      },
      key,
      dataBuffer
    );

    return {
      encrypted: this.arrayBufferToBase64(encrypted),
      iv: this.arrayBufferToBase64(iv)
    };
  }

  /**
   * Déchiffre des données avec AES-GCM
   * @param encryptedBase64 Données chiffrées en base64
   * @param ivBase64 IV en base64
   * @param key Clé AES
   * @returns Promise avec les données déchiffrées (string)
   */
  async decryptWithAES(encryptedBase64: string, ivBase64: string, key: CryptoKey): Promise<string> {
    const encrypted = this.base64ToArrayBuffer(encryptedBase64);
    const iv = this.base64ToArrayBuffer(ivBase64);
    
    const decrypted = await window.crypto.subtle.decrypt(
      {
        name: 'AES-GCM',
        iv: iv,
      },
      key,
      encrypted
    );

    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
  }

  /**
   * Stocke une clé privée dans le localStorage (à sécuriser davantage en production)
   * @param keyId Identifiant unique pour la clé
   * @param privateKeyPEM Clé privée en format PEM
   */
  storePrivateKey(keyId: string, privateKeyPEM: string): void {
    localStorage.setItem(`private_key_${keyId}`, privateKeyPEM);
  }

  /**
   * Récupère une clé privée depuis le localStorage
   * @param keyId Identifiant unique pour la clé
   * @returns Clé privée en format PEM ou null
   */
  getPrivateKey(keyId: string): string | null {
    return localStorage.getItem(`private_key_${keyId}`);
  }

  /**
   * Stocke une clé publique dans le localStorage
   * @param keyId Identifiant unique pour la clé
   * @param publicKeyPEM Clé publique en format PEM
   */
  storePublicKey(keyId: string, publicKeyPEM: string): void {
    localStorage.setItem(`public_key_${keyId}`, publicKeyPEM);
  }

  /**
   * Récupère une clé publique depuis le localStorage
   * @param keyId Identifiant unique pour la clé
   * @returns Clé publique en format PEM ou null
   */
  getPublicKey(keyId: string): string | null {
    return localStorage.getItem(`public_key_${keyId}`);
  }

  /**
   * Stocke une clé AES dans le localStorage (chiffrée avec la clé privée RSA de l'utilisateur)
   * @param keyId Identifiant unique pour la clé
   * @param aesKeyBase64 Clé AES en base64
   */
  storeAESKey(keyId: string, aesKeyBase64: string): void {
    localStorage.setItem(`aes_key_${keyId}`, aesKeyBase64);
  }

  /**
   * Récupère une clé AES depuis le localStorage
   * @param keyId Identifiant unique pour la clé
   * @returns Clé AES en base64 ou null
   */
  getAESKey(keyId: string): string | null {
    return localStorage.getItem(`aes_key_${keyId}`);
  }

  /**
   * Chiffre des données (string) avec RSA-OAEP
   * @param data Données à chiffrer (string)
   * @param publicKey Clé publique RSA
   * @returns Promise avec les données chiffrées en base64
   */
  async encryptWithRSA(data: string, publicKey: CryptoKey): Promise<string> {
    const encoder = new TextEncoder();
    const dataBuffer = encoder.encode(data);
    
    const encrypted = await window.crypto.subtle.encrypt(
      {
        name: 'RSA-OAEP',
      },
      publicKey,
      dataBuffer
    );

    return this.arrayBufferToBase64(encrypted);
  }

  /**
   * Déchiffre des données avec RSA-OAEP
   * @param encryptedBase64 Données chiffrées en base64
   * @param privateKey Clé privée RSA
   * @returns Promise avec les données déchiffrées (string)
   */
  async decryptWithRSA(encryptedBase64: string, privateKey: CryptoKey): Promise<string> {
    const encrypted = this.base64ToArrayBuffer(encryptedBase64);
    
    const decrypted = await window.crypto.subtle.decrypt(
      {
        name: 'RSA-OAEP',
      },
      privateKey,
      encrypted
    );

    const decoder = new TextDecoder();
    return decoder.decode(decrypted);
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
}

