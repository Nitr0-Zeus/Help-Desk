import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useAuth } from '../auth-provider'
import { Field } from '../../components/ui/Field'
import { Button } from '../../components/ui/Button'
import { useI18n } from '../../i18n/I18nProvider'

export default function LoginPage() {
  const { t } = useI18n()
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? '/tickets'

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError(t('auth.error.invalid'))
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

        <h1 className="mb-5 text-lg font-semibold">{t('route.login')}</h1>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
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
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          {error && (
            <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
              {error}
            </p>
          )}

          <Button type="submit" disabled={loading} className="w-full">
            {loading ? '…' : t('auth.submit.login')}
          </Button>
        </form>

        <p className="mt-5 text-center text-sm text-[var(--text-muted)]">
          <Link
            to="/register"
            className="font-medium text-[var(--accent)] hover:underline underline-offset-4"
          >
            {t('auth.switch.toRegister')}
          </Link>
        </p>
      </div>
    </div>
  )
}
