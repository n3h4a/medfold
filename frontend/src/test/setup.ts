import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { setAccessToken } from '../api/client.ts'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  setAccessToken(null)
})

// jsdom has no object URLs; the file viewer needs them.
let objectUrlCount = 0
URL.createObjectURL = () => `blob:test/${++objectUrlCount}`
URL.revokeObjectURL = () => {}
