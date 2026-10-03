import type { FormEvent } from 'react'
import { useState } from 'react'
import { useI18n } from '../../i18n/I18nProvider'
import { IconSearch } from '../../components/ui/icons'
import type { TicketFilters } from '../types'

interface FiltersBarProps {
  filters: TicketFilters
  onChange: (patch: Partial<TicketFilters>) => void
  onClear: () => void
  hasFilters: boolean
}

const selectClass =
  'h-9 rounded-md border border-[var(--border)] bg-[var(--surface)] px-2.5 text-sm ' +
  'text-[var(--text)] transition-colors duration-150 hover:border-slate-400 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]'

export function FiltersBar({ filters, onChange, onClear, hasFilters }: FiltersBarProps) {
  const { t } = useI18n()
  const [query, setQuery] = useState(filters.q)

  function submitSearch(event: FormEvent) {
    event.preventDefault()
    onChange({ q: query.trim() })
  }

  return (
    <div className="flex flex-wrap items-center gap-2.5">
      <form onSubmit={submitSearch} className="relative min-w-48 flex-1 sm:max-w-64">
        <span className="pointer-events-none absolute inset-y-0 left-2.5 flex items-center text-[var(--text-muted)]">
          <IconSearch width={16} height={16} />
        </span>
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder={t('tickets.filter.q')}
          aria-label={t('tickets.filter.q')}
          className="h-9 w-full rounded-md border border-[var(--border)] bg-[var(--surface)] pr-2.5 pl-8 text-sm text-[var(--text)] placeholder:text-[var(--text-muted)] transition-colors duration-150 hover:border-slate-400 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
        />
      </form>

      <select
        value={filters.status}
        onChange={(e) => onChange({ status: e.target.value })}
        aria-label={t('tickets.filter.status')}
        className={selectClass}
      >
        <option value="">{t('tickets.filter.status')} — {t('tickets.filter.all')}</option>
        <option value="ABERTO">{t('status.ABERTO')}</option>
        <option value="EM_ATENDIMENTO">{t('status.EM_ATENDIMENTO')}</option>
        <option value="AGUARDANDO_CLIENTE">{t('status.AGUARDANDO_CLIENTE')}</option>
        <option value="RESOLVIDO">{t('status.RESOLVIDO')}</option>
        <option value="FECHADO">{t('status.FECHADO')}</option>
      </select>

      <select
        value={filters.priority}
        onChange={(e) => onChange({ priority: e.target.value })}
        aria-label={t('tickets.filter.priority')}
        className={selectClass}
      >
        <option value="">{t('tickets.filter.priority')} — {t('tickets.filter.all')}</option>
        <option value="BAIXA">{t('priority.BAIXA')}</option>
        <option value="MEDIA">{t('priority.MEDIA')}</option>
        <option value="ALTA">{t('priority.ALTA')}</option>
        <option value="CRITICA">{t('priority.CRITICA')}</option>
      </select>

      <select
        value={filters.slaBreached}
        onChange={(e) => onChange({ slaBreached: e.target.value })}
        aria-label={t('tickets.filter.sla')}
        className={selectClass}
      >
        <option value="">{t('tickets.filter.sla')} — {t('tickets.filter.all')}</option>
        <option value="true">{t('sla.breached')}</option>
        <option value="false">{t('sla.onTrack')}</option>
      </select>

      {hasFilters && (
        <button
          type="button"
          onClick={onClear}
          className="h-9 rounded-md px-2.5 text-sm font-medium text-[var(--text-muted)] transition-colors duration-150 hover:text-[var(--text)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
        >
          {t('tickets.clearFilters')}
        </button>
      )}
    </div>
  )
}
