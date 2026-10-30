import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { accountApi } from '../../api/account.ts'
import { errorMessage, fieldErrors } from '../../auth/errors.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { TextField } from '../../components/TextField.tsx'

function DeleteAccount() {
  const { logout } = useAuth()
  const [password, setPassword] = useState('')
  const remove = useMutation({
    mutationFn: () => accountApi.remove(password),
    // The session is gone with the account; logging out returns to the login page.
    onSuccess: () => logout(),
  })
  const errors = fieldErrors(remove.error)

  return (
    <section aria-labelledby="delete-heading" className="rounded-xl border border-red-200 bg-white p-4 shadow-sm">
      <h2 id="delete-heading" className="text-sm font-semibold text-red-800">
        Delete account
      </h2>
      <p className="mt-1 text-sm text-slate-600">
        Permanently deletes your account, every uploaded file and everything read from them. This can't be undone.
        Download your data first if you want to keep a copy.
      </p>
      <form
        className="mt-3 space-y-3"
        onSubmit={(event) => {
          event.preventDefault()
          if (window.confirm('Delete your account and all your records permanently?')) remove.mutate()
        }}
      >
        <TextField
          label="Your password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
          required
        />
        {remove.isError && !errors.password && (
          <p role="alert" className="text-sm text-red-700">
            {errorMessage(remove.error)}
          </p>
        )}
        <button
          type="submit"
          disabled={remove.isPending || password.length === 0}
          className="rounded-lg bg-red-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-red-800 focus:outline-none focus:ring-2 focus:ring-red-400 disabled:opacity-50"
        >
          {remove.isPending ? 'Deleting…' : 'Delete my account'}
        </button>
      </form>
    </section>
  )
}

export function AccountPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated') return null

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Your account</h1>
        <p className="mt-1 text-slate-600">
          {state.user.fullName ? `${state.user.fullName} · ` : ''}
          {state.user.email}
        </p>
      </div>
      <DeleteAccount />
    </div>
  )
}
