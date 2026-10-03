/** UX-only validation that mirrors the API limits (spec decision 8). */
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024
export const ALLOWED_MIME_TYPES = [
  'image/png',
  'image/jpeg',
  'image/gif',
  'application/pdf',
  'text/plain',
  'application/zip',
] as const

export type AttachmentIssue =
  | { kind: 'tooBig'; filename: string }
  | { kind: 'badType'; filename: string }

/** The backend stays the authority; this only improves feedback before upload. */
export function validateFiles(files: File[]): AttachmentIssue[] {
  const issues: AttachmentIssue[] = []
  for (const file of files) {
    if (file.size > MAX_ATTACHMENT_BYTES) {
      issues.push({ kind: 'tooBig', filename: file.name })
    } else if (!ALLOWED_MIME_TYPES.includes(file.type as (typeof ALLOWED_MIME_TYPES)[number])) {
      issues.push({ kind: 'badType', filename: file.name })
    }
  }
  return issues
}
