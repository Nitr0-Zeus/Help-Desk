import { NavLink } from 'react-router-dom'
import { useI18n } from '../../i18n/I18nProvider'
import {
  IconClock,
  IconMetrics,
  IconTickets,
  IconUser,
} from '../ui/icons'

interface SidebarProps {
  onNavigate?: () => void
}

const navItems = [
  { to: '/tickets', key: 'nav.tickets', Icon: IconTickets },
  { to: '/metrics', key: 'nav.metrics', Icon: IconMetrics },
  { to: '/sla-policies', key: 'nav.sla', Icon: IconClock },
  { to: '/profile', key: 'nav.profile', Icon: IconUser },
]

export function Sidebar({ onNavigate }: SidebarProps) {
  const { t } = useI18n()
  return (
    <div className="flex h-full flex-col bg-[var(--sidebar)] border-r border-[var(--border)]">
      <div className="flex items-center gap-2.5 px-5 h-16 shrink-0 border-b border-[var(--border)]">
        <span
          className="flex h-8 w-8 items-center justify-center rounded-md bg-[var(--accent)] text-white text-sm font-bold"
          aria-hidden
        >
          HD
        </span>
        <span className="text-[15px] font-semibold tracking-tight">{t('app.name')}</span>
      </div>
      <nav className="flex-1 overflow-y-auto px-3 py-4" aria-label={t('nav.menu')}>
        <ul className="space-y-1">
          {navItems.map(({ to, key, Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                onClick={onNavigate}
                className={({ isActive }) =>
                  [
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors duration-150',
                    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]',
                    isActive
                      ? 'bg-[var(--accent-soft)] text-[var(--accent)] font-medium'
                      : 'text-[var(--text-muted)] hover:bg-[var(--hover)] hover:text-[var(--text)]',
                  ].join(' ')
                }
              >
                <Icon />
                {t(key)}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}
