import type { ButtonHTMLAttributes } from 'react'
import { buttonClass } from './button-styles'
import type { ButtonSize, ButtonVariant } from './button-styles'

export function Button({
  variant = 'primary',
  size = 'md',
  className,
  type = 'button',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
}) {
  return <button type={type} className={buttonClass(variant, size, className)} {...props} />
}
