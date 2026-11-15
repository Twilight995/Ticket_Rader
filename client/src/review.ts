// 화면 ② 추출 결과 확인의 폼 값과 판단 없는 변환(순수 함수). tests/review.test.ts 가 검사한다.
import type { CheckRequest, DeliveryMethod, Evidence, Extracted } from './api.ts'

export interface ReviewForm {
  pricePerTicket: string
  seat: string
  quantity: string
  deliveryMethod: DeliveryMethod | ''
  prepaymentRequired: boolean | null
}
export type FieldErrors = Partial<Record<keyof ReviewForm, string>>

const FIELDS = ['pricePerTicket', 'seat', 'quantity', 'deliveryMethod', 'prepaymentRequired'] as const
const MAX_QUANTITY = 100

export function formFromExtracted(extracted: Extracted): ReviewForm {
  return {
    pricePerTicket: extracted.pricePerTicket?.toString() ?? '',
    seat: extracted.seat ?? '',
    quantity: extracted.quantity?.toString() ?? '',
    deliveryMethod: extracted.deliveryMethod ?? '',
    prepaymentRequired: extracted.prepaymentRequired,
  }
}

/** "250,000" 처럼 쉼표가 있어도 숫자로 본다. 빈 칸은 null, 숫자가 아니면 NaN. */
function toNumber(value: string): number | null {
  const plain = value.replaceAll(',', '').trim()
  if (plain === '') return null
  return /^\d{1,9}$/.test(plain) ? Number(plain) : NaN
}

export function validateForm(form: ReviewForm): FieldErrors {
  const errors: FieldErrors = {}
  if (Number.isNaN(toNumber(form.pricePerTicket))) errors.pricePerTicket = '숫자만 입력해 주세요'
  const quantity = toNumber(form.quantity)
  if (Number.isNaN(quantity)) errors.quantity = '숫자만 입력해 주세요'
  else if (quantity !== null && (quantity < 1 || quantity > MAX_QUANTITY)) {
    errors.quantity = `1장부터 ${MAX_QUANTITY}장까지 입력해 주세요`
  }
  return errors
}

/** 주인공 버튼 활성 조건: 가격 또는 전달 방식이 있고, 칸 오류가 없다. */
export function canCompare(form: ReviewForm): boolean {
  const hasPriceOrDelivery = toNumber(form.pricePerTicket) !== null || form.deliveryMethod !== ''
  return hasPriceOrDelivery && Object.keys(validateForm(form)).length === 0
}

/** 사용자가 고친 칸(처음 AI 값과 다른 칸)은 근거를 null 로 바꿔 보낸다. */
export function buildCheckRequest(
  eventId: number,
  form: ReviewForm,
  initial: ReviewForm,
  evidence: Evidence | null,
): CheckRequest {
  const keptEvidence = evidence
    ? (Object.fromEntries(FIELDS.map((f) => [f, form[f] === initial[f] ? evidence[f] : null])) as Evidence)
    : null
  return {
    eventId,
    pricePerTicket: toNumber(form.pricePerTicket),
    seat: form.seat.trim() || null,
    quantity: toNumber(form.quantity),
    deliveryMethod: form.deliveryMethod || null,
    prepaymentRequired: form.prepaymentRequired,
    evidence: keptEvidence,
  }
}
