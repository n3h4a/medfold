import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, setAccessToken, setSessionHandlers } from './client.ts'
import { uploadDocument } from './documents.ts'

interface Scripted {
  status: number
  body: unknown
}

/** Minimal XMLHttpRequest stand-in that answers each send() with the next scripted response. */
class FakeXhr {
  static script: Scripted[] = []
  static sent: { url: string; headers: Record<string, string>; body: FormData }[] = []

  upload: { onprogress: ((event: { lengthComputable: boolean; loaded: number; total: number }) => void) | null } = {
    onprogress: null,
  }
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  status = 0
  responseText = ''
  private url = ''
  private headers: Record<string, string> = {}

  open(_method: string, url: string) {
    this.url = url
  }

  setRequestHeader(name: string, value: string) {
    this.headers[name] = value
  }

  send(body: FormData) {
    FakeXhr.sent.push({ url: this.url, headers: this.headers, body })
    const next = FakeXhr.script.shift()
    setTimeout(() => {
      if (!next) {
        this.onerror?.()
        return
      }
      this.upload.onprogress?.({ lengthComputable: true, loaded: 50, total: 100 })
      this.upload.onprogress?.({ lengthComputable: true, loaded: 100, total: 100 })
      this.status = next.status
      this.responseText = JSON.stringify(next.body)
      this.onload?.()
    }, 0)
  }
}

function useFakeXhr(script: Scripted[]) {
  FakeXhr.script = script
  FakeXhr.sent = []
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
}

const created = { id: 'doc-1', originalFilename: 'cbc.pdf', status: 'UPLOADED' }
const file = new File(['%PDF-1.4'], 'cbc.pdf', { type: 'application/pdf' })

afterEach(() => setSessionHandlers(null))

describe('uploadDocument', () => {
  it('sends the file with the access token and reports progress', async () => {
    setAccessToken('token-1')
    useFakeXhr([{ status: 201, body: created }])
    const progress: number[] = []

    const result = await uploadDocument(file, { onProgress: (p) => progress.push(p) })

    expect(result).toEqual(created)
    expect(FakeXhr.sent[0].url).toBe('/api/v1/documents')
    expect(FakeXhr.sent[0].headers.Authorization).toBe('Bearer token-1')
    expect(FakeXhr.sent[0].body.get('file')).toBe(file)
    expect(progress).toEqual([0, 0.5, 1])
  })

  it('refreshes an expired token and retries the upload once', async () => {
    setAccessToken('expired')
    const refresh = vi.fn(async () => {
      setAccessToken('fresh')
      return true
    })
    setSessionHandlers({ refresh, onSessionExpired: vi.fn() })
    useFakeXhr([
      { status: 401, body: { status: 401, code: 'INVALID_TOKEN', message: 'expired' } },
      { status: 201, body: created },
    ])

    await expect(uploadDocument(file)).resolves.toEqual(created)

    expect(refresh).toHaveBeenCalledTimes(1)
    expect(FakeXhr.sent.map((r) => r.headers.Authorization)).toEqual(['Bearer expired', 'Bearer fresh'])
  })

  it('turns error responses into ApiError with the server code', async () => {
    setAccessToken('token-1')
    useFakeXhr([{ status: 409, body: { status: 409, code: 'DUPLICATE_DOCUMENT', message: 'dup' } }])

    const error = await uploadDocument(file).catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('DUPLICATE_DOCUMENT')
  })

  it('rejects with a network error when the connection fails', async () => {
    setAccessToken('token-1')
    useFakeXhr([])

    await expect(uploadDocument(file)).rejects.toBeInstanceOf(TypeError)
  })
})
