import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client.ts'
import type { MedicalDocument } from '../../api/documents.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { polling } from './format.ts'

const uploadDocument = vi.fn()

vi.mock('../../api/documents.ts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/documents.ts')>()),
  uploadDocument: (...args: unknown[]) => uploadDocument(...args),
}))

function doc(overrides: Partial<MedicalDocument>): MedicalDocument {
  return {
    id: 'doc-1',
    originalFilename: 'cbc.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 2048,
    status: 'UPLOADED',
    failureReason: null,
    pageCount: null,
    docType: null,
    docDate: null,
    title: null,
    providerName: null,
    facilityName: null,
    summary: null,
    userEdited: false,
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    ...overrides,
  }
}

function page(items: MedicalDocument[]) {
  return { items, page: 0, size: 50, totalItems: items.length, totalPages: 1 }
}

function pdf(name: string) {
  return new File(['%PDF-1.4'], name, { type: 'application/pdf' })
}

const loggedIn = { 'POST /auth/refresh': () => json(200, authResponse()) }

async function openPage() {
  renderApp('/')
  await screen.findByRole('heading', { name: 'Your documents' })
  return userEvent.setup({ applyAccept: false })
}

beforeEach(() => {
  uploadDocument.mockReset()
})

describe('document list', () => {
  it('shows an empty state for new users', async () => {
    mockApi(loggedIn)
    await openPage()

    expect(await screen.findByText('No documents yet')).toBeInTheDocument()
  })

  it('lists documents with title, type, date and status', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents': () =>
        json(200, page([
          doc({ id: 'a', title: 'Complete Blood Count', docType: 'LAB_REPORT', docDate: '2025-03-14', status: 'READY' }),
          doc({ id: 'b', originalFilename: 'scan.png', mimeType: 'image/png', status: 'FAILED' }),
        ])),
    })
    await openPage()

    const first = await screen.findByText('Complete Blood Count')
    const row = first.closest('li')!
    // Date format follows the user's locale, so only check its parts.
    expect(within(row).getByText(/^Lab report · .*14.*2025 · 2 KB$/)).toBeInTheDocument()
    expect(within(row).getByText('Ready')).toBeInTheDocument()
    expect(screen.getByText('scan.png')).toBeInTheDocument()
    expect(screen.getByText('Failed')).toBeInTheDocument()
    expect(screen.getByText('2 documents')).toBeInTheDocument()
  })

  it('deletes a document after confirmation and refreshes the list', async () => {
    let items = [doc({ id: 'a', originalFilename: 'old.pdf' })]
    const fetchMock = mockApi({
      ...loggedIn,
      'GET /documents': () => json(200, page(items)),
      'DELETE /documents/a': () => {
        items = []
        return new Response(null, { status: 204 })
      },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = await openPage()

    await user.click(await screen.findByRole('button', { name: 'Delete old.pdf' }))

    expect(await screen.findByText('No documents yet')).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/documents/a') && init?.method === 'DELETE')).toBe(true)
  })

  it('does nothing when deletion is cancelled', async () => {
    const fetchMock = mockApi({ ...loggedIn, 'GET /documents': () => json(200, page([doc({ id: 'a' })])) })
    vi.spyOn(window, 'confirm').mockReturnValue(false)
    const user = await openPage()

    await user.click(await screen.findByRole('button', { name: 'Delete cbc.pdf' }))

    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
  })
})

describe('uploading', () => {
  it('uploads valid files, rejects invalid ones instantly and refreshes the list', async () => {
    let items: MedicalDocument[] = []
    mockApi({ ...loggedIn, 'GET /documents': () => json(200, page(items)) })
    uploadDocument.mockImplementation(async (file: File) => {
      const created = doc({ id: file.name, originalFilename: file.name })
      items = [created, ...items]
      return created
    })
    const user = await openPage()

    await user.upload(screen.getByLabelText('Upload documents'), [
      pdf('cbc.pdf'),
      new File(['x'], 'notes.docx', { type: 'application/msword' }),
    ])

    const queue = screen.getByRole('region', { name: 'Uploads' })
    expect(within(queue).getByText('Only PDF, JPG and PNG files are supported')).toBeInTheDocument()
    expect(await within(queue).findByText('Uploaded')).toBeInTheDocument()
    expect(uploadDocument).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('1 document')).toBeInTheDocument()

    await user.click(within(queue).getByRole('button', { name: 'Clear finished' }))
    expect(screen.queryByRole('region', { name: 'Uploads' })).not.toBeInTheDocument()
  })

  it('explains server rejections such as duplicates', async () => {
    mockApi(loggedIn)
    uploadDocument.mockRejectedValue(
      new ApiError(409, { status: 409, code: 'DUPLICATE_DOCUMENT', message: 'You have already uploaded this file' }),
    )
    const user = await openPage()

    await user.upload(screen.getByLabelText('Upload documents'), pdf('cbc.pdf'))

    expect(await screen.findByText('Already in your documents')).toBeInTheDocument()
  })

  it('uploads at most three files at a time', async () => {
    mockApi(loggedIn)
    const resolvers: (() => void)[] = []
    uploadDocument.mockImplementation(
      () => new Promise<MedicalDocument>((resolve) => resolvers.push(() => resolve(doc({})))),
    )
    const user = await openPage()

    await user.upload(screen.getByLabelText('Upload documents'), ['1', '2', '3', '4', '5'].map((n) => pdf(`${n}.pdf`)))

    await waitFor(() => expect(uploadDocument).toHaveBeenCalledTimes(3))
    expect(screen.getAllByText('Waiting…')).toHaveLength(2)

    resolvers[0]()
    await waitFor(() => expect(uploadDocument).toHaveBeenCalledTimes(4))
  })

  it('shows per-file progress', async () => {
    mockApi(loggedIn)
    uploadDocument.mockImplementation((_file: File, options: { onProgress: (p: number) => void }) => {
      options.onProgress(0.42)
      return new Promise(() => {})
    })
    const user = await openPage()

    await user.upload(screen.getByLabelText('Upload documents'), pdf('big.pdf'))

    expect(await screen.findByText('42%')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Uploading big.pdf' })).toHaveAttribute('aria-valuenow', '42')
  })

  it('refreshes the list while documents are processing and stops once they are done', async () => {
    polling.intervalMs = 20
    let listCalls = 0
    mockApi({
      'POST /auth/refresh': () => json(200, authResponse()),
      'GET /documents': () => {
        listCalls += 1
        return json(200, page([doc({ status: listCalls < 3 ? 'PROCESSING' : 'READY', title: 'Lipid profile' })]))
      },
    })

    try {
      renderApp('/')

      expect(await screen.findByText('Processing')).toBeInTheDocument()
      expect(await screen.findByText('Ready')).toBeInTheDocument()
      const settled = listCalls
      await new Promise((resolve) => setTimeout(resolve, 100))
      expect(listCalls).toBe(settled)
    } finally {
      polling.intervalMs = 3000
    }
  })

  it('groups documents by the month of the record, undated ones last', async () => {
    mockApi({
      'POST /auth/refresh': () => json(200, authResponse()),
      'GET /documents': () =>
        json(200, page([
          doc({ id: 'a', status: 'READY', title: 'Lipid profile', docDate: '2026-03-20' }),
          doc({ id: 'b', status: 'READY', title: 'Prescription', docDate: '2026-03-02' }),
          doc({ id: 'c', status: 'READY', title: 'X-ray report', docDate: '2025-11-10' }),
          doc({ id: 'd', status: 'READY', title: null, originalFilename: 'scan.jpg' }),
        ])),
    })

    renderApp('/')

    const headings = await screen.findAllByRole('heading', { level: 3 })
    expect(headings.map((h) => h.textContent)).toEqual(['March 2026', 'November 2025', 'Date not found'])
    const march = headings[0].nextElementSibling as HTMLElement
    expect(within(march).getAllByRole('link').map((l) => l.textContent)).toEqual(['Lipid profile', 'Prescription'])
  })
})
