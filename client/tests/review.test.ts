// ② 추출 결과 확인의 순수 함수: 빈 폼, 입력 검사, /api/check 로 보낼 값 만들기
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { buildCheckRequest, canCompare, formFromExtracted, validateForm } from '../src/review.ts'

const extracted = {
  pricePerTicket: 250000,
  seat: 'VIP석 A구역',
  quantity: 2,
  deliveryMethod: 'CAPTURE_QR' as const,
  prepaymentRequired: true,
}
const evidence = {
  pricePerTicket: '장당 25만',
  seat: null,
  quantity: '2장',
  deliveryMethod: 'QR 캡처로 보내드려요',
  prepaymentRequired: '선입금 확인 후',
}
const empty = { pricePerTicket: null, seat: null, quantity: null, deliveryMethod: null, prepaymentRequired: null }

test('AI가 못 읽은 값(fallback)은 빈 폼이 된다', () => {
  assert.deepEqual(formFromExtracted(empty), {
    pricePerTicket: '', seat: '', quantity: '', deliveryMethod: '', prepaymentRequired: null,
  })
})

test('고치지 않은 칸은 근거를 그대로, 고친 칸은 근거를 null로 보낸다', () => {
  const initial = formFromExtracted(extracted)
  const edited = { ...initial, pricePerTicket: '150,000', prepaymentRequired: false }
  const request = buildCheckRequest(1, edited, initial, evidence)
  assert.deepEqual(request, {
    eventId: 1,
    pricePerTicket: 150000,
    seat: 'VIP석 A구역',
    quantity: 2,
    deliveryMethod: 'CAPTURE_QR',
    prepaymentRequired: false,
    evidence: { ...evidence, pricePerTicket: null, prepaymentRequired: null },
  })
})

test('빈 칸은 null로 보낸다', () => {
  const form = formFromExtracted(empty)
  const request = buildCheckRequest(1, { ...form, deliveryMethod: 'ON_SITE_PICKUP' }, form, null)
  assert.equal(request.pricePerTicket, null)
  assert.equal(request.seat, null)
  assert.equal(request.quantity, null)
  assert.equal(request.deliveryMethod, 'ON_SITE_PICKUP')
  assert.equal(request.evidence, null)
})

test('가격에 글자가 있으면 "숫자만 입력해 주세요", 수량은 1~100', () => {
  const form = formFromExtracted(empty)
  assert.equal(validateForm({ ...form, pricePerTicket: '25만' }).pricePerTicket, '숫자만 입력해 주세요')
  assert.equal(validateForm({ ...form, pricePerTicket: '250,000' }).pricePerTicket, undefined)
  assert.ok(validateForm({ ...form, quantity: '0' }).quantity)
  assert.ok(validateForm({ ...form, quantity: '101' }).quantity)
  assert.equal(validateForm({ ...form, quantity: '4' }).quantity, undefined)
})

test('가격이나 전달 방식 중 하나가 있고 오류가 없을 때만 비교할 수 있다', () => {
  const form = formFromExtracted(empty)
  assert.equal(canCompare(form), false)
  assert.equal(canCompare({ ...form, pricePerTicket: '150000' }), true)
  assert.equal(canCompare({ ...form, deliveryMethod: 'OTHER' }), true)
  assert.equal(canCompare({ ...form, pricePerTicket: '25만' }), false)
})
