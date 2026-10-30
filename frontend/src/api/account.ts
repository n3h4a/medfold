import { apiRequest } from './client.ts'

export const accountApi = {
  /** Permanently deletes the account and everything in it. Needs the current password. */
  remove: (password: string) => apiRequest<null>('/account', { method: 'DELETE', body: { password } }),
}
