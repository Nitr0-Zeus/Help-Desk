import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/auth-provider'
import { useI18n } from '../../i18n/I18nProvider'
import { useAssign, useDeleteTicket, useTransition } from '../hooks/useTicket'
import { availableTransitions, canAssign, canDelete } from './ticket-transitions'
import type { Ticket, TicketStatus } from '../types'

const TRANSITION_LABEL: Record<TicketStatus, string> = {
  ABERTO: '',
  EM_ATENDIMENTO: 'transition.take',
  AGUARDANDO_CLIENTE: 'transition.wait',
  RESOLVIDO: 'transition.resolve',
  FECHADO: 'transition.close',
}

const outlineButton =
  'h-9 rounded-md border border-[var(--border)] px-3.5 text-sm font-medium ' +
  'transition-colors duration-150 hover:bg-[var(--hover)] ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

const resumeButton =
  'h-9 rounded-md border border-transparent bg-[var(--accent)] px-3.5 text-sm font-medium ' +
  'text-white transition-opacity duration-150 hover:opacity-90 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

const dangerButton =
  'h-9 rounded-md border border-red-300 px-3.5 text-sm font-medium text-red-600 ' +
  'transition-colors duration-150 hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950 ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-500 ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

export function TicketActions({ ticket }: { ticket: Ticket }) {
  const { t } = useI18n()
  const { user } = useAuth()
  const navigate = useNavigate()
  const transition = useTransition(ticket.id)
  const assign = useAssign(ticket.id)
  const remove = useDeleteTicket(ticket.id)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const role = user?.role ?? 'CLIENTE'
  const isOwner = user?.id === String(ticket.createdById)
  const transitions = availableTransitions(ticket.status, role, isOwner)
  const assignable = canAssign(ticket.status, role)
  const deletable = canDelete(ticket.status, role, isOwner)
  const assignedToMe = ticket.assignedToId != null && user?.id === String(ticket.assignedToId)
  const busy = transition.isPending || assign.isPending || remove.isPending
  const error = transition.error ?? assign.error ?? remove.error

  return (
    <div className="space-y-2.5">
      <div className="flex flex-wrap gap-2">
        {transitions.map((target, index) => (
          <button
            key={target}
            type="button"
            disabled={busy}
            onClick={() => transition.mutate(target)}
            className={index === 0 ? resumeButton : outlineButton}
          >
            {t(TRANSITION_LABEL[target])}
          </button>
        ))}

        {assignable && !ticket.assignedToId && (
          <button
            type="button"
            disabled={busy}
            onClick={() => assign.mutate(false)}
            className={outlineButton}
          >
            {t('action.assignSelf')}
          </button>
        )}
        {assignable && assignedToMe && (
          <button
            type="button"
            disabled={busy}
            onClick={() => assign.mutate(true)}
            className={outlineButton}
          >
            {t('action.release')}
          </button>
        )}

        {deletable &&
          (confirmingDelete ? (
            <button
              type="button"
              disabled={busy}
            onClick={() => {
              remove.mutate(undefined, { onSuccess: () => navigate('/tickets') })
            }}
              className={dangerButton}
            >
              {t('action.confirmDelete')}
            </button>
          ) : (
            <button
              type="button"
              disabled={busy}
              onClick={() => setConfirmingDelete(true)}
              className={dangerButton}
            >
              {t('action.delete')}
            </button>
          ))}
      </div>

      {confirmingDelete && !remove.isSuccess && (
        <p className="text-xs text-red-600 dark:text-red-400">{t('action.deleteHint')}</p>
      )}

      {error != null && (
        <p
          role="alert"
          className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
        >
          {error instanceof Error ? error.message : t('tickets.error')}
        </p>
      )}
    </div>
  )
}
