// 화면 ① 판매글 입력: 판매글 하나(글 또는 캡처)를 서비스에 넣는다. 추출 요청은 이 화면이 보낸다.
import { useEffect, useRef, useState } from 'react'
import type { ExtractResult, TicketEvent } from '../api.ts'
import type { RequestState } from '../hooks.ts'
import { AppHeader, CtaRow, EventInfo, HowItWorks, TwoColumn } from '../components/Layout.tsx'
import { Icon } from '../components/Icon.tsx'
import { Card, CircleButton, FooterNote, InfoLine, Pill, PrimaryButton, Tag } from '../components/ui.tsx'

export interface Draft {
  text: string
  image: File | null
}

const MAX_TEXT = 5000
const MAX_IMAGE_BYTES = 5 * 1024 * 1024
const IMAGE_TYPES = ['image/png', 'image/jpeg']
const PLACEHOLDER = '예: VIP석 2장 양도, 장당 25만, 선입금 후 QR 전송'

/** 브라우저에서 먼저 막는다(서버가 파일 앞부분 바이트로 다시 확인한다). */
function checkImage(file: File): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return 'PNG나 JPG 이미지만 올릴 수 있어요'
  if (file.size > MAX_IMAGE_BYTES) return '5MB 이하 이미지만 올릴 수 있어요'
  return null
}

/** 미리보기 주소. 읽는 동안은 null(그 자리에 회색 박스). 이미지는 브라우저 메모리에서만 쓴다. */
function usePreviewUrl(file: File | null) {
  const [preview, setPreview] = useState<{ file: File; url: string } | null>(null)
  useEffect(() => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => setPreview({ file, url: String(reader.result) })
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])
  return preview?.file === file ? preview.url : null
}

/**
 * 연결 실패·서버 오류는 정해진 문구, 너무 잦은 요청(429)처럼 서버가 이유를 준 경우는 그 문구.
 * 글·이미지 칸 오류는 칸 아래에 보여 주므로 여기서는 빼고, 그 밖의 칸 오류는 이 줄로 보여 준다.
 */
function connectionMessage(state: RequestState<ExtractResult>): string | null {
  if (state.status !== 'error') return null
  const { status, message, fields } = state.error
  if (fields?.text || fields?.image) return null
  if (status === 0 || status >= 500) return '연결하지 못했어요. 입력한 내용은 그대로 있어요'
  return `${message.replace(/\.?$/, '.')} 입력한 내용은 그대로 있어요`
}

interface Props {
  eventState: RequestState<TicketEvent | null>
  onRetryEvent: () => void
  draft: Draft
  onDraftChange: (draft: Draft) => void
  extractState: RequestState<ExtractResult>
  onSubmit: () => void
  onClear: () => void
  onBack: () => void
}

export function InputScreen({ eventState, onRetryEvent, draft, onDraftChange, extractState, onSubmit, onClear, onBack }: Props) {
  const [imageError, setImageError] = useState<string | null>(null)
  const busy = extractState.status === 'loading'
  const connectionError = connectionMessage(extractState)
  const event = eventState.status === 'success' ? eventState.data : null
  // 거부된 이미지는 첨부되지 않으므로, 글이 있으면 버튼은 그대로 켠다(디자인 규칙 6).
  const canSubmit = event !== null && (draft.text.trim() !== '' || draft.image !== null)
  const clearAll = () => {
    setImageError(null)
    onClear()
  }

  return (
    <div className="tr-page">
      <AppHeader title="판매글 입력" step={1} onBack={onBack} right={event && <Tag icon="check"><span className="tr-ellipsis">{event.name}</span></Tag>} />
      <TwoColumn reference={<InputReference eventState={eventState} onRetryEvent={onRetryEvent} />}>
        <div className="tr-screen-head">
          <h2 className="tr-screen-title">판매글을 확인해 보세요</h2>
          <p className="tr-screen-desc">판매글을 공연 공식 안내와 비교해 드려요. 진위·위법 여부는 판단하지 않아요.</p>
        </div>
        {!event && (
          // 1024px 미만에는 참고 영역이 없으므로, 공연을 못 불러왔을 때(로딩·오류·빈) 이유를 여기 보여 준다.
          <div className="tr-below-pc">
            <Card><EventInfo state={eventState} onRetry={onRetryEvent} /></Card>
          </div>
        )}
        <ListingCard
          draft={draft}
          onDraftChange={onDraftChange}
          busy={busy}
          imageError={imageError}
          onImageError={setImageError}
          serverFields={extractState.status === 'error' ? extractState.error.fields : null}
        />
        {connectionError && <InfoLine tone="error">{connectionError}</InfoLine>}
        <CtaRow clearLabel="입력 내용 지우기" onClear={clearAll} disabled={busy}>
          <PrimaryButton icon="search" onClick={onSubmit} disabled={!canSubmit} busy={busy} busyLabel="읽는 중…">
            {connectionError ? '다시 시도' : '이 판매글 확인하기'}
          </PrimaryButton>
        </CtaRow>
      </TwoColumn>
    </div>
  )
}

interface ListingCardProps {
  draft: Draft
  onDraftChange: (draft: Draft) => void
  busy: boolean
  imageError: string | null
  onImageError: (error: string | null) => void
  serverFields: Record<string, string> | null
}

/** 입력 카드: 캡처 이미지 칩, 판매글 칸, 올린 이미지. 읽는 중에는 전부 잠긴다. */
function ListingCard({ draft, onDraftChange, busy, imageError, onImageError, serverFields }: ListingCardProps) {
  const fileInput = useRef<HTMLInputElement>(null)
  const openPicker = () => fileInput.current?.click()
  const pickImage = (file: File | undefined) => {
    if (!file) return
    const error = checkImage(file)
    onImageError(error)
    onDraftChange({ ...draft, image: error ? null : file })
  }
  return (
    <Card className="tr-input-card">
      <input ref={fileInput} type="file" accept="image/png,image/jpeg" hidden onChange={(e) => { pickImage(e.target.files?.[0]); e.target.value = '' }} />
      <div className="tr-row">
        <button type="button" className="tr-chip-dashed" disabled={busy} onClick={openPicker}>
          <Icon name="image" />
          캡처 이미지
        </button>
        <span className="tr-hint">PNG·JPG, 5MB 이하</span>
      </div>
      <label className="tr-visually-hidden" htmlFor="listing-text">판매글</label>
      <textarea id="listing-text" className="tr-input-card__text" placeholder={PLACEHOLDER} maxLength={MAX_TEXT} value={draft.text} disabled={busy} onChange={(e) => onDraftChange({ ...draft, text: e.target.value })} />
      {serverFields?.text && <p className="tr-field__error" role="alert">{serverFields.text}</p>}
      <Attachment image={draft.image} busy={busy} error={imageError ?? serverFields?.image ?? null} onRemove={() => onDraftChange({ ...draft, image: null })} onPickAnother={openPicker} />
    </Card>
  )
}

interface AttachmentProps {
  image: File | null
  busy: boolean
  error: string | null
  onRemove: () => void
  onPickAnother: () => void
}

/** 올린 캡처 미리보기 + 삭제, 이미지 오류(칸 아래 빨간 글씨 + "다른 이미지 올리기"). */
function Attachment({ image, busy, error, onRemove, onPickAnother }: AttachmentProps) {
  const previewUrl = usePreviewUrl(image)
  return (
    <>
      {image && (
        <div className="tr-attachment">
          {previewUrl ? (
            <img className="tr-attachment__thumb" src={previewUrl} alt="올린 캡처 이미지 미리보기" />
          ) : (
            <span className="tr-attachment__thumb" aria-label="이미지를 읽는 중" role="img" />
          )}
          <span className="tr-attachment__name">{image.name}</span>
          <CircleButton icon="close" size="tool" label="캡처 이미지 삭제" disabled={busy} onClick={onRemove} />
        </div>
      )}
      {error && (
        <div className="tr-stack-sm">
          <p className="tr-field__error" role="alert">{error}</p>
          <div>
            <Pill icon="image" onClick={onPickAnother}>다른 이미지 올리기</Pill>
          </div>
        </div>
      )}
    </>
  )
}

function InputReference({ eventState, onRetryEvent }: { eventState: RequestState<TicketEvent | null>; onRetryEvent: () => void }) {
  return (
    <>
      <Card>
        <h2 className="tr-card__heading">지금 확인하는 공연</h2>
        <EventInfo state={eventState} onRetry={onRetryEvent} />
      </Card>
      <Card>
        <h2 className="tr-card__heading">이렇게 확인해요</h2>
        <HowItWorks />
      </Card>
      <FooterNote />
    </>
  )
}
