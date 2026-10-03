import { useI18n } from '../../i18n/I18nProvider'
import { formatHours, formatPercent } from '../../lib/formatMetrics'
import { useAgentMetrics, useMetrics } from '../hooks/useMetrics'
import type { TicketStatus } from '../../tickets/types'

const STATUS_ORDER: TicketStatus[] = [
  'ABERTO',
  'EM_ATENDIMENTO',
  'AGUARDANDO_CLIENTE',
  'RESOLVIDO',
  'FECHADO',
]

const STATUS_BAR: Record<TicketStatus, string> = {
  ABERTO: 'bg-slate-400',
  EM_ATENDIMENTO: 'bg-blue-500',
  AGUARDANDO_CLIENTE: 'bg-amber-400',
  RESOLVIDO: 'bg-emerald-500',
  FECHADO: 'bg-slate-600 dark:bg-slate-500',
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-4">
      <p className="text-xs font-medium text-[var(--text-muted)]">{label}</p>
      <p className="mt-1.5 text-2xl font-semibold tabular-nums tracking-tight">{value}</p>
    </div>
  )
}

export default function MetricsPage() {
  const { t, locale } = useI18n()
  const metrics = useMetrics()
  const agents = useAgentMetrics()

  if (metrics.isPending || agents.isPending) {
    return (
      <div className="space-y-4" aria-busy>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-24 animate-pulse rounded-lg bg-[var(--hover)]" />
          ))}
        </div>
        <div className="h-64 animate-pulse rounded-lg bg-[var(--hover)]" />
      </div>
    )
  }

  if (metrics.isError || agents.isError) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950">
        <p className="text-sm font-medium text-red-700 dark:text-red-300">{t('tickets.error')}</p>
        <button
          type="button"
          onClick={() => {
            metrics.refetch()
            agents.refetch()
          }}
          className="mt-3 rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-700 hover:bg-red-100 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900"
        >
          {t('tickets.retry')}
        </button>
      </div>
    )
  }

  const data = metrics.data
  const maxStatus = Math.max(1, ...STATUS_ORDER.map((s) => data.byStatus[s] ?? 0))

  return (
    <div className="space-y-6">
      <section aria-label={t('metrics.summary')} className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label={t('metrics.total')} value={String(data.total)} />
        <StatCard
          label={t('metrics.slaCompliance')}
          value={formatPercent(data.slaCompliancePercent, locale)}
        />
        <StatCard
          label={t('metrics.avgFirstResponse')}
          value={formatHours(data.avgFirstResponseHours, locale)}
        />
        <StatCard
          label={t('metrics.avgResolution')}
          value={formatHours(data.avgResolutionHours, locale)}
        />
      </section>

      <section
        aria-labelledby="by-status-heading"
        className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5"
      >
        <h2 id="by-status-heading" className="mb-4 text-sm font-medium text-[var(--text-muted)]">
          {t('metrics.byStatus')}
        </h2>
        <ul className="space-y-3">
          {STATUS_ORDER.map((status) => {
            const count = data.byStatus[status] ?? 0
            return (
              <li key={status} className="grid grid-cols-[10rem_1fr_3rem] items-center gap-3 text-sm">
                <span className="truncate">{t(`status.${status}`)}</span>
                <span
                  className="h-2.5 overflow-hidden rounded-full bg-[var(--hover)]"
                  role="presentation"
                >
                  <span
                    className={`block h-full rounded-full ${STATUS_BAR[status]}`}
                    style={{ width: `${Math.round((count / maxStatus) * 100)}%` }}
                  />
                </span>
                <span className="text-right tabular-nums text-[var(--text-muted)]">{count}</span>
              </li>
            )
          })}
        </ul>
      </section>

      <section
        aria-labelledby="by-agent-heading"
        className="overflow-hidden rounded-lg border border-[var(--border)] bg-[var(--surface)]"
      >
        <h2
          id="by-agent-heading"
          className="border-b border-[var(--border)] px-5 py-3.5 text-sm font-medium text-[var(--text-muted)]"
        >
          {t('metrics.byAgent')}
        </h2>
        {agents.data.length === 0 ? (
          <p className="px-5 py-8 text-center text-sm text-[var(--text-muted)]">
            {t('metrics.empty')}
          </p>
        ) : (
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="border-b border-[var(--border)] text-left text-xs uppercase tracking-wide text-[var(--text-muted)]">
                <th className="px-5 py-2.5 font-medium">{t('metrics.col.agent')}</th>
                <th className="px-5 py-2.5 text-right font-medium">{t('metrics.col.assigned')}</th>
                <th className="px-5 py-2.5 text-right font-medium">{t('metrics.col.resolved')}</th>
                <th className="px-5 py-2.5 text-right font-medium">{t('metrics.slaCompliance')}</th>
                <th className="hidden px-5 py-2.5 text-right font-medium sm:table-cell">
                  {t('metrics.avgFirstResponse')}
                </th>
                <th className="hidden px-5 py-2.5 text-right font-medium sm:table-cell">
                  {t('metrics.avgResolution')}
                </th>
              </tr>
            </thead>
            <tbody>
              {agents.data.map((agent) => (
                <tr
                  key={agent.agentId}
                  className="border-b border-[var(--border)] last:border-b-0"
                >
                  <td className="px-5 py-3 font-medium">{agent.agentName}</td>
                  <td className="px-5 py-3 text-right tabular-nums">{agent.assigned}</td>
                  <td className="px-5 py-3 text-right tabular-nums">{agent.resolved}</td>
                  <td className="px-5 py-3 text-right tabular-nums">
                    {formatPercent(agent.slaCompliancePercent, locale)}
                  </td>
                  <td className="hidden px-5 py-3 text-right tabular-nums sm:table-cell">
                    {formatHours(agent.avgFirstResponseHours, locale)}
                  </td>
                  <td className="hidden px-5 py-3 text-right tabular-nums sm:table-cell">
                    {formatHours(agent.avgResolutionHours, locale)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
