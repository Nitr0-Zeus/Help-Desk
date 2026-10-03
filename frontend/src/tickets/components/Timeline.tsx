import { useI18n } from '../../i18n/I18nProvider'
import { formatDate } from '../../lib/format'
import { downloadAttachment } from '../api/TicketsApi'
import { StatusBadge } from './Badges'
import type { AttachmentView, TimelineEntry } from '../api/TicketsApi'

function initials(name: string): string {
  return name
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
}

function eventLabel(entry: TimelineEntry): string {
  switch (entry.eventType) {
    case 'STATUS_CHANGED':
      return 'event.STATUS_CHANGED'
    case 'SLA_PAUSED':
      return 'event.SLA_PAUSED'
    case 'SLA_RESUMED':
      return 'event.SLA_RESUMED'
    case 'SLA_BREACHED':
      return 'event.SLA_BREACHED'
    case 'ASSIGNED':
      return 'event.ASSIGNED'
    default:
      return 'event.STATUS_CHANGED'
  }
}

function statusFromPayload(payload: string | null): { from?: string; to?: string } {
  if (!payload) return {}
  try {
    return JSON.parse(payload) as { from?: string; to?: string }
  } catch {
    return {}
  }
}

export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  const { t, locale } = useI18n()

  return (
    <ol className="space-y-3">
      {entries.map((entry) => {
        if (entry.type === 'COMMENT') {
          return (
            <li
              key={entry.id}
              className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4"
            >
              <div className="flex items-center gap-2.5">
                <span
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[var(--accent-soft)] text-xs font-semibold text-[var(--accent)]"
                  aria-hidden
                >
                  {initials(entry.authorName ?? '?')}
                </span>
                <span className="text-sm font-medium">{entry.authorName}</span>
                <time
                  dateTime={entry.createdAt}
                  className="ml-auto text-xs text-[var(--text-muted)] tabular-nums"
                >
                  {formatDate(entry.createdAt, locale)}
                </time>
              </div>
              <p className="mt-2.5 text-sm whitespace-pre-wrap text-[var(--text)]">
                {entry.body}
              </p>
              {entry.attachments && entry.attachments.length > 0 && (
                <ul className="mt-3 space-y-1 border-t border-[var(--border)] pt-2.5">
                  {entry.attachments.map((attachment: AttachmentView) => (
                    <li key={attachment.id}>
                      <button
                        type="button"
                        onClick={() => downloadAttachment(attachment.id, attachment.filename)}
                        className="inline-flex items-center gap-1.5 text-xs font-medium text-[var(--accent)] transition-colors duration-150 hover:underline underline-offset-4"
                      >
                        {attachment.filename}
                        <span className="font-normal text-[var(--text-muted)]">
                          ({Math.max(1, Math.round(attachment.size / 1024))} KB)
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        }
        const { from, to } = statusFromPayload(entry.payload)
        return (
          <li
            key={entry.id}
            className="flex flex-wrap items-center gap-2 px-1 text-xs text-[var(--text-muted)]"
          >
            <time dateTime={entry.createdAt} className="tabular-nums">
              {formatDate(entry.createdAt, locale)}
            </time>
            <span aria-hidden>·</span>
            <span>{t(eventLabel(entry))}</span>
            {entry.eventType === 'STATUS_CHANGED' && from && to && (
              <>
                <span aria-hidden>·</span>
                <StatusBadge status={from as never} />
                <span aria-hidden>→</span>
                <StatusBadge status={to as never} />
              </>
            )}
            {entry.authorName && <span>— {entry.authorName}</span>}
          </li>
        )
      })}
    </ol>
  )
}
