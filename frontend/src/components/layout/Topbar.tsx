import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/auth-provider'
import { useI18n } from '../../i18n/I18nProvider'
import { useTheme } from '../../theme/ThemeProvider'
import { IconClose, IconGlobe, IconLogout, IconMenu, IconMoon, IconSun } from '../ui/icons'

interface TopbarProps {
  onOpenMenu: () => void
  menuOpen: boolean
}

const pathTitle: Array<[RegExp, string]> = [
  [/^\/tickets\/new/, 'route.newTicket'],
  [/^\/tickets\/\d+/, 'route.ticketDetail'],
  [/^\/tickets/, 'route.tickets'],
  [/^\/metrics/, 'route.metrics'],
  [/^\/sla-policies/, 'route.sla'],
  [/^\/profile/, 'route.profile'],
]

export function Topbar({ onOpenMenu, menuOpen }: TopbarProps) {
  const { pathname } = useLocation()
  const { t, locale, setLocale } = useI18n()
  const { theme, toggleTheme } = useTheme()
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const titleKey = pathTitle.find(([re]) => re.test(pathname))?.[1] ?? 'route.tickets'
  const controlClass =
    'flex h-9 w-9 items-center justify-center rounded-md text-[var(--text-muted)] ' +
    'transition-colors duration-150 hover:bg-[var(--hover)] hover:text-[var(--text)] ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]'

  return (
    <header className="flex h-16 shrink-0 items-center gap-3 border-b border-[var(--border)] bg-[var(--surface)] px-4 lg:px-6">
      <button
        type="button"
        className={`${controlClass} lg:hidden`}
        onClick={onOpenMenu}
        aria-label={menuOpen ? t('action.closeMenu') : t('nav.menu')}
        aria-expanded={menuOpen}
      >
        {menuOpen ? <IconClose /> : <IconMenu />}
      </button>

      <h1 className="min-w-0 flex-1 truncate text-lg font-semibold tracking-tight">
        {t(titleKey)}
      </h1>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          className={controlClass}
          onClick={() => setLocale(locale === 'pt-BR' ? 'en' : 'pt-BR')}
          aria-label={t('locale.label')}
          title={locale === 'pt-BR' ? 'English' : 'Português (BR)'}
        >
          <IconGlobe />
        </button>
        <button
          type="button"
          className={controlClass}
          onClick={toggleTheme}
          aria-label={theme === 'light' ? t('theme.dark') : t('theme.light')}
        >
          {theme === 'light' ? <IconMoon /> : <IconSun />}
        </button>

        <div className="mx-1 h-6 w-px bg-[var(--border)]" aria-hidden />

        {user && (
          <div className="flex items-center gap-2.5">
            <span className="hidden text-sm text-[var(--text-muted)] sm:inline">
              {user.email}
            </span>
            <span
              className="flex h-7 w-7 items-center justify-center rounded-full bg-[var(--accent-soft)] text-xs font-semibold text-[var(--accent)]"
              aria-hidden
            >
              {user.email.slice(0, 1).toUpperCase()}
            </span>
            <button
              type="button"
              className={controlClass}
              aria-label={t('action.logout')}
              onClick={() => {
                logout()
                navigate('/login')
              }}
            >
              <IconLogout />
            </button>
          </div>
        )}
      </div>
    </header>
  )
}
