import { useEffect, useState } from 'react'

/**
 * A blob: URL for showing a downloaded file, revoked when the blob changes or the component
 * unmounts so the file's bytes don't stay in browser memory.
 *
 * Object URLs are an external browser resource, so they are created and revoked in an effect.
 * Creating them during render would leak one per render, and under StrictMode a memoized URL
 * would be revoked by the first cleanup while still being displayed.
 */
export function useObjectUrl(blob: Blob | undefined): string | null {
  const [current, setCurrent] = useState<{ blob: Blob; url: string } | null>(null)

  useEffect(() => {
    if (!blob) return
    const url = URL.createObjectURL(blob)
    // oxlint-disable-next-line react/set-state-in-effect -- syncing with an external resource (see above)
    setCurrent({ blob, url })
    return () => URL.revokeObjectURL(url)
  }, [blob])

  // Never hand out a URL that belongs to a previous blob.
  return current && current.blob === blob ? current.url : null
}
