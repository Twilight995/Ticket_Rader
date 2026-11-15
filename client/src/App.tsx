// 화면 고르기: 주소 해시(#/, #/input, #/review, #/result, #/admin)에 따라 화면 하나를 그린다.
import { useEffect } from 'react'
import './App.css'
import type { TicketEvent } from './api.ts'
import { useEvent, useHashRoute, type RequestState, type Route } from './hooks.ts'
import type { ReviewForm } from './review.ts'
import { useListingFlow } from './useListingFlow.ts'
import { AdminScreen } from './screens/AdminScreen.tsx'
import { HomeScreen } from './screens/HomeScreen.tsx'
import { InputScreen } from './screens/InputScreen.tsx'
import { ResultScreen } from './screens/ResultScreen.tsx'
import { ReviewScreen } from './screens/ReviewScreen.tsx'

const EMPTY_FORM: ReviewForm = { pricePerTicket: '', seat: '', quantity: '', deliveryMethod: '', prepaymentRequired: null }

export default function App() {
  const { route, navigate } = useHashRoute()
  const event = useEvent()
  const eventId = event.state.status === 'success' ? event.state.data?.id : undefined
  const flow = useListingFlow(eventId, navigate)
  const shared = { eventState: event.state, onRetryEvent: () => void event.run() }

  // ②③은 앞 단계 값이 있어야 열린다(새로고침하면 값이 사라지므로 ①로 보낸다).
  // ③은 비교 요청이 없는데(값을 고친 뒤 앞으로 가기 등) 열리면 ②로 보낸다.
  const missingStep = (route === 'review' && !flow.form && flow.extractState.status !== 'loading') || (route === 'result' && !flow.form)
  const staleResult = route === 'result' && !!flow.form && flow.checkState.status === 'idle'
  useEffect(() => {
    if (missingStep) navigate('input')
    else if (staleResult) navigate('review')
  }, [missingStep, staleResult, navigate])

  if (missingStep || staleResult) return null
  if (route === 'admin') return <AdminScreen onHome={() => navigate('home')} />
  if (route === 'home') return <HomeScreen {...shared} onStart={() => navigate('input')} />
  return <FanStep route={route} flow={flow} shared={shared} navigate={navigate} />
}

interface FanStepProps {
  route: Route
  flow: ReturnType<typeof useListingFlow>
  shared: { eventState: RequestState<TicketEvent | null>; onRetryEvent: () => void }
  navigate: (route: Route) => void
}

/** ① ② ③ 화면에 흐름 값과 이동을 연결한다. */
function FanStep({ route, flow, shared, navigate }: FanStepProps) {
  if (route === 'review') {
    return (
      <ReviewScreen
        {...shared}
        result={flow.aiResult}
        form={flow.form ?? EMPTY_FORM}
        initialForm={flow.initialForm ?? EMPTY_FORM}
        onFormChange={flow.changeForm}
        serverErrors={flow.serverErrors}
        compareFailed={flow.compareFailed}
        onCompare={flow.compare}
        onRestart={flow.restart}
        onBack={() => navigate('input')}
      />
    )
  }
  if (route === 'result') {
    return (
      <ResultScreen
        {...shared}
        checkState={flow.checkState}
        source={flow.aiResult}
        onRetry={flow.compare}
        onBackToReview={flow.backToReview}
        onNewListing={flow.restart}
      />
    )
  }
  return (
    <InputScreen
      {...shared}
      draft={flow.draft}
      onDraftChange={flow.changeDraft}
      extractState={flow.extractState}
      onSubmit={flow.submitListing}
      onClear={flow.clearDraft}
      onBack={() => navigate('home')}
    />
  )
}
