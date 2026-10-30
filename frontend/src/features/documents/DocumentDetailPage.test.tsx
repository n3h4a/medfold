import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { MedicalDocument } from '../../api/documents.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'

function doc(overrides: Partial<MedicalDocument> = {}): MedicalDocument {
  return {
    id: 'doc-1',
    originalFilename: 'cbc-march.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 2048,
    status: 'READY',
    failureReason: null,
    pageCount: 2,
    docType: 'LAB_REPORT',
    docDate: '2025-03-14',
    title: 'Complete Blood Count',
    providerName: 'Dr. Mehta',
    facilityName: 'City Diagnostics',
    summary: 'Blood count report listing haemoglobin and white cell values.',
    userEdited: false,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  }
}

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

function fileResponse(type: string) {
  return () => new Response('file-bytes', { status: 200, headers: { 'Content-Type': type } })
}

describe('document detail page', () => {
  it('shows details, summary and the PDF in an embedded viewer fetched with the access token', async () => {
    const fetchMock = mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
    })

    renderApp('/documents/doc-1')

    expect(await screen.findByRole('heading', { name: 'Complete Blood Count' })).toBeInTheDocument()
    expect(screen.getByText('Lab report')).toBeInTheDocument()
    expect(screen.getByText('Dr. Mehta')).toBeInTheDocument()
    expect(screen.getByText('City Diagnostics')).toBeInTheDocument()
    expect(screen.getByText('2 KB · 2 pages')).toBeInTheDocument()
    expect(screen.getByText(/haemoglobin and white cell values/)).toBeInTheDocument()
    expect(screen.getByText(/is not medical advice/)).toBeInTheDocument()

    const viewer = await screen.findByTitle('Complete Blood Count (PDF)')
    expect(viewer.tagName).toBe('IFRAME')
    expect(viewer.getAttribute('src')).toMatch(/^blob:/)
    expect(screen.getByRole('link', { name: 'Download' })).toHaveAttribute('download', 'cbc-march.pdf')

    const fileCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/documents/doc-1/file'))
    expect(new Headers(fileCall?.[1]?.headers).get('Authorization')).toBe('Bearer access-token-1')
  })

  it('shows images inline', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc({ mimeType: 'image/png', title: null, originalFilename: 'rx.png' })),
      'GET /documents/doc-1/file': fileResponse('image/png'),
    })

    renderApp('/documents/doc-1')

    const image = await screen.findByRole('img', { name: 'rx.png' })
    expect(image.getAttribute('src')).toMatch(/^blob:/)
  })

  it('marks fields that are not extracted yet and explains the missing summary', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () =>
        json(200, doc({ status: 'UPLOADED', docType: null, docDate: null, providerName: null, summary: null, title: null })),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
    })

    renderApp('/documents/doc-1')

    expect(await screen.findByRole('heading', { name: 'cbc-march.pdf' })).toBeInTheDocument()
    expect(screen.getByText('Waiting to process')).toBeInTheDocument()
    expect(screen.getAllByText('Not found yet').length).toBeGreaterThanOrEqual(3)
    expect(screen.getByText(/summary will appear here once the document has been processed/)).toBeInTheDocument()
  })

  it('shows why processing failed', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc({ status: 'FAILED', failureReason: "We couldn't read any text in this file" })),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
    })

    renderApp('/documents/doc-1')

    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't read any text in this file")
  })

  it('shows a not-found state for missing or foreign documents', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/nope': () => json(404, { status: 404, code: 'DOCUMENT_NOT_FOUND', message: 'Document not found' }),
    })

    renderApp('/documents/nope')

    expect(await screen.findByRole('heading', { name: 'Document not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /All documents/ })).toHaveAttribute('href', '/')
  })

  it('shows an error in the viewer if the file itself is missing', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': () =>
        json(404, { status: 404, code: 'FILE_NOT_FOUND', message: 'The original file for this document is missing' }),
    })

    renderApp('/documents/doc-1')

    expect(await screen.findByText('The original file for this document is missing')).toBeInTheDocument()
  })

  it('deletes the document and returns to the list', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'DELETE /documents/doc-1': () => new Response(null, { status: 204 }),
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { router } = renderApp('/documents/doc-1')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Delete document' }))

    expect(await screen.findByRole('heading', { name: 'Your documents' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/')
  })

  it('is reachable from the document list', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents': () => json(200, { items: [doc()], page: 0, size: 50, totalItems: 1, totalPages: 1 }),
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
    })
    const { router } = renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Complete Blood Count' }))

    expect(router.state.location.pathname).toBe('/documents/doc-1')
    expect(await screen.findByRole('heading', { name: 'Complete Blood Count' })).toBeInTheDocument()
  })
})
