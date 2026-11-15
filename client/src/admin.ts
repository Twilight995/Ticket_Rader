// 화면 ⑤ 관리자 편집의 순수 함수. 서버가 다시 확인하고, 여기서는 칸 아래 안내를 먼저 보여 주려고 쓴다.
import type { EventRule, PriceTier, TicketEvent } from './api.ts'

const URL_MESSAGE = 'http로 시작하는 주소를 넣어 주세요'
const isHttpUrl = (value: string) => /^https?:\/\/\S+$/i.test(value.trim())

const today = () => new Date().toLocaleDateString('sv-SE') // YYYY-MM-DD (로컬 날짜)
export const newTier = (): PriceTier => ({ grade: '', price: 0, sourceUrl: '', checkedOn: today() })
export const newRule = (): EventRule => ({ type: 'OTHER', text: '', sourceUrl: '', checkedOn: today() })

export const blankEvent = (): TicketEvent => ({
  name: '',
  startsAt: '',
  venue: '',
  category: 'CONCERT',
  officialUrl: '',
  priceTiers: [],
  rules: [],
})

/** 칸 이름은 서버 오류 fields 와 같은 경로(`priceTiers[1].sourceUrl`)를 쓴다. 빈 칸은 서버가 "필수"로 알려 준다. */
export function findUrlErrors(event: TicketEvent): Record<string, string> {
  const errors: Record<string, string> = {}
  if (event.officialUrl && !isHttpUrl(event.officialUrl)) errors.officialUrl = URL_MESSAGE
  event.priceTiers.forEach((t, i) => {
    if (t.sourceUrl && !isHttpUrl(t.sourceUrl)) errors[`priceTiers[${i}].sourceUrl`] = URL_MESSAGE
  })
  event.rules.forEach((r, i) => {
    if (r.sourceUrl && !isHttpUrl(r.sourceUrl)) errors[`rules[${i}].sourceUrl`] = URL_MESSAGE
  })
  return errors
}

/** 서버에서 받은 공연을 편집용으로: id 를 빼고, 일시는 datetime-local 칸이 읽는 "YYYY-MM-DDTHH:mm" 으로. */
export function toDraft(event: TicketEvent): TicketEvent {
  return {
    name: event.name,
    startsAt: event.startsAt.slice(0, 16),
    venue: event.venue,
    category: event.category,
    officialUrl: event.officialUrl,
    priceTiers: event.priceTiers.map(({ grade, price, sourceUrl, checkedOn }) => ({ grade, price, sourceUrl, checkedOn })),
    rules: event.rules.map(({ type, text, sourceUrl, checkedOn }) => ({ type, text, sourceUrl, checkedOn })),
  }
}

/** 저장 요청 몸통: 계약서의 일시 모양 "YYYY-MM-DDTHH:mm:ss" 로 맞춘다. */
export function toSaveBody(draft: TicketEvent): TicketEvent {
  const startsAt = draft.startsAt.length === 16 ? `${draft.startsAt}:00` : draft.startsAt
  return { ...draft, startsAt }
}
