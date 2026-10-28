import { DocumentList } from './DocumentList.tsx'
import { UploadDropzone } from './UploadDropzone.tsx'
import { UploadQueue } from './UploadQueue.tsx'
import { useUploadQueue } from './useUploadQueue.ts'

export function DocumentsPage() {
  const { items, addFiles, clearFinished } = useUploadQueue()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-slate-900">Your documents</h1>
        <p className="mt-1 text-slate-600">Upload once. Find everything. Understand your medical history.</p>
      </div>
      <UploadDropzone onFiles={addFiles} />
      <UploadQueue items={items} onClear={clearFinished} />
      <DocumentList />
    </div>
  )
}
