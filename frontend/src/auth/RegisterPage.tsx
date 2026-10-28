import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { TextField } from '../components/TextField.tsx'
import { AuthCard, FormError, SubmitButton } from './AuthCard.tsx'
import { errorMessage, fieldErrors } from './errors.ts'
import { useAuth } from './useAuth.ts'

export function RegisterPage() {
  const { register } = useAuth()

  const [fullName, setFullName] = useState('')
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
      await register({ email, password, fullName: fullName.trim() || undefined })
    } catch (caught) {
      setError(caught)
      setPending(false)
    }
  }

  const fields = fieldErrors(error)

  return (
    <AuthCard title="Create your Medfold account" subtitle="Upload once. Find everything. Understand your medical history.">
      <form onSubmit={handleSubmit} noValidate className="space-y-4">
        <FormError message={error && Object.keys(fields).length === 0 ? errorMessage(error) : null} />
        <TextField
          label="Full name (optional)"
          autoComplete="name"
          value={fullName}
          onChange={(event) => setFullName(event.target.value)}
          error={fields.fullName}
        />
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
          autoComplete="new-password"
          required
          minLength={8}
          hint="At least 8 characters."
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={fields.password}
        />
        <SubmitButton pending={pending}>{pending ? 'Creating account…' : 'Create account'}</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        Already have an account?{' '}
        <Link to="/login" className="font-medium text-brand-600 hover:underline">
          Log in
        </Link>
      </p>
    </AuthCard>
  )
}
