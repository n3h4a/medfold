import { useQueries, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { documentsApi, type MedicalDocument, type Medication } from '../../api/documents.ts'
import { recordsApi, type LabTestSummary } from '../../api/records.ts'
import { useAuth } from '../../auth/useAuth.ts'
import {
  ActivityIcon,
  AlertIcon,
  ArrowRightIcon,
  ClipboardIcon,
  FlaskIcon,
  FolderIcon,
  PillIcon,
  ScanIcon,
  TrendIcon,
  UploadIcon,
} from '../../components/icons.tsx'
import { Card, FlagBadge, PageHeader, SkeletonList, Sparkline, StatCard } from '../../components/ui.tsx'
import { DocumentTypeIcon } from '../documents/DocumentTypeIcon.tsx'
import { displayTitle, formatDate, isInProgress, typeLabel } from '../documents/format.ts'
import { StatusPill } from '../documents/StatusPill.tsx'
import { plottablePoints } from '../labs/trendPoints.ts'
import { latestMedicines, outsideRange } from './insights.ts'

function greeting(now = new Date()): string {
  const hour = now.getHours()
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

const linkButton =
  'inline-flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2'

function Onboarding() {
  const steps = [
    { icon: <UploadIcon />, title: 'Upload', text: 'A PDF or a photo of a lab report, prescription or discharge summary.' },
    { icon: <ScanIcon />, title: 'Medfold reads it', text: 'OCR and AI pull out the date, doctor, every test value and every medicine.' },
    { icon: <TrendIcon />, title: 'See the story', text: 'Values from different labs line up on one chart. Search finds any word.' },
  ]
  return (
    <Card className="overflow-hidden">
      <div className="bg-gradient-to-br from-brand-500 to-brand-700 px-6 py-8 text-white sm:px-8">
        <h2 className="text-xl font-semibold">Let's put your paperwork in one place</h2>
        <p className="mt-1 max-w-xl text-brand-100">
          Start with any report you have on your phone or desk. It takes a few seconds to read.
        </p>
        <Link to="/documents" className={`${linkButton} mt-5 bg-white text-brand-700 shadow-sm hover:bg-brand-50`}>
          <UploadIcon className="h-4 w-4" /> Upload your first document
        </Link>
      </div>
      <ol className="grid gap-px bg-slate-200 sm:grid-cols-3">
        {steps.map((step, i) => (
          <li key={step.title} className="bg-surface p-6">
            <div className="flex items-center gap-3">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-50 text-brand-ink">{step.icon}</span>
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">Step {i + 1}</span>
            </div>
            <p className="mt-3 font-medium text-slate-900">{step.title}</p>
            <p className="mt-1 text-sm text-slate-600">{step.text}</p>
          </li>
        ))}
      </ol>
    </Card>
  )
}

function WorthALook({ tests }: { tests: LabTestSummary[] }) {
  const shown = tests.slice(0, 5)
  const trends = useQueries({
    queries: shown.map((t) => ({ queryKey: ['labs', t.testKey], queryFn: () => recordsApi.labTrend(t.testKey) })),
  })
  return (
    <Card className="p-5">
      <div className="flex items-center gap-2">
        <AlertIcon className="h-5 w-5 text-amber-800" />
        <h2 className="font-semibold text-slate-900">Outside the printed range</h2>
      </div>
      <p className="mt-1 text-sm text-slate-500">
        Latest results outside the range printed on their own report. Worth bringing to your next appointment.
      </p>
      {shown.length === 0 ? (
        <p className="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
          Every latest result is within the range printed on its report.
        </p>
      ) : (
        <ul className="mt-3 divide-y divide-slate-100">
          {shown.map((t, i) => {
            const values = trends[i].data ? plottablePoints(trends[i].data.results).points.map((p) => p.value) : []
            return (
              <li key={t.testKey} className="relative flex items-center gap-4 py-3">
                <div className="min-w-0 flex-1">
                  <Link
                    to={`/labs/${encodeURIComponent(t.testKey)}`}
                    className="font-medium text-slate-900 after:absolute after:inset-0 hover:text-brand-ink"
                  >
                    {t.testName}
                  </Link>
                  <p className="text-xs text-slate-500">
                    {t.latestObservedOn ? formatDate(t.latestObservedOn) : 'No date'} · {t.resultCount}{' '}
                    {t.resultCount === 1 ? 'result' : 'results'}
                  </p>
                </div>
                <Sparkline values={values} className="hidden h-8 w-24 sm:block" />
                <div className="w-28 text-right">
                  <p className="font-semibold text-slate-900">
                    {t.latestValueText}
                    {t.unit && <span className="ml-1 text-xs font-normal text-slate-500">{t.unit}</span>}
                  </p>
                  <FlagBadge flag={t.latestFlag} />
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {tests.length > shown.length && (
        <Link to="/labs?filter=outside" className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-brand-ink hover:underline">
          See all {tests.length} <ArrowRightIcon className="h-4 w-4" />
        </Link>
      )}
    </Card>
  )
}

function RecentDocuments({ documents }: { documents: MedicalDocument[] }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900">Recent documents</h2>
        <Link to="/documents" className="text-sm font-medium text-brand-ink hover:underline">
          View all
        </Link>
      </div>
      <ul className="mt-3 space-y-1">
        {documents.slice(0, 5).map((d) => (
          <li key={d.id} className="relative flex items-center gap-3 rounded-xl p-2 hover:bg-slate-50">
            <DocumentTypeIcon type={d.docType} mimeType={d.mimeType} />
            <div className="min-w-0 flex-1">
              <Link to={`/documents/${d.id}`} className="block truncate text-sm font-medium text-slate-900 after:absolute after:inset-0">
                {displayTitle(d)}
              </Link>
              <p className="truncate text-xs text-slate-500">
                {[d.docType ? typeLabel[d.docType] : null, d.docDate ? formatDate(d.docDate) : null].filter(Boolean).join(' · ')}
              </p>
            </div>
            {d.status !== 'READY' && <StatusPill status={d.status} />}
          </li>
        ))}
      </ul>
    </Card>
  )
}

function LatestMedicines({ medications }: { medications: Medication[] }) {
  const { date, items } = latestMedicines(medications)
  if (items.length === 0) return null
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="font-semibold text-slate-900">Latest prescription</h2>
          <p className="text-xs text-slate-500">{date ? formatDate(date) : 'Undated'} · as written by your doctor</p>
        </div>
        <Link to="/medications" className="text-sm font-medium text-brand-ink hover:underline">
          All medicines
        </Link>
      </div>
      <ul className="mt-3 grid gap-2 sm:grid-cols-2">
        {items.map((m) => (
          <li key={m.id} className="flex items-start gap-3 rounded-xl border border-slate-200 p-3">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-50 text-violet-700">
              <PillIcon className="h-4 w-4" />
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{m.name}</p>
              <p className="truncate text-xs text-slate-500">{[m.dosage, m.frequency].filter(Boolean).join(' · ') || '—'}</p>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  )
}

/** Home: what's in the record, what changed, and what is worth a conversation. */
export function OverviewPage() {
  const { state } = useAuth()
  const documents = useQuery({ queryKey: ['documents'], queryFn: () => documentsApi.list() })
  const labs = useQuery({ queryKey: ['labs'], queryFn: recordsApi.labTests })
  const medications = useQuery({ queryKey: ['medications'], queryFn: recordsApi.medications })

  const name = state.status === 'authenticated' ? state.user.fullName?.split(' ')[0] : null
  const docs = documents.data?.items ?? []
  const tests = labs.data ?? []
  const meds = medications.data ?? []
  const flagged = outsideRange(tests)
  const processing = docs.filter((d) => isInProgress(d.status)).length
  const resultCount = tests.reduce((sum, t) => sum + t.resultCount, 0)
  const medicineNames = new Set(meds.map((m) => m.name.toLowerCase())).size

  return (
    <div className="animate-rise space-y-8">
      <PageHeader
        title={`${greeting()}${name ? `, ${name}` : ''}`}
        subtitle="Your health record at a glance."
        actions={
          <>
            <Link to="/summary" className={`${linkButton} border border-slate-300 bg-surface text-slate-700 hover:bg-slate-50`}>
              <ClipboardIcon className="h-4 w-4" /> Visit summary
            </Link>
            <Link to="/documents" className={`${linkButton} bg-brand-600 text-white shadow-sm hover:bg-brand-700`}>
              <UploadIcon className="h-4 w-4" /> Upload
            </Link>
          </>
        }
      />

      {documents.isPending ? (
        <SkeletonList rows={3} label="Loading your overview" />
      ) : docs.length === 0 ? (
        <Onboarding />
      ) : (
        <>
          <section aria-label="Summary" className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <StatCard
              label="Documents"
              value={documents.data?.totalItems ?? docs.length}
              detail={processing > 0 ? `${processing} being read now` : 'All read'}
              icon={<FolderIcon />}
            />
            <StatCard label="Tests tracked" value={tests.length} detail={`${resultCount} results over time`} icon={<FlaskIcon />} tone="sky" />
            <StatCard
              label="Outside printed range"
              value={flagged.length}
              detail="Latest result of each test"
              icon={<ActivityIcon />}
              tone="amber"
            />
            <StatCard label="Medicines" value={medicineNames} detail="Across all prescriptions" icon={<PillIcon />} tone="violet" />
          </section>
          <div className="grid gap-6 lg:grid-cols-5">
            <div className="lg:col-span-3">
              <WorthALook tests={flagged} />
            </div>
            <div className="lg:col-span-2">
              <RecentDocuments documents={docs} />
            </div>
          </div>
          <LatestMedicines medications={meds} />
        </>
      )}
    </div>
  )
}
