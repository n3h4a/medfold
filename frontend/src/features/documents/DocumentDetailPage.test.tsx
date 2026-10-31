import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { DocumentResults, MedicalDocument } from '../../api/documents.ts'
import { authResponse, json, mockApi } from '../../test/mockApi.ts'
import { renderApp } from '../../test/renderApp.tsx'
import { polling } from './format.ts'

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

const noResults: DocumentResults = { labResults: [], medications: [], terms: [] }

const cbcResults: DocumentResults = {
  labResults: [
    {
      id: 'lab-1', documentId: 'doc-1', testName: 'Haemoglobin', testKey: 'hemoglobin', valueText: '12.1', valueNumeric: 12.1,
      unit: 'g/dL', refText: '13.0 - 17.0', refLow: 13, refHigh: 17, flag: 'LOW', observedOn: '2025-03-14', pageNumber: 1,
      userEdited: false,
    },
    {
      id: 'lab-2', documentId: 'doc-1', testName: 'Platelets', testKey: 'platelets', valueText: '2.5', valueNumeric: 2.5,
      unit: 'lakh/cumm', refText: '1.5 - 4.5', refLow: 1.5, refHigh: 4.5, flag: 'NORMAL', observedOn: '2025-03-14',
      pageNumber: 1, userEdited: false,
    },
    {
      id: 'lab-3', documentId: 'doc-1', testName: 'Urine sugar', testKey: 'urine_sugar', valueText: 'Negative',
      valueNumeric: null, unit: null, refText: null, refLow: null, refHigh: null, flag: null, observedOn: '2025-03-14',
      pageNumber: 2, userEdited: false,
    },
  ],
  medications: [
    {
      id: 'med-1', documentId: 'doc-1', name: 'Ferrous sulfate 200 mg', dosage: '200 mg', frequency: '1-0-0', route: null,
      duration: '30 days', instructions: 'after food', prescribedOn: '2025-03-14', userEdited: false,
    },
  ],
  terms: [{ term: 'CBC', definition: 'A common blood test that counts blood cells.' }],
}

function fileResponse(type: string) {
  return () => new Response('file-bytes', { status: 200, headers: { 'Content-Type': type } })
}

describe('document detail page', () => {
  it('shows details, summary and the PDF in an embedded viewer fetched with the access token', async () => {
    const fetchMock = mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, noResults),
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
      'GET /documents/doc-1/results': () => json(200, noResults),
    })
    const { router } = renderApp('/')
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Complete Blood Count' }))

    expect(router.state.location.pathname).toBe('/documents/doc-1')
    expect(await screen.findByRole('heading', { name: 'Complete Blood Count' })).toBeInTheDocument()
  })

  it('shows extracted test results compared with the printed range, medicines and terms', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, cbcResults),
    })

    renderApp('/documents/doc-1')

    const table = await screen.findByRole('table')
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(4)
    expect(within(rows[1]).getByRole('rowheader')).toHaveTextContent('Haemoglobin')
    expect(rows[1]).toHaveTextContent('12.1g/dL')
    expect(rows[1]).toHaveTextContent('13.0 - 17.0')
    expect(within(rows[1]).getByText('Below printed range')).toBeInTheDocument()
    expect(within(rows[2]).getByText('Within printed range')).toBeInTheDocument()
    expect(rows[3]).toHaveTextContent('Negative')
    expect(rows[3]).not.toHaveTextContent('printed range')
    expect(screen.getByText(/not a medical judgment/)).toBeInTheDocument()

    expect(screen.getByText('Ferrous sulfate 200 mg')).toBeInTheDocument()
    expect(screen.getByText('200 mg · 1-0-0 · 30 days')).toBeInTheDocument()
    expect(screen.getByText('after food')).toBeInTheDocument()
    expect(screen.getByText('CBC')).toBeInTheDocument()
    expect(screen.getByText('A common blood test that counts blood cells.')).toBeInTheDocument()
  })

  it('follows processing and shows the results once the document is ready', async () => {
    polling.intervalMs = 20
    let calls = 0
    const fetchMock = mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => {
        calls += 1
        return json(200, calls < 3 ? doc({ status: 'PROCESSING', title: null, summary: null }) : doc())
      },
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, cbcResults),
    })

    try {
      renderApp('/documents/doc-1')

      expect(await screen.findByText(/Reading this document/)).toBeInTheDocument()
      expect(await screen.findByRole('heading', { name: 'Complete Blood Count' })).toBeInTheDocument()
      expect(await screen.findByText('Below printed range')).toBeInTheDocument()
      expect(screen.queryByText(/Reading this document/)).not.toBeInTheDocument()

      const settled = calls
      await new Promise((resolve) => setTimeout(resolve, 100))
      expect(calls).toBe(settled)
      const resultCalls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/results'))
      expect(resultCalls.length).toBeGreaterThan(0)
    } finally {
      polling.intervalMs = 3000
    }
  })

  it('lets the user try a failed document again', async () => {
    let reprocessed = false
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () =>
        json(200, reprocessed
          ? doc({ status: 'PROCESSING', failureReason: null })
          : doc({ status: 'FAILED', failureReason: 'The AI service was unavailable. Please try again later' })),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'POST /documents/doc-1/reprocess': () => {
        reprocessed = true
        return json(202, { id: 'doc-1', status: 'PROCESSING', failureReason: null })
      },
    })
    const user = userEvent.setup()
    renderApp('/documents/doc-1')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The AI service was unavailable')
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByText('Processing')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(reprocessed).toBe(true)
  })

  it('can process a ready document again after confirming', async () => {
    const fetchMock = mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, noResults),
      'POST /documents/doc-1/reprocess': () => json(202, { id: 'doc-1', status: 'PROCESSING', failureReason: null }),
    })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const user = userEvent.setup()
    renderApp('/documents/doc-1')

    const button = await screen.findByRole('button', { name: 'Process again' })
    const reprocessCalls = () => fetchMock.mock.calls.filter(([url]) => String(url).endsWith('/reprocess'))
    await user.click(button)
    expect(reprocessCalls()).toHaveLength(0)
    await user.click(button)

    expect(confirm).toHaveBeenCalledTimes(2)
    expect(reprocessCalls()).toHaveLength(1)
  })

  it('corrects document details and shows a field error from the server', async () => {
    let current = doc()
    const bodies: unknown[] = []
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, current),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, noResults),
      'PATCH /documents/doc-1': (init) => {
        const body = JSON.parse(String(init?.body))
        bodies.push(body)
        if (body.title === 'bad') {
          return json(400, { status: 400, code: 'VALIDATION_ERROR', message: 'Some fields are invalid', fields: { docDate: 'The record date can\'t be in the future' } })
        }
        current = doc({ title: body.title, providerName: null, userEdited: true })
        return json(200, current)
      },
    })
    const user = userEvent.setup()
    renderApp('/documents/doc-1')

    await user.click(await screen.findByRole('button', { name: 'Edit details' }))
    const form = screen.getByRole('form', { name: 'Edit document details' })
    const title = within(form).getByLabelText('Title')
    await user.clear(title)
    await user.type(title, 'bad')
    await user.click(within(form).getByRole('button', { name: 'Save' }))
    expect(await within(form).findByText("The record date can't be in the future")).toBeInTheDocument()

    await user.clear(title)
    await user.type(title, 'Full blood count')
    await user.clear(within(form).getByLabelText('Doctor'))
    await user.click(within(form).getByRole('button', { name: 'Save' }))

    expect(await screen.findByRole('heading', { name: 'Full blood count' })).toBeInTheDocument()
    expect(screen.getByText('Edited by you')).toBeInTheDocument()
    expect(bodies[1]).toEqual({
      docType: 'LAB_REPORT',
      docDate: '2025-03-14',
      title: 'Full blood count',
      providerName: '',
      facilityName: 'City Diagnostics',
      summary: 'Blood count report listing haemoglobin and white cell values.',
    })
  })

  it('corrects a misread lab value in place', async () => {
    let results = cbcResults
    let patched: unknown = null
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, results),
      'PATCH /lab-results/lab-1': (init) => {
        patched = JSON.parse(String(init?.body))
        const fixed = { ...cbcResults.labResults[0], valueText: '14.1', valueNumeric: 14.1, flag: 'NORMAL' as const, userEdited: true }
        results = { ...cbcResults, labResults: [fixed, ...cbcResults.labResults.slice(1)] }
        return json(200, fixed)
      },
    })
    const user = userEvent.setup()
    renderApp('/documents/doc-1')

    await user.click(await screen.findByRole('button', { name: 'Edit Haemoglobin' }))
    const form = screen.getByRole('form', { name: 'Edit Haemoglobin' })
    const value = within(form).getByLabelText('Result')
    await user.clear(value)
    await user.type(value, '14.1')
    await user.click(within(form).getByRole('button', { name: 'Save' }))

    const row = (await screen.findByText('14.1')).closest('tr') as HTMLElement
    expect(within(row).getByText('Within printed range')).toBeInTheDocument()
    expect(within(row).getByText('Edited')).toBeInTheDocument()
    expect(patched).toEqual({ testName: 'Haemoglobin', valueText: '14.1', unit: 'g/dL', refText: '13.0 - 17.0' })
  })

  it('removes a wrongly read medicine after confirming', async () => {
    let removed = false
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc()),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, removed ? { ...cbcResults, medications: [] } : cbcResults),
      'DELETE /medications/med-1': () => {
        removed = true
        return new Response(null, { status: 204 })
      },
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const user = userEvent.setup()
    renderApp('/documents/doc-1')

    await user.click(await screen.findByRole('button', { name: 'Remove Ferrous sulfate 200 mg' }))

    await waitFor(() => expect(screen.queryByText('Ferrous sulfate 200 mg')).not.toBeInTheDocument())
    expect(removed).toBe(true)
  })

  it('says "1 page" for a single page', async () => {
    mockApi({
      ...loggedIn,
      'GET /documents/doc-1': () => json(200, doc({ pageCount: 1 })),
      'GET /documents/doc-1/file': fileResponse('application/pdf'),
      'GET /documents/doc-1/results': () => json(200, noResults),
    })

    renderApp('/documents/doc-1')

    expect(await screen.findByText('2 KB · 1 page')).toBeInTheDocument()
  })
})
