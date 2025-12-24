// Simple sanitizers to reduce XSS risk via filenames or text rendering

export function sanitizeFilename(name: string): string {
  return (name || '')
     .replace(/[^\x20-\x7E]/g, '') // strip control characters for safety in downloads
    .replace(/[^\w\-. ]/g, '')
    .trim()
    .slice(0, 200) || 'download';
}

export function sanitizeText(text: string): string {
  return (text || '').replace(/[<>]/g, '').trim();
}
