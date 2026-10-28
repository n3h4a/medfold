export const MAX_FILE_BYTES = 10 * 1024 * 1024

const ACCEPTED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png']
const ACCEPTED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.png']

/** Value for the file input's accept attribute. */
export const ACCEPT_ATTRIBUTE = [...ACCEPTED_MIME_TYPES, ...ACCEPTED_EXTENSIONS].join(',')

/**
 * Quick checks before uploading, so obvious mistakes fail instantly instead of after a long upload.
 * The server still checks the real file content; this is only a convenience.
 */
export function validateFile(file: File): string | null {
  const name = file.name.toLowerCase()
  const knownType = ACCEPTED_MIME_TYPES.includes(file.type) || ACCEPTED_EXTENSIONS.some((ext) => name.endsWith(ext))
  if (!knownType) return 'Only PDF, JPG and PNG files are supported'
  if (file.size === 0) return 'This file is empty'
  if (file.size > MAX_FILE_BYTES) return 'File is larger than the 10 MB limit'
  return null
}
