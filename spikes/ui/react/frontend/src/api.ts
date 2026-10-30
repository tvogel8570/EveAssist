// The shapes the Java API returns (PlanCosting.CostedPlan and CostedNode), written again in TypeScript.
// In a real build these would be generated from the API so the two cannot drift apart.

export type Decision = 'BUILD' | 'BUY'

export interface Facility {
  id: string
  name: string
  materialBonus: number
  taxRate: number
}

export interface CostedNode {
  path: string
  depth: number
  itemId: string
  name: string
  quantity: number
  buildable: boolean
  buyOnly: boolean
  decision: Decision
  overridden: boolean
  facilityId: string | null
  buyCost: number
  buildCost: number | null
  chosenCost: number
  children: CostedNode[]
}

export interface CostedPlan {
  quantity: number
  root: CostedNode
  totalCost: number
  productValue: number
  profit: number
  marginPercent: number
  facilities: Facility[]
}

async function post(url: string, body?: unknown): Promise<CostedPlan> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) throw new Error(`${url} failed: ${response.status}`)
  return response.json()
}

export const api = {
  async plan(): Promise<CostedPlan> {
    const response = await fetch('/api/plan')
    if (!response.ok) throw new Error(`Loading the plan failed: ${response.status}`)
    return response.json()
  },
  decide: (path: string, decision: Decision) => post('/api/plan/decision', { path, decision }),
  facility: (path: string, facility: string) => post('/api/plan/facility', { path, facility }),
  quantity: (quantity: number) => post('/api/plan/quantity', { quantity }),
  reset: () => post('/api/plan/reset'),
}

/** Formats ISK the way players read it: 1.23B, 45.6M, 7.8k. Same rules as Isk.java. */
export function isk(value: number): string {
  const abs = Math.abs(value)
  const sign = value < 0 ? '-' : ''
  if (abs >= 1e9) return `${sign}${(abs / 1e9).toFixed(2)}B`
  if (abs >= 1e6) return `${sign}${(abs / 1e6).toFixed(1)}M`
  if (abs >= 1e3) return `${sign}${(abs / 1e3).toFixed(1)}k`
  return `${sign}${abs.toFixed(0)}`
}
