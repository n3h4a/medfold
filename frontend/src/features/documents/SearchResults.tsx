import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { recordsApi } from '../../api/records.ts'
import { errorMessage } from '../../auth/errors.ts'
import { displayTitle, formatDate, typeLabel } from './format.ts'

/** Documents containing every word of the query: details, page text, test or medicine names. */
export function SearchResults({ query }: { query: string }) {
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['search', query],
    queryFn: () => recordsApi.search(query),
  })

  if (isPending) return <p className="text-sm text-slate-500" role="status">Searching…</p>
  if (isError) {
    return (
      <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
        {errorMessage(error)}
      </p>
    )
  }
  if (data.length === 0) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white px-6 py-10 text-center">
        <p className="font-medium text-slate-900">Nothing found for “{query}”</p>
        <p className="mt-1 text-sm text-slate-500">Try another word, such as a test, medicine, doctor or hospital name.</p>
      </div>
    )
  }

  return (
    <section aria-labelledby="search-heading">
      <h2 id="search-heading" className="mb-2 text-sm font-semibold text-slate-900">
        {data.length} {data.length === 1 ? 'match' : 'matches'}
        {data.length === 50 && ' (showing the first 50)'}
      </h2>
      <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        {data.map(({ document, snippet, pageNumber }) => (
          <li key={document.id} className="relative px-4 py-3 hover:bg-slate-50">
            <Link
              to={`/documents/${document.id}`}
              className="block truncate font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-700 focus:outline-none focus-visible:underline"
            >
              {displayTitle(document)}
            </Link>
            <p className="truncate text-sm text-slate-500">
              {[document.docType ? typeLabel[document.docType] : null, document.docDate ? formatDate(document.docDate) : null]
                .filter(Boolean)
                .join(' · ')}
            </p>
            {snippet && (
              <p className="mt-1 text-sm text-slate-600">
                {pageNumber && <span className="text-slate-400">Page {pageNumber}: </span>}
                {snippet}
              </p>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
