import { useState, useCallback } from 'react'
import './App.css'

const CHEMBL_BASE = 'https://www.ebi.ac.uk/chembl/api/data'
const PAGE_SIZE = 20

// ── Helpers ──────────────────────────────────────────────────────────────────

function safeNum(v) {
  if (v == null || v === '' || v === 'None') return null
  const n = parseFloat(v)
  return isNaN(n) ? null : n
}

function fmtValue(v) {
  const n = safeNum(v)
  if (n == null) return null
  if (n < 0.01 || n >= 100000) return n.toExponential(2)
  return parseFloat(n.toFixed(2)).toString()
}

function badgeClass(type) {
  if (!type) return 'badge badge-other'
  const t = type.toUpperCase()
  if (t === 'IC50') return 'badge badge-ic50'
  if (t === 'KI') return 'badge badge-ki'
  if (t === 'EC50') return 'badge badge-ec50'
  return 'badge badge-other'
}

function computeSummary(records) {
  if (!records.length) {
    return { total: 0, uniqueCompounds: 0, mostCommon: 'N/A', top5: [], typeCounts: {} }
  }

  const total = records.length

  const compounds = new Set(
    records.map(r => r.molecule_chembl_id).filter(Boolean)
  )

  const typeCounts = {}
  records.forEach(r => {
    if (r.standard_type) {
      typeCounts[r.standard_type] = (typeCounts[r.standard_type] || 0) + 1
    }
  })
  const mostCommon = Object.entries(typeCounts)
    .sort((a, b) => b[1] - a[1])[0]?.[0] || 'N/A'

  // Best (lowest) value per compound
  const bestPerCompound = {}
  records.forEach(r => {
    const id = r.molecule_chembl_id
    const v = safeNum(r.standard_value)
    if (!id || v == null) return
    if (!bestPerCompound[id] || v < bestPerCompound[id].v) {
      bestPerCompound[id] = { v, r }
    }
  })
  const top5 = Object.values(bestPerCompound)
    .sort((a, b) => a.v - b.v)
    .slice(0, 5)
    .map(x => x.r)

  return { total, uniqueCompounds: compounds.size, mostCommon, top5, typeCounts }
}

async function fetchAll(targetId, filters) {
  const params = new URLSearchParams({
    target_chembl_id: targetId.toUpperCase(),
    limit: 1000,
    offset: 0,
  })
  if (filters.activityType) params.set('standard_type', filters.activityType)
  if (filters.assayType)    params.set('assay_type', filters.assayType)
  if (filters.organism)     params.set('target_organism', filters.organism)

  let all = []
  let offset = 0
  let total = Infinity

  while (offset < total) {
    params.set('offset', offset)
    const res = await fetch(`${CHEMBL_BASE}/activity.json?${params}`)

    if (!res.ok) {
      if (res.status === 404)
        throw new Error('Target not found in ChEMBL. Verify the ID (e.g. CHEMBL203).')
      throw new Error(`ChEMBL API error: HTTP ${res.status}`)
    }

    const data = await res.json()
    const acts = data.activities || []
    total = data.page_meta?.total_count ?? 0
    all = [...all, ...acts]
    offset += 1000

    // Safety cap: stop at 3000 records
    if (acts.length === 0 || all.length >= total || all.length >= 3000) break
  }

  return all
}

// ── Components ────────────────────────────────────────────────────────────────

function MetricCard({ label, value, sub }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {sub && <div className="metric-sub">{sub}</div>}
    </div>
  )
}

function TopCompounds({ compounds }) {
  if (!compounds.length) return null
  return (
    <section className="section">
      <h2 className="section-title">Top 5 Strongest Compounds</h2>
      <p className="section-hint">Ranked by lowest activity value (lower = stronger potency)</p>
      <div className="top5-grid">
        {compounds.map((r, i) => (
          <div key={i} className="compound-card">
            <div className="compound-rank">#{i + 1} strongest</div>
            <div className="compound-id" title={r.molecule_chembl_id}>
              {r.molecule_chembl_id || '—'}
            </div>
            <div className="compound-value">
              {fmtValue(r.standard_value) != null ? (
                <>
                  {fmtValue(r.standard_value)}
                  <span className="compound-units"> {r.standard_units || ''}</span>
                </>
              ) : (
                <span className="null-val">no value</span>
              )}
            </div>
            <div className="compound-type">{r.standard_type || '—'}</div>
          </div>
        ))}
      </div>
    </section>
  )
}

function ActivityTable({ records, page, setPage, totalPages }) {
  const from = page * PAGE_SIZE + 1
  const to = Math.min((page + 1) * PAGE_SIZE, records.length)

  return (
    <section className="section">
      <h2 className="section-title">Activity Records</h2>
      <p className="result-count">
        Showing {from}–{to} of {records.length.toLocaleString()} records
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Compound ID</th>
              <th>Activity Type</th>
              <th>Value</th>
              <th>Units</th>
              <th>Assay ID</th>
              <th>Assay Type</th>
              <th>Organism</th>
            </tr>
          </thead>
          <tbody>
            {records.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE).map((r, i) => (
              <tr key={r.activity_id || i}>
                <td className="mono">{r.molecule_chembl_id || <span className="null-val">—</span>}</td>
                <td>
                  {r.standard_type
                    ? <span className={badgeClass(r.standard_type)}>{r.standard_type}</span>
                    : <span className="null-val">—</span>}
                </td>
                <td>
                  {safeNum(r.standard_value) != null
                    ? `${r.standard_relation && r.standard_relation !== '=' ? r.standard_relation + ' ' : ''}${fmtValue(r.standard_value)}`
                    : <span className="null-val">—</span>}
                </td>
                <td>{r.standard_units || <span className="null-val">—</span>}</td>
                <td className="mono small">{r.assay_chembl_id || <span className="null-val">—</span>}</td>
                <td>{r.assay_type || <span className="null-val">—</span>}</td>
                <td className="small">{r.target_organism || <span className="null-val">—</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="pagination">
          <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}>
            ← Prev
          </button>
          <span>Page {page + 1} of {totalPages}</span>
          <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))} disabled={page === totalPages - 1}>
            Next →
          </button>
        </div>
      )}
    </section>
  )
}

// ── Main App ──────────────────────────────────────────────────────────────────

export default function App() {
  const [targetInput, setTargetInput]   = useState('CHEMBL203')
  const [filters, setFilters]           = useState({ activityType: 'IC50', assayType: '', organism: '' })
  const [records, setRecords]           = useState(null)
  const [summary, setSummary]           = useState(null)
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState(null)
  const [page, setPage]                 = useState(0)
  const [searched, setSearched]         = useState(false)

  const setFilter = (key, val) => setFilters(f => ({ ...f, [key]: val }))

  const search = useCallback(async () => {
    const tid = targetInput.trim().toUpperCase()
    if (!tid) { setError('Please enter a Target ID.'); return }
    if (!/^CHEMBL\d+$/i.test(tid)) {
      setError('Invalid format. Use CHEMBL followed by digits, e.g. CHEMBL203.')
      return
    }
    setLoading(true)
    setError(null)
    setRecords(null)
    setSummary(null)
    setPage(0)
    setSearched(true)

    try {
      const all = await fetchAll(tid, filters)
      setRecords(all)
      setSummary(computeSummary(all))
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [targetInput, filters])

  const totalPages = records ? Math.ceil(records.length / PAGE_SIZE) : 0

  return (
    <div className="layout">
      {/* ── Header ── */}
      <header className="app-header">
        <h1>🧬 ChEMBL Target Activity Explorer</h1>
        <p>Search experimentally validated bioactivity data for any ChEMBL target</p>
      </header>

      <main className="app-main">
        {/* ── Search & Filters ── */}
        <div className="search-panel">
          <div className="search-row">
            <input
              type="text"
              placeholder="ChEMBL Target ID, e.g. CHEMBL203"
              value={targetInput}
              onChange={e => setTargetInput(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && search()}
              aria-label="ChEMBL Target ID"
            />
            <button onClick={search} disabled={loading} className="btn-primary">
              {loading ? 'Searching…' : 'Search'}
            </button>
          </div>

          <div className="filters-row">
            <div className="filter-group">
              <label>Activity Type</label>
              <select value={filters.activityType} onChange={e => setFilter('activityType', e.target.value)}>
                <option value="">All types</option>
                <option value="IC50">IC50</option>
                <option value="Ki">Ki</option>
                <option value="EC50">EC50</option>
                <option value="Kd">Kd</option>
                <option value="AC50">AC50</option>
                <option value="MIC">MIC</option>
                <option value="GI50">GI50</option>
              </select>
            </div>
            <div className="filter-group">
              <label>Assay Type</label>
              <select value={filters.assayType} onChange={e => setFilter('assayType', e.target.value)}>
                <option value="">All assays</option>
                <option value="B">Binding (B)</option>
                <option value="F">Functional (F)</option>
                <option value="A">ADME (A)</option>
                <option value="T">Toxicity (T)</option>
              </select>
            </div>
            <div className="filter-group">
              <label>Organism</label>
              <select value={filters.organism} onChange={e => setFilter('organism', e.target.value)}>
                <option value="">All organisms</option>
                <option value="Homo sapiens">Homo sapiens</option>
                <option value="Rattus norvegicus">Rattus norvegicus</option>
                <option value="Mus musculus">Mus musculus</option>
              </select>
            </div>
          </div>
        </div>

        {/* ── Error ── */}
        {error && (
          <div className="error-box" role="alert">
            ⚠️ {error}
          </div>
        )}

        {/* ── Loading ── */}
        {loading && (
          <div className="loading-box">
            <div className="spinner" />
            <span>Querying ChEMBL database…</span>
          </div>
        )}

        {/* ── Empty initial state ── */}
        {!loading && !searched && (
          <div className="empty-state">
            <div className="empty-icon">🔬</div>
            <p className="empty-title">Enter a ChEMBL Target ID to begin</p>
            <p className="empty-hint">Try <strong>CHEMBL203</strong> (EGFR kinase) or <strong>CHEMBL301</strong> (Estrogen Receptor)</p>
          </div>
        )}

        {/* ── No results ── */}
        {!loading && searched && records && records.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">🔍</div>
            <p className="empty-title">No records found</p>
            <p className="empty-hint">Try removing filters or check that the Target ID exists in ChEMBL</p>
          </div>
        )}

        {/* ── Results ── */}
        {!loading && summary && records && records.length > 0 && (
          <>
            {/* Summary Cards */}
            <section className="section">
              <h2 className="section-title">Summary</h2>
              <div className="metrics-grid">
                <MetricCard
                  label="Total Records"
                  value={summary.total.toLocaleString()}
                  sub="matching activities"
                />
                <MetricCard
                  label="Unique Compounds"
                  value={summary.uniqueCompounds.toLocaleString()}
                  sub="distinct molecules"
                />
                <MetricCard
                  label="Most Common Type"
                  value={summary.mostCommon}
                  sub={`${(summary.typeCounts[summary.mostCommon] || 0).toLocaleString()} records`}
                />
                <MetricCard
                  label="Total Pages"
                  value={totalPages}
                  sub={`${PAGE_SIZE} records per page`}
                />
              </div>
            </section>

            <TopCompounds compounds={summary.top5} />

            <ActivityTable
              records={records}
              page={page}
              setPage={setPage}
              totalPages={totalPages}
            />
          </>
        )}
      </main>

      <footer className="app-footer">
        Data sourced from the <a href="https://www.ebi.ac.uk/chembl/" target="_blank" rel="noreferrer">ChEMBL database</a> (EMBL-EBI)
      </footer>
    </div>
  )
}
