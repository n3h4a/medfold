import { ApiError, apiGet } from './client.ts'

export type ServiceStatus = 'UP' | 'DOWN'

export interface HealthResponse {
  service: string
  status: ServiceStatus
  database: ServiceStatus
  schemaVersion?: string
  /** Not part of status: the API works without it, documents just wait to be processed. */
  aiService?: 'UP' | 'DOWN' | 'NOT_CONFIGURED'
  processing?: 'ON' | 'OFF'
}

/**
 * The backend answers 503 with a normal health body when the database is down,
 * so that case is returned as data rather than thrown.
 */
export async function fetchHealth(): Promise<HealthResponse> {
  try {
    return await apiGet<HealthResponse>('/health')
  } catch (error) {
    if (error instanceof ApiError && error.status === 503 && error.body) {
      return error.body as HealthResponse
    }
    throw error
  }
}
