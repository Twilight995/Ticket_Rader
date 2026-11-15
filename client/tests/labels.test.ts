// ③ 공식 신고센터 링크: 공연 종류마다 맞는 분야 주소와 안내 이름
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { REPORT_CENTERS } from '../src/labels.ts'

test('공연은 공연 분야, 스포츠는 스포츠 분야 신고 주소로 잇는다', () => {
  assert.deepEqual(REPORT_CENTERS.CONCERT, { label: '공연 분야', url: 'https://ent.kocca.kr/ticket/receive.do' })
  assert.deepEqual(REPORT_CENTERS.SPORTS, { label: '스포츠 분야', url: 'https://cleanpass.kspo.or.kr' })
})
