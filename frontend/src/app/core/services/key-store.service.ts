import { Injectable } from '@angular/core';

type KeyKind = 'rsa-private' | 'rsa-public';

interface StoredKeyRecord {
  id: string;
  userId: string;
  kind: KeyKind;
  key: CryptoKey;
  createdAt: number;
}

@Injectable({ providedIn: 'root' })
export class KeyStoreService {
  private readonly DB_NAME = 'health_keystore';
  private readonly DB_VERSION = 1;
  private readonly STORE = 'keys';

  private dbPromise: Promise<IDBDatabase> | null = null;

  private openDb(): Promise<IDBDatabase> {
    if (this.dbPromise) return this.dbPromise;

    this.dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(this.DB_NAME, this.DB_VERSION);

      req.onupgradeneeded = () => {
        const db = req.result;
        if (!db.objectStoreNames.contains(this.STORE)) {
          const store = db.createObjectStore(this.STORE, { keyPath: 'id' });
          store.createIndex('by_user', 'userId', { unique: false });
          store.createIndex('by_kind', 'kind', { unique: false });
        }
      };

      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });

    return this.dbPromise;
  }

  private async withStore<T>(
    mode: IDBTransactionMode,
    fn: (store: IDBObjectStore) => IDBRequest<T>
  ): Promise<T> {
    const db = await this.openDb();
    return new Promise<T>((resolve, reject) => {
      const tx = db.transaction(this.STORE, mode);
      const store = tx.objectStore(this.STORE);
      const req = fn(store);

      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);

      // If the transaction itself aborts
      tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'));
    });
  }

  private makeId(userId: string, kind: KeyKind): string {
    return `${userId}:${kind}`;
  }

  //----------------------------------------------------
  //----------------- PRIVATE KEY      ------------------
  //----------------------------------------------------
  async putRsaPrivateKey(userId: string, key: CryptoKey): Promise<void> {
    const record: StoredKeyRecord = {
      id: this.makeId(userId, 'rsa-private'),
      userId,
      kind: 'rsa-private',
      key,
      createdAt: Date.now(),
    };
    await this.withStore('readwrite', (store) => store.put(record));
  }

  async getRsaPrivateKey(userId: string): Promise<CryptoKey | null> {
    const id = this.makeId(userId, 'rsa-private');
    const rec = await this.withStore<StoredKeyRecord | undefined>('readonly', (store) => store.get(id));
    return rec?.key ?? null;
  }

  async deleteRsaPrivateKey(userId: string): Promise<void> {
    const id = this.makeId(userId, 'rsa-private');
    await this.withStore('readwrite', (store) => store.delete(id));
  }


  //----------------------------------------------------
  //----------------- PUBLIC KEY      ------------------
  //----------------------------------------------------
  async putRsaPublicKey(userId: string, key: CryptoKey): Promise<void> {
    const record: StoredKeyRecord = {
      id: `${userId}:rsa-public`,
      userId,
      kind: 'rsa-public',
      key,
      createdAt: Date.now(),
    };
    await this.withStore('readwrite', (store) => store.put(record));
  }

  async getRsaPublicKey(userId: string): Promise<CryptoKey | null> {
    const rec = await this.withStore<StoredKeyRecord | undefined>(
      'readonly',
      (store) => store.get(`${userId}:rsa-public`)
    );
    return rec?.key ?? null;
  }

  async clearAll(): Promise<void> {
    await this.withStore('readwrite', (store) => store.clear());
  }
}
