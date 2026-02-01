import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatusPill from './StatusPill.vue'
import RiskMeter from './RiskMeter.vue'
import FlagBadge from './FlagBadge.vue'

describe('StatusPill', () => {
  it('renders a label, not just a colour', () => {
    const w = mount(StatusPill, { props: { status: 'under_review' } })
    expect(w.text()).toBe('Under review')
    expect(w.find('svg').exists()).toBe(true)
  })
})

describe('RiskMeter', () => {
  it('fills segments proportional to the score and exposes it to screen readers', () => {
    const w = mount(RiskMeter, { props: { score: 72 } })
    expect(w.findAll('.on')).toHaveLength(7)
    expect(w.attributes('aria-valuenow')).toBe('72')
    expect(w.classes()).toContain('high')
  })
})

describe('FlagBadge', () => {
  it('shows a friendly rule name', () => {
    const w = mount(FlagBadge, { props: { flag: { rule: 'no_matching_sale', severity: 'high', message: 'No sale' } } })
    expect(w.text()).toContain('No matching sale')
    expect(w.classes()).toContain('high')
  })
})
