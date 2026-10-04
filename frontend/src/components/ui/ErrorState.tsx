import { Button } from './Button'

interface ErrorStateProps {
  message: string
  tone?: 'danger' | 'neutral'
  actionLabel?: string
  onAction?: () => void
}

export function ErrorState({ message, tone = 'danger', actionLabel, onAction }: ErrorStateProps) {
  const danger = tone === 'danger'
  return (
    <div
      className={
        danger
          ? 'rounded-lg border border-red-200 bg-red-50 p-6 text-center dark:border-red-900 dark:bg-red-950'
          : 'rounded-lg border border-[var(--border)] bg-[var(--surface)] p-10 text-center'
      }
    >
      {danger ? (
        <p role="alert" className="text-sm font-medium text-red-700 dark:text-red-300">
          {message}
        </p>
      ) : (
        <h2 className="text-base font-semibold">{message}</h2>
      )}
      {actionLabel && onAction ? (
        danger ? (
          <button
            type="button"
            onClick={onAction}
            className="mt-3 inline-flex h-8 items-center justify-center rounded-md border border-red-300 px-3 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-900"
          >
            {actionLabel}
          </button>
        ) : (
          <Button variant="secondary" size="sm" className="mt-4" onClick={onAction}>
            {actionLabel}
          </Button>
        )
      ) : null}
    </div>
  )
}
