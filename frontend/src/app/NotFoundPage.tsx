import { Link } from 'react-router-dom'
import { useI18n } from '../i18n/I18nProvider'
import { buttonClass } from '../components/ui/button-styles'

export default function NotFoundPage() {
  const { t } = useI18n()
  return (
    <div className="flex min-h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <p className="text-sm font-medium text-[var(--text-muted)]">404</p>
      <h1 className="text-xl font-semibold">{t('common.notFound')}</h1>
      <Link
        to="/tickets"
        className={buttonClass('primary', 'md', 'mt-2')}
      >
        {t('common.backToTickets')}
      </Link>
    </div>
  )
}
