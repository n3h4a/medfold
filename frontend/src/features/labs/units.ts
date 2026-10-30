/**
 * Units for trend charts. Only two kinds of change are made, and only for drawing the chart:
 * different spellings of the same unit are treated as equal, and a short list of standard
 * conversions is applied for specific tests. Tables always show values exactly as printed.
 */

const SPELLINGS: Record<string, string> = {
  'g/dl': 'g/dl',
  'gm/dl': 'g/dl',
  'gms/dl': 'g/dl',
  'gm%': 'g/dl',
  'g%': 'g/dl',
  'mg/dl': 'mg/dl',
  'mg%': 'mg/dl',
  'mmol/l': 'mmol/l',
  'umol/l': 'umol/l',
  'mmol/mol': 'mmol/mol',
  'ng/ml': 'ng/ml',
  'nmol/l': 'nmol/l',
  'g/l': 'g/l',
  '%': '%',
  // Per microlitre: a cubic millimetre is a microlitre.
  '/cumm': '/ul',
  '/cmm': '/ul',
  '/mm3': '/ul',
  '/ul': '/ul',
  'cells/cumm': '/ul',
  'cells/ul': '/ul',
  'cells/mm3': '/ul',
  // Thousands per microlitre = 10^9 per litre.
  '10^3/ul': '10^3/ul',
  'x10^3/ul': '10^3/ul',
  '10^9/l': '10^3/ul',
  'x10^9/l': '10^3/ul',
  'thou/cumm': '10^3/ul',
  'thou/ul': '10^3/ul',
}

/** A canonical form for comparing units: case, spaces and µ/μ/u spellings don't matter. */
export function normalizeUnit(unit: string | null): string | null {
  if (unit === null) return null
  const compact = unit.toLowerCase().replace(/\s+/g, '').replace(/[µμ]/g, 'u').replace(/\*/g, '^').replace(/³/g, '3')
  return SPELLINGS[compact] ?? compact
}

interface Conversion {
  from: string
  to: string
  convert: (value: number) => number
}

/** Linear conversions both ways: value_in_to = value_in_from × factor. */
function linear(from: string, to: string, factor: number): Conversion[] {
  return [
    { from, to, convert: (v) => v * factor },
    { from: to, to: from, convert: (v) => v / factor },
  ]
}

const GLUCOSE = linear('mg/dl', 'mmol/l', 1 / 18.016)
const CHOLESTEROL = linear('mg/dl', 'mmol/l', 1 / 38.67)

/** Standard conversions per canonical test key (see the AI service's lab_keys.py). */
const CONVERSIONS: Record<string, Conversion[]> = {
  glucose_fasting: GLUCOSE,
  glucose_pp: GLUCOSE,
  glucose_random: GLUCOSE,
  cholesterol_total: CHOLESTEROL,
  ldl: CHOLESTEROL,
  hdl: CHOLESTEROL,
  triglycerides: linear('mg/dl', 'mmol/l', 1 / 88.57),
  creatinine: linear('mg/dl', 'umol/l', 88.42),
  uric_acid: linear('mg/dl', 'umol/l', 59.48),
  urea: linear('mg/dl', 'mmol/l', 1 / 6.006),
  haemoglobin: linear('g/dl', 'g/l', 10),
  vitamin_d: linear('ng/ml', 'nmol/l', 2.496),
  // IFCC–NGSP master equation: mmol/mol = (% − 2.152) × 10.929.
  hba1c: [
    { from: '%', to: 'mmol/mol', convert: (v) => (v - 2.152) * 10.929 },
    { from: 'mmol/mol', to: '%', convert: (v) => v / 10.929 + 2.152 },
  ],
}

/**
 * The value in the target unit, or null if it can't be expressed in it with certainty.
 * Same unit (in any spelling) needs no conversion.
 */
export function toUnit(testKey: string, value: number, from: string | null, to: string | null): number | null {
  const a = normalizeUnit(from)
  const b = normalizeUnit(to)
  if (a === b) return value
  if (a === null || b === null) return null
  const conversion = CONVERSIONS[testKey]?.find((c) => c.from === a && c.to === b)
  return conversion ? conversion.convert(value) : null
}
