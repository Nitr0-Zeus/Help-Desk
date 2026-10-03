import { apiFetch } from '../../api/client'
import type { SlaPolicy } from '../types'

export function getSlaPolicies(): Promise<SlaPolicy[]> {
  return apiFetch<SlaPolicy[]>('/sla-policies')
}
