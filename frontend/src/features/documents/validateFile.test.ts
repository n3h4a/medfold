import { describe, expect, it } from 'vitest'
import { MAX_FILE_BYTES, validateFile } from './validateFile.ts'

function fileOf(name: string, type: string, size: number): File {
  const file = new File(['x'], name, { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateFile', () => {
  it('accepts PDF, JPG and PNG by type or extension', () => {
    expect(validateFile(fileOf('a.pdf', 'application/pdf', 100))).toBeNull()
    expect(validateFile(fileOf('scan.JPEG', '', 100))).toBeNull()
    expect(validateFile(fileOf('photo', 'image/png', 100))).toBeNull()
  })

  it('rejects other types, empty files and files over 10 MB', () => {
    expect(validateFile(fileOf('notes.docx', 'application/msword', 100))).toMatch(/Only PDF, JPG and PNG/)
    expect(validateFile(fileOf('a.pdf', 'application/pdf', 0))).toBe('This file is empty')
    expect(validateFile(fileOf('a.pdf', 'application/pdf', MAX_FILE_BYTES + 1))).toMatch(/10 MB/)
    expect(validateFile(fileOf('a.pdf', 'application/pdf', MAX_FILE_BYTES))).toBeNull()
  })
})
