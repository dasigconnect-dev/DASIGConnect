import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  createDraft,
  deleteDraft,
  submitForReview,
  updateDraft,
  uploadSubmissionMedia,
  validateGuardRails,
  type GuardRailResult,
  type SubmissionPayload,
  type SubmissionStatus,
  type SubmissionSummary,
} from '../../api/submissionApi'
import { useSubmissionLookups, useSubmissions } from '../../hooks/useSubmissions'
import type { User } from '../../types/auth.types'

interface SubmissionScreenProps {
  user: User
}

interface FormState {
  id: string | null
  eventTitle: string
  eventDate: string
  caption: string
  description: string
  category: string
  scheduledDate: string
  scheduledTime: string
  tags: string[]
  files: File[]
}

type QueueFilter = 'drafts' | 'submitted' | 'all'
type ModalState = 'submit' | 'success' | 'delete' | null
type SaveState = 'idle' | 'saving' | 'saved'

const initialForm: FormState = {
  id: null,
  eventTitle: '',
  eventDate: '',
  caption: '',
  description: '',
  category: '',
  scheduledDate: '',
  scheduledTime: '',
  tags: [],
  files: [],
}

const statusLabels: Record<SubmissionStatus, string> = {
  draft: 'Draft',
  pending: 'Submitted',
  in_review: 'Under Review',
  needs_revision: 'Needs Revision',
  scheduled: 'Scheduled',
  publish_failed: 'Publish Failed',
  published: 'Published',
  published_manual: 'Published',
  admin_direct_post: 'Direct Post',
  rejected: 'Rejected',
}

export default function SubmissionScreen({ user }: SubmissionScreenProps) {
  const navigate = useNavigate()
  const { submissions, setSubmissions, loading, error, refresh } = useSubmissions()
  const { lookups, loading: lookupsLoading, error: lookupsError } = useSubmissionLookups()
  const [filter, setFilter] = useState<QueueFilter>('drafts')
  const [selectedFileIndex, setSelectedFileIndex] = useState(0)
  const [form, setForm] = useState<FormState>(initialForm)
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [toast, setToast] = useState<{ message: string; type: 'success' | 'info' | 'err' | 'warn' } | null>(null)
  const [modal, setModal] = useState<ModalState>(null)
  const [submitting, setSubmitting] = useState(false)
  const [guardRails, setGuardRails] = useState<GuardRailResult | null>(null)
  const [guardRailError, setGuardRailError] = useState('')

  const queued = useMemo(() => {
    if (filter === 'drafts') return submissions.filter((item) => item.status === 'draft')
    if (filter === 'submitted') return submissions.filter((item) => item.status !== 'draft')
    return submissions
  }, [filter, submissions])

  const draftCount = useMemo(
    () => submissions.filter((item) => item.status === 'draft').length,
    [submissions],
  )

  const scheduledAt = useMemo(() => {
    if (!form.scheduledDate || !form.scheduledTime) return undefined
    const date = new Date(`${form.scheduledDate}T${form.scheduledTime}`)
    if (Number.isNaN(date.getTime())) return undefined
    return date.toISOString()
  }, [form.scheduledDate, form.scheduledTime])

  const readiness = useMemo(() => calculateReadiness(form, guardRails), [form, guardRails])
  const previewCaption = form.caption.trim() || 'Your caption preview will appear here as you write.'

  useEffect(() => {
    if (!scheduledAt || !form.id) {
      setGuardRails(null)
      setGuardRailError('')
      return
    }

    const timer = window.setTimeout(() => {
      validateGuardRails(form.id!, scheduledAt)
        .then((response) => {
          setGuardRails(response.data)
          setGuardRailError('')
        })
        .catch((err: any) => {
          setGuardRails(null)
          setGuardRailError(err.response?.data?.error || err.message || 'Slot validation is unavailable.')
        })
    }, 350)

    return () => window.clearTimeout(timer)
  }, [scheduledAt, form.id])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 2800)
    return () => window.clearTimeout(timer)
  }, [toast])

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }))
    setSaveState('idle')
  }

  function applySubmission(submission: SubmissionSummary) {
    setForm({
      id: submission.id,
      eventTitle: submission.eventTitle || '',
      eventDate: submission.eventDate || '',
      caption: submission.caption || '',
      description: submission.description || '',
      category: '',
      scheduledDate: submission.scheduledAt ? submission.scheduledAt.slice(0, 10) : '',
      scheduledTime: submission.scheduledAt ? formatTimeInput(submission.scheduledAt) : '',
      tags: [],
      files: [],
    })
    setToast({ message: 'Submission loaded', type: 'info' })
  }

  async function handleSave() {
    setSaveState('saving')
    try {
      const payload = toPayload(form, scheduledAt)
      const response = form.id ? await updateDraft(form.id, payload) : await createDraft(payload)
      if (form.files.length > 0) {
        await uploadSubmissionMedia(response.data.id, form.files)
      }
      setForm((current) => ({ ...current, id: response.data.id, files: [] }))
      setSubmissions((current) => upsertSubmission(current, response.data))
      setSaveState('saved')
      setToast({ message: 'Draft saved successfully', type: 'success' })
    } catch (err: any) {
      setSaveState('idle')
      setToast({ message: err.response?.data?.error || err.message || 'Draft could not be saved.', type: 'err' })
    }
  }

  async function handleSubmit() {
    setSubmitting(true)
    try {
      const payload = toPayload(form, scheduledAt)
      const draft = form.id ? await updateDraft(form.id, payload) : await createDraft(payload)
      if (form.files.length > 0) {
        await uploadSubmissionMedia(draft.data.id, form.files)
      }
      const submitted = await submitForReview(draft.data.id)
      setSubmissions((current) => upsertSubmission(current, submitted.data))
      setForm((current) => ({ ...current, id: submitted.data.id, files: [] }))
      setModal('success')
      setToast({ message: 'Submission sent for review', type: 'success' })
      void refresh()
    } catch (err: any) {
      setToast({ message: err.response?.data?.error || err.message || 'Submission failed.', type: 'err' })
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete() {
    if (!form.id) {
      setForm(initialForm)
      setModal(null)
      return
    }

    try {
      await deleteDraft(form.id)
      setSubmissions((current) => current.filter((item) => item.id !== form.id))
      setForm(initialForm)
      setModal(null)
      setToast({ message: 'Draft deleted', type: 'info' })
    } catch (err: any) {
      setToast({ message: err.response?.data?.error || err.message || 'Draft could not be deleted.', type: 'err' })
    }
  }

  function handleFiles(files: FileList | null) {
    if (!files) return
    setForm((current) => ({ ...current, files: [...current.files, ...Array.from(files)] }))
    setSelectedFileIndex(0)
    setSaveState('idle')
  }

  return (
    <div className="submission-screen">
      <nav className="sub-topnav">
        <div className="sub-nav-left">
          <button className="sub-back-btn" type="button" onClick={() => navigate('/dashboard')}>
            <i className="ti ti-arrow-left"></i>
          </button>
          <div className="sub-nav-brand">
            <div className="sub-nav-brand-icon"><BrandMark /></div>
            <div className="sub-nav-brand-name">DASIG<em>Connect</em></div>
          </div>
          <div className="sub-nav-breadcrumb">
            <i className="ti ti-chevron-right"></i>
            <span>Submit Content</span>
          </div>
        </div>
        <div className="sub-nav-right">
          <div className={`sub-nav-save-status ${saveState === 'saved' ? 'saved' : ''}`}>
            <i className={saveState === 'saving' ? 'ti ti-loader-2 sub-spin' : saveState === 'saved' ? 'ti ti-cloud-check' : 'ti ti-cloud'}></i>
            {saveState === 'saving' ? 'Saving...' : saveState === 'saved' ? 'Draft saved' : 'Unsaved draft'}
          </div>
          <div className="sub-nav-chip">{formatRole(user.role)}</div>
          <div className="sub-nav-avatar">{user.initials}</div>
        </div>
      </nav>

      <div className="sub-workspace">
        <aside className="sub-sidebar">
          <div className="sub-sidebar-header">
            <div className="sub-sidebar-title">Submission Queue</div>
            <div className="sub-sidebar-tabs">
              <button className={`sub-stab ${filter === 'drafts' ? 'active' : ''}`} type="button" onClick={() => setFilter('drafts')}>
                Drafts {draftCount > 0 && <span>{draftCount}</span>}
              </button>
              <button className={`sub-stab ${filter === 'submitted' ? 'active' : ''}`} type="button" onClick={() => setFilter('submitted')}>
                Submitted
              </button>
              <button className={`sub-stab ${filter === 'all' ? 'active' : ''}`} type="button" onClick={() => setFilter('all')}>
                All
              </button>
            </div>
          </div>
          <div className="sub-sidebar-list">
            {loading && <QueueState icon="ti-loader-2 sub-spin" title="Loading submissions" />}
            {!loading && error && (
              <QueueState
                icon="ti-database-off"
                title="Unable to load submissions"
                description="Check your session and backend connection, then refresh the queue."
              />
            )}
            {!loading && !error && queued.length === 0 && (
              <QueueState icon="ti-folder-open" title="No submissions in this view" />
            )}
            {!loading && !error && queued.map((item) => (
              <button
                className={`sub-queue-item ${item.id === form.id ? 'active' : ''}`}
                key={item.id}
                type="button"
                onClick={() => applySubmission(item)}
              >
                <div className="sub-qi-top">
                  <div className="sub-qi-title">{item.eventTitle || 'Untitled submission'}</div>
                  <span className={`sub-qi-badge status-${item.status}`}>{statusLabels[item.status]}</span>
                </div>
                <div className="sub-qi-meta">
                  <i className="ti ti-building"></i>
                  {item.institutionName || user.inst}
                  <span className="sub-qi-dot"></span>
                  {formatDate(item.eventDate)}
                </div>
                <div className="sub-qi-media">
                  <div className="sub-qi-thumb"><i className="ti ti-photo"></i></div>
                  {(item.mediaCount ?? 0) > 1 && <div className="sub-qi-thumb">+{(item.mediaCount ?? 0) - 1}</div>}
                </div>
              </button>
            ))}
          </div>
          <div className="sub-sidebar-new">
            <button className="sub-btn-new" type="button" onClick={() => setForm(initialForm)}>
              <i className="ti ti-plus"></i> New Submission
            </button>
          </div>
        </aside>

        <main className="sub-form-canvas">
          <div className="sub-form-page-head">
            <div>
              <h1 className="sub-form-page-title">Submit Content</h1>
              <p className="sub-form-page-sub">Prepare event media, caption, tags, and a preferred publishing slot.</p>
            </div>
            <div className="sub-form-page-actions">
              <button className="sub-btn-ghost" type="button" onClick={() => setModal('delete')}>
                <i className="ti ti-trash"></i> Delete
              </button>
              <button className="sub-btn-ghost" type="button" onClick={() => void handleSave()}>
                <i className="ti ti-device-floppy"></i> Save Draft
              </button>
              <button className="sub-btn-primary" type="button" onClick={() => setModal('submit')} disabled={readiness.score < 60}>
                <i className="ti ti-send"></i> Submit for Review
              </button>
            </div>
          </div>

          <section className="sub-form-section">
            <SectionHead icon="ti-photo-up" tone="blue" title="Media Assets" subtitle="Attach photos or videos from the event." />
            <label className="sub-upload-zone">
              <input className="sub-file-input" type="file" multiple accept="image/*,video/*" onChange={(event) => handleFiles(event.target.files)} />
              <div className="sub-upload-icon"><i className="ti ti-cloud-upload"></i></div>
              <div className="sub-upload-title">Drop files here or browse</div>
              <div className="sub-upload-sub">Images and videos are attached to the draft and uploaded when you save.</div>
              <div className="sub-upload-types">
                <span>JPG</span><span>PNG</span><span>MP4</span><span>MOV</span>
              </div>
            </label>
            {form.files.length > 0 && (
              <div className="sub-filmstrip">
                {form.files.map((file, index) => (
                  <button
                    className={`sub-film-item ${selectedFileIndex === index ? 'selected' : ''}`}
                    key={`${file.name}-${file.lastModified}`}
                    type="button"
                    onClick={() => setSelectedFileIndex(index)}
                  >
                    {file.type.startsWith('image/') ? (
                      <img src={filePreviewUrl(file)} alt={file.name} />
                    ) : (
                      <div className="sub-film-video"><i className="ti ti-video"></i></div>
                    )}
                    <span className="sub-film-num">{index + 1}</span>
                    <span className="sub-film-badge">{file.type.startsWith('video/') ? 'VIDEO' : 'IMAGE'}</span>
                    <span
                      className="sub-film-del"
                      role="button"
                      tabIndex={0}
                      onClick={(event) => {
                        event.stopPropagation()
                        setForm((current) => ({ ...current, files: current.files.filter((_, fileIndex) => fileIndex !== index) }))
                      }}
                    >
                      <i className="ti ti-x"></i>
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="sub-form-section">
            <SectionHead icon="ti-edit" tone="gold" title="Post Details" subtitle="Use backend field names for the saved submission draft." />
            <div className="sub-field-row">
              <Field label="Event Title">
                <input className="sub-finput" value={form.eventTitle} onChange={(event) => updateField('eventTitle', event.target.value)} />
              </Field>
              <Field label="Event Date">
                <input className="sub-finput" type="date" value={form.eventDate} onChange={(event) => updateField('eventDate', event.target.value)} />
              </Field>
            </div>
            <Field label="Caption" count={`${form.caption.length} / 500 chars`} tone={captionTone(form.caption)}>
              <textarea
                className={`sub-finput ${captionTone(form.caption)}`}
                rows={4}
                value={form.caption}
                onChange={(event) => updateField('caption', event.target.value)}
                placeholder="Write a compelling caption for the DASIG Facebook page..."
              />
              <div className="sub-finput-hint">Captions between 150-500 characters perform best on Facebook. Include relevant hashtags.</div>
            </Field>

            <div className="sub-ai-assist">
              <div className="sub-ai-icon"><i className="ti ti-sparkles"></i></div>
              <div>
                <div className="sub-ai-title">AI caption assist</div>
                <div className="sub-ai-text">
                  AI caption generation will appear here when the backend integration for media analysis is available.
                </div>
              </div>
            </div>

            <div className="sub-field-row">
              <Field label="Event Category">
                <select className="sub-fselect" value={form.category} onChange={(event) => updateField('category', event.target.value)}>
                  <option value="">{lookupsLoading ? 'Loading options...' : 'Backend categories unavailable'}</option>
                </select>
              </Field>
              <Field label="Institution Scope">
                <input className="sub-finput" value={user.inst} readOnly />
              </Field>
            </div>

            <Field label="Tags">
              {lookupsError && (
                <div className="sub-inline-note">
                  Backend tag options are not available yet. You can still save and submit the required submission fields.
                </div>
              )}
              <div className="sub-tag-row">
                <span className="sub-muted-text">No backend tags available.</span>
              </div>
            </Field>

            <Field label="Description / Validator Notes">
              <textarea
                className="sub-finput"
                rows={2}
                value={form.description}
                onChange={(event) => updateField('description', event.target.value)}
                placeholder="Notes for your Validator..."
              />
            </Field>
          </section>

          <section className="sub-form-section">
            <SectionHead icon="ti-calendar-event" tone="purple" title="Preferred Schedule" subtitle="Guardrails validate the requested slot when both fields are set." />
            <div className="sub-field-row">
              <Field label="Preferred Date">
                <input className="sub-finput" type="date" value={form.scheduledDate} onChange={(event) => updateField('scheduledDate', event.target.value)} />
              </Field>
              <Field label="Preferred Time">
                <input className="sub-finput" type="time" value={form.scheduledTime} onChange={(event) => updateField('scheduledTime', event.target.value)} />
              </Field>
            </div>
            {guardRailError && <div className="sub-inline-error">{guardRailError}</div>}
          </section>
        </main>

        <aside className="sub-guard-panel">
          <div className="sub-guard-header">
            <div className="sub-guard-title"><i className="ti ti-shield-check"></i> Readiness</div>
            <ReadinessRing score={readiness.score} />
            <div className="sub-score-grade">{readiness.grade}</div>
            <div className="sub-score-desc">{readiness.description}</div>
          </div>

          <GuardSection title="Required Fields" icon="ti-list-check">
            <CheckItem pass={Boolean(form.eventTitle.trim())} title="Event title" sub={form.eventTitle || 'Required'} />
            <CheckItem pass={Boolean(form.eventDate)} title="Event date" sub={form.eventDate ? formatDate(form.eventDate) : 'Required'} />
            <CheckItem pass={captionTone(form.caption) === 'ok'} title="Caption length" sub={`${form.caption.length} characters`} />
            <CheckItem pass title="Event category" sub="Not required by backend UC-1.3" />
          </GuardSection>

          <GuardSection title="Media Quality" icon="ti-photo">
            <CheckItem pass={form.files.length > 0} title="Minimum 1 file uploaded" sub={`${form.files.length} file(s) attached`} />
            <CheckItem pass={form.files.every((file) => file.size <= lookups.maxFileSizeMb * 1024 * 1024)} title="All files within size limit" sub={`${lookups.maxFileSizeMb} MB max per file`} />
            <CheckItem pass={form.files.every((file) => isAllowedFile(file, lookups.allowedFileTypes))} title="Accepted file formats only" sub={lookups.allowedFileTypes.join(', ') || 'Awaiting backend lookup'} />
          </GuardSection>

          <GuardSection title="Scheduling" icon="ti-calendar">
            <CheckItem pass={Boolean(scheduledAt)} title="Preferred slot selected" sub={scheduledAt ? formatDateTime(scheduledAt) : 'Select date and time'} />
            {guardRails ? (
              <CheckItem pass={!guardRails.blocked} title="Slot confirmation" sub={guardRails.clean ? 'Guardrails passed' : `${guardRails.hardBlocks.length} blocking issue(s)`} />
            ) : (
              <CheckItem pass={false} idle title="Slot confirmation" sub="Awaiting backend guardrail response" />
            )}
          </GuardSection>

          <div className="sub-fb-preview-wrap">
            <div className="sub-guard-section-title"><i className="ti ti-brand-facebook"></i> Facebook Preview</div>
            <div className="sub-fb-preview">
              <div className="sub-fb-preview-head">
                <div className="sub-fb-page-icon"><i className="ti ti-brand-facebook"></i></div>
                <div>
                  <div className="sub-fb-page-name">DASIG Facebook Page</div>
                  <div className="sub-fb-page-date">{scheduledAt ? formatDate(scheduledAt) : 'Unscheduled'} <i className="ti ti-world"></i></div>
                </div>
              </div>
              <div className="sub-fb-preview-img">
                {form.files[selectedFileIndex]?.type.startsWith('image/') ? (
                  <img src={filePreviewUrl(form.files[selectedFileIndex])} alt={form.files[selectedFileIndex].name} />
                ) : (
                  <i className="ti ti-photo"></i>
                )}
              </div>
              <div className="sub-fb-preview-caption">{previewCaption}</div>
            </div>
          </div>

          <div className="sub-guard-actions">
            <button className="sub-guard-submit-btn" type="button" onClick={() => setModal('submit')} disabled={readiness.score < 60}>
              <i className="ti ti-send"></i> Submit for Review
            </button>
            <button className="sub-guard-save-btn" type="button" onClick={() => void handleSave()}>
              <i className="ti ti-device-floppy"></i> Save Draft
            </button>
          </div>
        </aside>
      </div>

      {modal === 'submit' && (
        <ConfirmModal
          icon="ti-send"
          title="Submit for Review?"
          description={`This submission will be sent to your institution's Validator. Readiness score: ${readiness.score} / 100.`}
          cancelLabel="Go Back"
          confirmLabel={submitting ? 'Submitting...' : 'Confirm Submission'}
          onCancel={() => setModal(null)}
          onConfirm={() => void handleSubmit()}
        />
      )}
      {modal === 'success' && (
        <ConfirmModal
          icon="ti-circle-check"
          tone="success"
          title="Submission sent!"
          description="Your content has been submitted for validation. You will be notified when it is reviewed."
          confirmLabel="Done"
          onConfirm={() => setModal(null)}
        />
      )}
      {modal === 'delete' && (
        <ConfirmModal
          icon="ti-trash"
          tone="danger"
          title="Delete this draft?"
          description="This will delete the current draft from the submission queue."
          cancelLabel="Cancel"
          confirmLabel="Delete Draft"
          onCancel={() => setModal(null)}
          onConfirm={() => void handleDelete()}
        />
      )}
      {toast && (
        <div className={`sub-toast ${toast.type}`}>
          <i className={`ti ${toastIcon(toast.type)}`}></i>
          {toast.message}
        </div>
      )}
    </div>
  )
}

function SectionHead({ icon, tone, title, subtitle }: { icon: string; tone: string; title: string; subtitle: string }) {
  return (
    <div className="sub-section-head">
      <div className="sub-section-label">
        <div className={`sub-section-icon ${tone}`}><i className={`ti ${icon}`}></i></div>
        <div>
          <div className="sub-section-title">{title}</div>
          <div className="sub-section-subtitle">{subtitle}</div>
        </div>
      </div>
    </div>
  )
}

function Field({ label, count, tone, children }: { label: string; count?: string; tone?: string; children: ReactNode }) {
  return (
    <label className="sub-fgroup">
      <span className="sub-flabel">
        {label}
        {count && <span className={`sub-flabel-count ${tone || ''}`}>{count}</span>}
      </span>
      {children}
    </label>
  )
}

function GuardSection({ title, icon, children }: { title: string; icon: string; children: ReactNode }) {
  return (
    <div className="sub-guard-section">
      <div className="sub-guard-section-title"><i className={`ti ${icon}`}></i> {title}</div>
      {children}
    </div>
  )
}

function CheckItem({ pass, idle, title, sub }: { pass: boolean; idle?: boolean; title: string; sub: string }) {
  return (
    <div className="sub-check-item">
      <div className={`sub-check-icon ${idle ? 'idle' : pass ? 'pass' : 'warn'}`}>
        <i className={`ti ${idle ? 'ti-clock' : pass ? 'ti-check' : 'ti-alert-triangle'}`}></i>
      </div>
      <div>
        <div className="sub-check-title">{title}</div>
        <div className="sub-check-sub">{sub}</div>
      </div>
    </div>
  )
}

function QueueState({ icon, title, description }: { icon: string; title: string; description?: string }) {
  return (
    <div className="sub-queue-state">
      <i className={`ti ${icon}`}></i>
      <span>{title}</span>
      {description && <small>{description}</small>}
    </div>
  )
}

function ReadinessRing({ score }: { score: number }) {
  const circumference = 175.9
  const offset = circumference - (score / 100) * circumference
  return (
    <div className="sub-score-ring">
      <svg viewBox="0 0 64 64">
        <circle className="sub-score-bg" cx="32" cy="32" r="28" />
        <circle
          className="sub-score-fill"
          cx="32"
          cy="32"
          r="28"
          style={{ strokeDashoffset: offset, stroke: score >= 80 ? '#16A34A' : score >= 60 ? '#D97706' : '#DC2626' }}
        />
      </svg>
      <div className="sub-score-num" style={{ color: score >= 80 ? '#16A34A' : score >= 60 ? '#D97706' : '#DC2626' }}>{score}</div>
    </div>
  )
}

function ConfirmModal({
  icon,
  tone = 'info',
  title,
  description,
  cancelLabel,
  confirmLabel,
  onCancel,
  onConfirm,
}: {
  icon: string
  tone?: 'info' | 'success' | 'danger'
  title: string
  description: string
  cancelLabel?: string
  confirmLabel: string
  onCancel?: () => void
  onConfirm: () => void
}) {
  return (
    <div className="sub-modal-overlay" onClick={onCancel || onConfirm}>
      <div className="sub-modal" onClick={(event) => event.stopPropagation()}>
        <div className={`sub-modal-icon ${tone}`}><i className={`ti ${icon}`}></i></div>
        <div className="sub-modal-title">{title}</div>
        <div className="sub-modal-desc">{description}</div>
        <div className="sub-modal-actions">
          {onCancel && <button className="sub-modal-btn cancel" type="button" onClick={onCancel}>{cancelLabel}</button>}
          <button className={`sub-modal-btn ${tone}`} type="button" onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}

function BrandMark() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
    </svg>
  )
}

function toPayload(form: FormState, scheduledAt?: string): SubmissionPayload {
  return {
    eventTitle: form.eventTitle.trim(),
    eventDate: form.eventDate,
    caption: form.caption.trim(),
    description: form.description.trim(),
    scheduledAt,
  }
}

function upsertSubmission(items: SubmissionSummary[], next: SubmissionSummary) {
  const exists = items.some((item) => item.id === next.id)
  if (!exists) return [next, ...items]
  return items.map((item) => (item.id === next.id ? next : item))
}

function calculateReadiness(form: FormState, guardRails: GuardRailResult | null) {
  let score = 0
  if (form.eventTitle.trim()) score += 15
  if (form.eventDate) score += 15
  if (captionTone(form.caption) === 'ok') score += 20
  score += 10
  if (form.files.length > 0) score += 20
  if (form.scheduledDate && form.scheduledTime) score += 10
  if (guardRails && !guardRails.blocked) score += 10

  return {
    score,
    grade: score >= 80 ? 'Good to submit' : score >= 60 ? 'Needs attention' : 'Incomplete',
    description: score >= 80
      ? 'Most checks pass. Non-blocking warnings can still be reviewed.'
      : score >= 60
        ? 'Resolve the highlighted items before sending.'
        : 'Complete the required fields to prepare this submission.',
  }
}

function captionTone(caption: string) {
  if (caption.length >= 150 && caption.length <= 500) return 'ok'
  if (caption.length === 0) return ''
  return 'warn'
}

const previewUrls = new WeakMap<File, string>()

function filePreviewUrl(file: File) {
  const existing = previewUrls.get(file)
  if (existing) return existing
  const next = URL.createObjectURL(file)
  previewUrls.set(file, next)
  return next
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' }).format(date)
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(date)
}

function formatTimeInput(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
}

function formatRole(role: User['role']) {
  if (role === 'admin') return 'Administrator'
  return role.charAt(0).toUpperCase() + role.slice(1)
}

function toastIcon(type: 'success' | 'info' | 'err' | 'warn') {
  if (type === 'success') return 'ti-circle-check'
  if (type === 'err') return 'ti-alert-circle'
  if (type === 'warn') return 'ti-alert-triangle'
  return 'ti-info-circle'
}

function isAllowedFile(file: File, allowedFileTypes: string[]) {
  if (allowedFileTypes.length === 0) return true
  const extension = file.name.split('.').pop()?.toLowerCase()
  return Boolean(extension && allowedFileTypes.includes(extension))
}
