// ⑤ 관리자 편집: URL 형식 안내, 편집용 변환, 저장 몸통
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { blankEvent, findUrlErrors, toDraft, toSaveBody } from '../src/admin.ts'

const saved = {
  id: 1,
  name: '2026 ○○ 콘서트 (가상)',
  startsAt: '2026-12-20T18:00:00',
  venue: '○○ 아레나 (가상)',
  category: 'CONCERT' as const,
  officialUrl: 'https://example.com/notice',
  priceTiers: [{ id: 3, grade: 'R', price: 143000, sourceUrl: 'https://example.com/notice#price', checkedOn: '2026-10-28' }],
  rules: [{ id: 5, type: 'ON_SITE_PICKUP_REQUIRED' as const, text: '현장 수령', sourceUrl: 'ftp://example.com', checkedOn: '2026-10-28' }],
}

test('http(s)로 시작하지 않는 주소만 칸 경로로 알려 준다', () => {
  assert.deepEqual(findUrlErrors(saved), { 'rules[0].sourceUrl': 'http로 시작하는 주소를 넣어 주세요' })
  assert.deepEqual(findUrlErrors(blankEvent()), {})
})

test('편집용으로 바꾸면 id 가 빠지고 일시는 분까지, 저장할 때 초를 붙인다', () => {
  const draft = toDraft(saved)
  assert.equal(draft.startsAt, '2026-12-20T18:00')
  assert.equal('id' in draft, false)
  assert.equal('id' in draft.priceTiers[0], false)
  assert.equal(toSaveBody(draft).startsAt, '2026-12-20T18:00:00')
})
