export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'dangerSolid'
export type ButtonSize = 'md' | 'sm'

const BASE =
  'inline-flex items-center justify-center rounded-md font-medium ' +
  'disabled:cursor-not-allowed disabled:opacity-60'

const VARIANT: Record<ButtonVariant, string> = {
  primary:
    'bg-[var(--accent)] text-white transition-opacity duration-150 hover:opacity-90 ' +
    'dark:text-slate-900',
  secondary:
    'border border-[var(--border)] transition-colors duration-150 hover:bg-[var(--hover)]',
  danger:
    'border border-red-300 text-red-600 transition-colors duration-150 hover:bg-red-50 ' +
    'dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950 focus-visible:outline-red-500',
  dangerSolid:
    'bg-red-600 text-white transition-opacity duration-150 hover:opacity-90 ' +
    'focus-visible:outline-red-500',
}

const SIZE: Record<ButtonSize, string> = {
  md: 'h-9 px-4 text-sm',
  sm: 'h-8 px-3 text-sm',
}

export function buttonClass(
  variant: ButtonVariant = 'primary',
  size: ButtonSize = 'md',
  className?: string,
): string {
  return [BASE, VARIANT[variant], SIZE[size], className].filter(Boolean).join(' ')
}
