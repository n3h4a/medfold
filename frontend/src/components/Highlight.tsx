/** Text with every occurrence of the given words marked, case-insensitively. */
export function Highlight({ text, words }: { text: string; words: string[] }) {
  const terms = words.map((w) => w.trim()).filter(Boolean)
  if (terms.length === 0) return <>{text}</>
  const pattern = new RegExp(`(${terms.map((t) => t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|')})`, 'gi')
  return (
    <>
      {text.split(pattern).map((part, i) =>
        i % 2 === 1 ? (
          <mark key={i} className="rounded bg-amber-100 px-0.5 text-inherit">
            {part}
          </mark>
        ) : (
          part
        ),
      )}
    </>
  )
}
