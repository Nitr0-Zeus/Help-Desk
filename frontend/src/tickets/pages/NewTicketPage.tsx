import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { Field } from '../../components/ui/Field'
import { Button } from '../../components/ui/Button'
import { useI18n } from '../../i18n/I18nProvider'
import { createTicket } from '../api/TicketsApi'
import type { Priority } from '../types'

export default function NewTicketPage() {
  const { t } = useI18n()
  const navigate = useNavigate()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<Priority>('MEDIA')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (title.trim().length < 3 || description.trim().length < 3) {
      setError(t('newTicket.error'))
      return
    }
    setLoading(true)
    try {
      const ticket = await createTicket({
        title: title.trim(),
        description: description.trim(),
        priority,
      })
      navigate(`/tickets/${ticket.id}`, { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        setError(Object.values(err.problem.violations ?? {}).join(' · ') || t('newTicket.error'))
      } else {
        setError(t('newTicket.error'))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="max-w-xl">
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
          <Field
            label={t('newTicket.title')}
            name="title"
            required
            maxLength={160}
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <div className="mt-4 space-y-1.5">
            <label htmlFor="description" className="block text-sm font-medium text-[var(--text)]">
              {t('newTicket.description')}
            </label>
            <textarea
              id="description"
              name="description"
              required
              rows={6}
              maxLength={5000}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full rounded-md border border-[var(--border)] bg-[var(--surface)] px-3 py-2 text-sm text-[var(--text)] placeholder:text-[var(--text-muted)] transition-colors duration-150 hover:border-slate-400 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
            />
          </div>
          <div className="mt-4 space-y-1.5">
            <label htmlFor="priority" className="block text-sm font-medium text-[var(--text)]">
              {t('newTicket.priority')}
            </label>
            <select
              id="priority"
              name="priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value as Priority)}
              className="h-9 w-full rounded-md border border-[var(--border)] bg-[var(--surface)] px-2.5 text-sm text-[var(--text)] transition-colors duration-150 hover:border-slate-400 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[var(--accent)]"
            >
              <option value="BAIXA">{t('priority.BAIXA')}</option>
              <option value="MEDIA">{t('priority.MEDIA')}</option>
              <option value="ALTA">{t('priority.ALTA')}</option>
              <option value="CRITICA">{t('priority.CRITICA')}</option>
            </select>
          </div>
        </div>

        {error && (
          <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
            {error}
          </p>
        )}

        <div className="flex gap-2.5">
          <Button type="submit" variant="primary" disabled={loading}>
            {loading ? '…' : t('newTicket.submit')}
          </Button>
          <Button variant="secondary" onClick={() => navigate('/tickets')}>
            {t('newTicket.cancel')}
          </Button>
        </div>
      </form>
    </div>
  )
}
