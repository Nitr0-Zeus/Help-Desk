import { useI18n } from '../../i18n/I18nProvider'
import type { Priority, TicketStatus } from '../types'

const statusStyles: Record<TicketStatus, string> = {
  ABERTO: 'bg-slate-100 text-slate-700 dark:bg-slate-700 dark:text-slate-300',
  EM_ATENDIMENTO:
    'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  AGUARDANDO_CLIENTE:
    'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
  RESOLVIDO: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300',
  FECHADO: 'bg-slate-200 text-slate-600 dark:bg-slate-800 dark:text-slate-400',
}

const priorityStyles: Record<Priority, string> = {
  BAIXA: 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-400',
  MEDIA: 'bg-blue-100 text-blue-700 dark:bg-blue-950 dark:text-blue-300',
  ALTA: 'bg-orange-100 text-orange-700 dark:bg-orange-950 dark:text-orange-300',
  CRITICA: 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300',
}

function Pill({ className, children }: { className: string; children: React.ReactNode }) {
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-full px-2 py-0.5 text-xs font-medium ${className}`}
    >
      {children}
    </span>
  )
}

export function StatusBadge({ status }: { status: TicketStatus }) {
  const { t } = useI18n()
  return <Pill className={statusStyles[status]}>{t(`status.${status}`)}</Pill>
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  const { t } = useI18n()
  return <Pill className={priorityStyles[priority]}>{t(`priority.${priority}`)}</Pill>
}

/** The memorable chip: SLA state read before the ticket text (direction contract). */
export function SlaChip({
  breached,
  paused,
}: {
  breached: boolean
  paused: boolean
  dueAt?: string | null
}) {
  const { t } = useI18n()
  if (breached) {
    return (
      <Pill className="bg-red-600 text-white dark:bg-red-600 dark:text-white">
        {t('sla.breached')}
      </Pill>
    )
  }
  if (paused) {
    return <Pill className="bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{t('sla.paused')}</Pill>
  }
  return (
    <Pill className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-300">
      {t('sla.onTrack')}
    </Pill>
  )
}
