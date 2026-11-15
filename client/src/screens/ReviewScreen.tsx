// 화면 ② 추출 결과 확인: AI가 뽑은 값을 사람이 확인하고 고친다. AI가 실패(fallback)했으면 빈 폼.
import { useState, type ReactNode } from 'react'
import type { DeliveryMethod, ExtractResult, TicketEvent } from '../api.ts'
import type { RequestState } from '../hooks.ts'
import { DELIVERY_LABELS } from '../labels.ts'
import { canCompare, validateForm, type FieldErrors, type ReviewForm } from '../review.ts'
import { AppHeader, CtaRow, EventInfo, SourceCard, TwoColumn } from '../components/Layout.tsx'
import { Icon } from '../components/Icon.tsx'
import { Card, EvidenceChip, Field, InfoLine, Pill, PrimaryButton, ProgressPill, Skeleton, Toggle } from '../components/ui.tsx'

interface Props {
  result: ExtractResult | null
  form: ReviewForm
  initialForm: ReviewForm
  onFormChange: (form: ReviewForm) => void
  serverErrors: FieldErrors
  compareFailed: boolean
  eventState: RequestState<TicketEvent | null>
  onRetryEvent: () => void
  onCompare: () => void
  onRestart: () => void
  onBack: () => void
}

export function ReviewScreen(props: Props) {
  const { result, form, compareFailed, onCompare, onRestart, onBack } = props
  const [sourceOpen, setSourceOpen] = useState(false)
  const header = (
    <AppHeader
      title="읽은 값 확인"
      step={2}
      onBack={onBack}
      right={result && (
        <span className="tr-below-pc">
          <Pill icon={sourceOpen ? 'close' : 'chevronDown'} onClick={() => setSourceOpen(!sourceOpen)}>판매글</Pill>
        </span>
      )}
    />
  )
  if (!result) return <ReviewLoading header={header} />

  const errors = { ...props.serverErrors, ...validateForm(form) }
  const reference = (
    <>
      <SourceCard result={result} />
      <Card>
        <h2 className="tr-card__heading">확인하는 공연</h2>
        <EventInfo state={props.eventState} onRetry={props.onRetryEvent} />
      </Card>
    </>
  )
  return (
    <div className="tr-page">
      {header}
      <TwoColumn referenceFirst reference={reference}>
        {sourceOpen && <div className="tr-below-pc"><SourceCard result={result} /></div>}
        {!compareFailed && (
          <InfoLine>{result.aiStatus === 'ok' ? 'AI가 읽은 값이에요. 틀린 곳을 고쳐 주세요' : 'AI가 내용을 읽지 못했어요. 아래에 직접 입력해 주세요'}</InfoLine>
        )}
        <MainFields {...props} errors={errors} />
        <ExtraFields {...props} errors={errors} />
        {compareFailed && <InfoLine tone="error">비교하지 못했어요. 입력값은 그대로 있어요</InfoLine>}
        <CtaRow clearLabel="처음부터 다시" onClear={onRestart}>
          <PrimaryButton icon="search" onClick={onCompare} disabled={!canCompare(form)}>
            {compareFailed ? '다시 시도' : '공식 안내와 비교하기'}
          </PrimaryButton>
        </CtaRow>
      </TwoColumn>
    </div>
  )
}

function ReviewLoading({ header }: { header: ReactNode }) {
  return (
    <div className="tr-page">
      {header}
      <main className="tr-single">
        <ProgressPill>판매글을 읽는 중…</ProgressPill>
        {[0, 1, 2].map((i) => <Card key={i}><Skeleton lines={1} /></Card>)}
      </main>
    </div>
  )
}

type FieldProps = Props & { errors: FieldErrors }

/** 고치지 않은 칸만 AI 근거 구절을 보여 준다. 오류가 있으면 근거 대신 오류. */
function evidenceFor(props: FieldProps, key: keyof ReviewForm) {
  const quote = props.result?.evidence?.[key]
  return quote && props.form[key] === props.initialForm[key] ? <EvidenceChip quote={quote} /> : undefined
}

function MainFields(props: FieldProps) {
  const { form, onFormChange, errors } = props
  const set = (patch: Partial<ReviewForm>) => onFormChange({ ...form, ...patch })
  return (
    <>
      <div className="tr-pair">
        <Card>
          <Field label="1장당 가격(원)" htmlFor="price" error={errors.pricePerTicket} hint={evidenceFor(props, 'pricePerTicket')}>
            <input id="price" className="tr-input tr-input--value" inputMode="numeric" maxLength={11} placeholder="예: 150000" value={form.pricePerTicket} onChange={(e) => set({ pricePerTicket: e.target.value })} aria-invalid={!!errors.pricePerTicket} />
          </Field>
        </Card>
        <Card>
          <Field label="좌석 등급·구역" htmlFor="seat" error={errors.seat} hint={evidenceFor(props, 'seat')}>
            <input id="seat" className="tr-input tr-input--value" maxLength={100} placeholder="예: R석 B구역" value={form.seat} onChange={(e) => set({ seat: e.target.value })} />
          </Field>
        </Card>
      </div>
      <Card>
        <fieldset className="tr-fieldset">
          <legend className="tr-field__label">전달 방식</legend>
          <div className="tr-row tr-row--wrap">
            {(Object.keys(DELIVERY_LABELS) as DeliveryMethod[]).map((code) => (
              <button key={code} type="button" className="tr-choice" aria-pressed={form.deliveryMethod === code} onClick={() => set({ deliveryMethod: form.deliveryMethod === code ? '' : code })}>
                {form.deliveryMethod === code && <Icon name="check" />}
                {DELIVERY_LABELS[code]}
              </button>
            ))}
          </div>
          {evidenceFor(props, 'deliveryMethod')}
        </fieldset>
      </Card>
    </>
  )
}

/** 접힌 "추가 항목": 수량, 선입금 요구 여부. */
function ExtraFields(props: FieldProps) {
  const { form, onFormChange, errors } = props
  const [open, setOpen] = useState(Boolean(errors.quantity))
  const set = (patch: Partial<ReviewForm>) => onFormChange({ ...form, ...patch })
  return (
    <Card className="tr-option">
      <button type="button" className="tr-option__toggle" aria-expanded={open} onClick={() => setOpen(!open)}>
        <Icon name={open ? 'chevronDown' : 'chevronRight'} size={18} />
        {open ? '추가 항목 접기' : '추가 항목 펼치기'}
        <span className="tr-hint">수량 · 선입금 요구</span>
      </button>
      {open && (
        <div className="tr-option__body">
          <Field label="수량(장)" htmlFor="quantity" error={errors.quantity} hint={evidenceFor(props, 'quantity')}>
            <input id="quantity" className="tr-input" inputMode="numeric" maxLength={3} placeholder="예: 2" value={form.quantity} onChange={(e) => set({ quantity: e.target.value })} aria-invalid={!!errors.quantity} />
          </Field>
          <div className="tr-field">
            <div className="tr-row tr-row--between">
              <span className="tr-field__label">선입금 요구</span>
              <Toggle label="선입금 요구" checked={form.prepaymentRequired === true} onChange={(next) => set({ prepaymentRequired: next })} />
            </div>
            {evidenceFor(props, 'prepaymentRequired')}
          </div>
        </div>
      )}
    </Card>
  )
}
