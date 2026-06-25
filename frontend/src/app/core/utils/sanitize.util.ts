// Simple sanitizer to reduce XSS risk via filenames in downloads

export function sanitizeFilename(name: string): string {
  return (name || '')
     .replace(/[^\x20-\x7E]/g, '') // strip control characters for safety in downloads
    .replace(/[^\w\-. ]/g, '')
    .trim()
    .slice(0, 200) || 'download';
}
