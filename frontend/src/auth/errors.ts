import { ApiError } from '../api/client.ts'

export const NETWORK_ERROR_MESSAGE = "Can't reach Medfold right now. Check your connection and try again."

/** A user-facing message for any error thrown by an API call. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status >= 500) return 'Something went wrong on our side. Please try again.'
    return error.serverMessage ?? 'Request failed. Please try again.'
  }
  return NETWORK_ERROR_MESSAGE
}

export function fieldErrors(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fields : {}
}
