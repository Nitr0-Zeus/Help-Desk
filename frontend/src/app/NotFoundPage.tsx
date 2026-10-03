import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18nProvider'

export default function NotFoundPage() {
  const { t } = useI18n()
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <p className="text-sm font-medium text-[var(--text-muted)]">404</p>
      <h1 className="text-xl font-semibold">{t('common.notFound')}</h1>
      <Link
        to="/tickets"
        className="mt-2 rounded-md bg-[var(--accent)] px-4 py-2 text-sm font-medium text-white transition-opacity duration-150 hover:opacity-90 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
      >
        {t('common.backToTickets')}
      </Link>
    </div>
  )
}
