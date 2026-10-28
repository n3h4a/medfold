import { apiGet, apiPost } from './client.ts'

export interface User {
  id: string
  email: string
  fullName: string | null
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: User
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest extends LoginRequest {
  fullName?: string
}

export const authApi = {
  login: (request: LoginRequest) => apiPost<AuthResponse>('/auth/login', request),
  register: (request: RegisterRequest) => apiPost<AuthResponse>('/auth/register', request),
  refresh: () => apiPost<AuthResponse>('/auth/refresh'),
  logout: () => apiPost<null>('/auth/logout'),
  me: () => apiGet<User>('/auth/me'),
}
