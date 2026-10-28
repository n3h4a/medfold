import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, type AuthResponse, type LoginRequest, type RegisterRequest } from '../api/auth.ts'
import { setAccessToken, setSessionHandlers } from '../api/client.ts'
import { AuthContext, type AuthContextValue, type AuthState } from './authContext.ts'
import { refreshSession } from './session.ts'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [state, setState] = useState<AuthState>({ status: 'loading', user: null })

  // Restore the session from the refresh cookie when the app first loads.
  useEffect(() => {
    let active = true
    refreshSession()
      .then((session) => {
        if (!active) return
        setState(session ? { status: 'authenticated', user: session.user } : { status: 'anonymous', user: null })
      })
      .catch(() => {
        if (active) setState({ status: 'anonymous', user: null })
      })
    return () => {
      active = false
    }
  }, [])

  // Let the API client refresh expired access tokens and end the session when that fails.
  useEffect(() => {
    setSessionHandlers({
      refresh: async () => {
        const session = await refreshSession()
        if (session) setState({ status: 'authenticated', user: session.user })
        return session !== null
      },
      onSessionExpired: () => {
        setAccessToken(null)
        queryClient.clear()
        setState((current) =>
          current.status === 'authenticated' ? { status: 'anonymous', user: null, reason: 'expired' } : current,
        )
      },
    })
    return () => setSessionHandlers(null)
  }, [queryClient])

  const startSession = useCallback((response: AuthResponse) => {
    setAccessToken(response.accessToken)
    setState({ status: 'authenticated', user: response.user })
  }, [])

  const login = useCallback(
    async (request: LoginRequest) => startSession(await authApi.login(request)),
    [startSession],
  )

  const register = useCallback(
    async (request: RegisterRequest) => startSession(await authApi.register(request)),
    [startSession],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setAccessToken(null)
      queryClient.clear()
      setState({ status: 'anonymous', user: null })
    }
  }, [queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({ state, login, register, logout }),
    [state, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
