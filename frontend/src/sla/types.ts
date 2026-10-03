import type { Priority } from '../tickets/types'

export interface SlaPolicy {
  priority: Priority
  firstResponseHours: number
  resolutionHours: number
  active: boolean
}
