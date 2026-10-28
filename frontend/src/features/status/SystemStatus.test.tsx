import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { SystemStatus } from './SystemStatus.tsx'

function renderWithQuery() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <SystemStatus />
    </QueryClientProvider>,
  )
}

function mockFetch(status: number, body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })),
  )
}

describe('SystemStatus', () => {
  it('shows backend online and database connected', async () => {
    mockFetch(200, {
      service: 'medfold-backend',
      status: 'UP',
      database: 'UP',
      schemaVersion: '1',
    })

    renderWithQuery()

    expect(await screen.findByText('Online')).toBeInTheDocument()
    expect(screen.getByText('Connected')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('shows database unavailable when backend answers 503', async () => {
    mockFetch(503, { service: 'medfold-backend', status: 'DOWN', database: 'DOWN' })

    renderWithQuery()

    expect(await screen.findByText('Unavailable')).toBeInTheDocument()
    expect(screen.getByText('Online')).toBeInTheDocument()
  })

  it('shows backend unreachable when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    renderWithQuery()

    expect(await screen.findByText('Unreachable')).toBeInTheDocument()
  })
})
