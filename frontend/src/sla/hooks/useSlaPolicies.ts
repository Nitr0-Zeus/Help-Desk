import { useQuery } from '@tanstack/react-query'
import { getSlaPolicies } from '../api/SlaApi'

export function useSlaPolicies() {
  return useQuery({
    queryKey: ['sla-policies'],
    queryFn: getSlaPolicies,
  })
}
