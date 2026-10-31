import { describe, expect, it } from 'vitest'
import { normalizeUnit, toUnit } from './units.ts'

describe('normalizeUnit', () => {
  it.each([
    ['g/dL', 'g/dl'],
    ['gm/dl', 'g/dl'],
    ['Gms/dL', 'g/dl'],
    ['mg %', 'mg/dl'],
    ['µmol/L', 'umol/l'],
    ['μmol/l', 'umol/l'],
    ['/cumm', '/ul'],
    ['cells/mm³', '/ul'],
    ['x10*9/L', '10^3/ul'],
    ['Thou/cumm', '10^3/ul'],
    ['IU/L', 'u/l'],
    ['U/L', 'u/l'],
    ['µIU/mL', 'miu/l'],
    ['mIU/L', 'miu/l'],
    ['pg/mL', 'ng/l'],
    ['mcg/L', 'ng/ml'],
    ['ng/mL', 'ng/ml'],
    ['mcg/dL', 'ug/dl'],
  ])('%s → %s', (unit, expected) => {
    expect(normalizeUnit(unit)).toBe(expected)
  })
})

describe('toUnit', () => {
  const close = (value: number | null, expected: number) => expect(value).toBeCloseTo(expected, 2)

  it('treats spellings of one unit as the same unit for any test', () => {
    expect(toUnit('anything', 12.1, 'gm/dl', 'g/dL')).toBe(12.1)
    expect(toUnit('wbc', 7400, '/cumm', 'cells/µL')).toBe(7400)
  })

  it('applies standard conversions both ways for known tests', () => {
    close(toUnit('glucose_fasting', 90, 'mg/dL', 'mmol/L'), 4.996)
    close(toUnit('glucose_random', 5, 'mmol/L', 'mg/dL'), 90.08)
    close(toUnit('ldl', 3, 'mmol/L', 'mg/dl'), 116.01)
    close(toUnit('triglycerides', 150, 'mg/dL', 'mmol/L'), 1.694)
    close(toUnit('creatinine', 1, 'mg/dL', 'µmol/L'), 88.42)
    close(toUnit('haemoglobin', 135, 'g/L', 'g/dL'), 13.5)
    close(toUnit('vitamin_d', 30, 'ng/mL', 'nmol/L'), 74.88)
    close(toUnit('hba1c', 6.5, '%', 'mmol/mol'), 47.52)
    close(toUnit('hba1c', 48, 'mmol/mol', '%'), 6.544)
  })

  it('treats units that are equal by definition as the same unit', () => {
    expect(toUnit('tsh', 2.1, 'µIU/mL', 'mIU/L')).toBe(2.1)
    expect(toUnit('alt', 34, 'IU/L', 'U/L')).toBe(34)
    expect(toUnit('vitamin_b12', 410, 'pg/mL', 'ng/L')).toBe(410)
    expect(toUnit('ferritin', 80, 'mcg/L', 'ng/mL')).toBe(80)
    expect(toUnit('sodium', 140, 'mEq/L', 'mmol/L')).toBe(140)
  })

  it('never guesses: unknown tests, unknown pairs and missing units give null', () => {
    expect(toUnit('tsh', 2, 'uIU/mL', 'ng/dL')).toBeNull()
    expect(toUnit('calcium', 4.8, 'mEq/L', 'mmol/L')).toBeNull()
    expect(toUnit('glucose_fasting', 90, 'mg/dL', 'g/L')).toBeNull()
    expect(toUnit('haemoglobin', 90, 'mg/dL', 'mmol/L')).toBeNull()
    expect(toUnit('glucose_fasting', 90, null, 'mmol/L')).toBeNull()
  })
})
