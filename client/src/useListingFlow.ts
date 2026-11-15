// 팬 흐름 ① → ② → ③ 의 상태와 이동. 화면은 이 값만 그리고, 요청 순서와 화면 이동은 여기서 정한다.
// 판매글·결과는 브라우저 메모리에만 있고 어디에도 저장하지 않는다(ADR 2). 새로고침하면 사라진다.
import { useState } from 'react'
import { api, type ExtractResult } from './api.ts'
import { useRequest, type Route } from './hooks.ts'
import { buildCheckRequest, formFromExtracted, type FieldErrors, type ReviewForm } from './review.ts'
import type { Draft } from './screens/InputScreen.tsx'

const EMPTY_DRAFT: Draft = { text: '', image: null }
// ②가 칸 아래에 오류를 그릴 수 있는 칸. 서버 오류 중 이 칸만 ②로 가져간다.
const FORM_ERROR_KEYS = ['pricePerTicket', 'seat', 'quantity'] as const

function formFieldErrors(fields: Record<string, string> | null): FieldErrors {
  if (!fields) return {}
  return Object.fromEntries(FORM_ERROR_KEYS.filter((k) => fields[k]).map((k) => [k, fields[k]]))
}

/** ① 판매글 입력: 글·이미지와 추출 요청. 연결 실패·입력 오류는 ①에 그대로 남는다. */
function useDraftStep() {
  const extract = useRequest(api.extract)
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT)
  return {
    draft,
    extract,
    changeDraft: (next: Draft) => {
      if (extract.state.status === 'error') extract.reset()
      setDraft(next)
    },
    clearDraft: () => {
      setDraft(EMPTY_DRAFT)
      extract.reset()
    },
  }
}

/** ② 읽은 값 확인 + ③ 비교 요청. */
function useReviewStep() {
  const check = useRequest(api.check)
  const [aiResult, setAiResult] = useState<ExtractResult | null>(null)
  const [initialForm, setInitialForm] = useState<ReviewForm | null>(null)
  const [form, setForm] = useState<ReviewForm | null>(null)
  const [compareFailed, setCompareFailed] = useState(false)

  /** 추출 결과로 ② 폼을 채운다. null 이면 모두 지운다. */
  const load = (result: ExtractResult | null) => {
    const initial = result ? formFromExtracted(result.extracted) : null
    setAiResult(result)
    setInitialForm(initial)
    setForm(initial)
    setCompareFailed(false)
    check.reset()
  }
  // 값을 고치면 이전 비교 결과는 더 이상 이 값의 결과가 아니므로 지운다(앞으로 가기로 ③에 와도 낡은 결과가 안 보이게).
  const changeForm = (next: ReviewForm) => {
    check.reset()
    setCompareFailed(false)
    setForm(next)
  }
  const runCheck = (eventId: number) => {
    if (!form || !initialForm) return Promise.resolve(check.state)
    setCompareFailed(false)
    return check.run(buildCheckRequest(eventId, form, initialForm, aiResult?.evidence ?? null))
  }
  const serverErrors = check.state.status === 'error' ? formFieldErrors(check.state.error.fields) : {}
  return { aiResult, form, initialForm, compareFailed, setCompareFailed, serverErrors, checkState: check.state, load, changeForm, runCheck }
}

export function useListingFlow(eventId: number | undefined, navigate: (route: Route) => void) {
  const { draft, extract, changeDraft, clearDraft } = useDraftStep()
  const review = useReviewStep()

  /** ① → ②: 추출이 성공하면(AI 실패 fallback 포함) ②로. */
  const submitListing = async () => {
    if (eventId === undefined) return
    const outcome = await extract.run({ eventId, ...draft })
    if (outcome.status !== 'success') return
    review.load(outcome.data)
    navigate('review')
  }
  /**
   * ② → ③: ③이 로딩을 보여 주는 동안 비교한다. 요청을 먼저 시작해 ③이 열릴 때 이미 로딩 상태다.
   * 서버가 ②에 있는 칸의 오류(400)를 주면 ②로 돌아가 칸 아래에 보여 주고, 그 밖의 실패는 ③ 오류 상태로 둔다.
   */
  const compare = async () => {
    if (eventId === undefined) return
    const pending = review.runCheck(eventId)
    navigate('result')
    const outcome = await pending
    if (outcome.status === 'error' && Object.keys(formFieldErrors(outcome.error.fields)).length > 0) navigate('review')
  }
  /** "처음부터 다시"·"다른 판매글 확인하기": 모든 값을 지우고 ①로. */
  const restart = () => {
    clearDraft()
    review.load(null)
    navigate('input')
  }
  /** ③ → ②: 비교에 실패한 채 돌아가면 ② 버튼 위에 "비교하지 못했어요" 한 줄. */
  const backToReview = () => {
    review.setCompareFailed(review.checkState.status === 'error')
    navigate('review')
  }
  return { ...review, draft, changeDraft, clearDraft, extractState: extract.state, submitListing, compare, restart, backToReview }
}
