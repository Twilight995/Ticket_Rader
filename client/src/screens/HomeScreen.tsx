// 화면 ⓪ 홈: 무엇을 해 주는 서비스인지 알고 ① 판매글 입력으로 넘어간다.
import type { TicketEvent } from '../api.ts'
import type { RequestState } from '../hooks.ts'
import { EventInfo, HowItWorks } from '../components/Layout.tsx'
import { Card, FooterNote, PrimaryButton } from '../components/ui.tsx'

interface Props {
  eventState: RequestState<TicketEvent | null>
  onRetryEvent: () => void
  onStart: () => void
}

export function HomeScreen({ eventState, onRetryEvent, onStart }: Props) {
  const ready = eventState.status === 'success' && eventState.data !== null
  return (
    <div className="tr-page tr-page--home">
      <header className="tr-hero">
        <h1 className="tr-hero__title">
          티켓 판매글, 결제 전에
          <br />
          공식 안내와 맞춰 보세요
        </h1>
        <p className="tr-hero__desc">판매글을 공연 공식 정가·입장 규정과 비교해 확인이 필요한 부분을 보여 드려요. 진위·위법 여부는 판단하지 않아요.</p>
      </header>
      <div className="tr-home-cards">
        <Card>
          <h2 className="tr-card__heading">이렇게 확인해요</h2>
          <HowItWorks />
        </Card>
        <Card>
          <h2 className="tr-card__heading">지금 확인할 수 있는 공연</h2>
          <EventInfo state={eventState} onRetry={onRetryEvent} />
        </Card>
      </div>
      <div className="tr-home-cta">
        <PrimaryButton icon="arrowRight" onClick={onStart} disabled={!ready} wide="home">
          판매글 확인하기
        </PrimaryButton>
      </div>
      <FooterNote />
    </div>
  )
}
