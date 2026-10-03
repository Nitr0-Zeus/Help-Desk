import { Link, useSearchParams } from 'react-router-dom'
import { useI18n } from '../../i18n/I18nProvider'
import { IconPlus } from '../../components/ui/icons'
import { FiltersBar } from '../components/FiltersBar'
import { TicketTable } from '../components/TicketTable'
import { useTickets } from '../hooks/useTickets'
import type { TicketFilters } from '../types'

const INITIAL_SIZE = 20

function parseFilters(params: URLSearchParams): TicketFilters {
  return {
    status: params.get('status') ?? '',
    priority: params.get('priority') ?? '',
    slaBreached: params.get('slaBreached') ?? '',
    q: params.get('q') ?? '',
    page: Number(params.get('page') ?? '0') || 0,
    size: Number(params.get('size') ?? String(INITIAL_SIZE)) || INITIAL_SIZE,
  }
}

export default function TicketsPage() {
  const { t } = useI18n()
  const [searchParams, setSearchParams] = useSearchParams()
  const filters = parseFilters(searchParams)
  const hasFilters = Boolean(filters.status || filters.priority || filters.slaBreached || filters.q)
  const { data, isLoading, isError, refetch } = useTickets(filters)

  // URL is the single source of truth for filters (spec decision 7)
  function update(patch: Partial<TicketFilters>) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '' || value === 0) next.delete(key)
      else next.set(key, String(value))
    }
    if (!('page' in patch)) next.delete('page')
    setSearchParams(next, { replace: true })
  }

  const newTicketButton = (
    <Link
      to="/tickets/new"
      className="inline-flex h-9 items-center gap-1.5 rounded-md bg-[var(--accent)] px-3.5 text-sm font-medium text-white transition-opacity duration-150 hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
    >
      <IconPlus width={16} height={16} />
      {t('action.newTicket')}
    </Link>
  )

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <FiltersBar
          filters={filters}
          onChange={update}
          onClear={() => setSearchParams({}, { replace: true })}
          hasFilters={hasFilters}
        />
        {newTicketButton}
      </div>

      {isLoading && (
        <div className="overflow-hidden rounded-lg border border-[var(--border)] bg-[var(--surface)]">
          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className="flex items-center gap-4 border-b border-[var(--border)] px-4 py-3.5 last:border-b-0"
            >
              <div className="h-3 w-20 animate-pulse rounded bg-[var(--hover)]" />
              <div className="h-3 flex-1 animate-pulse rounded bg-[var(--hover)]" />
              <div className="h-5 w-24 animate-pulse rounded-full bg-[var(--hover)]" />
            </div>
          ))}
        </div>
      )}

      {isError && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950">
          <p className="text-sm font-medium text-red-700 dark:text-red-300">
            {t('tickets.error')}
          </p>
          <button
            type="button"
            onClick={() => refetch()}
            className="mt-3 rounded-md border border-red-300 px-3 py-1.5 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900"
          >
            {t('tickets.retry')}
          </button>
        </div>
      )}

      {data && data.content.length > 0 && (
        <>
          <TicketTable tickets={data.content} />
          <div className="flex items-center justify-between text-sm text-[var(--text-muted)]">
            <span>
              {data.totalElements} {t('tickets.total')}
            </span>
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={data.page <= 0}
                onClick={() => update({ page: filters.page - 1 })}
                className="h-8 rounded-md border border-[var(--border)] px-3 font-medium transition-colors duration-150 hover:bg-[var(--hover)] disabled:cursor-not-allowed disabled:opacity-40"
              >
                {t('tickets.page.prev')}
              </button>
              <span className="tabular-nums">
                {data.page + 1} / {Math.max(data.totalPages, 1)}
              </span>
              <button
                type="button"
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => update({ page: filters.page + 1 })}
                className="h-8 rounded-md border border-[var(--border)] px-3 font-medium transition-colors duration-150 hover:bg-[var(--hover)] disabled:cursor-not-allowed disabled:opacity-40"
              >
                {t('tickets.page.next')}
              </button>
            </div>
          </div>
        </>
      )}

      {data && data.content.length === 0 && (
        <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-10 text-center">
          <h2 className="text-base font-semibold">
            {hasFilters ? t('tickets.emptyFiltered.title') : t('tickets.empty.title')}
          </h2>
          <p className="mt-1.5 text-sm text-[var(--text-muted)]">
            {hasFilters ? t('tickets.emptyFiltered.body') : t('tickets.empty.body')}
          </p>
          <div className="mt-4">
            {hasFilters ? (
              <button
                type="button"
                onClick={() => setSearchParams({}, { replace: true })}
                className="rounded-md border border-[var(--border)] px-3.5 py-1.5 text-sm font-medium transition-colors duration-150 hover:bg-[var(--hover)]"
              >
                {t('tickets.clearFilters')}
              </button>
            ) : (
              newTicketButton
            )}
          </div>
        </div>
      )}
    </div>
  )
}
