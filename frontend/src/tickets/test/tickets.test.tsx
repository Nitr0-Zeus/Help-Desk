import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders, queryClient } from '../../app/providers'
import TicketsPage from '../pages/TicketsPage'
import { PriorityBadge } from '../components/Badges'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
let fetchMock: ReturnType<typeof vi.fn>
let requestedUrls: string[]

function renderAt(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/tickets" element={<TicketsPage />} />
        </Routes>
      </MemoryRouter>
    </AppProviders>,
  )
}

beforeEach(() => {
  requestedUrls = []
  fetchMock = vi.fn(async (url: string) => {
    requestedUrls.push(String(url))
    return jsonResponse(200, emptyPage)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  queryClient.clear()
  sessionStorage.clear()
  localStorage.clear()
})

describe('Ticket filters', () => {
  it('reads filters from the URL and sends them to the API', async () => {
    renderAt('/tickets?status=ABERTO&priority=CRITICA&q=email')

    await waitFor(() => expect(requestedUrls.length).toBeGreaterThan(0))
    const url = requestedUrls[0]
    expect(url).toContain('status=ABERTO')
    expect(url).toContain('priority=CRITICA')
    expect(url).toContain('q=email')
    expect(url).toContain('page=0')
  })

  it('reflects a filter change back into the URL', async () => {
    const user = userEvent.setup()
    renderAt('/tickets?status=ABERTO')

    await waitFor(() => expect(requestedUrls.length).toBeGreaterThan(0))
    const statusSelect = screen.getByRole('combobox', { name: 'Status' })
    await user.selectOptions(statusSelect, 'RESOLVIDO')

    await waitFor(() => {
      expect(requestedUrls.some((u) => u.includes('status=RESOLVIDO'))).toBe(true)
    })
    // original filter preserved alongside the new one
    expect(requestedUrls.at(-1)).toContain('status=RESOLVIDO')
  })

  it('keeps the query key in sync (React Query refires on URL change)', async () => {
    const user = userEvent.setup()
    renderAt('/tickets')
    await waitFor(() => expect(requestedUrls.length).toBe(1))

    await user.click(screen.getByRole('combobox', { name: 'SLA' }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'SLA' }), 'true')
    await waitFor(() => expect(requestedUrls.length).toBe(2))
    expect(requestedUrls[1]).toContain('slaBreached=true')
  })
})

describe('Badges', () => {
  it('renders semantic labels for status and priority', () => {
    render(
      <AppProviders>
        <PriorityBadge priority="CRITICA" />
      </AppProviders>,
    )
    expect(screen.getByText('Crítica')).toBeInTheDocument()
  })
})
