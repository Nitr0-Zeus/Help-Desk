import { useI18n } from '../../i18n/I18nProvider'
import { ErrorState } from '../../components/ui/ErrorState'
import { PriorityBadge } from '../../tickets/components/Badges'
import { useSlaPolicies } from '../hooks/useSlaPolicies'

const PRIORITY_ORDER = ['CRITICA', 'ALTA', 'MEDIA', 'BAIXA'] as const

export default function SlaPoliciesPage() {
  const { t } = useI18n()
  const policies = useSlaPolicies()

  if (policies.isPending) {
    return <div className="h-64 animate-pulse rounded-lg bg-[var(--hover)]" aria-busy />
  }

  if (policies.isError) {
    return (
      <ErrorState
        message={t('tickets.error')}
        actionLabel={t('tickets.retry')}
        onAction={() => policies.refetch()}
      />
    )
  }

  const ordered = [...policies.data].sort(
    (a, b) => PRIORITY_ORDER.indexOf(a.priority) - PRIORITY_ORDER.indexOf(b.priority),
  )

  return (
    <div className="space-y-4">
      <p className="max-w-2xl text-sm text-[var(--text-muted)]">{t('sla.howItWorks')}</p>

      <div className="overflow-hidden rounded-lg border border-[var(--border)] bg-[var(--surface)]">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-[var(--border)] text-left text-xs uppercase tracking-wide text-[var(--text-muted)]">
              <th className="px-5 py-3 font-medium">{t('col.priority')}</th>
              <th className="px-5 py-3 text-right font-medium">{t('sla.firstResponse')}</th>
              <th className="px-5 py-3 text-right font-medium">{t('sla.resolution')}</th>
              <th className="px-5 py-3 text-right font-medium">{t('sla.active')}</th>
            </tr>
          </thead>
          <tbody>
            {ordered.map((policy) => (
              <tr key={policy.priority} className="border-b border-[var(--border)] last:border-b-0">
                <td className="px-5 py-3.5">
                  <PriorityBadge priority={policy.priority} />
                </td>
                <td className="px-5 py-3.5 text-right tabular-nums">
                  {policy.firstResponseHours} {t('sla.hoursUnit')}
                </td>
                <td className="px-5 py-3.5 text-right tabular-nums">
                  {policy.resolutionHours} {t('sla.hoursUnit')}
                </td>
                <td className="px-5 py-3.5 text-right">
                  {policy.active ? (
                    <span className="inline-flex items-center gap-1.5 text-emerald-700 dark:text-emerald-400">
                      <span
                        className="h-1.5 w-1.5 rounded-full bg-emerald-500"
                        aria-hidden
                      />
                      {t('sla.activeYes')}
                    </span>
                  ) : (
                    <span className="text-[var(--text-muted)]">{t('sla.activeNo')}</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
