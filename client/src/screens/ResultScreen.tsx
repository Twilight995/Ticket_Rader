// 화면 ③ 확인사항 결과: 판매글의 어느 부분이 공식 안내와 다른지 근거와 함께 본다. 비교 요청은 이 화면이 보낸다.
import type { CheckResult, ExtractResult, Finding, TicketEvent } from '../api.ts'
import type { RequestState } from '../hooks.ts'
import { REPORT_CENTER_URLS } from '../labels.ts'
import { AppHeader, EventInfo, SourceCard, TwoColumn } from '../components/Layout.tsx'
import { Badge, Card, ErrorNotice, FooterNote, Pill, ProgressPill, Skeleton, Tag } from '../components/ui.tsx'

interface Props {
  checkState: RequestState<CheckResult>
  source: ExtractResult | null
  eventState: RequestState<TicketEvent | null>
  onRetryEvent: () => void
  onRetry: () => void
  onBackToReview: () => void
  onNewListing: () => void
}

export function ResultScreen({ checkState, source, eventState, onRetryEvent, onRetry, onBackToReview, onNewListing }: Props) {
  const reference = (
    <>
      {source && <SourceCard result={source} />}
      <Card>
        <h2 className="tr-card__heading">확인한 공연</h2>
        <EventInfo state={eventState} onRetry={onRetryEvent} />
      </Card>
      <FooterNote />
    </>
  )
  return (
    <div className="tr-page tr-page--with-veil">
      <AppHeader title="확인사항 결과" step={3} onBack={onBackToReview} />
      <TwoColumn referenceFirst reference={reference}>
        <ResultBody checkState={checkState} eventState={eventState} onRetry={onRetry} onBackToReview={onBackToReview} onNewListing={onNewListing} />
      </TwoColumn>
      <div className="tr-veil">
        <FooterNote />
      </div>
    </div>
  )
}

type BodyProps = Pick<Props, 'checkState' | 'eventState' | 'onRetry' | 'onBackToReview' | 'onNewListing'>

function ResultBody({ checkState, eventState, onRetry, onBackToReview, onNewListing }: BodyProps) {
  if (checkState.status === 'idle' || checkState.status === 'loading') {
    return (
      <>
        <ProgressPill>공식 안내와 비교하는 중…</ProgressPill>
        {[0, 1, 2].map((i) => <Card key={i}><Skeleton lines={3} /></Card>)}
      </>
    )
  }
  if (checkState.status === 'error') {
    return (
      <ErrorNotice title="비교 결과를 불러오지 못했어요">
        <Pill icon="refresh" onClick={onRetry}>다시 시도</Pill>
        <Pill icon="back" onClick={onBackToReview}>입력한 값으로 돌아가기</Pill>
      </ErrorNotice>
    )
  }
  const { summary, findings, officialUrl } = checkState.data
  const category = eventState.status === 'success' ? eventState.data?.category : undefined
  const reportUrl = category ? REPORT_CENTER_URLS[category] : ''
  return (
    <>
      <h2 className="tr-summary">{summary}</h2>
      {findings.length === 0 ? (
        <div>
          <Pill variant="white" icon="external" href={officialUrl}>공식 안내 원문 보기</Pill>
        </div>
      ) : (
        findings.map((finding) => <FindingCard key={finding.type} finding={finding} officialUrl={officialUrl} />)
      )}
      <div className="tr-row tr-row--wrap">
        <Pill variant="white" icon="refresh" onClick={onNewListing}>다른 판매글 확인하기</Pill>
        {reportUrl && <Pill icon="external" href={reportUrl}>공식 신고센터</Pill>}
      </div>
    </>
  )
}

/** 확인사항 카드 한 장: 이름 + 라벨, 판매글 구절, 공식 안내 문장, 출처 + 날짜, 쉬운 설명, 재확인 안내. */
function FindingCard({ finding, officialUrl }: { finding: Finding; officialUrl: string }) {
  const { official } = finding
  return (
    <Card as="article" className="tr-finding">
      <div className="tr-row tr-row--between">
        <h3 className="tr-finding__title">{finding.title}</h3>
        <Badge severity={finding.severity} label={finding.severityLabel} />
      </div>
      <div className="tr-finding__compare">
        <div className="tr-stack-sm">
          <p className="tr-label">판매글</p>
          <p className="tr-finding__quote">“{finding.listingQuote}”</p>
        </div>
        {official ? (
          <div className="tr-official">
            <p className="tr-label">공식 안내</p>
            <p>{official.text}</p>
            <a className="tr-official__source" href={official.sourceUrl} target="_blank" rel="noopener noreferrer">
              출처 {official.sourceUrl} · {official.checkedOn} 확인
            </a>
          </div>
        ) : (
          <div className="tr-official">
            <p className="tr-label">공식 안내</p>
            <p className="tr-sub">이 항목에 맞는 공식 문장은 없어요. 원문에서 직접 확인해 보세요.</p>
            <div>
              <Pill variant="link" icon="external" href={officialUrl}>공식 안내 원문 보기</Pill>
            </div>
          </div>
        )}
      </div>
      <p>{finding.explanation}</p>
      <div className="tr-row tr-row--wrap">
        <Tag icon="info">예매처에 재확인하세요</Tag>
        {official && <Pill variant="link" icon="external" href={official.sourceUrl}>공식 안내 원문 보기</Pill>}
      </div>
    </Card>
  )
}
