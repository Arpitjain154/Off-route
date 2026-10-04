export type CloudEntry = { sessionId?: string; state: string; message?: string };
export function cloudJourneyLabel(entry?: CloudEntry): string {
  if (!entry) return 'Saved on phone';
  return entry.state === 'uploaded'
    ? 'Uploaded to Supabase'
    : entry.state === 'uploading'
      ? 'Uploading to Supabase'
      : entry.state === 'queued'
        ? 'Upload queued'
        : entry.state === 'action-required'
          ? 'Needs attention: ' + (entry.message || 'Retry upload')
          : 'Saved on phone';
}
export function canUploadJourney(source: string, connected: boolean, consent: boolean) {
  return source === 'device' && connected && consent;
}
