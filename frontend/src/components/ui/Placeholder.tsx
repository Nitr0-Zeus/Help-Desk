import { useI18n } from '../../i18n/I18nProvider'

/** Temporary scaffold shown until the domain page lands in its plan step. */
export function Placeholder({ titleKey }: { titleKey: string }) {
  const { t } = useI18n()
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--surface)] p-8 text-center">
      <h2 className="text-base font-semibold">{t(titleKey)}</h2>
      <p className="mt-1.5 text-sm text-[var(--text-muted)]">{t('placeholder.hint')}</p>
    </div>
  )
}
