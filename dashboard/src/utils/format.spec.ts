import { describe, expect, it } from 'vitest'
import { humanize, money, moneyCompact, percent, relative } from './format'

describe('format', () => {
  it('formats KES from cents', () => {
    expect(money(1_250_000)).toBe('KES 12,500')
    expect(moneyCompact(125_000_000)).toBe('KES 1.3M')
  })

  it('formats percentages', () => {
    expect(percent(0.1234)).toBe('12.3%')
  })

  it('humanizes snake_case', () => {
    expect(humanize('under_review')).toBe('Under review')
  })

  it('describes relative time', () => {
    const now = new Date('2026-05-04T12:00:00Z').getTime()
    expect(relative('2026-05-04T11:59:30Z', now)).toBe('just now')
    expect(relative('2026-05-04T09:00:00Z', now)).toBe('3h ago')
    expect(relative('2026-05-03T09:00:00Z', now)).toBe('yesterday')
  })
})
