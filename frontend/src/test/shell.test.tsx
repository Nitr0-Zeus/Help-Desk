import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AppProviders } from '../app/providers'
import { AppRouter } from '../app/router'

function fakeToken(email = 'ana@dev.io', role = 'CLIENTE'): string {
  const payload = btoa(JSON.stringify({ sub: '1', email, role }))
  return `e30.${payload}.sig`
}

function renderAt(path: string, token?: string) {
  sessionStorage.clear()
  localStorage.clear()
  if (token) sessionStorage.setItem('hd.access', token)
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <AppRouter />
      </MemoryRouter>
    </AppProviders>,
  )
}

afterEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})

describe('App shell routing', () => {
  it('redirects unauthenticated /tickets to the login screen', () => {
    renderAt('/tickets')
    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('keeps anonymous visitors out of protected routes', () => {
    renderAt('/metrics')
    expect(screen.getByRole('heading', { name: 'Entrar' })).toBeInTheDocument()
  })

  it('renders the shell sidebar and route title with a session', () => {
    renderAt('/tickets', fakeToken())
    expect(screen.getByRole('navigation', { name: 'Menu' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Chamados' })).toBeInTheDocument()
    expect(screen.getByText('ana@dev.io')).toBeInTheDocument()
  })

  it('redirects authenticated visitors away from /login', () => {
    renderAt('/login', fakeToken())
    expect(screen.getByRole('navigation', { name: 'Menu' })).toBeInTheDocument()
  })

  it('shows the not-found page inside the shell for unknown routes', () => {
    renderAt('/nope', fakeToken())
    expect(screen.getByText('Página não encontrada')).toBeInTheDocument()
  })
})

describe('Preferences', () => {
  it('toggles the theme attribute', async () => {
    const user = userEvent.setup()
    renderAt('/tickets', fakeToken())
    const initial = document.documentElement.dataset.theme
    const toggle = screen.getByRole('button', {
      name: initial === 'light' ? 'Tema escuro' : 'Tema claro',
    })
    await user.click(toggle)
    expect(document.documentElement.dataset.theme).not.toBe(initial)
    expect(localStorage.getItem('hd.theme')).toBe(document.documentElement.dataset.theme)
  })

  it('switches the interface language', async () => {
    const user = userEvent.setup()
    renderAt('/tickets', fakeToken())
    expect(screen.getByText('Métricas')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Idioma' }))
    expect(screen.getByText('Metrics')).toBeInTheDocument()
    expect(localStorage.getItem('hd.locale')).toBe('en')
  })
})
