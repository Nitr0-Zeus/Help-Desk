import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders, queryClient } from '../../app/providers'
import { formatHours, formatPercent } from '../../lib/formatMetrics'
import MetricsPage from '../pages/MetricsPage'

const globalMetrics = {
  total: 3,
  byStatus: {
    ABERTO: 1,
    EM_ATENDIMENTO: 0,
    AGUARDANDO_CLIENTE: 0,
    RESOLVIDO: 2,
    FECHADO: 0,
  },
  slaCompliancePercent: 50,
  avgFirstResponseHours: 1.5,
  avgResolutionHours: 6.5,
}

const agentMetrics = [
  {
    agentId: 2,
    agentName: 'Agente Demo',
    assigned: 3,
    resolved: 2,
    slaCompliancePercent: null,
    avgFirstResponseHours: null,
    avgResolutionHours: 4,
  },
]

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  fetchMock = vi.fn(async (url: string) => {
    const body = String(url).includes('/agents') ? agentMetrics : globalMetrics
    return new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  queryClient.clear()
  sessionStorage.clear()
})

describe('MetricsPage', () => {
  it('renders global cards and the per-agent table', async () => {
    render(
      <AppProviders>
        <MemoryRouter initialEntries={['/metrics']}>
          <MetricsPage />
        </MemoryRouter>
      </AppProviders>,
    )

    await waitFor(() => expect(screen.getByText('Total de chamados')).toBeInTheDocument())
    expect(screen.getAllByText('3').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('50%')).toBeInTheDocument()
    expect(screen.getByText('Agente Demo')).toBeInTheDocument()
    expect(screen.queryByText('Nobody')).not.toBeInTheDocument()
  })

  it('shows an em dash for missing averages and compliance', async () => {
    render(
      <AppProviders>
        <MemoryRouter initialEntries={['/metrics']}>
          <MetricsPage />
        </MemoryRouter>
      </AppProviders>,
    )

    await waitFor(() => expect(screen.getByText('Agente Demo')).toBeInTheDocument())
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBeGreaterThanOrEqual(2)
  })
})

describe('metric formatters', () => {
  it('formats percent and hours per locale', () => {
    expect(formatPercent(null, 'pt-BR')).toBe('—')
    expect(formatPercent(50, 'pt-BR')).toBe('50%')
    expect(formatPercent(99.5, 'pt-BR')).toBe('100%')
    expect(formatHours(null, 'pt-BR')).toBe('—')
    expect(formatHours(6.55, 'pt-BR')).toBe('6,6')
    expect(formatHours(1.5, 'en')).toBe('1.5')
  })
})
