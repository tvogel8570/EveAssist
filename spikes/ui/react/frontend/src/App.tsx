import { useEffect, useState } from 'react'
import { api, isk, type CostedNode, type CostedPlan, type Decision, type Facility } from './api'

export default function App() {
  const [plan, setPlan] = useState<CostedPlan | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.plan().then(setPlan).catch((e) => setError(String(e)))
  }, [])

  // Every change goes to the server, which recalculates the whole plan and sends it back.
  async function change(request: () => Promise<CostedPlan>) {
    setBusy(true)
    try {
      setPlan(await request())
      setError(null)
    } catch (e) {
      setError(String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="navbar bg-base-100 shadow-sm px-4">
        <span className="text-lg font-semibold">EveAssist</span>
        <span className="ml-3 badge badge-outline">Spike B: React</span>
        {busy && <span className="loading loading-spinner loading-sm ml-auto" />}
      </div>
      <main className="mx-auto max-w-6xl p-4 space-y-4">
        {error && <div className="alert alert-error">{error}</div>}
        {plan && <Editor plan={plan} change={change} />}
      </main>
    </>
  )
}

interface EditorProps {
  plan: CostedPlan
  change: (request: () => Promise<CostedPlan>) => Promise<void>
}

function Editor({ plan, change }: EditorProps) {
  // The quantity field keeps its own text while the user types; it is sent when they leave the field or press Enter.
  const [quantity, setQuantity] = useState(String(plan.quantity))
  useEffect(() => setQuantity(String(plan.quantity)), [plan.quantity])

  const sendQuantity = () => {
    const value = Number(quantity)
    if (Number.isInteger(value) && value !== plan.quantity) change(() => api.quantity(value))
  }

  return (
    <>
      <div className="stats stats-vertical sm:stats-horizontal w-full bg-base-100 shadow">
        <Stat title="Total cost" value={isk(plan.totalCost)} />
        <Stat title="Value at Jita sell" value={isk(plan.productValue)} />
        <Stat
          title="Profit"
          value={isk(plan.profit)}
          valueClass={plan.profit >= 0 ? 'text-success' : 'text-error'}
          desc={`${plan.marginPercent.toFixed(1)} % margin`}
        />
      </div>

      <div className="card bg-base-100 shadow">
        <div className="card-body gap-4">
          <div className="flex flex-wrap items-end gap-3">
            <label className="form-control">
              <span className="label-text mb-1">Ishtars to build</span>
              <input
                type="number" min={1} max={1000} className="input input-bordered w-32"
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
                onBlur={sendQuantity}
                onKeyDown={(e) => e.key === 'Enter' && sendQuantity()}
              />
            </label>
            <button className="btn btn-ghost" onClick={() => change(api.reset)}>Reset choices</button>
            <span className="text-sm opacity-70">
              Intermediates follow "build if cheaper" unless you override them.
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="table table-sm">
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="text-right">Quantity</th>
                  <th>Build or buy</th>
                  <th>Facility</th>
                  <th className="text-right hidden md:table-cell">Buy</th>
                  <th className="text-right hidden md:table-cell">Build</th>
                  <th className="text-right">Cost</th>
                </tr>
              </thead>
              <tbody>
                <Rows node={plan.root} facilities={plan.facilities} change={change} />
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </>
  )
}

function Stat({ title, value, valueClass = '', desc }:
                { title: string, value: string, valueClass?: string, desc?: string }) {
  return (
    <div className="stat">
      <div className="stat-title">{title}</div>
      <div className={`stat-value text-2xl ${valueClass}`}>{value}</div>
      {desc && <div className="stat-desc">{desc}</div>}
    </div>
  )
}

interface RowsProps {
  node: CostedNode
  facilities: Facility[]
  change: (request: () => Promise<CostedPlan>) => Promise<void>
}

/** One table row for the node, then the rows of its children. */
function Rows({ node, facilities, change }: RowsProps) {
  const decide = (decision: Decision) => change(() => api.decide(node.path, decision))
  const cheaperIsBuild = node.buildCost !== null && node.buildCost < node.buyCost
  const extraCost = node.buildCost === null ? 0
    : node.decision === 'BUILD' ? node.buildCost - node.buyCost : node.buyCost - node.buildCost

  return (
    <>
      <tr className="hover">
        <td style={{ paddingLeft: `${0.75 + node.depth * 1.5}rem` }}>
          {node.name}
          {node.buyOnly && <span className="badge badge-ghost badge-sm ml-1">reaction, buy only</span>}
          {node.overridden && <span className="badge badge-warning badge-sm ml-1">override</span>}
        </td>
        <td className="text-right tabular-nums">{node.quantity.toLocaleString('en-US')}</td>
        <td>
          {node.depth === 0 && <span className="badge badge-primary badge-sm">product</span>}
          {node.depth > 0 && node.buildable && (
            <div className="join">
              {(['BUILD', 'BUY'] as const).map((d) => (
                <button key={d} className={`btn btn-xs join-item ${node.decision === d ? 'btn-primary' : ''}`}
                        onClick={() => decide(d)}>
                  {d === 'BUILD' ? 'Build' : 'Buy'}
                </button>
              ))}
            </div>
          )}
          {node.depth > 0 && !node.buildable && <span className="text-xs opacity-60">buy</span>}
        </td>
        <td>
          {node.buildable && node.decision === 'BUILD' && (
            <select className="select select-xs select-bordered" value={node.facilityId ?? ''}
                    onChange={(e) => change(() => api.facility(node.path, e.target.value))}>
              {facilities.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
            </select>
          )}
        </td>
        <td className={`text-right tabular-nums hidden md:table-cell ${node.buildCost !== null && !cheaperIsBuild ? 'text-success' : ''}`}>
          {isk(node.buyCost)}
        </td>
        <td className={`text-right tabular-nums hidden md:table-cell ${cheaperIsBuild ? 'text-success' : ''}`}>
          {node.buildCost === null ? '—' : isk(node.buildCost)}
        </td>
        <td className="text-right tabular-nums font-semibold">
          {isk(node.chosenCost)}
          {node.overridden && extraCost > 0 && (
            <div className="text-xs font-normal text-error">+{isk(extraCost)} vs. rule</div>
          )}
        </td>
      </tr>
      {node.children.map((c) => <Rows key={c.path} node={c} facilities={facilities} change={change} />)}
    </>
  )
}
