// 절대 규칙 4: 화면 문구(소스·index.html) 어디에도 금지 단어와 "안전합니다"가 없다.
// 이 파일은 검사 대상(src, index.html) 밖에 있어서 단어 목록을 직접 적어도 된다.
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const FORBIDDEN = /암표|불법|사기|상습|범죄|안전합니다/
const CLIENT_ROOT = join(import.meta.dirname, '..')

function listFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    return statSync(path).isDirectory() ? listFiles(path) : [path]
  })
}

test('화면 소스와 index.html에 금지 단어가 없다', () => {
  const files = [
    ...listFiles(join(CLIENT_ROOT, 'src')).filter((p) => /\.(tsx?|css)$/.test(p)),
    join(CLIENT_ROOT, 'index.html'),
  ]
  const offenders = files.filter((p) => FORBIDDEN.test(readFileSync(p, 'utf8')))
  assert.deepEqual(offenders, [])
})
