import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/auth-provider'
import { Field } from '../../components/ui/Field'
import { useI18n } from '../../i18n/I18nProvider'
import { useChangePassword, useDeactivate, useProfile, useUpdateProfile } from '../hooks/useProfile'
import { mapProfileError } from '../profile-errors'

const primaryButton =
  'h-9 rounded-md bg-[var(--accent)] px-4 text-sm font-medium text-white ' +
  'transition-opacity duration-150 hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60'

const outlineButton =
  'h-9 rounded-md border border-[var(--border)] px-4 text-sm font-medium ' +
  'transition-colors duration-150 hover:bg-[var(--hover)] disabled:cursor-not-allowed disabled:opacity-60'

function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p
      role="alert"
      className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
    >
      {message}
    </p>
  )
}

export default function ProfilePage() {
  const { t } = useI18n()
  const { logout } = useAuth()
  const navigate = useNavigate()
  const profile = useProfile()
  const update = useUpdateProfile()
  const changePassword = useChangePassword()
  const deactivate = useDeactivate()

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [profileError, setProfileError] = useState<string | null>(null)
  const [profileOk, setProfileOk] = useState(false)

  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [passwordOk, setPasswordOk] = useState(false)

  const [confirming, setConfirming] = useState(false)

  if (profile.isPending) {
    return <div className="h-72 animate-pulse rounded-lg bg-[var(--hover)]" aria-busy />
  }

  if (profile.isError) {
    return <ErrorBanner message={t('tickets.error')} />
  }

  const user = profile.data

  function submitProfile(event: FormEvent) {
    event.preventDefault()
    setProfileError(null)
    setProfileOk(false)
    update.mutate(
      { name: name.trim() || user.name, email: email.trim() || user.email },
      {
        onSuccess: () => setProfileOk(true),
        onError: (error) => setProfileError(t(mapProfileError(error, 'profile.error.generic'))),
      },
    )
  }

  function submitPassword(event: FormEvent) {
    event.preventDefault()
    setPasswordError(null)
    setPasswordOk(false)
    changePassword.mutate(
      { currentPassword: current, newPassword: next },
      {
        onSuccess: () => {
          setPasswordOk(true)
          setCurrent('')
          setNext('')
        },
        onError: (error) => setPasswordError(t(mapProfileError(error, 'profile.error.generic'))),
      },
    )
  }

  async function confirmDeactivate() {
    await deactivate.mutateAsync()
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="max-w-xl space-y-5">
      <section className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-4 text-sm font-medium text-[var(--text-muted)]">{t('profile.data')}</h2>
        <form onSubmit={submitProfile} className="space-y-4" noValidate>
          <div className="flex items-center gap-2 text-sm text-[var(--text-muted)]">
            <span className="rounded-full bg-[var(--accent-soft)] px-2 py-0.5 text-xs font-medium text-[var(--accent)]">
              {t(`role.${user.role}`)}
            </span>
            {user.email}
          </div>
          <Field
            label={t('auth.name')}
            name="name"
            defaultValue={user.name}
            maxLength={120}
            onChange={(e) => setName(e.target.value)}
          />
          <Field
            label={t('auth.email')}
            type="email"
            name="email"
            defaultValue={user.email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <ErrorBanner message={profileError} />
          {profileOk && (
            <p className="text-sm text-emerald-700 dark:text-emerald-400">{t('profile.saved')}</p>
          )}
          <div className="flex gap-2.5">
            <button type="submit" disabled={update.isPending} className={primaryButton}>
              {update.isPending ? '…' : t('profile.save')}
            </button>
            <button
              type="button"
              onClick={() => {
                setName('')
                setEmail('')
                setProfileError(null)
                setProfileOk(false)
              }}
              className={outlineButton}
            >
              {t('profile.reset')}
            </button>
          </div>
        </form>
      </section>

      <section className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-5">
        <h2 className="mb-4 text-sm font-medium text-[var(--text-muted)]">{t('profile.password')}</h2>
        <form onSubmit={submitPassword} className="space-y-4" noValidate>
          <Field
            label={t('profile.currentPassword')}
            type="password"
            name="currentPassword"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
          />
          <Field
            label={t('profile.newPassword')}
            type="password"
            name="newPassword"
            autoComplete="new-password"
            minLength={8}
            value={next}
            onChange={(e) => setNext(e.target.value)}
          />
          <ErrorBanner message={passwordError} />
          {passwordOk && (
            <p className="text-sm text-emerald-700 dark:text-emerald-400">{t('profile.passwordChanged')}</p>
          )}
          <button
            type="submit"
            disabled={changePassword.isPending || !current || !next}
            className={primaryButton}
          >
            {changePassword.isPending ? '…' : t('profile.changePassword')}
          </button>
        </form>
      </section>

      <section className="rounded-lg border border-red-200 bg-[var(--surface)] p-5 dark:border-red-900">
        <h2 className="mb-2 text-sm font-medium text-red-700 dark:text-red-400">
          {t('profile.dangerZone')}
        </h2>
        <p className="mb-3 text-sm text-[var(--text-muted)]">{t('profile.deactivateHint')}</p>
        {confirming ? (
          <div className="flex gap-2.5">
            <button
              type="button"
              onClick={confirmDeactivate}
              disabled={deactivate.isPending}
              className="h-9 rounded-md bg-red-600 px-4 text-sm font-medium text-white transition-opacity hover:opacity-90 disabled:opacity-60"
            >
              {deactivate.isPending ? '…' : t('profile.confirmDeactivate')}
            </button>
            <button type="button" onClick={() => setConfirming(false)} className={outlineButton}>
              {t('newTicket.cancel')}
            </button>
          </div>
        ) : (
          <button
            type="button"
            onClick={() => setConfirming(true)}
            className="h-9 rounded-md border border-red-300 px-4 text-sm font-medium text-red-600 transition-colors hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950"
          >
            {t('profile.deactivate')}
          </button>
        )}
      </section>
    </div>
  )
}
