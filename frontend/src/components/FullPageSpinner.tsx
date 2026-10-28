export function FullPageSpinner() {
  return (
    <div className="flex min-h-[50vh] items-center justify-center" role="status" aria-live="polite">
      <div className="h-8 w-8 animate-spin rounded-full border-2 border-brand-100 border-t-brand-600" />
      <span className="sr-only">Loading…</span>
    </div>
  )
}
