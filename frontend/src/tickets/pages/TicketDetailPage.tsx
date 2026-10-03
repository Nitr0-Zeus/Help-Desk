import { useParams } from 'react-router-dom'
import { useAuth } from '../../auth/auth-provider'
import { useI18n } from '../../i18n/I18nProvider'
import { formatDate } from '../../lib/format'
import { PriorityBadge, SlaChip, StatusBadge } from '../components/Badges'
import { TicketActions } from '../components/TicketActions'
import { Timeline } from '../components/Timeline'
import { useTicket, useTimeline } from '../hooks/useTicket'

export default function TicketDetailPage() {
  const { id } = useParams()
  const { t, locale } = useI18n()
  const { user } = useAuth()
  const ticketId = Number(id)
  const ticket = useTicket(ticketId)
  const timeline = useTimeline(ticketId)

  if (ticket.isPending) {
    return (
      <div className="space-y-4" aria-busy>
        <div className="h-8 w-2/3 animate-pulse rounded bg-[var(--hover)]" />
        <div className="h-40 animate-pulse rounded-lg bg-[var(--hover)]" />
      </div>
    )
  }

  if (ticket.isError) {
    const notFound = (ticket.error as { status?: number }).status === 404
    return (
      <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-10 text-center">
        <h2 className="text-base font-semibold">
          {notFound ? t('detail.notFound') : t('tickets.error')}
        </h2>
        <button
          type="button"
          onClick={() => ticket.refetch()}
          className="mt-4 rounded-md border border-[var(--border)] px-3.5 py-1.5 text-sm font-medium transition-colors duration-150 hover:bg-[var(--hover)]"
        >
          {notFound ? t('common.backToTickets') : t('tickets.retry')}
        </button>
      </div>
    )
  }

  const data = ticket.data
  const waitingForCustomer =
    data.status === 'AGUARDANDO_CLIENTE' && user?.role === 'CLIENTE'

  return (
    <div className="space-y-5">
      <header className="space-y-3">
        <div className="flex flex-wrap items-center gap-2.5">
          <span className="font-mono text-xs text-[var(--text-muted)]">{data.protocol}</span>
          <StatusBadge status={data.status} />
          <PriorityBadge priority={data.priority} />
          <SlaChip
            breached={data.slaBreached}
            paused={data.waitingSince !== null}
            dueAt={data.dueAt}
          />
        </div>
        <h2 className="text-xl font-semibold tracking-tight">{data.title}</h2>
        <TicketActions ticket={data} />
      </header>

      {waitingForCustomer && (
        <p className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-300">
          {t('detail.waitingBanner')}
        </p>
      )}

      <div className="grid gap-5 lg:grid-cols-[1fr_16rem]">
        <div className="min-w-0 space-y-5">
          <section className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4">
            <h3 className="mb-2 text-sm font-medium text-[var(--text-muted)]">
              {t('detail.description')}
            </h3>
            <p className="text-sm whitespace-pre-wrap">{data.description}</p>
          </section>

          <section aria-labelledby="timeline-heading" className="space-y-3">
            <h3 id="timeline-heading" className="text-sm font-medium text-[var(--text-muted)]">
              {t('timeline.title')}
            </h3>
            {timeline.isPending && (
              <div className="h-24 animate-pulse rounded-lg bg-[var(--hover)]" />
            )}
            {timeline.data && timeline.data.content.length > 0 && (
              <Timeline entries={timeline.data.content} />
            )}
            {timeline.data && timeline.data.content.length === 0 && (
              <p className="text-sm text-[var(--text-muted)]">{t('timeline.empty')}</p>
            )}
          </section>
        </div>

        <aside className="space-y-3 lg:border-l lg:border-[var(--border)] lg:pl-5">
          <h3 className="text-sm font-medium text-[var(--text-muted)]">{t('detail.about')}</h3>
          <dl className="space-y-2.5 text-sm">
            <div>
              <dt className="text-xs text-[var(--text-muted)]">{t('detail.createdBy')}</dt>
              <dd className="font-medium">{data.createdByName}</dd>
            </div>
            <div>
              <dt className="text-xs text-[var(--text-muted)]">{t('detail.assignee')}</dt>
              <dd className="font-medium">{data.assignedToName ?? t('detail.noAssignee')}</dd>
            </div>
            <div>
              <dt className="text-xs text-[var(--text-muted)]">{t('detail.createdAt')}</dt>
              <dd className="tabular-nums">{formatDate(data.createdAt, locale)}</dd>
            </div>
            {data.firstResponseAt && (
              <div>
                <dt className="text-xs text-[var(--text-muted)]">{t('detail.firstResponseAt')}</dt>
                <dd className="tabular-nums">{formatDate(data.firstResponseAt, locale)}</dd>
              </div>
            )}
            {data.resolvedAt && (
              <div>
                <dt className="text-xs text-[var(--text-muted)]">{t('detail.resolvedAt')}</dt>
                <dd className="tabular-nums">{formatDate(data.resolvedAt, locale)}</dd>
              </div>
            )}
            <div>
              <dt className="text-xs text-[var(--text-muted)]">{t('detail.dueAt')}</dt>
              <dd className="tabular-nums">
                {data.dueAt ? formatDate(data.dueAt, locale) : '—'}
              </dd>
            </div>
          </dl>
        </aside>
      </div>
    </div>
  )
}
