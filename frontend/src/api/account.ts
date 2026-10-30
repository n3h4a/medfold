import { apiGetBlob, apiRequest } from './client.ts'

export const accountApi = {
  /** Everything Medfold holds about the user, as one JSON file. */
  export: () => apiGetBlob('/account/export'),
  /** Permanently deletes the account and everything in it. Needs the current password. */
  remove: (password: string) => apiRequest<null>('/account', { method: 'DELETE', body: { password } }),
}
