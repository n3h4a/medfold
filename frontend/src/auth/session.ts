import { authApi, type AuthResponse } from '../api/auth.ts'
import { ApiError, setAccessToken } from '../api/client.ts'

let inFlightRefresh: Promise<AuthResponse | null> | null = null

/**
 * Exchanges the refresh cookie for a new access token.
 *
 * Concurrent callers share one request. The backend rotates the refresh token on every use
 * and treats a second use of the same token as theft, so two parallel refreshes would log
 * the user out everywhere.
 *
 * Resolves to null when there is no valid session (401); rethrows network or server errors.
 */
export function refreshSession(): Promise<AuthResponse | null> {
  if (!inFlightRefresh) {
    inFlightRefresh = authApi
      .refresh()
      .then((response) => {
        setAccessToken(response.accessToken)
        return response
      })
      .catch((error: unknown) => {
        setAccessToken(null)
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      })
      .finally(() => {
        inFlightRefresh = null
      })
  }
  return inFlightRefresh
}
