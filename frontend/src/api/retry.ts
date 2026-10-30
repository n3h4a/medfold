import { ApiError } from './client.ts'

/**
 * React Query retry policy: retry once for network or server errors, never for 4xx
 * (not found, not yours, invalid) because asking again gives the same answer.
 */
export function retryUnlessClientError(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 1
}
