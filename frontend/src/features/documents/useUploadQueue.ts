import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useRef, useState } from 'react'
import { ApiError } from '../../api/client.ts'
import { uploadDocument } from '../../api/documents.ts'
import { errorMessage } from '../../auth/errors.ts'
import { validateFile } from './validateFile.ts'

export type UploadState = 'queued' | 'uploading' | 'done' | 'error'

export interface UploadItem {
  id: string
  name: string
  size: number
  state: UploadState
  progress: number
  error: string | null
}

const MAX_PARALLEL_UPLOADS = 3

function uploadErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'DUPLICATE_DOCUMENT') return 'Already in your documents'
  if (!(error instanceof ApiError)) return 'Upload failed. Check your connection and try again.'
  return errorMessage(error)
}

/**
 * Uploads files one request each, at most three at a time, tracking progress per file.
 * Work is driven from event handlers (not effects), so React StrictMode can't start an upload twice.
 */
export function useUploadQueue() {
  const queryClient = useQueryClient()
  const [items, setItems] = useState<UploadItem[]>([])
  const pending = useRef<{ id: string; file: File }[]>([])
  const active = useRef(0)
  const nextId = useRef(0)

  const update = useCallback((id: string, changes: Partial<UploadItem>) => {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...changes } : item)))
  }, [])

  const pump = useCallback(function pumpQueue() {
    while (active.current < MAX_PARALLEL_UPLOADS && pending.current.length > 0) {
      const { id, file } = pending.current.shift()!
      active.current += 1
      update(id, { state: 'uploading', progress: 0 })
      uploadDocument(file, { onProgress: (progress) => update(id, { progress }) })
        .then(() => {
          update(id, { state: 'done', progress: 1 })
          void queryClient.invalidateQueries({ queryKey: ['documents'] })
        })
        .catch((error: unknown) => update(id, { state: 'error', error: uploadErrorMessage(error) }))
        .finally(() => {
          active.current -= 1
          pumpQueue()
        })
    }
  }, [queryClient, update])

  const addFiles = useCallback(
    (files: Iterable<File>) => {
      const added: UploadItem[] = []
      for (const file of files) {
        const id = `upload-${nextId.current++}`
        const problem = validateFile(file)
        added.push({
          id,
          name: file.name,
          size: file.size,
          state: problem ? 'error' : 'queued',
          progress: 0,
          error: problem,
        })
        if (!problem) pending.current.push({ id, file })
      }
      setItems((current) => [...added, ...current])
      pump()
    },
    [pump],
  )

  const clearFinished = useCallback(() => {
    setItems((current) => current.filter((item) => item.state === 'queued' || item.state === 'uploading'))
  }, [])

  return { items, addFiles, clearFinished }
}
