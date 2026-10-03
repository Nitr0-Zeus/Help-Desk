import { describe, expect, it } from 'vitest'
import type { TicketStatus } from '../types'
import { availableTransitions, canAssign, canDelete } from '../components/ticket-transitions'

const ALL: TicketStatus[] = [
  'ABERTO',
  'EM_ATENDIMENTO',
  'AGUARDANDO_CLIENTE',
  'RESOLVIDO',
  'FECHADO',
]

describe('availableTransitions', () => {
  it('gives agents the full status matrix', () => {
    expect(availableTransitions('ABERTO', 'AGENTE', false)).toEqual(['EM_ATENDIMENTO'])
    expect(availableTransitions('EM_ATENDIMENTO', 'AGENTE', false)).toEqual([
      'AGUARDANDO_CLIENTE',
      'RESOLVIDO',
    ])
    expect(availableTransitions('AGUARDANDO_CLIENTE', 'AGENTE', false)).toEqual([
      'EM_ATENDIMENTO',
    ])
    expect(availableTransitions('RESOLVIDO', 'AGENTE', false)).toEqual(['FECHADO'])
    expect(availableTransitions('FECHADO', 'AGENTE', false)).toEqual([])
  })

  it('lets clients only close their own resolved ticket', () => {
    expect(availableTransitions('RESOLVIDO', 'CLIENTE', true)).toEqual(['FECHADO'])
    expect(availableTransitions('RESOLVIDO', 'CLIENTE', false)).toEqual([])
    for (const status of ALL.filter((s) => s !== 'RESOLVIDO')) {
      expect(availableTransitions(status, 'CLIENTE', true)).toEqual([])
    }
  })
})

describe('canAssign', () => {
  it('is agent-only and only before resolution', () => {
    expect(canAssign('ABERTO', 'AGENTE')).toBe(true)
    expect(canAssign('EM_ATENDIMENTO', 'AGENTE')).toBe(true)
    expect(canAssign('AGUARDANDO_CLIENTE', 'AGENTE')).toBe(false)
    expect(canAssign('RESOLVIDO', 'AGENTE')).toBe(false)
    expect(canAssign('FECHADO', 'AGENTE')).toBe(false)
    expect(canAssign('ABERTO', 'CLIENTE')).toBe(false)
  })
})

describe('canDelete', () => {
  it('is owner-only and only while open', () => {
    expect(canDelete('ABERTO', 'CLIENTE', true)).toBe(true)
    expect(canDelete('ABERTO', 'CLIENTE', false)).toBe(false)
    expect(canDelete('EM_ATENDIMENTO', 'CLIENTE', true)).toBe(false)
    expect(canDelete('ABERTO', 'AGENTE', true)).toBe(false)
    expect(canDelete('ABERTO', 'AGENTE', false)).toBe(false)
  })
})
