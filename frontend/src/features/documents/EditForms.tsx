import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  documentsApi,
  type DocumentType,
  type LabResult,
  type MedicalDocument,
  type Medication,
} from '../../api/documents.ts'
import { errorMessage, fieldErrors } from '../../auth/errors.ts'
import { TextField } from '../../components/TextField.tsx'
import { refreshEverywhere } from './editing.ts'
import { typeLabel } from './format.ts'

const primaryButton =
  'rounded-lg bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-500 disabled:opacity-50'
const secondaryButton =
  'rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-brand-500'

function FormError({ error }: { error: unknown }) {
  if (!error) return null
  const fields = fieldErrors(error)
  return Object.keys(fields).length > 0 ? null : (
    <p role="alert" className="text-sm text-red-700">
      {errorMessage(error)}
    </p>
  )
}

function Actions({ pending, onCancel }: { pending: boolean; onCancel: () => void }) {
  return (
    <div className="flex gap-2">
      <button type="submit" disabled={pending} className={primaryButton}>
        {pending ? 'Saving…' : 'Save'}
      </button>
      <button type="button" onClick={onCancel} className={secondaryButton}>
        Cancel
      </button>
    </div>
  )
}

export function DocumentDetailsForm({ document, onDone }: { document: MedicalDocument; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [values, setValues] = useState({
    docType: document.docType ?? '',
    docDate: document.docDate ?? '',
    title: document.title ?? '',
    providerName: document.providerName ?? '',
    facilityName: document.facilityName ?? '',
    summary: document.summary ?? '',
  })
  const save = useMutation({
    mutationFn: () =>
      documentsApi.update(document.id, {
        // The type and date can be changed but not cleared.
        docType: (values.docType || undefined) as DocumentType | undefined,
        docDate: values.docDate || undefined,
        title: values.title,
        providerName: values.providerName,
        facilityName: values.facilityName,
        summary: values.summary,
      }),
    onSuccess: async (updated) => {
      queryClient.setQueryData(['documents', document.id], updated)
      await refreshEverywhere(queryClient)
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const set = (field: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [field]: event.target.value }))

  return (
    <form
      onSubmit={(event: FormEvent) => {
        event.preventDefault()
        save.mutate()
      }}
      className="space-y-3"
      aria-label="Edit document details"
    >
      <div>
        <label htmlFor="edit-doc-type" className="block text-sm font-medium text-slate-700">
          Document type
        </label>
        <select
          id="edit-doc-type"
          value={values.docType}
          onChange={set('docType')}
          className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
        >
          {!document.docType && <option value="">Not set</option>}
          {Object.entries(typeLabel).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <TextField label="Record date" type="date" value={values.docDate} onChange={set('docDate')} error={errors.docDate} />
      <TextField label="Title" value={values.title} onChange={set('title')} maxLength={255} error={errors.title} />
      <TextField label="Doctor" value={values.providerName} onChange={set('providerName')} maxLength={255} error={errors.providerName} />
      <TextField
        label="Hospital or lab"
        value={values.facilityName}
        onChange={set('facilityName')}
        maxLength={255}
        error={errors.facilityName}
      />
      <div>
        <label htmlFor="edit-summary" className="block text-sm font-medium text-slate-700">
          Summary
        </label>
        <textarea
          id="edit-summary"
          rows={5}
          maxLength={2000}
          value={values.summary}
          onChange={set('summary')}
          className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-100"
        />
      </div>
      <p className="text-xs text-slate-500">Your corrections are kept if the document is processed again.</p>
      <FormError error={save.error} />
      <Actions pending={save.isPending} onCancel={onDone} />
    </form>
  )
}

export function LabResultForm({ result, onDone }: { result: LabResult; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [values, setValues] = useState({
    testName: result.testName,
    valueText: result.valueText,
    unit: result.unit ?? '',
    refText: result.refText ?? '',
  })
  const save = useMutation({
    mutationFn: () => documentsApi.updateLabResult(result.id, values),
    onSuccess: async () => {
      await refreshEverywhere(queryClient)
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const set = (field: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [field]: event.target.value }))

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate()
      }}
      aria-label={`Edit ${result.testName}`}
      className="grid gap-3 bg-slate-50 p-3 sm:grid-cols-2"
    >
      <TextField label="Test" value={values.testName} onChange={set('testName')} required maxLength={255} error={errors.testName} />
      <TextField label="Result" value={values.valueText} onChange={set('valueText')} required maxLength={100} error={errors.valueText} />
      <TextField label="Unit" value={values.unit} onChange={set('unit')} maxLength={50} error={errors.unit} />
      <TextField label="Reference range" value={values.refText} onChange={set('refText')} maxLength={100} error={errors.refText} />
      <div className="space-y-2 sm:col-span-2">
        <FormError error={save.error} />
        <Actions pending={save.isPending} onCancel={onDone} />
      </div>
    </form>
  )
}

export function MedicationForm({ medication, onDone }: { medication: Medication; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [values, setValues] = useState({
    name: medication.name,
    dosage: medication.dosage ?? '',
    frequency: medication.frequency ?? '',
    route: medication.route ?? '',
    duration: medication.duration ?? '',
    instructions: medication.instructions ?? '',
  })
  const save = useMutation({
    mutationFn: () => documentsApi.updateMedication(medication.id, values),
    onSuccess: async () => {
      await refreshEverywhere(queryClient)
      onDone()
    },
  })
  const errors = fieldErrors(save.error)
  const set = (field: keyof typeof values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [field]: event.target.value }))

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault()
        save.mutate()
      }}
      aria-label={`Edit ${medication.name}`}
      className="grid gap-3 rounded-lg bg-slate-50 p-3 sm:grid-cols-2"
    >
      <TextField label="Medicine" value={values.name} onChange={set('name')} required maxLength={255} error={errors.name} className="sm:col-span-2" />
      <TextField label="Dose" value={values.dosage} onChange={set('dosage')} maxLength={100} error={errors.dosage} />
      <TextField label="How often" value={values.frequency} onChange={set('frequency')} maxLength={100} error={errors.frequency} />
      <TextField label="Route" value={values.route} onChange={set('route')} maxLength={50} error={errors.route} />
      <TextField label="Duration" value={values.duration} onChange={set('duration')} maxLength={100} error={errors.duration} />
      <TextField
        label="Instructions"
        value={values.instructions}
        onChange={set('instructions')}
        maxLength={500}
        error={errors.instructions}
        className="sm:col-span-2"
      />
      <div className="space-y-2 sm:col-span-2">
        <FormError error={save.error} />
        <Actions pending={save.isPending} onCancel={onDone} />
      </div>
    </form>
  )
}
