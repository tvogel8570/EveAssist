import { useEffect, useState } from 'react'
import {
  DndContext, DragOverlay, PointerSensor, useDraggable, useDroppable, useSensor, useSensors,
  type DragEndEvent, type DragOverEvent, type DragStartEvent,
} from '@dnd-kit/core'

// The shapes JobBoard.java returns, written again in TypeScript.
interface JobView { id: string, product: string, runs: number, baseSeconds: number, seconds: number, duration: string }
interface Column {
  pilotId: string | null, name: string, slots: number | null, used: number, timeBonus: number, jobs: JobView[]
}
interface BoardView { columns: Column[], message: string | null }

const UNASSIGNED = 'unassigned'
const columnKey = (c: Column) => c.pilotId ?? UNASSIGNED
const isFull = (c: Column) => c.slots !== null && c.used >= c.slots

/** Same rules as JobBoard.duration in Java. */
function duration(seconds: number): string {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (days > 0) return `${days}d ${hours}h`
  if (hours > 0) return `${hours}h ${minutes}m`
  return `${minutes}m`
}

const secondsFor = (job: JobView, column: Column) => Math.round(job.baseSeconds * (1 - column.timeBonus))

/** Moves a job between columns in the browser, before the server has answered. */
function moveLocally(board: BoardView, jobId: string, target: string): BoardView {
  const job = board.columns.flatMap((c) => c.jobs).find((j) => j.id === jobId)!
  const columns = board.columns.map((c) => {
    const without = c.jobs.filter((j) => j.id !== jobId)
    if (columnKey(c) !== target) return { ...c, jobs: without, used: c.pilotId ? without.length : 0 }
    const seconds = secondsFor(job, c)
    const jobs = [...without, { ...job, seconds, duration: duration(seconds) }]
    return { ...c, jobs, used: c.pilotId ? jobs.length : 0 }
  })
  return { columns, message: null }
}

async function post(url: string, body?: unknown): Promise<BoardView> {
  const response = await fetch(url, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (response.status === 409) throw new Error((await response.json()).message)
  if (!response.ok) throw new Error(`${url} failed: ${response.status}`)
  return response.json()
}

export default function JobBoard() {
  const [board, setBoard] = useState<BoardView | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [dragged, setDragged] = useState<JobView | null>(null)
  const [over, setOver] = useState<Column | null>(null)
  // A small movement threshold, so a click on a card is not taken as a drag.
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 5 } }))

  useEffect(() => {
    fetch('/api/jobs').then((r) => r.json()).then(setBoard).catch((e) => setMessage(String(e)))
  }, [])

  if (!board) return message ? <div className="alert alert-error">{message}</div> : null

  const findColumn = (key: string) => board.columns.find((c) => columnKey(c) === key)!

  const onDragStart = (e: DragStartEvent) => {
    setDragged(board.columns.flatMap((c) => c.jobs).find((j) => j.id === e.active.id) ?? null)
    setMessage(null)
  }
  const onDragOver = (e: DragOverEvent) => setOver(e.over ? findColumn(String(e.over.id)) : null)

  const onDragEnd = async (e: DragEndEvent) => {
    setDragged(null)
    setOver(null)
    if (!e.over) return
    const jobId = String(e.active.id)
    const target = findColumn(String(e.over.id))
    const source = board.columns.find((c) => c.jobs.some((j) => j.id === jobId))!
    if (columnKey(source) === columnKey(target)) return
    if (target.pilotId && isFull(target)) {
      setMessage(`${target.name} has no free manufacturing slot.`)
      return
    }
    // Optimistic update: show the move now, then let the server confirm or refuse it.
    const before = board
    setBoard(moveLocally(board, jobId, columnKey(target)))
    try {
      setBoard(await post('/api/jobs/move', { job: jobId, pilot: target.pilotId }))
    } catch (err) {
      setBoard(before)
      setMessage(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <span className="text-sm opacity-70">
          Drag jobs between columns. While you drag, each card shows how long it would take for that character.
        </span>
        <button className="btn btn-ghost btn-sm ml-auto"
                onClick={async () => { setBoard(await post('/api/jobs/reset')); setMessage(null) }}>
          Reset board
        </button>
      </div>
      {message && <div role="alert" className="alert alert-warning py-2">{message}</div>}

      <DndContext sensors={sensors} onDragStart={onDragStart} onDragOver={onDragOver} onDragEnd={onDragEnd}
                  onDragCancel={() => { setDragged(null); setOver(null) }}>
        <div className="grid gap-4 grid-cols-1 sm:grid-cols-2 lg:grid-cols-4">
          {board.columns.map((c) => (
            <ColumnCard key={columnKey(c)} column={c} dragging={dragged !== null}
                        preview={dragged && over && columnKey(over) === columnKey(c) ? dragged : null} />
          ))}
        </div>
        {/* The card under the pointer; it shows the duration for the column it is over. */}
        <DragOverlay>
          {dragged && <JobCard job={dragged} durationText={over ? duration(secondsFor(dragged, over)) : dragged.duration}
                               highlight={over !== null && over.pilotId !== null && isFull(over) ? 'error' : 'primary'} />}
        </DragOverlay>
      </DndContext>
    </div>
  )
}

function ColumnCard({ column, dragging, preview }: { column: Column, dragging: boolean, preview: JobView | null }) {
  const { setNodeRef, isOver } = useDroppable({ id: columnKey(column) })
  const full = isFull(column)
  // While a job hovers over this column, the slot count shows what it would become.
  const usedPreview = preview && column.pilotId && !column.jobs.some((j) => j.id === preview.id)
    ? column.used + 1 : column.used
  const outline = !dragging ? '' : full && column.pilotId ? 'outline-2 outline-dashed outline-error'
    : isOver ? 'outline-2 outline-primary' : 'outline-2 outline-dashed outline-primary/40'

  return (
    <div className="card bg-base-100 shadow">
      <div className="card-body p-3 gap-2">
        <div className="flex items-baseline justify-between">
          <h2 className="font-semibold">{column.name}</h2>
          {column.slots !== null && (
            <span className={`badge badge-sm ${usedPreview > column.slots ? 'badge-error'
              : usedPreview !== column.used ? 'badge-primary' : full ? 'badge-error' : 'badge-ghost'}`}>
              {usedPreview} / {column.slots} slots
            </span>
          )}
        </div>
        {column.slots !== null && (
          <div className="text-xs opacity-60">{Math.round(column.timeBonus * 100)} % faster from skills</div>
        )}
        <ul ref={setNodeRef} className={`min-h-24 flex-1 space-y-2 rounded-box p-1 ${outline}`}>
          {column.jobs.map((j) => <DraggableJob key={j.id} job={j} />)}
        </ul>
      </div>
    </div>
  )
}

function DraggableJob({ job }: { job: JobView }) {
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({ id: job.id })
  return (
    <li ref={setNodeRef} {...listeners} {...attributes} className={isDragging ? 'opacity-40' : ''}>
      <JobCard job={job} durationText={job.duration} />
    </li>
  )
}

function JobCard({ job, durationText, highlight }:
                   { job: JobView, durationText: string, highlight?: 'primary' | 'error' }) {
  const ring = highlight === 'error' ? 'ring-2 ring-error' : highlight === 'primary' ? 'ring-2 ring-primary' : ''
  return (
    <div className={`cursor-grab rounded-box bg-base-200 p-2 text-sm shadow-sm ${ring}`}>
      <div className="font-medium">{job.product}</div>
      <div className="flex justify-between text-xs opacity-70">
        <span>{job.runs} runs</span>
        <span>{durationText}</span>
      </div>
    </div>
  )
}
