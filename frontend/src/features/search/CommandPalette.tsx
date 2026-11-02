import { useQuery } from '@tanstack/react-query'
import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { recordsApi } from '../../api/records.ts'
import { ArrowRightIcon, DocumentIcon, FlaskIcon, SearchIcon } from '../../components/icons.tsx'
import { Highlight } from '../../components/Highlight.tsx'
import { navItems } from '../../app/nav.ts'
import { displayTitle, formatDate, typeLabel } from '../documents/format.ts'

interface Option {
  key: string
  group: 'Pages' | 'Tests' | 'Documents'
  to: string
  label: string
  detail?: ReactNode
  icon: ReactNode
}

/**
 * Search everything from anywhere (⌘K): pages, tests by name, and documents by any word in them,
 * using the same search as the documents page. Arrow keys move, Enter opens, Escape closes.
 */
export function CommandPalette({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)
  const listId = useId()

  useEffect(() => {
    inputRef.current?.focus()
  }, [])
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), 200)
    return () => clearTimeout(timer)
  }, [input])

  const labs = useQuery({ queryKey: ['labs'], queryFn: recordsApi.labTests })
  const search = useQuery({
    queryKey: ['search', query],
    queryFn: () => recordsApi.search(query),
    enabled: query.length > 0,
  })

  const words = query.split(/\s+/).filter(Boolean)
  const options = useMemo<Option[]>(() => {
    const needle = input.trim().toLowerCase()
    const pages = navItems
      .filter((item) => !needle || item.label.toLowerCase().includes(needle))
      .map<Option>(({ to, label, icon: ItemIcon }) => ({
        key: `page:${to}`,
        group: 'Pages',
        to,
        label,
        icon: <ItemIcon className="h-4 w-4" />,
      }))
    const tests = needle
      ? (labs.data ?? [])
          .filter((t) => t.testName.toLowerCase().includes(needle) || t.testKey.includes(needle))
          .slice(0, 5)
          .map<Option>((t) => ({
            key: `test:${t.testKey}`,
            group: 'Tests',
            to: `/labs/${encodeURIComponent(t.testKey)}`,
            label: t.testName,
            detail: `${t.latestValueText}${t.unit ? ` ${t.unit}` : ''} · ${t.resultCount} ${t.resultCount === 1 ? 'result' : 'results'}`,
            icon: <FlaskIcon className="h-4 w-4" />,
          }))
      : []
    const documents = query
      ? (search.data ?? []).slice(0, 8).map<Option>(({ document, snippet, highlights }) => ({
          key: `doc:${document.id}`,
          group: 'Documents',
          to: `/documents/${document.id}`,
          label: displayTitle(document),
          detail: snippet ? (
            <Highlight text={snippet} words={[...words, ...highlights]} />
          ) : (
            [document.docType ? typeLabel[document.docType] : null, document.docDate ? formatDate(document.docDate) : null]
              .filter(Boolean)
              .join(' · ')
          ),
          icon: <DocumentIcon className="h-4 w-4" />,
        }))
      : []
    return [...pages, ...tests, ...documents]
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [input, query, labs.data, search.data])

  useEffect(() => {
    setActive(0)
  }, [options.length])

  function open(option: Option | undefined) {
    if (!option) return
    onClose()
    navigate(option.to)
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((i) => Math.min(i + 1, options.length - 1))
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((i) => Math.max(i - 1, 0))
    } else if (event.key === 'Enter') {
      event.preventDefault()
      open(options[active])
    }
  }

  const searching = query.length > 0 && search.isPending
  let lastGroup: string | null = null

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center p-4 pt-[12vh]" onKeyDown={onKeyDown}>
      <div className="absolute inset-0 bg-slate-900/40 backdrop-blur-sm dark:bg-black/60" onClick={onClose} aria-hidden="true" />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="Search Medfold"
        className="relative w-full max-w-xl animate-rise overflow-hidden rounded-2xl border border-slate-200 bg-surface shadow-2xl"
      >
        <div className="flex items-center gap-3 border-b border-slate-200 px-4">
          <SearchIcon className="h-5 w-5 text-slate-400" />
          <input
            ref={inputRef}
            role="combobox"
            aria-expanded="true"
            aria-controls={listId}
            aria-activedescendant={options[active] ? `${listId}-${active}` : undefined}
            aria-label="Search pages, tests and documents"
            value={input}
            maxLength={100}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Search tests, medicines, doctors or any word…"
            className="h-14 flex-1 bg-transparent text-base text-slate-900 placeholder:text-slate-400 focus:outline-none"
          />
          <kbd className="rounded border border-slate-200 px-1.5 text-[11px] text-slate-500">Esc</kbd>
        </div>
        <ul id={listId} role="listbox" aria-label="Results" className="max-h-[55vh] overflow-y-auto p-2">
          {options.map((option, index) => {
            const header = option.group !== lastGroup ? option.group : null
            lastGroup = option.group
            return (
              <li key={option.key} role="presentation">
                {header && <p className="px-3 pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wider text-slate-400">{header}</p>}
                <div
                  id={`${listId}-${index}`}
                  role="option"
                  aria-selected={index === active}
                  onMouseMove={() => setActive(index)}
                  onClick={() => open(option)}
                  className={`flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 ${
                    index === active ? 'bg-brand-50 text-brand-ink' : 'text-slate-700'
                  }`}
                >
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-500">
                    {option.icon}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium text-slate-900">{option.label}</span>
                    {option.detail && <span className="block truncate text-xs text-slate-500">{option.detail}</span>}
                  </span>
                  {index === active && <ArrowRightIcon className="h-4 w-4 shrink-0" />}
                </div>
              </li>
            )
          })}
          {searching && (
            <li role="presentation" className="px-3 py-3 text-sm text-slate-500" aria-live="polite">
              Searching your documents…
            </li>
          )}
          {query && !searching && options.length === 0 && (
            <li role="presentation" className="px-3 py-6 text-center text-sm text-slate-500">
              Nothing found for “{query}”
            </li>
          )}
        </ul>
      </div>
    </div>
  )
}
