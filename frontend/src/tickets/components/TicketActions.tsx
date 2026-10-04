import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/auth-provider'
import { useI18n } from '../../i18n/I18nProvider'
import { Button } from '../../components/ui/Button'
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
          <Button
            key={target}
            variant={index === 0 ? 'primary' : 'secondary'}
            disabled={busy}
            onClick={() => transition.mutate(target)}
          >
            {t(TRANSITION_LABEL[target])}
          </Button>
        ))}

        {assignable && !ticket.assignedToId && (
          <Button variant="secondary" disabled={busy} onClick={() => assign.mutate(false)}>
            {t('action.assignSelf')}
          </Button>
        )}
        {assignable && assignedToMe && (
          <Button variant="secondary" disabled={busy} onClick={() => assign.mutate(true)}>
            {t('action.release')}
          </Button>
        )}

        {deletable &&
          (confirmingDelete ? (
            <Button
              variant="danger"
              disabled={busy}
              onClick={() => {
                remove.mutate(undefined, { onSuccess: () => navigate('/tickets') })
              }}
            >
              {t('action.confirmDelete')}
            </Button>
          ) : (
            <Button variant="danger" disabled={busy} onClick={() => setConfirmingDelete(true)}>
              {t('action.delete')}
            </Button>
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
