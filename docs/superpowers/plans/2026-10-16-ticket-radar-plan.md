# 티켓레이더 구현 계획

- 작성일: 2026-10-16 (v2: 판매자 지문·신고 자료 묶음 추가, 공연 등록 화면 제외, 브라우저 확장은 선택 6단계)
- 기준 문서: `docs/superpowers/specs/2026-10-16-ticket-radar-design.md` v2 (승인된 설계 명세서, 이하 "명세")
- 전제: 팀 3명(역할 3개), 3주, 1인당 주 20시간 안팎. 2명일 때의 조정은 9절 끝에 적었다.
- 작성: ecc:planner 에이전트 초안 → 검토·보강 → v2 반영

v2에서 바뀐 것 요약: ① 신호 9종(SELLER_PATTERN 추가) ② 새 작업 T1-11 판매자 지문, T2-8 신고 자료·지문 API, T4-11 지문 탭, T4-12 신고 자료 페이지, T5-9 보관 기간 정리 ③ T4-9 공연 등록 화면 제외(seed로 대체), T4-8은 T4-11로 흡수 ④ T3-5 캡처 입력은 선택 ⑤ 6단계 브라우저 확장(선택) 추가 ⑥ 결정 D15~D18 추가

---

## 1. 개요

팬이 붙여넣은 중고 티켓 판매글을 AI가 항목별로 정리하고, 코드로 만든 규칙 엔진이 공식 정가·입장 규정과 비교해 "검토가 필요한 매물" 신호를 근거와 함께 보여주는 웹 서비스를 3주 안에 시연 가능한 상태로 만든다. 규칙 엔진·유사도·마스킹은 테스트를 먼저 쓰는 TDD(테스트 주도 개발: 실패하는 테스트를 먼저 쓰고, 그 테스트를 통과시키는 코드를 나중에 쓰는 방식)로 만들고, AI가 실패해도 사람이 직접 입력해 끝까지 쓸 수 있게 한다. 작업은 공통 "계약"(화면·서버·AI가 함께 쓰는 데이터 모양 정의)을 첫날 정해 두고, 그 뒤로는 세 역할이 서로 기다리지 않고 동시에 진행하는 구조로 짰다.

---

## 2. 0단계: 프로젝트 뼈대

### 2.0 작업 공통 규칙 (모든 단계에 적용)

| 항목 | 규칙 |
|---|---|
| 브랜치 | `main` 보호 + 작업마다 짧은 브랜치 `feat/T1-5-rule-engine` 형식. 하루~이틀 안에 PR(풀 리퀘스트: 내 변경을 main에 합쳐 달라는 요청) |
| 이슈 | 아래 작업 ID(T0-1 등)마다 GitHub Issue 1개. 진행 상황은 이슈에 기록 |
| 작업 순서 | ① 먼저 쓸 테스트 작성 → 실행해서 **실패 확인(RED)** → ② 최소 구현으로 통과(GREEN) → ③ 정리(REFACTOR) → ④ PR + 코드 리뷰(`ecc:code-reviewer`) |
| 커밋 메시지 | `feat: 규칙 엔진 정가 초과 신호 추가`처럼 `type: 설명` 형식 (feat/fix/test/refactor/docs/chore) |
| 완료 정의 | 테스트 통과, 타입 검사 통과, 린트 통과, 리뷰 1명 승인, `console.log` 없음, 함수 50줄 이하 |
| 문구 | 결과 명칭은 항상 **"검토가 필요한 매물"**. 화면·설명 문장에 법적 판단을 단정하는 표현을 쓰지 않음 (T3-1의 검사 테스트로 자동 확인) |

### 2.1 착수 전 팀이 확정할 결정 (명세 보완 사항)

명세를 코드로 옮기다 보니 빈칸이거나 해석이 둘로 갈리는 부분이 있었다. T0-0 회의에서 아래 권장안을 승인하거나 바꾼 뒤 시작한다.

| # | 빈칸 / 모호한 점 | 권장 결정 | 이유 |
|---|---|---|---|
| D1 | 판매글의 가격이 "1장당"인지 "총액"인지 정해져 있지 않음 ("2장 30만 원") | 추출 스키마의 `price`는 **1장당 가격**으로 정의. 폼 라벨도 "1장당 가격(원)" | 배율(가격 ÷ 정가) 계산이 1장 기준이라야 맞음 |
| D2 | 신고센터 링크를 공연/스포츠로 나눠야 하는데 `events`에 종류 칸이 없음 | `events.category` (`performance` \| `sports`) 칸 추가 | 하단 링크 선택에 필요 |
| D3 | 정가 초과 신호의 근거에 출처 URL·확인 시각이 필요한데 `price_tiers`에 칸이 없음 | `price_tiers`에 `sourceUrl`, `checkedAt` 추가 | 모든 신호 카드에 출처를 보여준다는 원칙 유지 |
| D4 | 규정 유형이 한글 값(본인확인필수 등) | DB에는 영문 코드(`identity_required`, `transfer_prohibited`, `screenshot_entry_banned`, `onsite_pickup_required`, `other`), 화면에는 한글 라벨 | 코드 비교 시 오타·인코딩 문제 방지 |
| D5 | 한 매물이 여러 규정과 동시에 충돌하면 카드가 몇 장 나오는지 | **신호 코드 1종당 카드 최대 1장**. 전달 방식 충돌은 설정 파일의 우선순위가 가장 높은 규정 1개를 근거로 표시 | 카드 수를 예측할 수 있어야 시연("카드 3장")이 흔들리지 않음 |
| D6 | "판매글이 양도를 제안"의 판정 기준 | 공연에 `transfer_prohibited` 규정이 있고, 추출 결과에 가격 또는 전달 방식이 하나라도 있으면 발생 | 판매글은 사실상 모두 양도 제안이라 별도 판단이 무의미 |
| D7 | D6 때문에 양도 금지 공연에서는 "정가 이하 1회 양도 → 신호 0개"가 성립하지 않음 | **신호 0개 사례·시연은 양도 금지 규정이 없는 공연으로** 한다. seed 공연 2건 중 1건(콘서트)은 양도 금지 규정을 두지 않음 | 명세의 "신호 0개 사례 필수"를 지키기 위함 |
| D8 | 반복 게시 신호의 "비교 대상 매물 ID"를 넣을 칸이 신호 모양에 없음 | 신호에 선택 칸 `meta: { priceRatio?, relatedListingIds? }` 추가 | 기존 모양은 그대로 두고 확장만 함 |
| D9 | 화면과 서버가 같은 Zod 스키마·타입을 써야 함 | `shared/` 워크스페이스 추가. 명세의 `server/src/ai/schema.ts`는 shared를 다시 내보내는 얇은 파일로 둠 | 같은 정의를 두 번 쓰면 둘 중 하나만 고치는 실수가 생김 |
| D10 | Node 20은 **2026-04-30에 지원이 끝남**(EOL: 보안 패치가 더 이상 나오지 않는 상태) | **Node 22 LTS 이상 권장**. 명세대로 20을 유지할 경우 better-sqlite3 최신판이 20을 지원하는지 설치 시 확인 | 오늘(2026-10-16) 기준 사실. 단, 각 라이브러리의 지원 범위는 설치 시점에 확인 필요 |
| D11 | 화면 이동(라우팅)·서버 데이터 관리 방식이 명세에 없음 | React Router + TanStack Query(서버에서 받은 데이터를 저장해 두고, 바뀌면 자동으로 다시 받아오는 라이브러리) 추가 | 검토 상태를 바꾼 뒤 목록을 새로고침하는 코드를 직접 짜지 않아도 됨 |
| D12 | 심각도 값 | `info`(정보) / `caution`(주의) / `check`(확인 필요) | 화면 색과 정렬 순서에 사용 |
| D13 | 원문 마스킹 시점 | 원문 텍스트는 **LLM에 보내기 전에** 정규식으로 마스킹하고, LLM 응답(원문 구절·연락 채널)에도 한 번 더 적용 | 외부로 나가는 개인정보를 최소로 줄임 (명세의 두 번의 마스킹을 모두 유지) |
| D14 | 대시보드 "정가 초과 매물 비율"의 분모 | 배율을 계산할 수 있는 매물 수(정가 대조 불가 매물 제외). 대조 불가 건수는 타일에 따로 표시 | 분모가 섞이면 비율이 왜곡됨 |
| D15 | 판매자 지문의 해시 방식 | 연락처를 정규화(숫자만, 소문자)한 뒤 `FINGERPRINT_SALT` + SHA-256. 솔트는 `.env`에만. 원문은 어디에도 저장하지 않음 | 같은 번호인지 비교는 되지만 번호를 되돌릴 수는 없게 |
| D16 | 지문 묶음 연결 기준 | 연락처 해시 일치만 묶음 병합. 문구 유사도는 묶음 안 "유사 글" 표시로만 사용 | 문구가 비슷하다고 같은 사람으로 묶으면 오연결 위험 |
| D17 | 신고 자료 형식 | 인쇄용 HTML 페이지(`@media print`). 팬이 브라우저 "PDF로 저장"으로 내려받음. 서버 PDF 생성 라이브러리는 쓰지 않음 | 의존성 하나 줄이고 디자인을 CSS로 통제 |
| D18 | 브라우저 확장(6단계) 착수 조건 | T5-1 E2E가 3회 연속 통과하고 3주차 수요일 전이면 착수. 아니면 발표 "확장" 장표로만 | 필수 범위가 먼저 끝나야 함 |

### 2.2 폴더 구조 (저장소 루트 `C:\Projects\Daelim_ai`가 명세의 `ticket-radar/`에 해당)

모노레포(monorepo: 화면·서버·공통 코드를 저장소 하나에 함께 두는 방식)와 npm 워크스페이스(workspace: 한 저장소 안의 여러 패키지를 npm이 한꺼번에 설치·연결해 주는 기능)를 쓴다.

```
Daelim_ai/
  package.json            워크스페이스(shared, server, client)와 공통 스크립트
  tsconfig.base.json      공통 TypeScript 설정 (strict)
  .env.example            환경 변수 예시 (실제 값은 .env, 커밋 금지)
  .gitignore              node_modules, dist, .env, data/, uploads/, coverage/
  .dockerignore
  Dockerfile
  playwright.config.ts
  e2e/check-to-dashboard.spec.ts
  .github/workflows/ci.yml
  shared/src/             enums.ts, labels.ts, extraction.ts, signal.ts, api.ts, index.ts
  server/src/
    index.ts, app.ts       서버 시작 / 앱 조립(createApp)
    config/env.ts          환경 변수 검증
    routes/                health.ts, events.ts, extract.ts, listings.ts, fingerprints.ts, report.ts
    middleware/            validate.ts, errorHandler.ts, basicAuth.ts, rateLimit.ts, upload.ts(선택)
    rules/                 rules.config.ts, seatMatch.ts, engine.ts, similarity.ts, grouping.ts, distribution.ts
    fingerprint/           normalize.ts, hash.ts, cluster.ts, stats.ts     ← 판매자 지문 (v2)
    services/              listingService.ts, reportService.ts
    ai/                    client.ts, schema.ts, prompts.ts, extract.ts, explain.ts, explainTemplates.ts, legalTone.ts, cache.ts
    db/                    schema.sql, index.ts, repositories/(events, listings, fingerprints, aiCache).ts, seed/(events.json, listings.json, run.ts)
    lib/                   mask.ts, response.ts, hash.ts, logger.ts, imageType.ts(선택), legal.ts(법 조문 요지·신고센터 상수)
  server/test/             API 통합 테스트
  server/scripts/          validation-report.ts, clean-uploads.ts, retention.ts
  extension/               (선택, 6단계) manifest.json, content.ts, popup.html
  client/src/
    main.tsx, App.tsx, styles/index.css
    api/                   http.ts, hooks.ts
    components/ui/         Button, Card, Badge, Field, Select, Spinner, Notice
    components/check/      ListingInput, ExtractionForm, SignalCard, SignalList, FingerprintLine, ResultFooter
    components/dashboard/  SummaryTiles, RatioHistogram, ListingTable, ListingDetail, FingerprintTable, FingerprintDetail
    components/report/     ReportSheet (인쇄용)
    pages/                 CheckPage, DashboardPage, ReportPage
    lib/format.ts          원 단위·날짜·배율 표시
  docs/                    specs/, plans/, validation.md, demo-script.md
```

테스트 파일은 대상 파일 옆에 `*.test.ts(x)`로 둔다 (API 통합 테스트만 `server/test/`).

### 2.3 작업 목록

#### T0-0 착수 결정 회의
- ① 파일: `docs/decisions.md` (2.1절 표의 최종 결정 기록)
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: D1~D14 각각 "승인/변경" 표시, 이슈 보드에 T0-1~T5-8 이슈 생성
- ④ 예상: 1h (전원 참석 30분 + 정리)
- ⑤ 담당: AI·발표 (회의 진행)

#### T0-1 루트 모노레포 설정
- ① 파일: `package.json`, `tsconfig.base.json`, `.gitignore`, `.env.example`, `.editorconfig`, `eslint.config.js`, `.prettierrc`
- ② 먼저 쓸 테스트: 해당 없음 (설정 작업)
- ③ 완료 기준
  - `npm install` 한 번으로 세 패키지가 설치됨
  - 루트 스크립트가 존재함: `dev`(서버·화면 동시 실행), `build`, `test`, `test:coverage`, `test:e2e`, `lint`, `typecheck`, `seed`, `start`, `clean:uploads`
  - `.env.example`에 `ANTHROPIC_API_KEY=`, `ANTHROPIC_MODEL=claude-opus-5-5`, `ADMIN_PASSWORD=`, `FINGERPRINT_SALT=`, `RETENTION_DAYS=90`, `PORT=3000`, `DATABASE_PATH=./data/ticket-radar.db`, `UPLOAD_DIR=./uploads`, `AI_TIMEOUT_MS=20000`, `SEED_ON_START=false`
  - `.env`, `data/`, `uploads/`가 `.gitignore`에 포함됨
- ④ 예상: 2h
- ⑤ 담당: 화면

#### T0-2 공통 계약 패키지 (shared)
- ① 파일: `shared/src/enums.ts`, `labels.ts`, `extraction.ts`, `signal.ts`, `api.ts`, `index.ts`, `shared/src/extraction.test.ts`
- ② 먼저 쓸 테스트 (`extraction.test.ts`)
  - 명세 6절 모양의 정상 JSON은 통과
  - `deliveryMethod: "telegram"`처럼 목록에 없는 값은 실패
  - `price: -1000`, `price: 1.5`(정수 아님), `quantity: 0`은 실패
  - 모든 값이 `null`이고 `prepaymentRequested: false`, `contacts: []`, `quotes: []`인 "빈 추출 결과"는 통과 (AI 실패 시 쓰는 기본값)
  - `contacts: [{ type: "phone", value: "010-0000-1234" }]`는 통과, `type: "email"`은 실패
  - 공연 seed 스키마에서 `sourceUrl: "javascript:alert(1)"`은 실패 (http/https만 허용)
  - 클라이언트로 나가는 매물 타입(`ListingPublic`)에는 `contacts` 칸이 아예 없음 (타입 수준에서 연락처 유출 차단)
- ③ 완료 기준
  - Zod(입력 데이터의 모양을 선언해 두고 자동으로 검사해 주는 라이브러리) 스키마: `ExtractionSchema`, `EMPTY_EXTRACTION`, `EventSeedSchema`, `ListingCreateSchema`, `ReviewUpdateSchema`, `ReportStatusUpdateSchema`
  - 타입: `Signal`, `Evidence`, `Severity`, `SignalCode`(9종), `FingerprintSummary`, `ReportData`, `ApiResponse<T>`, `DashboardResponse`
  - `labels.ts`: 신호·심각도·전달 방식·규정 유형·검토 상태의 한글 라벨, 고정 문구("이 결과는 참고용이며 최종 판단은 사용자가 합니다"), 신고센터 링크 상수 (KOCCA 신고센터 공식 주소는 담당자가 직접 확인해서 입력. 추측해서 넣지 않음)
  - 테스트 통과
- ④ 예상: 3h
- ⑤ 담당: 데이터·규칙

#### T0-3 서버 뼈대
- ① 파일: `server/package.json`, `tsconfig.json`, `vitest.config.ts`, `tsup.config.ts`, `src/index.ts`, `src/app.ts`, `src/config/env.ts`, `src/routes/health.ts`, `src/lib/logger.ts`, `server/test/health.test.ts`
- ② 먼저 쓸 테스트
  - `GET /api/health` → 200, `{ ok: true, data: { status: "up" }, error: null }` (supertest: 서버를 실제로 띄우지 않고 HTTP 요청을 보내 보는 테스트 도구)
  - `env.ts`: `ADMIN_PASSWORD`가 없으면 시작 시 오류. `ANTHROPIC_API_KEY`가 없으면 오류가 아니라 "AI 꺼짐" 상태로 시작
- ③ 완료 기준
  - Express(Node.js 웹 서버 프레임워크) 앱을 `createApp(deps)` 함수로 만듦 (테스트에서 가짜 DB·가짜 AI를 끼워 넣을 수 있게)
  - `npm run dev -w server`로 시작, `npm test -w server` 통과
  - Vitest 커버리지(테스트가 코드의 몇 %를 실행했는지) 기준: `src/rules/**`, `src/lib/**` 80% 미만이면 실패하도록 설정
  - 빌드는 tsup(TypeScript를 한 파일로 묶어 주는 도구)으로 shared까지 함께 묶고, better-sqlite3는 묶지 않음(external)
- ④ 예상: 3h
- ⑤ 담당: 화면 (명세 11절의 "프로젝트 뼈대 = 화면 담당"을 따름)

#### T0-4 화면 뼈대
- ① 파일: `client/package.json`, `vite.config.ts`, `index.html`, `src/main.tsx`, `src/App.tsx`, `src/styles/index.css`, `src/api/http.ts`, `src/pages/*.tsx`(빈 화면 3개)
- ② 먼저 쓸 테스트: `src/api/http.test.ts` — 서버 응답 `{ ok: false, error: "..." }`를 받으면 그 메시지를 담은 오류를 던지는지
- ③ 완료 기준
  - `/check`, `/dashboard`, `/report/:listingId` 주소로 이동 가능 (React Router)
  - 대시보드·신고 자료 화면은 `React.lazy`로 코드 분할(처음 접속 때 필요한 코드만 내려받고 나머지는 해당 화면에 들어갈 때 받는 방식)
  - Tailwind의 `@theme`에 디자인 토큰(색·글꼴·여백 값을 이름 붙여 한곳에 모아 둔 것) 정의: 심각도 3색, 기본 여백, 시스템 글꼴
  - Vite 개발 서버가 `/api` 요청을 서버(3000번 포트)로 넘겨줌(proxy)
- ④ 예상: 4h
- ⑤ 담당: 화면

#### T0-5 Docker 초안
- ① 파일: `Dockerfile`, `.dockerignore`
- ② 먼저 쓸 테스트: 해당 없음 (빌드 확인으로 대신함)
- ③ 완료 기준
  - 멀티 스테이지 빌드(빌드용 이미지에서 결과물만 만들고, 실행용 이미지에는 결과물만 복사해 크기를 줄이는 방식)
  - `docker build` 성공, 컨테이너 안에서 `/api/health` 200
  - 빌드 단계와 실행 단계의 Node 버전·OS가 같음 (better-sqlite3는 OS별로 컴파일된 파일을 쓰기 때문)
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T0-6 CI (자동 검사)
- ① 파일: `.github/workflows/ci.yml`
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: PR마다 `lint`, `typecheck`, `test:coverage`가 GitHub Actions에서 자동 실행되고, 실패하면 PR에 빨간 표시가 뜸 (CI: 코드를 올릴 때마다 검사를 자동으로 돌려 주는 장치)
- ④ 예상: 1.5h
- ⑤ 담당: AI·발표

---

## 3. 1단계: 데이터·규칙 엔진 (테스트 먼저)

이 단계의 모든 함수는 **순수 함수**(같은 입력이면 항상 같은 결과를 내고, 바깥 상태를 바꾸지 않는 함수)로 만든다. 입력 객체를 직접 고치지 않고 새 객체를 돌려준다. 테스트에서는 입력에 `Object.freeze`를 걸어 이것을 확인한다.

#### T1-1 개인정보 마스킹
- ① 파일: `server/src/lib/mask.ts`, `mask.test.ts`
- ② 먼저 쓸 테스트
  - 휴대폰 번호: `010-1234-5678`, `01012345678`, `010 1234 5678`, `010.1234.5678`, `+82 10-1234-5678` → `[삭제]`
  - 계좌번호: `110-123-456789`, `3333-01-1234567`, `1002-123-456789`, 숫자 10~14자리 연속 → `[삭제]`
  - **지우면 안 되는 것**: `150,000원`, `2026-10-16`, `19:30`, `A구역 12열 5번`, `2장`, `3층 R석` (날짜·가격·좌석이 지워지면 판정이 깨짐)
  - 한 글에 여러 번 나오면 모두 치환
  - 두 번 적용해도 결과가 같음 (`mask(mask(x)) === mask(x)`)
  - `maskExtraction(e)`는 `quotes[].text`, `contactChannel`을 마스킹한 **새 객체**를 돌려주고 원본은 그대로
- ③ 완료 기준: 위 테스트 통과, `lib/` 커버리지 80% 이상, 정규식마다 무엇을 잡는지 주석 한 줄
- ④ 예상: 3h
- ⑤ 담당: 데이터·규칙

#### T1-2 문장 유사도
- ① 파일: `server/src/rules/similarity.ts`, `similarity.test.ts`
- ② 먼저 쓸 테스트
  - `normalize`: 공백·구두점 제거, 영문 소문자화
  - 같은 글 → 1.0
  - 전혀 다른 판매글 2개(실제처럼 쓴 한국어 글) → 0.2 이하
  - 띄어쓰기만 다른 글 → 0.9 이상
  - 구두점만 다른 글 → 0.9 이상
  - 긴 글에서 숫자 1~2개만 바꾼 글 → 0.8 이상
  - 빈 문자열이나 정규화 후 1글자 이하 → 0 (빈 글 두 개가 "1.0 같음"으로 묶이는 것 방지)
  - `sim(a, b) === sim(b, a)` (순서를 바꿔도 같음)
- ③ 완료 기준: 외부 라이브러리 없이 구현. 글자 2-gram(연속한 두 글자씩 잘라 만든 조각. "티켓팝" → "티켓", "켓팝") 집합의 자카드 유사도(두 집합의 공통 원소 수 ÷ 합친 원소 수, 0~1 사이) 계산. 테스트 통과
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

#### T1-3 반복 게시 묶음
- ① 파일: `server/src/rules/grouping.ts`, `grouping.test.ts`
- ② 먼저 쓸 테스트
  - A≈B, B≈C(각각 0.8 이상)이고 A와 C는 0.8 미만 → `{A, B, C}` 한 묶음 (연결된 것끼리 모두 묶음)
  - 어느 것과도 비슷하지 않은 매물은 묶음에 나오지 않음
  - 기준값을 0.95로 바꾸면 결과가 달라짐
  - 매물 0건, 1건 → 빈 배열
- ③ 완료 기준: `groupSimilar(listings, threshold)`가 묶음 배열을 돌려줌. 전수 비교(모든 쌍을 비교, 데모 규모 40~100건이면 충분. 수천 건이 넘으면 색인이 필요하다는 주석을 남김)
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

#### T1-4 규칙 설정 파일과 좌석 등급 맞추기
- ① 파일: `server/src/rules/rules.config.ts`, `seatMatch.ts`, `seatMatch.test.ts`
- ② 먼저 쓸 테스트 (`seatMatch.test.ts`)
  - 등급 `["VIP", "R", "S"]`, 좌석 `"VIP석 A구역 3열"` → VIP
  - 등급 `["S", "SR"]`, 좌석 `"SR석"` → SR (여러 개가 맞으면 **가장 긴 이름** 선택)
  - 대소문자 무시: `"vip석"` → VIP
  - `"스탠딩"`처럼 맞는 등급이 없으면 → `null`
  - `seatLabel`이 `null` → `null`
- ③ 완료 기준
  - `rules.config.ts`에 숫자를 모두 모음: 배율 주의 기준 1.0, 확인 필요 기준 1.5, 대량 수량 4, 유사도 0.8, 외부 채널 단어 목록(오픈채팅, 오픈톡, open.kakao.com, 텔레그램, telegram, t.me 등), 전달 방식 ↔ 규정 유형 충돌표와 우선순위, 원문 최대 길이(5,000자)
  - 충돌표 기본안 (팀 검토 후 확정): `screenshot_qr` ↔ 캡처 입장 불가·현장 수령 필수·본인 확인 필수 / `account_transfer` ↔ 본인 확인 필수·현장 수령 필수 / `mobile_transfer` ↔ 현장 수령 필수
  - 테스트 통과
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

#### T1-5 규칙 엔진 (신호 9종)
- ① 파일: `server/src/rules/engine.ts`, `engine.test.ts`, `server/src/rules/__fixtures__/events.ts`(테스트용 가상 공연)
- ② 먼저 쓸 테스트 (신호마다 "발생" / "미발생"을 둘 다 씀)
  - **정가 이하 1회 양도**(가격 = 정가, 수량 1, 허용된 전달 방식, 선입금 없음, 외부 연락처 없음, 양도 금지 규정이 없는 공연, 지문 묶음 없음) → **신호 0개**
  - `SELLER_PATTERN`: 입력으로 받은 지문 통계가 매물 2건·공연 1건 → 없음 / 매물 3건·공연 1건 → 주의 / 매물 2건·공연 2건 → 확인 필요, `meta`에 `fingerprintId`·`listingCount`·`eventCount`·`totalQuantity` / 지문 없음(`null`) → 없음 / 설정을 매물 5건으로 바꾸면 3건 → 없음. 설명 템플릿에 "상습"이라는 단어가 없음
  - `PRICE_ABOVE_FACE`: 배율 1.0 → 없음 / 1.2 → 주의 / 1.5 → 주의(경계값) / 1.51 → 확인 필요 / 2.5 → 확인 필요, `meta.priceRatio === 2.5`. 근거 문장에 등급명·정가가 들어가고 출처는 정가표의 `sourceUrl`·`checkedAt`
  - `PRICE_UNKNOWN_SEAT`: 좌석 등급을 못 맞춤 → 정보, 근거 `null`. 가격이 `null`이면 가격 관련 신호 둘 다 없음
  - `PREPAYMENT`: `true` → 확인 필요, 근거 `null`(일반 주의), 원문 구절은 `quotes`에서 가져옴 / `false` → 없음
  - `DELIVERY_CONFLICT`: QR 캡처 전송 + 현장 수령 필수 → 확인 필요, 해당 규정 문장·URL·확인 시각이 근거 / 충돌 규정 2개 → 우선순위 높은 1개만(카드 1장) / 현장 수령 + 현장 수령 필수 → 없음 / 전달 방식 `null` → 없음
  - `TRANSFER_PROHIBITED`: 양도 금지 규정 있음 → 확인 필요 + 규정 근거 / 없음 → 없음
  - `BULK_QUANTITY`: 3 → 없음 / 4 → 주의 / 설정을 6으로 바꾸면 4 → 없음(**기준값 변경 반영**) / `null` → 없음
  - `REPEATED_POST`: 같은 공연의 다른 매물과 유사도 1.0 → 주의, `meta.relatedListingIds`에 상대 ID / 0.79 → 없음
  - `OFF_PLATFORM_CONTACT`: 연락 채널 "오픈채팅" → 정보 / 원문에 "텔레그램 @abc" → 정보 / "댓글 주세요" → 없음
  - **시연 글 고정 테스트**: 명세 12절의 시연 글(정가 2.5배 + 선입금 + QR 캡처, 현장 수령 필수 공연) → 정확히 `[PRICE_ABOVE_FACE, PREPAYMENT, DELIVERY_CONFLICT]` 3개
  - 같은 입력을 두 번 넣으면 결과가 완전히 같음 (재현성)
  - 정렬: 심각도 높은 순 → 신호 코드 순서
- ③ 완료 기준
  - `evaluate({ extracted, rawText, event, otherListings, fingerprintStats }, config)` → `SignalDraft[]` (설명 문장은 비워 두고 서비스 단계에서 채움). 엔진은 지문을 계산하지 않고 통계만 입력으로 받는다(T1-11과 분리)
  - 엔진 안에 숫자가 직접 쓰여 있지 않음 (전부 `rules.config.ts`)
  - 신호별 판정을 작은 함수 9개로 나누고 `evaluate`는 이를 모으기만 함 (함수 50줄 이하 유지)
  - `rules/` 커버리지 80% 이상
- ④ 예상: 9h
- ⑤ 담당: 데이터·규칙

#### T1-11 판매자 지문 (v2 신규)
- ① 파일: `server/src/fingerprint/normalize.ts`, `hash.ts`, `cluster.ts`, `stats.ts`, 각 `*.test.ts`
- ② 먼저 쓸 테스트
  - `normalize`: `010-1234-5678`, `01012345678`, `+82 10 1234 5678` → 같은 문자열 / 계좌 `110-123-456789`와 `110123456789` → 같음 / 텔레그램 `@AbC`와 `abc` → 같음 / `open.kakao.com/o/xyz`는 경로까지 포함
  - `hash`: 같은 입력 + 같은 솔트 → 같은 해시 / 솔트가 다르면 다른 해시 / 해시 결과에 원문 숫자가 들어 있지 않음 / 솔트가 비어 있으면 시작 시 오류
  - `cluster`: 매물 A·B가 해시 하나를 공유 → 한 묶음 / A-B 공유, B-C 공유 → {A,B,C} / 공유 없음 → 각자 묶음 없음(`null`) / 기존 묶음 2개가 새 매물로 연결되면 하나로 합쳐지고 작은 쪽 ID가 사라짐 / 문구 유사도만 높고 해시가 다르면 묶지 않음(D16)
  - `stats`: 묶음의 매물 수, 공연 수(중복 제거), 총 수량(`null` 수량은 0), 정가 초과 건수, 첫·마지막 게시일 / 매물 1건 → 공연 1, 매물 1
  - 묶음 ID는 `FP-` + 영숫자 4자리, 생성마다 다름, 해시 값과 무관
  - 입력 객체를 바꾸지 않음(`Object.freeze`)
- ③ 완료 기준: 순수 함수로 구현, 외부 라이브러리 없음(해시는 Node `crypto`), `fingerprint/` 커버리지 80% 이상. 유니온-파인드(연결된 것끼리 모두 한 묶음으로 만드는 알고리즘)는 데모 규모(수백 건)에 맞춰 단순하게. (ponytail: 수만 건이면 DB 쪽 인덱스 병합으로 교체)
- ④ 예상: 6h
- ⑤ 담당: 데이터·규칙

#### T1-6 배율 분포와 요약 계산
- ① 파일: `server/src/rules/distribution.ts`, `distribution.test.ts`
- ② 먼저 쓸 테스트
  - 경계값: 1.0 → `≤1.0` / 1.0001 → `1.0~1.5` / 1.5 → `1.0~1.5` / 2.0 → `1.5~2.0` / 3.0 → `2.0~3.0` / 3.01 → `3.0+`
  - 배율 `null`은 구간에 넣지 않고 `unknownCount`로 셈
  - 요약: 매물 수, 정가 초과 비율(D14 기준), 검토 대기 수
  - 매물 0건 → 비율 0, 0으로 나누기 오류 없음
- ③ 완료 기준: `bucketRatio`, `buildDistribution`, `buildSummary` 테스트 통과
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

#### T1-7 DB 스키마와 저장소
- ① 파일: `server/src/db/schema.sql`, `db/index.ts`, `db/repositories/events.ts`, `listings.ts`, `aiCache.ts`, 각 `*.test.ts`
- ② 먼저 쓸 테스트 (메모리 DB `:memory:` 사용, 파일을 만들지 않음)
  - 공연 + 정가표 + 규정을 한 번에 저장하고 다시 읽으면 같은 값
  - 저장 도중 하나라도 실패하면 전부 취소됨 (트랜잭션: 여러 저장을 "모두 성공 아니면 모두 취소"로 묶는 기능)
  - 매물 저장 시 `signals`, `extracted`는 JSON으로 저장되고 읽을 때 객체로 돌아옴. 저장된 `extracted`에 `contacts`가 없음
  - `contact_hashes`에는 `type`과 `hash`만 있고 원문 칸이 없음(스키마 수준에서 확인)
  - 해시로 기존 매물 찾기, 묶음 병합 시 매물들의 `fingerprintId`가 한 번에 바뀜(트랜잭션)
  - 검토 상태·신고 상태 변경, 없는 ID → `null` 반환
  - 같은 공연의 매물만 조회됨 (다른 공연 매물이 섞이지 않음). 지문 묶음 조회는 공연을 넘나듦
- ③ 완료 기준
  - 명세 8절 테이블(`contact_hashes`, `fingerprints` 포함), 외래 키·인덱스(`listings.eventId`, `listings.fingerprintId`, `contact_hashes.hash`)
  - 모든 SQL은 미리 준비된 문장(prepared statement: 값을 `?` 자리에 따로 넣어 SQL 주입 공격을 막는 방식)으로만 실행. 문자열 이어 붙이기 금지
  - 저장소 패턴(DB 접근 코드를 한곳에 모아 두고 나머지 코드는 `findById`, `create` 같은 함수만 부르게 하는 방식)
- ④ 예상: 6h
- ⑤ 담당: 데이터·규칙

#### T1-8 seed 데이터 작성 (가상 자료)
- ① 파일: `server/src/db/seed/events.json`, `listings.json`
- ② 먼저 쓸 테스트: `seed/seed.test.ts` — 두 파일이 shared 스키마를 통과하는지, 매물 수와 유형 비율이 명세와 맞는지(정상 양도 20%, 웃돈만 20%, 선입금 15%, 규정 충돌 15%, 반복 게시 10%, 지문 묶음 20%, 허용 오차 ±1건), 가상 연락처가 모두 `010-0000-` 대역인지
- ③ 완료 기준
  - seed는 처음 실행할 때 DB에 미리 채워 넣는 샘플 데이터라는 뜻
  - 공연 2건: 콘서트 1(양도 금지 규정 없음, 현장 수령 필수 규정 있음, D7) / 스포츠 1. 정가표와 규정 문장은 **실제 공개 공지를 손으로 옮겨 적은 것**이고, 각각 출처 URL과 확인 시각을 기록
  - 매물 약 50건: 팀이 직접 쓴 가상 글. 실제 인물·계정·전화번호를 쓰지 않음(연락처는 `010-0000-xxxx`, 가상 계좌). 각 매물에 `expectedSignals`(이 글에서 나와야 하는 신호 코드 목록)와 `expectedFingerprintGroup`(같은 묶음이어야 하는 매물끼리 같은 라벨) 칸을 둠 → T5-3 회귀 테스트와 검증표에 사용
  - **지문 묶음 2개**: 묶음 1은 같은 전화번호로 콘서트·스포츠 양쪽에 걸쳐 6건(총 14장), 묶음 2는 같은 계좌로 콘서트에 4건. 둘 다 `SELLER_PATTERN`이 나와야 함
  - 시연용 글 포함: (가) 정가 2.5배 + 선입금 + QR 캡처 + 묶음 1의 전화번호, (나) 정가 이하 1회 양도(연락처는 댓글 유도만), (다) 반복 게시 글 여러 건
- ④ 예상: 9h (공연 정보 3h는 1주차, 매물 50건 6h는 2주차)
- ⑤ 담당: 데이터·규칙

#### T1-9 매물 생성 서비스 (판정 + 저장)
- ① 파일: `server/src/services/listingService.ts`, `listingService.test.ts`
- ② 먼저 쓸 테스트
  - 없는 공연 ID → `NotFound` 오류
  - 원문과 추출 결과의 전화번호가 저장 전에 `[삭제]`로 바뀜. 저장된 어느 테이블에도 원문 번호가 없음(DB 전체 문자열 검색으로 확인)
  - `contacts`는 해시로 바뀌어 `contact_hashes`에 들어가고, 응답 객체에는 `contacts`가 없음
  - 기존 매물과 해시가 겹치면 같은 묶음에 들어가고, 그 묶음 통계로 `SELLER_PATTERN`이 계산됨 / 겹치지 않으면 `fingerprintId`가 `null`이고 응답의 `fingerprintSummary`도 `null`
  - 같은 공연의 기존 매물과만 반복 게시를 비교 (다른 공연과 같은 글이어도 `REPEATED_POST` 없음)
  - 설명 함수(`explain`)를 가짜로 끼워 넣으면 그 문장이 신호에 들어감 / 설명 함수가 오류를 내면 템플릿 문장이 들어가고 저장은 성공
  - `priceRatio`가 저장됨 (대조 불가면 `null`)
- ③ 완료 기준: `createListing(input, deps)` 하나로 마스킹 → 해시 → 지문 연결 → 엔진 → 설명 → 저장 순서가 끝남(한 트랜잭션). 의존성 주입(필요한 부품을 함수 바깥에서 넘겨받는 방식, 테스트에서 가짜 부품을 쓰기 쉬움)으로 DB·AI를 받음
- ④ 예상: 5h
- ⑤ 담당: 데이터·규칙

#### T1-10 seed 실행 스크립트
- ① 파일: `server/src/db/seed/run.ts`
- ② 먼저 쓸 테스트: 메모리 DB에 seed 실행 → 공연 2건, 매물 수 일치. 두 번 실행해도 중복 생성되지 않음
- ③ 완료 기준
  - 매물은 **T1-9 서비스를 그대로 거쳐** 저장 (신호를 손으로 적지 않음. 엔진이 바뀌면 seed 결과도 따라 바뀜)
  - 설명은 템플릿을 사용 (seed 실행 때 AI를 부르지 않음, 비용 0)
  - `npm run seed`, 그리고 `SEED_ON_START=true`이고 DB가 비어 있으면 서버 시작 시 자동 실행
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

---

## 4. 2단계: 서버 API

모든 응답은 `{ ok: boolean, data, error }` 모양 하나로 통일한다. 테스트는 supertest로 쓴다.

#### T2-1 응답 형식·입력 검증·오류 처리 미들웨어
- ① 파일: `server/src/lib/response.ts`, `middleware/validate.ts`, `middleware/errorHandler.ts`, 각 `*.test.ts`
- ② 먼저 쓸 테스트
  - `ok(data)` → `{ ok: true, data, error: null }`, `fail(msg)` → `{ ok: false, data: null, error: msg }`
  - Zod 검증 실패 → 400과 사람이 읽을 수 있는 한국어 메시지 (예: "price: 0 이상의 정수여야 합니다")
  - 예상하지 못한 오류 → 500, 응답에 스택 정보(오류가 난 코드 위치)나 내부 경로가 나오지 않음, 서버 로그에는 자세히 남음
- ③ 완료 기준: 미들웨어(요청이 라우트에 닿기 전·후에 공통 처리를 끼워 넣는 함수) 3종 완성, 테스트 통과
- ④ 예상: 2h
- ⑤ 담당: 데이터·규칙

#### T2-2 담당자 화면 보호 (HTTP Basic 인증)
- ① 파일: `server/src/middleware/basicAuth.ts`, `basicAuth.test.ts`
- ② 먼저 쓸 테스트
  - 인증 헤더 없음 → 401 + `WWW-Authenticate: Basic` 헤더 (브라우저가 비밀번호 창을 띄우게 하는 신호)
  - 비밀번호 틀림 → 401 / 맞음 → 다음 단계 진행
  - 길이가 다른 비밀번호를 넣어도 오류 없이 401
- ③ 완료 기준
  - HTTP Basic 인증: 브라우저 기본 로그인 창으로 아이디·비밀번호를 받는 가장 단순한 인증 방식
  - 비교는 `crypto.timingSafeEqual`(비교에 걸리는 시간으로 비밀번호를 추측하는 공격을 막는 비교 함수)
  - 적용 대상: `GET /api/events/:id/listings`, `GET /api/events/:id/fingerprints`, `PATCH /api/listings/:id/review`, `PATCH /api/fingerprints/:id/report-status`, 그리고 페이지 주소 `/dashboard`
  - 공개 대상: `GET /api/events`(팬의 공연 선택용), `POST /api/extract`, `POST /api/listings`
  - 발표에서는 "실제 서비스는 역할 기반 권한(RBAC)으로 확장"이라고만 언급
- ④ 예상: 2h
- ⑤ 담당: AI·발표

#### T2-3 속도 제한
- ① 파일: `server/src/middleware/rateLimit.ts`, `rateLimit.test.ts`
- ② 먼저 쓸 테스트: 같은 IP에서 `/api/extract` 10회는 통과, 11번째는 429와 "잠시 후 다시 시도해 주세요" 메시지
- ③ 완료 기준
  - 속도 제한: 일정 시간 안에 같은 사용자가 보낼 수 있는 요청 수를 묶어 두는 장치 (데모 중 API 키 사용량이 바닥나는 것 방지)
  - `express-rate-limit` 사용 (검증된 라이브러리. 직접 만들지 않음). 추출은 IP당 분당 10회, 나머지 API는 넉넉한 전체 제한(예: 분당 120회, 값은 설정 파일에)
- ④ 예상: 1.5h
- ⑤ 담당: AI·발표

#### T2-4 공연 API
- ① 파일: `server/src/routes/events.ts`, `server/test/events.api.test.ts`
- ② 먼저 쓸 테스트
  - `GET /api/events` → 200, 공연 목록(정가표·규정 포함)
  - (v2) `POST /api/events`는 범위 밖. 공연은 seed로만 들어감
- ③ 완료 기준: 테스트 통과, 응답 형식 통일
- ④ 예상: 1.5h
- ⑤ 담당: 데이터·규칙

#### T2-8 신고 자료·지문 API (v2 신규)
- ① 파일: `server/src/routes/report.ts`, `routes/fingerprints.ts`, `services/reportService.ts`, `lib/legal.ts`, `server/test/report.api.test.ts`, `fingerprints.api.test.ts`
- ② 먼저 쓸 테스트
  - `GET /api/listings/:id/report` 없는 ID → 404 / 정상 → 200, 응답에 판매글(마스킹됨)·추출 항목·신호 대조표·같은 지문 매물 목록(공연명·게시일·가격·수량·배율만)·법 조문 요지·신고센터 링크·고정 문구가 들어 있음
  - 신고 자료 응답을 문자열로 바꿔 검색했을 때 `hash`, `010-`, `contacts`가 없음 (연락처·해시 유출 차단)
  - 지문 묶음이 없는 매물의 신고 자료는 "같은 지문 매물" 절이 비어 있고 오류가 아님
  - 응답에 금지 표현("상습 판매자", "암표상", "불법")이 없음 (T3-1 검사기 재사용)
  - `GET /api/events/:id/fingerprints` 인증 없음 → 401 / 정상 → 해당 공연에 매물이 하나라도 있는 묶음 목록 + 통계, 매물 수 내림차순
  - `PATCH /api/fingerprints/:id/report-status` 잘못된 값 → 400 / 없는 ID → 404 / 정상 → 200
- ③ 완료 기준: `lib/legal.ts`에 법 조문 요지·링크·신고센터 주소·포상금 안내 문구를 상수로 모음(공식 주소는 담당자가 직접 확인해 입력, 추측 금지). 신고 자료 생성 시 해당 묶음의 `reportStatus`가 `none`이면 `generated`로 바뀜
- ④ 예상: 5h
- ⑤ 담당: 데이터·규칙

#### T2-5 매물 API
- ① 파일: `server/src/routes/listings.ts`, `server/test/listings.api.test.ts`
- ② 먼저 쓸 테스트
  - `POST /api/listings` 잘못된 본문 → 400 / 없는 공연 → 404 / 원문도 이미지도 없음 → 400 / `imageRef: "../../etc/passwd"` → 400 (경로 조작 공격: 파일 이름에 `../`를 넣어 다른 폴더 파일에 접근하려는 공격) / 정상 → 201 + 신호 포함 매물
  - `GET /api/events/:id/listings` → `{ listings, groups, distribution, summary }`, 인증 필요
  - `PATCH /api/listings/:id/review` 잘못된 상태 값 → 400 / 없는 ID → 404 / 정상 → 200
- ③ 완료 기준: 테스트 통과. `imageRef`는 서버가 만든 형식(`UUID.png|jpg`)만 정규식으로 허용
- ④ 예상: 5h
- ⑤ 담당: 데이터·규칙

#### T2-6 추출 API와 이미지 업로드
- ① 파일: `server/src/routes/extract.ts`, `middleware/upload.ts`, `lib/imageType.ts`, `server/test/extract.api.test.ts`
- ② 먼저 쓸 테스트 (AI는 가짜로 끼움)
  - 텍스트도 이미지도 없음 → 400
  - 5MB 초과 이미지 → 413 + 한국어 메시지
  - 확장자만 `.png`인 텍스트 파일 → 400 (파일 앞부분 바이트로 진짜 형식을 확인. 이것을 매직 바이트 검사라고 함: PNG는 `89 50 4E 47`, JPEG는 `FF D8 FF`로 시작)
  - AI 실패 → **200** + `{ extracted: EMPTY_EXTRACTION, aiStatus: "fallback" }` (오류가 아니라 "직접 입력" 안내로 처리)
  - 정상 → 200 + `{ extracted, imageRef, aiStatus: "ok" }`
- ③ 완료 기준
  - multer(Express에서 파일 업로드를 받는 라이브러리)를 메모리 저장 방식으로 쓰고, 검사를 통과한 이미지만 `uploads/`에 무작위 이름(UUID)으로 저장. 원래 파일 이름은 쓰지 않음
  - `uploads/`는 정적 서빙하지 않음 (외부에서 주소로 열 수 없음)
- ④ 예상: 4h
- ⑤ 담당: AI·발표

#### T2-7 화면 파일 제공과 페이지 보호
- ① 파일: `server/src/app.ts` (정적 서빙 부분), `server/test/static.test.ts`
- ② 먼저 쓸 테스트: `/check` → 200 HTML / `/dashboard` 인증 없음 → 401 / `/uploads/아무거나` → 404
- ③ 완료 기준: 운영 모드에서 서버가 빌드된 화면 파일을 함께 제공(정적 서빙). 모르는 주소는 `index.html`로 보냄(SPA 폴백: 화면 이동을 브라우저 쪽 코드가 처리하므로 어떤 주소든 같은 HTML을 주는 방식). 단 `/api/*`는 제외
- ④ 예상: 1.5h
- ⑤ 담당: AI·발표

---

## 5. 3단계: AI 추출·설명

AI는 서버에서만 호출하고, 키는 환경 변수에만 둔다. SDK는 `@anthropic-ai/sdk`만 쓴다. 테스트에서는 SDK를 가짜 객체로 바꿔 끼워 **실제 API를 부르지 않는다**.

> 구조화 출력(AI가 정해진 JSON 모양으로만 답하게 하는 기능) 관련 확인 사항 — 2026-10-16 공식 자료 기준:
> - 파라미터는 `output_config: { format: {...} }`이다. 예전 이름인 `output_format`은 더 이상 쓰지 않는다.
> - TypeScript SDK에는 `client.messages.parse()` 헬퍼가 있어 응답을 스키마로 자동 검증한다. T3-2에서 이 헬퍼로 시작하고, 쓸 수 없는 상황이면 JSON 스키마를 직접 넘기고 응답을 Zod로 다시 검사한다.
> - `claude-opus-5-5`에서는 어시스턴트 프리필과 강제 도구 선택(`tool_choice: any/tool`)이 400 오류다. 둘 다 쓰지 않는다. 생각(thinking)은 끌 수 없으므로 비용 조절은 `output_config.effort`를 `low`로 두는 방식으로 한다.
> - 세부 호출 코드는 `ai/client.ts` 한 파일에만 두어, 바뀌어도 그 파일만 고치면 되게 한다.

#### T3-1 설명 템플릿과 단정 표현 검사기
- ① 파일: `server/src/ai/explainTemplates.ts`, `ai/legalTone.ts`, `legalTone.test.ts`, `client/src/copy.test.ts`
- ② 먼저 쓸 테스트
  - `hasForbiddenTone("불법 판매입니다")` → `true`, `"예매처에 재확인하세요"` → `false`
  - 신고센터의 **공식 기관 이름**에 들어 있는 단어는 허용 목록으로 예외 처리
  - 템플릿 9종 모두 검사 통과, 2문장 이하. `SELLER_PATTERN` 템플릿은 "같은 연락처로 올라온 판매글이 N건 확인되었습니다. 상습 여부는 신고기관이 판단합니다" 형식이고 "상습 판매자"·"암표상"이 금지 목록에 있음
  - 화면 문구 검사: `client/src`와 `shared/src/labels.ts`의 문자열에 금지 표현이 없음
- ③ 완료 기준: 신호 9종 각각 쉬운 말 템플릿 문장(AI 없이도 쓰는 기본 설명). 금지 표현 목록은 설정 상수로 관리
- ④ 예상: 2h
- ⑤ 담당: AI·발표

#### T3-2 SDK 연결 부품
- ① 파일: `server/src/ai/client.ts`, `client.test.ts`
- ② 먼저 쓸 테스트: 모델 ID가 `ANTHROPIC_MODEL`에서 오고 없으면 `claude-opus-5-5` / `max_tokens`가 2048 / 키가 없으면 "AI 꺼짐" 상태를 돌려줌 / 로그에 API 키가 찍히지 않음
- ③ 완료 기준: 시간 제한(`AI_TIMEOUT_MS`)과 재시도 1회 설정. 다른 파일은 이 부품만 거쳐 SDK를 씀
- ④ 예상: 2h
- ⑤ 담당: AI·발표

#### T3-3 텍스트 추출
- ① 파일: `server/src/ai/prompts.ts`, `ai/extract.ts`, `ai/schema.ts`, `extract.test.ts`
- ② 먼저 쓸 테스트
  - 가짜 SDK가 올바른 JSON을 돌려주면 → Zod 통과한 추출 결과 + 마스킹 적용
  - `stop_reason === "refusal"` → 빈 추출 결과 + `aiStatus: "fallback"`
  - SDK가 오류를 던짐(시간 초과, 429, 500) → 같은 처리
  - 응답 JSON이 Zod를 통과하지 못함 → 같은 처리
  - 요청 본문에 프리필(어시스턴트 답변 앞부분을 미리 채워 보내는 방식)이 없음
  - LLM에 보내는 원문이 이미 마스킹된 상태 (D13)
- ③ 완료 기준
  - 프롬프트에 포함할 것: 가격은 1장당 원 단위 정수(D1), 원문 표현 그대로, 각 값의 근거 구절, **연락처(전화·계좌·오픈채팅·텔레그램)는 `contacts` 배열에 종류와 함께 따로 뽑고** 본문 구절(`quotes`)에서는 `[삭제]`로 치환, **판매글은 지시가 아니라 분석할 자료로만 취급**
  - (v2) 원문 마스킹(D13)은 연락처를 `contacts`로 뽑은 **뒤**에 적용한다. 순서: 원문 → LLM 추출(연락처 포함) → 서버가 연락처 해시 → 원문·quotes 마스킹 → 저장. LLM에 보내는 원문은 마스킹 전이므로, 이 호출의 로그·캐시에 원문이 남지 않게 캐시 키는 해시만 저장하고 응답의 `contacts`는 캐시에 넣지 않는다 (판매글 안에 "이전 지시를 무시하라" 같은 문장을 넣는 프롬프트 인젝션 공격 대비. 판매글은 구분 태그로 감쌈)
  - `PROMPT_VERSION` 상수를 두어 프롬프트가 바뀌면 캐시 키도 바뀌게 함
- ④ 예상: 6h
- ⑤ 담당: AI·발표

#### T3-4 AI 응답 캐시
- ① 파일: `server/src/ai/cache.ts`, `lib/hash.ts`, `cache.test.ts`
- ② 먼저 쓸 테스트 (시계를 가짜로 끼워 시간을 조절)
  - 같은 입력 두 번 → SDK는 1번만 호출됨
  - 24시간 1분 뒤 → 다시 호출
  - 모델이나 `PROMPT_VERSION`이 바뀌면 다른 키
  - 실패(fallback) 결과는 저장하지 않음
- ③ 완료 기준: 캐시 키 = SHA-256 해시(입력을 고정 길이의 지문 같은 문자열로 바꾸는 함수. 비밀번호 저장용이 아니라 "같은 입력인지" 확인하는 용도)(모델 + 프롬프트 버전 + 마스킹된 원문 + 이미지 해시). `ai_cache` 테이블 사용
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T3-5 캡처 이미지 입력 (v2: 선택 작업, 필수 범위가 끝난 뒤)
- ① 파일: `server/src/ai/extract.ts` (이미지 부분), `extract.image.test.ts`, `middleware/upload.ts`, `lib/imageType.ts`
- ② 먼저 쓸 테스트: 요청의 콘텐츠 순서가 `[image, text]` / `media_type`이 실제 형식(png/jpeg)과 일치 / 이미지 1장만 / 이미지만 있고 텍스트가 없어도 동작
- ③ 완료 기준: base64(이진 파일을 글자로 바꿔 JSON에 담는 방식) 이미지 블록 전송. 실제 캡처 3장으로 수동 확인
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T3-6 설명 생성
- ① 파일: `server/src/ai/explain.ts`, `explain.test.ts`
- ② 먼저 쓸 테스트
  - 신호 3개 → AI 호출 **1번**에 3개 설명을 받아 코드별로 연결
  - AI가 빠뜨린 코드 → 템플릿 / AI가 지어낸 코드 → 버림
  - 금지 표현 포함, 3문장 이상, 너무 긴 문장(예: 160자 초과) → 템플릿으로 교체
  - AI 실패 → 모든 신호에 템플릿
  - 설명이 신호의 코드·심각도·근거를 바꾸지 못함 (결과에서 문장만 다름)
  - 신호 0개 → AI를 부르지 않음
- ③ 완료 기준: 테스트 통과, 결과도 T3-4 캐시를 거침
- ④ 예상: 5h
- ⑤ 담당: AI·발표

#### T3-7 추출 품질 점검 (실제 API 사용)
- ① 파일: `server/scripts/eval-extract.ts`, `docs/eval-extract.md`
- ② 먼저 쓸 테스트: 해당 없음 (수동 평가 작업)
- ③ 완료 기준: seed 매물 10건 + 캡처 3장을 실제 모델로 추출, 항목별 정답률 표 작성. 자주 틀리는 표현은 프롬프트 수정 후 재측정. 결과는 발표 검증표(T5-3)에 반영
- ④ 예상: 4h
- ⑤ 담당: AI·발표

---

## 6. 4단계: 화면

공통 원칙: 화면 컴포넌트에는 표시만 두고, 데이터 가져오기·저장은 `api/hooks.ts`의 커스텀 훅(여러 화면에서 다시 쓰는 로직을 `useXxx` 함수로 뽑아 둔 것)에 둔다. 심각도는 색만으로 구분하지 않고 글자 라벨을 함께 쓴다(색을 구분하기 어려운 사용자 배려). `dangerouslySetInnerHTML`(HTML 문자열을 그대로 화면에 넣는 기능, XSS 공격 통로가 됨)은 쓰지 않는다. 출처 링크는 `target="_blank" rel="noopener noreferrer"`로 연다.

#### T4-1 공통 컴포넌트
- ① 파일: `client/src/components/ui/Button.tsx`, `Card.tsx`, `Badge.tsx`, `Field.tsx`, `Select.tsx`, `Spinner.tsx`, `Notice.tsx`, `Badge.test.tsx`
- ② 먼저 쓸 테스트: `Badge`가 심각도 3종을 한글 라벨("정보/주의/확인 필요")로 보여줌
- ③ 완료 기준: 디자인 토큰만 사용 (색 값을 컴포넌트에 직접 쓰지 않음), 키보드로 포커스 이동 가능
- ④ 예상: 5h
- ⑤ 담당: 화면

#### T4-2 API 연결 훅
- ① 파일: `client/src/api/hooks.ts`, `hooks.test.ts`
- ② 먼저 쓸 테스트: 검토 상태 변경 성공 후 해당 공연 매물 목록이 다시 불러와짐 (가짜 fetch 사용)
- ③ 완료 기준: `useEvents`, `useExtract`, `useCreateListing`, `useEventListings`, `useUpdateReview`, `useCreateEvent`. 응답 타입은 shared에서 가져옴. 서버 API가 완성되기 전에는 shared 타입에 맞춘 가짜 데이터로 화면 작업 진행
- ④ 예상: 3h
- ⑤ 담당: 화면

#### T4-3 검사 화면 입력부
- ① 파일: `client/src/components/check/ListingInput.tsx`, `ListingInput.test.tsx`
- ② 먼저 쓸 테스트: 6MB 파일이나 GIF 선택 → 업로드 전에 안내 문구 / 공연 미선택 시 검사 버튼 비활성
- ③ 완료 기준: 텍스트 영역, 이미지 업로드(미리보기, PNG/JPG, 5MB 이하), 공연 선택 드롭다운, 로딩 표시
- ④ 예상: 4h
- ⑤ 담당: 화면

#### T4-4 추출 결과 수정 폼
- ① 파일: `client/src/components/check/ExtractionForm.tsx`, `ExtractionForm.test.tsx`
- ② 먼저 쓸 테스트
  - `aiStatus: "fallback"`이면 "AI가 내용을 읽지 못했어요. 직접 입력해 주세요" 안내 + 빈 폼
  - 가격을 고치면 제출 데이터에 고친 값이 들어감
  - 각 항목 옆에 근거 원문 구절이 보임
- ③ 완료 기준: 명세 4.1-2의 모든 항목 수정 가능. 가격 라벨은 "1장당 가격(원)"
- ④ 예상: 6h
- ⑤ 담당: 화면

#### T4-5 신호 카드와 결과 영역
- ① 파일: `client/src/components/check/SignalCard.tsx`, `SignalList.tsx`, `ResultFooter.tsx`, `pages/CheckPage.tsx`, 각 `*.test.tsx`
- ② 먼저 쓸 테스트
  - 근거가 `null`이면 출처 칸에 "일반 주의"
  - 근거가 있으면 공식 문장 + 출처 링크 + "2026-10-12 확인" 형식의 확인 시각
  - 모든 카드에 "예매처에 재확인하세요" 안내
  - 신호 0개 → "이 판매글에서 확인이 필요한 점을 찾지 못했어요. 거래 전 예매처 규정은 꼭 확인하세요" (안전하다고 말하지 않음)
  - (v2) `FingerprintLine`: `fingerprintSummary`가 있으면 "같은 연락처로 올라온 판매글이 다른 공연 N건, 총 M장 확인됨" / `null`이면 아무것도 그리지 않음 / 묶음 ID·해시·연락처는 화면에 없음
  - (v2) "신고 자료 만들기" 버튼 → `/report/:listingId`로 이동. 신호 0개여도 버튼은 보이되 "확인이 필요한 점이 없어 신고 자료에 담을 내용이 적습니다" 안내
  - 하단 고정 문구, 공연 종류에 맞는 신고센터 링크, 포상금 제도 안내 한 줄
- ③ 완료 기준: 판매글 구절 ↔ 공식 문장을 나란히 보여줌(모바일에서는 위아래). 검사 화면 전체 흐름이 실제 API와 연결됨
- ④ 예상: 6h
- ⑤ 담당: 화면

#### T4-6 대시보드: 요약 타일과 배율 분포 차트
- ① 파일: `client/src/components/dashboard/SummaryTiles.tsx`, `RatioHistogram.tsx`, `pages/DashboardPage.tsx`
- ② 먼저 쓸 테스트: `SummaryTiles`가 매물 수·정가 초과 비율·검토 대기 수·대조 불가 건수를 표시
- ③ 완료 기준: 공연 선택, Recharts 히스토그램(값을 구간별로 세어 막대로 보여주는 차트) 5구간
- ④ 예상: 4h
- ⑤ 담당: 화면

#### T4-7 대시보드: 매물 표·상세·검토 상태
- ① 파일: `client/src/components/dashboard/ListingTable.tsx`, `ListingDetail.tsx`, `ListingTable.test.tsx`
- ② 먼저 쓸 테스트: 행 클릭 → 신호 카드 상세(T4-5의 `SignalCard` 재사용) / 상태를 "확인함"으로 바꾸면 변경 요청이 나감
- ③ 완료 기준: 등록 시각, 좌석, 가격, 배율, 신호 개수, 검토 상태(대기/확인함/문제없음) 열. 매물 수가 많아져도 표가 깨지지 않음
- ④ 예상: 4h
- ⑤ 담당: 화면

#### T4-8 (v2에서 T4-11로 흡수) 반복 게시 묶음
반복 게시(문구 유사)는 지문 탭의 "연결 근거" 표시로 들어간다. 별도 화면 없음.

#### T4-9 (v2에서 제외) 공연·규정 등록 화면
공연은 seed 파일로만 입력한다. 발표에서 "주최 측 입력 화면은 확장"으로 언급.

#### T4-11 대시보드: 판매자 지문 탭 (v2 신규)
- ① 파일: `client/src/components/dashboard/FingerprintTable.tsx`, `FingerprintDetail.tsx`, `FingerprintTable.test.tsx`
- ② 먼저 쓸 테스트
  - 묶음 2개 → 행 2개. 열: 지문 ID(`FP-xxxx`), 연결 근거("연락처 일치" / "연락처 일치 + 유사 글 3건"), 공연 수, 매물 수, 총 수량, 정가 초과 건수, 첫·마지막 게시일, 신고 상태
  - 행 클릭 → 묶인 매물 목록(공연명·게시일·가격·배율·신호 개수). 연락처·해시는 어디에도 없음
  - 신고 상태를 "접수 확인됨"으로 바꾸면 변경 요청이 나감
  - 묶음 0개 → "같은 연락처로 묶인 매물이 없어요"
  - 표 어디에도 "상습"이라는 단어가 없음
- ③ 완료 기준: 대시보드가 "매물" / "판매자 지문" 두 탭으로 나뉨. 요약 타일에 "지문 묶음 수" 추가
- ④ 예상: 5h
- ⑤ 담당: 화면

#### T4-12 신고 자료 페이지 (v2 신규)
- ① 파일: `client/src/components/report/ReportSheet.tsx`, `pages/ReportPage.tsx`, `ReportSheet.test.tsx`, `styles/print.css`
- ② 먼저 쓸 테스트
  - 명세 4.4절의 8개 절이 순서대로 렌더링됨
  - 같은 지문 매물 목록에 공연명·게시일·가격·수량·배율 열만 있음
  - 고정 문구("위법 여부는 신고기관이 판단합니다")가 항상 보임
  - 신호 0개 매물 → 대조표 절에 "확인이 필요한 점이 없습니다" 한 줄
  - "PDF로 저장" 버튼이 `window.print()`를 부름
- ③ 완료 기준: A4 인쇄 시 한두 장에 들어가고, 인쇄 화면에서는 버튼·내비게이션이 숨겨짐(`@media print`). 링크 URL이 인쇄물에 글자로 보임
- ④ 예상: 5h
- ⑤ 담당: 화면

#### T4-10 반응형·브라우저 점검
- ① 파일: 기존 컴포넌트 수정, `docs/qa-checklist.md`
- ② 먼저 쓸 테스트: 해당 없음 (수동 점검표)
- ③ 완료 기준: 검사 화면이 390px 폭에서 가로 스크롤 없이 세로로 쌓임 / 대시보드는 768px 이상에서 정상 / Chrome·Edge(PC), Safari(팀원 아이폰), Chrome(안드로이드)에서 시연 흐름 확인, 결과를 점검표에 기록
- ④ 예상: 3h
- ⑤ 담당: 화면

---

## 7. 5단계: E2E, Docker, 발표 준비

#### T5-1 E2E 시나리오 1개
- ① 파일: `playwright.config.ts`, `e2e/check-to-dashboard.spec.ts`
- ② 먼저 쓸 테스트: 이 작업 자체가 테스트. E2E(End-to-End: 실제 브라우저를 자동으로 움직여 사용자 흐름 전체를 확인하는 테스트) 시나리오는 아래와 같다
  1. `/check` 접속 → 콘서트 선택 → 시연 글 (가) 붙여넣기 → 검사
  2. **API 키 없이 실행**하므로 "직접 입력" 안내가 뜸 → 가격·좌석·전달 방식·선입금·연락처(가상 번호) 입력 → 판정
  3. 신호 카드 3장 + 지문 줄("다른 공연 N건") 확인 (seed에 같은 번호의 묶음 1이 있으므로)
  4. "신고 자료 만들기" → `/report/:id`에 8개 절이 보임, 연락처가 어디에도 없음
  5. `/dashboard`(Basic 인증 정보 포함) → 콘서트 선택 → 매물 탭에 방금 매물이 있음 → 지문 탭에서 묶음 1의 매물 수가 1 늘어남
- ③ 완료 기준: 매번 새 임시 DB로 실행, 3회 연속 통과. 이 테스트가 "AI 없이도 끝까지 동작한다"는 명세 요구까지 증명함. 브라우저는 Chromium 필수, WebKit(Safari 엔진)은 여유가 있으면 추가
- ④ 예상: 5h
- ⑤ 담당: 화면

#### T5-2 Docker 완성
- ① 파일: `Dockerfile`, `docs/run.md`
- ② 먼저 쓸 테스트: 해당 없음 (아래 실행 확인으로 대신함)
- ③ 완료 기준
  - 루트가 아닌 사용자로 실행, `data/`·`uploads/`는 볼륨(컨테이너를 지워도 남는 저장 공간)으로 분리, 헬스체크 설정
  - 발표 노트북에서 `docker run` → seed 자동 → 시연 흐름 끝까지 성공
  - Docker가 안 될 때를 위한 대체 실행법(`npm run build && npm start`)을 `docs/run.md`에 기록
  - 외부 서버에 올릴 경우 반드시 HTTPS 뒤에 둘 것을 문서에 명시 (Basic 인증은 암호화 없는 통신에서 비밀번호가 그대로 보임)
- ④ 예상: 4h
- ⑤ 담당: AI·발표

#### T5-3 seed 회귀 테스트와 검증표
- ① 파일: `server/src/db/seed/expected.test.ts`, `server/scripts/validation-report.ts`, `docs/validation.md`
- ② 먼저 쓸 테스트: seed 매물마다 엔진 결과의 신호 코드 목록이 `expectedSignals`와 같음 (회귀 테스트: 코드를 고친 뒤 예전에 맞던 결과가 틀어지지 않았는지 확인하는 테스트)
- ③ 완료 기준: 스크립트가 "잡은 사례 / 잡지 못한 사례 / 일부러 잡지 않는 사례(정가 이하 양도)" 표를 만들어 `docs/validation.md`로 저장. T3-7의 추출 정답률도 함께 정리
- ④ 예상: 4h
- ⑤ 담당: 데이터·규칙

#### T5-4 시연 대본 점검과 캐시 예열
- ① 파일: `docs/demo-script.md`
- ② 먼저 쓸 테스트: T1-5의 "시연 글 고정 테스트"와 "신호 0개 테스트"가 통과하는지 다시 확인 (대본이 기대하는 결과를 코드가 보장함)
- ③ 완료 기준: 명세 12절 5단계를 실제 화면으로 따라 하며 시간 측정(5분 이내). 시연 글을 발표 노트북에서 미리 한 번씩 넣어 AI 캐시를 채워 둠 (발표장 인터넷이 불안정해도 같은 입력은 캐시로 응답)
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T5-5 발표 자료
- ① 파일: `docs/presentation/` (장표 원본과 PDF)
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: 문제(사례 수치 + 개정 공연법 요지, 출처 표기) / 차별점("결제 전, 근거가 보이는 확인") / AI와 코드의 역할 분리 / 검증표 / 한계 장표(명세 2.3절 문장을 그대로 인용) / 모든 문구가 "검토가 필요한 매물" 표현으로 통일
- ④ 예상: 8h
- ⑤ 담당: AI·발표

#### T5-6 리허설과 백업
- ① 파일: `docs/presentation/backup-demo.mp4` (저장소에는 올리지 않고 공유 드라이브에 보관해도 됨)
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: 전체 리허설 2회 이상, 예상 질문("AI가 틀리면?", "실제 데이터가 아니잖아요?", "검토 필요와 확정의 차이는?") 답변 정리, 시연 녹화 영상 백업
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T5-7 업로드 이미지 정리 스크립트
- ① 파일: `server/scripts/clean-uploads.ts`
- ② 먼저 쓸 테스트: 임시 폴더의 파일을 지우되 폴더 밖 파일은 건드리지 않음
- ③ 완료 기준: `npm run clean:uploads`로 데모 후 캡처 이미지 삭제 (명세 13절)
- ④ 예상: 0.5h
- ⑤ 담당: AI·발표

#### T5-8 제출 전 보안·개인정보 점검
- ① 파일: 점검 결과를 `docs/security-check.md`에 기록
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: `ecc:security-reviewer`로 점검 → 심각(CRITICAL)·높음(HIGH) 0건. 저장소 전체에 API 키·비밀번호·솔트가 없는지 확인, `npm audit`(설치한 라이브러리의 알려진 보안 문제 검사) 높음 이상 0건. (v2) seed를 넣은 DB 파일과 서버 로그를 문자열 검색해 가상 연락처 원문이 어디에도 없음을 확인하고 결과를 기록 → 발표의 개인정보 장표 근거
- ④ 예상: 3h
- ⑤ 담당: AI·발표

#### T5-9 보관 기간 정리 스크립트 (v2 신규)
- ① 파일: `server/scripts/retention.ts`, `retention.test.ts`
- ② 먼저 쓸 테스트: 메모리 DB에 91일 전 매물과 어제 매물 → 실행 후 91일 전 매물·해당 `contact_hashes`만 삭제 / 매물이 모두 사라진 묶음은 `fingerprints`에서도 삭제 / `RETENTION_DAYS`를 30으로 바꾸면 기준이 바뀜
- ③ 완료 기준: `npm run retention`으로 실행. 서버 시작 시 자동 실행은 하지 않음(데모 데이터가 지워지면 안 됨)
- ④ 예상: 1.5h
- ⑤ 담당: AI·발표

---

## 7.5 6단계 (선택): 브라우저 확장 프로그램 B

착수 조건은 D18. 조건이 안 되면 발표 "확장" 장표로만 다루고 아래는 하지 않는다.

#### T6-1 확장 뼈대와 팝업
- ① 파일: `extension/manifest.json`, `popup.html`, `popup.ts`, `extension/README.md`
- ② 먼저 쓸 테스트: 해당 없음(수동 확인). 팝업에서 공연을 고르고 서버 주소를 저장하면 `chrome.storage`에 남음
- ③ 완료 기준: Manifest V3, 권한은 지원 사이트 1곳의 주소 패턴과 `storage`만. 서버 주소는 팝업에서 입력(기본 `http://localhost:3000`)
- ④ 예상: 3h
- ⑤ 담당: 화면

#### T6-2 콘텐츠 스크립트: 판매글 읽기 → 카드 띄우기
- ① 파일: `extension/content.ts`, `extension/overlay.css`, `extension/content.test.ts`(본문 추출 함수만 단위 테스트)
- ② 먼저 쓸 테스트: 지원 사이트의 판매글 HTML 샘플(팀이 저장한 정적 파일) → 제목·본문·가격 문자열을 뽑아냄 / 판매글이 아닌 페이지 → `null`
- ③ 완료 기준: 사용자가 **보고 있는 페이지의 본문만** 읽어 `/api/extract` → `/api/listings`를 호출하고, 결과 신호 카드와 지문 줄을 페이지 오른쪽 위에 띄움. 카드에 "티켓레이더로 자세히 보기" 링크. 서버는 사이트에 접속하지 않음
- ④ 예상: 6h
- ⑤ 담당: 화면

#### T6-3 시연 반영
- ① 파일: `docs/demo-script.md` 수정
- ② 먼저 쓸 테스트: 해당 없음
- ③ 완료 기준: 시연 첫 장면을 "판매 페이지에서 바로 경고가 뜨는 장면"으로 바꾸고, 그 뒤 기존 흐름으로 이어짐. 발표에서 "사용자 본인 화면만 읽는다"를 한 문장으로 명시
- ④ 예상: 1h
- ⑤ 담당: AI·발표

---

## 8. 의존 관계·병렬 묶음 표

### 8.1 작업별 선행 작업

| 작업 | 선행 작업 | 담당 | 병렬 묶음 |
|---|---|---|---|
| T0-0 | 없음 | AI·발표 | P0 |
| T0-1 | T0-0 | 화면 | P0 |
| T0-2 | T0-1 | 데이터·규칙 | P1 |
| T0-3 | T0-1 | 화면 | P1 |
| T0-4 | T0-1 | 화면 | P1 |
| T0-6 | T0-1 | AI·발표 | P1 |
| T1-8(공연 정보) | T0-1 | 데이터·규칙 | P1 |
| T0-5 | T0-3, T0-4 | AI·발표 | P2 |
| T1-1 | T0-2, T0-3 | 데이터·규칙 | P2 |
| T1-2 | T0-3 | 데이터·규칙 | P2 |
| T1-4 | T0-2, T0-3 | 데이터·규칙 | P2 |
| T1-6 | T0-3 | 데이터·규칙 | P2 |
| T1-7 | T0-2, T0-3 | 데이터·규칙 | P2 |
| T2-1 | T0-3 | 데이터·규칙 | P2 |
| T3-1 | T0-2 | AI·발표 | P2 |
| T3-2 | T0-3 | AI·발표 | P2 |
| T4-1 | T0-4 | 화면 | P2 |
| T4-2 | T0-2, T0-4 | 화면 | P2 |
| T1-3 | T1-2 | 데이터·규칙 | P3 |
| T1-5 | T1-2, T1-4 | 데이터·규칙 | P3 |
| T2-2, T2-3 | T2-1 | AI·발표 | P3 |
| T3-3 | T0-2, T1-1, T3-2 | AI·발표 | P3 |
| T4-3, T4-4, T4-5 | T4-1, T0-2 | 화면 | P3 |
| T4-6 | T4-1, T4-2 | 화면 | P3 |
| T1-11 | T0-2, T0-3 | 데이터·규칙 | P3 |
| T1-9 | T1-1, T1-5, T1-7, T1-11, T3-1 | 데이터·규칙 | P4 |
| T3-4 | T1-7, T3-3 | AI·발표 | P4 |
| T4-7 | T4-2, T4-5 | 화면 | P4 |
| T2-4 | T1-7, T2-1 | 데이터·규칙 | P4 |
| T1-8(매물 50건) | T0-2 | 데이터·규칙 | P4 |
| T1-10 | T1-8, T1-9 | 데이터·규칙 | P5 |
| T2-5 | T1-3, T1-6, T1-9, T2-1, T2-2 | 데이터·규칙 | P5 |
| T2-8 | T1-9, T1-11, T2-1, T2-2, T3-1 | 데이터·규칙 | P5 |
| T2-6 | T2-1, T2-3, T3-3, T3-4 | AI·발표 | P5 |
| T2-7 | T0-4, T2-2 | AI·발표 | P5 |
| T3-6 | T3-1, T3-2, T3-4 | AI·발표 | P5 |
| T4-11 | T4-7, T2-8 | 화면 | P5 |
| T4-12 | T4-5, T2-8 | 화면 | P5 |
| T5-9 | T1-7 | AI·발표 | P5 |
| T3-7 | T3-3, T1-8 | AI·발표 | P6 |
| T4-10 | T4-3 ~ T4-7, T4-11, T4-12 | 화면 | P6 |
| T5-1 | T1-10, T2-5, T2-6, T2-7, T2-8, T4-5, T4-7, T4-11, T4-12 | 화면 | P6 |
| T5-2 | T0-5, T1-10, T2-7 | AI·발표 | P6 |
| T5-3 | T1-10 (+T3-7 결과) | 데이터·규칙 | P6 |
| T5-7 | T2-6 | AI·발표 | P6 |
| T5-4 | T5-3, T4-5, T4-12, T2-6 | AI·발표 | P7 |
| T5-5 | T5-3, T5-8 | AI·발표 | P7 |
| T5-8 | 모든 T2 작업, T1-10 | AI·발표 | P7 |
| T5-6 | T5-2, T5-4, T5-5 | AI·발표 | P8 |
| T3-5(선택) | T2-6, T5-1 통과 후 | AI·발표 | P9 |
| T6-1~T6-3(선택) | D18 조건 충족 | 화면, AI·발표 | P9 |

### 8.2 병렬 묶음 설명

| 묶음 | 동시에 할 수 있는 작업 | 핵심 |
|---|---|---|
| P0 | T0-0 → T0-1 | 첫날 오전. 모두 함께 결정하고 저장소 뼈대를 만든다 |
| P1 | T0-2, T0-3, T0-4, T0-6, 공연 정보 수집 | **T0-2(공통 계약)를 최우선**으로 끝낸다. 이것이 끝나야 세 역할이 서로 기다리지 않는다 |
| P2 | 마스킹·유사도·좌석 맞추기·분포·DB / 템플릿·SDK 부품 / 공통 컴포넌트·훅 | 세 역할이 완전히 독립. 화면은 shared 타입으로 만든 가짜 데이터 사용 |
| P3 | 규칙 엔진·판매자 지문 / 텍스트 추출·인증·속도 제한 / 검사 화면 3종·대시보드 타일 | 규칙 엔진(T1-5)과 지문(T1-11)이 핵심 경로 |
| P4~P5 | 서비스·seed·매물 API·신고 자료 API / 캐시·설명·추출 API / 표·지문 탭·신고 자료 페이지 | 2주차 중반에 화면과 실제 API를 연결 |
| P6~P8 | E2E·Docker·검증표 → 대본·발표 → 리허설 | 3주차 |
| P9 (선택) | 캡처 입력, 브라우저 확장 | 필수 범위가 끝나고 D18 조건이 맞을 때만 |

**핵심 경로**(이 줄이 늦어지면 전체가 늦어지는 작업 순서): T0-1 → T0-2 → T1-2·T1-4·T1-11 → T1-5 → T1-9 → T2-5·T2-8 → T4-11·T4-12 → T5-1 → T5-4 → T5-6. 데이터·규칙 담당이 이 경로의 앞부분을 맡으므로, 막히면 다른 역할이 T1-8(매물 글 쓰기)과 T2-8(신고 자료 API)을 나눠 맡는다.

---

## 9. 3주 주차별 배치표 (담당 역할별)

1주차는 다음 주 월요일 착수를 가정한다(예: 10/19~10/25). 시간은 순수 작업 시간이며, 예상 밖의 일에 대비해 3주차 후반을 여유 시간으로 남겼다.

| 주 | 데이터·규칙 | 화면 | AI·발표 |
|---|---|---|---|
| **1주차** | T0-2 계약(3) · T1-1 마스킹(3) · T1-2 유사도(2) · T1-4 설정·좌석(2) · T1-5 규칙 엔진(9) · T1-11 판매자 지문(6) · T1-8 공연 정보(3) **= 28h** | T0-1 루트(2) · T0-3 서버 뼈대(3) · T0-4 화면 뼈대(4) · T4-1 공통 컴포넌트(5) · T4-3 입력부(4) · T4-4 추출 폼(6) **= 24h** | T0-0 회의(1) · T0-5 Docker 초안(3) · T0-6 CI(1.5) · T3-1 템플릿·금지 표현(2) · T3-2 SDK 부품(2) · T3-3 텍스트 추출(6) · T3-4 캐시(3) · T2-1 응답 형식(2) **= 20.5h** |
| 1주차 끝 점검 | 규칙 9종 테스트 전부 통과, 신호 0개 사례 통과, 같은 번호 매물이 한 묶음으로 묶임 | 검사 화면이 가짜 데이터로 입력 → 폼까지 동작 | 실제 판매글 1건이 AI로 추출되고 연락처가 `contacts`로 따로 나옴, 실패 시 빈 폼 |
| **2주차** | T1-7 DB(6) · T1-3 묶음(2) · T1-6 분포(2) · T1-8 매물 50건(6) · T1-9 서비스(5) · T1-10 seed(2) · T2-4 공연 API(1.5) · T2-5 매물 API(5) · T2-8 신고 자료·지문 API(5) **= 34.5h** | T4-2 훅(3) · T4-5 신호 카드·지문 줄(6) · T4-6 타일·차트(4) · T4-7 표·상세(4) · T4-11 지문 탭(5) · T4-12 신고 자료 페이지(5) **= 27h** | T2-2 인증(2) · T2-3 속도 제한(1.5) · T2-6 추출 API(4) · T2-7 정적 서빙(1.5) · T3-6 설명(5) · T3-7 품질 점검(4) · T5-9 보관 정리(1.5) **= 19.5h** |
| 2주차 끝 점검 | seed로 대시보드에 매물 50건·지문 묶음 2개 표시, 신고 자료 API 응답에 연락처 없음 | 팬 화면 → 신고 자료 페이지 → 대시보드 지문 탭이 실제 API로 동작 | `docker build` 성공, 금지 표현 검사 통과 |
| **3주차** | T5-3 검증표(4) + 버그 수정·E2E 지원·리허설 참여 **≈ 12h** | T4-10 반응형·브라우저·인쇄(3) · T5-1 E2E(5) + 다듬기 **≈ 14h** / 여유 시 T6 확장(10) | T5-2 Docker 완성(4) · T5-4 대본·캐시 예열(3) · T5-5 발표 자료(8) · T5-6 리허설(3) · T5-7 정리(0.5) · T5-8 보안·개인정보(3) **= 21.5h** / 여유 시 T3-5 캡처(3) |
| 3주차 점검 | **수요일까지 필수 범위 완료 → D18 판단 → 목요일 코드 동결**(새 기능 추가 중단, 버그 수정만) → 금요일 리허설 2회 | | |

역할별 합계(필수만): 데이터·규칙 약 75h, 화면 약 65h, AI·발표 약 62h. 데이터·규칙이 가장 무거우므로 2주차에 T2-8(신고 자료 API)과 T1-8(매물 글 쓰기)을 AI·발표 담당이 받는다. 그러면 약 65h / 65h / 72h.

### 2명일 때

명세대로 AI·발표 역할을 둘이 나눈다.
- A(데이터·규칙 + AI 서버): T1·T2 전부, T3-1~T3-6
- B(화면 + 발표·배포): T4 전부, T5-1, T5-2, T5-4~T5-9, T3-7
- 1인당 약 100h가 되므로 아래 "범위 축소 순서"를 1번부터 적용하고, 6단계는 하지 않는다.

### 범위 축소 순서 (일정이 밀릴 때 이 순서로 뺀다)

1. T6 브라우저 확장, T3-5 캡처 입력 (선택 작업이므로 처음부터 빠져 있음)
2. T0-6 CI → 각자 PR 전에 직접 `npm test` 실행으로 대체
3. T4-6 배율 분포 차트 → 요약 타일 숫자만
4. T5-1의 WebKit 실행, T3-7 품질 점검 표본 수 축소
5. T4-11 지문 탭의 "연결 근거" 열 → 지문 ID와 통계만

**절대 빼지 않는 것**: 규칙 엔진 테스트, 판매자 지문 묶음, 신고 자료 페이지, 신호 0개 사례, AI 실패 시 직접 입력 흐름, 마스킹·해시(연락처 원문 미저장), 하단 고정 문구, 출처·확인 시각 표시, 금지 표현 검사.

---

## 10. 위험과 대안

| 위험 | 가능성 | 대안 |
|---|---|---|
| 구조화 출력 파라미터나 Zod 연동 방식이 예상과 다름 | 낮음 | 5절 머리의 확인 사항대로 `output_config.format` + `messages.parse()`로 시작. SDK 호출은 `ai/client.ts` 한 곳에만 있으므로 그 파일만 수정. 최악의 경우 JSON 스키마를 직접 넘기고 응답을 Zod로 다시 검사 |
| Node 20 지원 종료 / better-sqlite3 설치 실패 (특히 Windows) | 중 | Node 22 LTS 이상으로 통일(D10). 설치가 안 되면 Docker 안에서 개발·테스트 |
| 시연 중 AI 응답 지연·네트워크 끊김 | 중 | 캐시 예열(T5-4), 실패 시 직접 입력 흐름, 녹화 영상 백업(T5-6) |
| 시연 결과가 대본과 다르게 나옴 (카드 4장 등) | 중 | 신호 코드당 카드 1장(D5), 시연 글 고정 테스트(T1-5), seed 회귀 테스트(T5-3) |
| 정가 이하 양도인데 신호가 나옴 | 중 | 양도 금지 규정 없는 공연으로 시연(D7). 신호 0개 테스트를 CI에서 항상 확인 |
| 마스킹 정규식이 날짜·가격을 지움 | 중 | "지우면 안 되는 것" 테스트 사례(T1-1). 새 오탐이 나오면 테스트를 먼저 추가 |
| 판매글 속 프롬프트 인젝션 | 낮음 | 판매글을 구분 태그로 감싸 자료로만 취급, 출력은 스키마로 제한, 판정은 코드가 하고 추출값은 팬이 확인 |
| AI 설명에 법적 단정 표현이 섞임 | 낮음 | 금지 표현 검사 → 템플릿으로 교체(T3-6), 화면 문구 자동 검사(T3-1) |
| 이미지 크기가 API 한도를 넘음 (base64로 바꾸면 약 33% 커짐) | 낮음 | 업로드 5MB 제한 유지 후 실제로 확인. 문제가 생기면 이미지 축소 라이브러리 추가를 검토 |
| 핵심 경로 담당(데이터·규칙) 과부하 | 중 | T1-8 매물 글 쓰기를 AI·발표 담당과 나눔, 범위 축소 순서 적용 |
| Basic 인증 비밀번호 노출 | 낮음 | 로컬 시연만 HTTP 허용, 외부 배포 시 HTTPS 필수(T5-2 문서), 비밀번호는 `.env`에만 저장 |
| 발표장 노트북에서 Docker가 실행되지 않음 | 중 | `npm run build && npm start` 대체 실행법(T5-2), 리허설을 발표 노트북으로 진행 |
| (v2) "판매자 낙인·사적 제재" 비판 | 중 | 지문은 사실 개수만, 판매자 정보·해시 비공개, 신고 자료에 "위법 여부는 신고기관이 판단" 고정 문구, 금지 표현 자동 검사(T3-1), 발표에 한계 장표 |
| (v2) 연락처 원문이 어딘가에 남음 (로그, 캐시, 응답) | 중 | `contacts`는 클라이언트 타입에서 제거(T0-2), 캐시에 넣지 않음(T3-3), 신고 자료 응답 문자열 검색 테스트(T2-8), 제출 전 DB·로그 전수 검색(T5-8) |
| (v2) 지문 오연결(번호 재사용, 가족 공유 계좌) | 낮음 | 연락처 일치만 병합(D16), 담당자 화면에 연결 근거 표시, 묶음 분리 기능은 확장안으로 발표 |
| (v2) 솔트 분실·변경으로 기존 지문이 끊김 | 낮음 | `.env.example`에 설명, 데모 DB와 솔트를 함께 백업, 발표 노트북에 같은 `.env` 사용 |
| (v2) 데이터·규칙 담당 과부하 | 중 | 2주차에 T2-8·T1-8을 AI·발표 담당에게 이관(9절), 범위 축소 순서 적용 |

---

## 11. 짚어 둘 점

- 명세의 빈칸 14개를 2.1절에 "권장 결정"으로 정리했다. 특히 **D5(카드 수)·D7(신호 0개 시연 공연 선택)·D10(Node 20 지원 종료)** 은 확정하지 않으면 시연이나 설치에서 바로 문제가 된다.
- Node 20 지원 종료일(2026-04-30)은 Node.js 공식 일정 기준의 사실이다. 반면 better-sqlite3·Vite·Tailwind 등의 최신 버전별 지원 범위는 착수 시 직접 확인이 필요하다.
- 명세 2.3절의 "하지 않는 것"은 작업으로 넣지 않았고, 발표의 한계 장표(T5-5)에서 명세 문장을 그대로 인용하는 데서만 다룬다.
