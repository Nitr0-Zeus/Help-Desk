import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useAuth } from '../auth-provider'
import { Field } from '../../components/ui/Field'
import { Button } from '../../components/ui/Button'
import { useI18n } from '../../i18n/I18nProvider'

export default function RegisterPage() {
  const { t } = useI18n()
  const { register } = useAuth()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [invite, setInvite] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    const code = invite.trim()
    try {
      // the API validates role + inviteCode; the form only presents the flow
      await register({
        name: name.trim(),
        email: email.trim(),
        password,
        role: code ? 'AGENTE' : 'CLIENTE',
        inviteCode: code || undefined,
      })
      navigate('/tickets', { replace: true })
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.problem.detail?.includes('invite')) setError(t('auth.error.invalidInvite'))
        else if (err.problem.detail?.includes('Email')) setError(t('auth.error.emailInUse'))
        else if (err.status === 422) {
          setError(Object.values(err.problem.violations ?? {}).join(' · ') || t('auth.error.generic'))
        } else setError(t('auth.error.generic'))
      } else {
        setError(t('auth.error.generic'))
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-[var(--bg)] p-4">
      <div className="w-full max-w-sm rounded-lg border border-[var(--border)] bg-[var(--surface)] p-6 shadow-sm">
        <div className="mb-6 flex items-center gap-2.5">
          <span
            className="flex h-8 w-8 items-center justify-center rounded-md bg-[var(--accent)] text-sm font-bold text-white"
            aria-hidden
          >
            HD
          </span>
          <span className="text-[15px] font-semibold tracking-tight">{t('app.name')}</span>
        </div>

        <h1 className="mb-5 text-lg font-semibold">{t('route.register')}</h1>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <Field
            label={t('auth.name')}
            name="name"
            autoComplete="name"
            required
            maxLength={120}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <Field
            label={t('auth.email')}
            type="email"
            name="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Field
            label={t('auth.password')}
            type="password"
            name="password"
            autoComplete="new-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Field
            label={t('auth.invite')}
            name="inviteCode"
            hint={t('auth.inviteHint')}
            value={invite}
            onChange={(e) => setInvite(e.target.value)}
          />

          {error && (
            <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
              {error}
            </p>
          )}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? '…' : t('auth.submit.register')}
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-[var(--text-muted)]">
          <Link
            to="/login"
            className="font-medium text-[var(--accent)] hover:underline underline-offset-4"
          >
            {t('auth.switch.toLogin')}
          </Link>
        </p>
      </div>
    </div>
  )
}
