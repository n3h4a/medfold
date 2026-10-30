import { useMutation, useQueryClient, type QueryClient } from '@tanstack/react-query'

/** Corrections show up everywhere the data appears: lists, trends, medicines and search. */
export function refreshEverywhere(queryClient: QueryClient) {
  return Promise.all(
    [['documents'], ['labs'], ['medications'], ['search']].map((queryKey) => queryClient.invalidateQueries({ queryKey })),
  )
}

/** Removes a wrongly read row, then refreshes everything that shows it. */
export function useRemove(remove: () => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: remove,
    onSuccess: () => refreshEverywhere(queryClient),
  })
}

export const smallButton =
  'rounded px-1.5 py-0.5 text-xs font-medium text-brand-700 hover:bg-brand-50 focus:outline-none focus:ring-2 focus:ring-brand-500 disabled:opacity-50'
