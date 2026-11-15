// 서버는 영문 코드만 주고받고, 한국어 표시는 화면이 붙인다 (api-contract.md 0절 표).
import type { Category, DeliveryMethod, RuleType } from './api.ts'

export const DELIVERY_LABELS: Record<DeliveryMethod, string> = {
  ON_SITE_PICKUP: '본인 현장 수령',
  CAPTURE_QR: '캡처·QR 전송',
  ACCOUNT_TRANSFER: '계정 양도',
  MOBILE_TICKET_TRANSFER: '모바일 티켓 전송',
  OTHER: '기타',
}

export const RULE_LABELS: Record<RuleType, string> = {
  IDENTITY_CHECK_REQUIRED: '본인 확인 필수',
  TRANSFER_PROHIBITED: '양도 금지',
  CAPTURE_ENTRY_NOT_ALLOWED: '캡처 입장 불가',
  ON_SITE_PICKUP_REQUIRED: '현장 수령 필수',
  OTHER: '기타',
}

export const CATEGORY_LABELS: Record<Category, string> = { CONCERT: '공연', SPORTS: '스포츠' }

// ③ "공식 신고센터" 링크(공연 종류별 1개). 팀이 공식 주소를 확인해 넣기 전까지 비워 두고, 비어 있으면 버튼을 숨긴다.
export const REPORT_CENTER_URLS: Record<Category, string> = { CONCERT: '', SPORTS: '' }

/** "2026-12-20T18:00:00" → "2026년 12월 20일 18:00" (서버가 한국 시간으로 준다) */
export function formatStartsAt(startsAt: string): string {
  const [date, time = ''] = startsAt.split('T')
  const [y, m, d] = date.split('-').map(Number)
  return `${y}년 ${m}월 ${d}일 ${time.slice(0, 5)}`.trim()
}

export const formatWon = (value: number) => `${value.toLocaleString('ko-KR')}원`
