import {
  ArcElement, BarElement, CategoryScale, Chart, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip,
} from 'chart.js'

Chart.register(ArcElement, BarElement, CategoryScale, Filler, Legend, LinearScale, LineElement, PointElement, Tooltip)

Chart.defaults.font.family = "'Inter', system-ui, sans-serif"
Chart.defaults.font.size = 12
Chart.defaults.color = '#6b6f66'
Chart.defaults.plugins.legend.labels.usePointStyle = true
Chart.defaults.plugins.legend.labels.boxWidth = 8
Chart.defaults.plugins.tooltip.backgroundColor = '#0e0f0c'
Chart.defaults.plugins.tooltip.padding = 10
Chart.defaults.plugins.tooltip.cornerRadius = 10
Chart.defaults.plugins.tooltip.titleFont = { weight: 600 }

export const palette = {
  ink: '#0e0f0c',
  lime: '#c8f169',
  moss: '#43511c',
  sage: '#bcc388',
  coral: '#e5484d',
  amber: '#f2b33d',
  lavender: '#9c98e0',
  sky: '#6aa7e0',
  grid: 'rgba(14, 15, 12, 0.06)',
}

export const categoryColors: Record<string, string> = {
  malaria_test: palette.ink,
  antimalarial: palette.moss,
  family_planning: palette.lime,
  ors_zinc: palette.sage,
  other: palette.lavender,
}

export const axis = {
  grid: { color: palette.grid, drawTicks: false },
  border: { display: false },
  ticks: { padding: 8 },
}
