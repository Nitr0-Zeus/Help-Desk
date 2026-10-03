import { useNavigate } from 'react-router-dom'
import { useI18n } from '../../i18n/I18nProvider'
import { formatDate } from '../../lib/format'
import { PriorityBadge, SlaChip, StatusBadge } from './Badges'
import type { Ticket } from '../types'

export function TicketTable({ tickets }: { tickets: Ticket[] }) {
  const { t, locale } = useI18n()
  const navigate = useNavigate()

  return (
    <div className="overflow-hidden rounded-lg border border-[var(--border)] bg-[var(--surface)]">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-[var(--border)] text-left text-xs font-medium text-[var(--text-muted)] uppercase tracking-wide">
            <th className="px-4 py-2.5 font-medium">{t('col.protocol')}</th>
            <th className="px-4 py-2.5 font-medium">{t('col.title')}</th>
            <th className="px-4 py-2.5 font-medium">{t('col.status')}</th>
            <th className="hidden px-4 py-2.5 font-medium sm:table-cell">{t('col.priority')}</th>
            <th className="px-4 py-2.5 font-medium">{t('col.sla')}</th>
            <th className="hidden px-4 py-2.5 font-medium lg:table-cell">{t('col.updated')}</th>
          </tr>
        </thead>
        <tbody>
          {tickets.map((ticket) => (
            <tr
              key={ticket.id}
              tabIndex={0}
              onClick={() => navigate(`/tickets/${ticket.id}`)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') navigate(`/tickets/${ticket.id}`)
              }}
              className="cursor-pointer border-b border-[var(--border)] last:border-b-0 transition-colors duration-150 hover:bg-[var(--hover)] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-[var(--accent)]"
            >
              <td className="px-4 py-3 font-mono text-xs text-[var(--text-muted)] whitespace-nowrap">
                {ticket.protocol}
              </td>
              <td className="max-w-72 px-4 py-3">
                <div className="truncate font-medium text-[var(--text)]">{ticket.title}</div>
                <div className="truncate text-xs text-[var(--text-muted)]">
                  {ticket.createdByName}
                </div>
              </td>
              <td className="px-4 py-3">
                <StatusBadge status={ticket.status} />
              </td>
              <td className="hidden px-4 py-3 sm:table-cell">
                <PriorityBadge priority={ticket.priority} />
              </td>
              <td className="px-4 py-3">
                <SlaChip
                  breached={ticket.slaBreached}
                  paused={ticket.waitingSince !== null}
                  dueAt={ticket.dueAt}
                />
              </td>
              <td className="hidden px-4 py-3 text-xs whitespace-nowrap text-[var(--text-muted)] lg:table-cell">
                {formatDate(ticket.updatedAt, locale)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
