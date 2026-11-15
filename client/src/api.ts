// docs/api-contract.md 의 요청·응답 모양과 호출 함수. 응답 봉투는 { ok, data, error } 하나.

export type Category = 'CONCERT' | 'SPORTS'
export type DeliveryMethod = 'ON_SITE_PICKUP' | 'CAPTURE_QR' | 'ACCOUNT_TRANSFER' | 'MOBILE_TICKET_TRANSFER' | 'OTHER'
export type RuleType = 'IDENTITY_CHECK_REQUIRED' | 'TRANSFER_PROHIBITED' | 'CAPTURE_ENTRY_NOT_ALLOWED' | 'ON_SITE_PICKUP_REQUIRED' | 'OTHER'
export type Severity = 'INFO' | 'CAUTION' | 'CHECK'

export interface PriceTier { id?: number; grade: string; price: number; sourceUrl: string; checkedOn: string }
export interface EventRule { id?: number; type: RuleType; text: string; sourceUrl: string; checkedOn: string }
export interface TicketEvent {
  id?: number
  name: string
  startsAt: string
  venue: string
  category: Category
  officialUrl: string
  priceTiers: PriceTier[]
  rules: EventRule[]
}

export interface Extracted {
  pricePerTicket: number | null
  seat: string | null
  quantity: number | null
  deliveryMethod: DeliveryMethod | null
  prepaymentRequired: boolean | null
}
export type Evidence = { [K in keyof Extracted]: string | null }
export interface ExtractResult {
  aiStatus: 'ok' | 'fallback'
  extracted: Extracted
  evidence: Evidence | null
  maskedText: string | null
}

export interface CheckRequest extends Extracted { eventId: number; evidence: Evidence | null }
export interface Finding {
  type: string
  title: string
  severity: Severity
  severityLabel: string
  listingQuote: string
  official: { text: string; sourceUrl: string; checkedOn: string } | null
  explanation: string
}
export interface CheckResult { summary: string; findings: Finding[]; officialUrl: string; notice: string }

/** 서버가 준 한국어 문구와 칸별 이유. status 0 은 서버에 닿지 못한 경우. */
export class ApiError extends Error {
  readonly status: number
  readonly fields: Record<string, string> | null
  constructor(status: number, message: string, fields: Record<string, string> | null = null) {
    super(message)
    this.status = status
    this.fields = fields
  }
}

const CONNECTION_FAILED = '연결하지 못했어요. 잠시 후 다시 시도해 주세요'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch {
    throw new ApiError(0, CONNECTION_FAILED)
  }
  const body = await response.json().catch(() => null)
  if (!response.ok || !body?.ok) {
    throw new ApiError(response.status, body?.error?.message ?? CONNECTION_FAILED, body?.error?.fields ?? null)
  }
  return body.data as T
}

const jsonInit = (method: string, data: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(data),
})

export const api = {
  getEvent: () => request<TicketEvent | null>('/api/event'),
  extract: (input: { eventId: number; text: string; image: File | null }) => {
    const form = new FormData()
    form.append('eventId', String(input.eventId))
    if (input.text.trim()) form.append('text', input.text)
    if (input.image) form.append('image', input.image)
    return request<ExtractResult>('/api/extract', { method: 'POST', body: form })
  },
  check: (body: CheckRequest) => request<CheckResult>('/api/check', jsonInit('POST', body)),
  // 관리자 비밀번호는 브라우저 기본 로그인창이 받고, 브라우저가 Authorization 헤더를 붙인다.
  getAdminEvent: () => request<TicketEvent | null>('/api/admin/event'),
  saveAdminEvent: (event: TicketEvent) => request<TicketEvent>('/api/admin/event', jsonInit('PUT', event)),
}
