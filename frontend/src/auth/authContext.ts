import { createContext } from 'react'
import type { LoginRequest, RegisterRequest, User } from '../api/auth.ts'

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'anonymous'; user: null; reason?: 'expired' }
  | { status: 'authenticated'; user: User }

export interface AuthContextValue {
  state: AuthState
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
