import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { TextField } from '../components/TextField.tsx'
import { AuthCard, FormError, SubmitButton } from './AuthCard.tsx'
import { errorMessage, fieldErrors } from './errors.ts'
import { useAuth } from './useAuth.ts'

export function LoginPage() {
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      // GuestRoute redirects once the session is set.
      await login({ email, password })
    } catch (caught) {
      setError(caught)
      setPending(false)
    }
  }

  const fields = fieldErrors(error)

  return (
    <AuthCard title="Log in to Medfold" subtitle="Your medical records, organized in one private place.">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error && Object.keys(fields).length === 0 ? errorMessage(error) : null} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fields.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fields.password}
        />
        <SubmitButton pending={pending}>{pending ? 'Logging in…' : 'Log in'}</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        New to Medfold?{' '}
        <Link to="/register" className="font-medium text-brand-600 hover:underline">
          Create an account
        </Link>
      </p>
    </AuthCard>
  )
}
