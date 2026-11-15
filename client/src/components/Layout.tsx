// 머리줄(모바일 상단 바 / PC 단계 표시)과 두 단 배치, 여러 화면이 같이 쓰는 카드 내용.
import type { ReactNode } from 'react'
import type { ExtractResult, TicketEvent } from '../api.ts'
import type { RequestState } from '../hooks.ts'
import { formatStartsAt } from '../labels.ts'
import { Icon } from './Icon.tsx'
import { Card, CircleButton, EvidenceChip, Pill, Skeleton, TextButton } from './ui.tsx'

/** ②③ "판매글 다시 보기": 원문이 아니라 서버가 연락처를 지운 maskedText 를 보여 준다. */
export function SourceCard({ result }: { result: ExtractResult }) {
  const quotes = result.evidence ? Object.values(result.evidence).filter((q): q is string => !!q) : []
  return (
    <Card>
      <h2 className="tr-card__heading">판매글 (연락처는 지운 상태)</h2>
      <p className="tr-source__text">{result.maskedText ?? '캡처 이미지로 읽어서 보여 드릴 글이 없어요.'}</p>
      {quotes.length > 0 && (
        <div className="tr-stack-sm">
          <p className="tr-label">AI가 근거로 본 구절</p>
          <div className="tr-row tr-row--wrap">{quotes.map((q, i) => <EvidenceChip key={i} quote={q} />)}</div>
        </div>
      )}
    </Card>
  )
}

const STEPS = ['판매글 입력', '읽은 값 확인', '확인사항 결과']

function StepIndicator({ current }: { current: number }) {
  return (
    <ol className="tr-steps" aria-label="진행 단계">
      {STEPS.map((label, i) => {
        const n = i + 1
        const state = n < current ? 'done' : n === current ? 'current' : 'next'
        return (
          <li key={label} className={`tr-steps__item tr-steps__item--${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            <span className="tr-steps__dot">{state === 'done' ? <Icon name="check" size={12} /> : n}</span>
            {label}
          </li>
        )
      })}
    </ol>
  )
}

interface HeaderProps {
  title: string
  step?: number
  onBack?: () => void
  right?: ReactNode
}

/** 모바일: 뒤로 원 + 제목 + 오른쪽 알약. 600px 이상: 브랜드 + 단계 표시 + 오른쪽 알약. */
export function AppHeader({ title, step, onBack, right }: HeaderProps) {
  return (
    <header className="tr-header">
      <div className="tr-header__mobile">
        {onBack && <CircleButton icon="back" label="뒤로" onClick={onBack} size="tool" />}
        <h1 className="tr-header__title">{title}</h1>
      </div>
      <a className="tr-header__brand" href="#/">
        티켓레이더
      </a>
      {step && <StepIndicator current={step} />}
      <div className="tr-header__right">{right}</div>
    </header>
  )
}

/** 주인공 버튼 줄(①②): 모바일은 휴지통 원 + 버튼, 600px 이상은 글자 보조 버튼 + 오른쪽 버튼 240. */
export function CtaRow({ clearLabel, onClear, disabled, children }: { clearLabel: string; onClear: () => void; disabled?: boolean; children: ReactNode }) {
  return (
    <div className="tr-cta-row">
      <span className="tr-mobile-only">
        <CircleButton icon="trash" label={clearLabel} onClick={onClear} disabled={disabled} />
      </span>
      <span className="tr-wide-only">
        <TextButton icon="trash" onClick={onClear} disabled={disabled}>
          {clearLabel}
        </TextButton>
      </span>
      {children}
    </div>
  )
}

/** PC 두 단: 참고 영역(320) + 작업 영역(616). 1024px 미만에서는 참고 영역을 숨기고 한 단. */
export function TwoColumn({ reference, children, referenceFirst }: { reference: ReactNode; children: ReactNode; referenceFirst?: boolean }) {
  return (
    <div className={`tr-columns${referenceFirst ? ' tr-columns--ref-first' : ''}`}>
      <main className="tr-columns__work">{children}</main>
      <aside className="tr-columns__ref">{reference}</aside>
    </div>
  )
}

const HOW_STEPS = [
  '판매글을 붙여넣거나 캡처를 올려요',
  'AI가 읽은 가격·좌석·전달 방식을 확인하고 고쳐요',
  '공식 안내와 다른 부분을 근거와 함께 봐요',
]

/** "이렇게 확인해요" 3단계 (⓪ 홈, ① PC 참고 영역). */
export function HowItWorks() {
  return (
    <ol className="tr-how">
      {HOW_STEPS.map((text, i) => (
        <li key={text} className="tr-how__item">
          <span className="tr-how__num">{i + 1}</span>
          {text}
        </li>
      ))}
    </ol>
  )
}

/** 공연 카드 안쪽: 로딩·오류·빈·정상 네 상태. */
export function EventInfo({ state, onRetry }: { state: RequestState<TicketEvent | null>; onRetry: () => void }) {
  if (state.status === 'idle' || state.status === 'loading') return <Skeleton lines={2} />
  if (state.status === 'error') {
    return (
      <div className="tr-stack-sm">
        <p className="tr-sub">공연 정보를 불러오지 못했어요</p>
        <div>
          <Pill icon="refresh" onClick={onRetry}>
            다시 시도
          </Pill>
        </div>
      </div>
    )
  }
  const event = state.data
  if (!event) {
    return <p className="tr-sub">아직 확인할 수 있는 공연이 없어요. 관리자가 공연 정보를 등록하면 시작할 수 있어요</p>
  }
  return (
    <div className="tr-stack-sm">
      <p className="tr-event__name">{event.name}</p>
      <p className="tr-sub">
        {formatStartsAt(event.startsAt)} · {event.venue}
      </p>
      <div>
        <Pill variant="link" icon="external" href={event.officialUrl}>
          공식 안내 원문 보기
        </Pill>
      </div>
    </div>
  )
}
