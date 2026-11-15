# 티켓레이더(가칭)

중고 공연 티켓 판매글을 붙여넣거나 캡처를 올리면, AI가 가격·좌석·전달 방식을 뽑고 서비스가 공연 공식 정가·입장 규정과 코드로 대조해 "확인이 필요한 부분"을 보여주는 반응형 웹. 2026 AID AI-Thon, 팀 월32.

설계 문서는 `docs/`에, 개발 규칙은 `CLAUDE.md`에 있다. 개발 순서는 `docs/dev-kickoff.md`.

## 실행 (팀 노트북)

준비물: Java 17+, Node 20+, MySQL 8 (서비스 `MySQL80` 켜 둘 것). Maven·Vite는 설치 불필요(저장소 안 `mvnw`, `npm`이 처리).

1. 저장소 루트에서 `.env.example`을 복사해 `.env`를 만들고 `DB_PASSWORD`를 채운다. (`.env`는 커밋하지 않는다)
2. 아래 한 줄로 서버를 띄운다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start.ps1
```

같은 일을 손으로 하려면: `cd server` → `.\mvnw.cmd spring-boot:run`

- 확인: 브라우저에서 http://localhost:8080/api/health → `{"ok":true,"data":{"status":"up"},"error":null}`
- 화면: `start.ps1`이 화면을 먼저 빌드(`client`에서 `npm run build`)하므로 http://localhost:8080 에서 홈이 열린다. 서버 빌드가 `client/dist`를 정적 폴더로 복사한다(`pom.xml`의 `copy-client-dist`). 손으로 띄울 때는 `client`에서 `npm run build`를 먼저 한다.
- 관리자 화면: http://localhost:8080/#/admin → 브라우저 기본 로그인창에 아이디 `admin`, 비밀번호는 `.env`의 `ADMIN_PASSWORD`.
- 데이터베이스 `ticket_radar`는 서버가 처음 붙을 때 없으면 만든다(JDBC URL의 `createDatabaseIfNotExist`).

화면을 고치면서 볼 때는 개발 서버를 따로 띄운다(저장하면 바로 바뀐다). `/api` 요청은 Vite가 8080으로 넘겨 준다.

```powershell
cd client; npm install; npm run dev
```

## `.env` 칸 설명

| 칸 | 뜻 | 언제부터 |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | 로컬 MySQL 접속 정보. 기본 `127.0.0.1:3306`, DB 이름 `ticket_radar` | 0단계 |
| `SERVER_PORT` | 서버 포트. 기본 8080 | 0단계 |
| `ADMIN_PASSWORD` | 관리자 화면(⑤) HTTP Basic 비밀번호 | 2단계 |
| `AI_PROVIDER` `AI_MODEL` `GEMINI_API_KEY` (`ANTHROPIC_API_KEY`) | AI 추출 공급자(`none`/`gemini`/`claude`), 모델 ID, 키. 키가 비어 있으면 AI 없이 `fallback`으로 동작(ADR 5). 무료 티어 한도 때문에 서버가 접속별 분당 10회·전체 분당 14회로 제한 | 4단계 |

## MySQL이 꺼져 있으면

서버가 **뜨지 않고 바로 종료**된다(2026-11-01 관찰, Spring Boot 4.1.2). 로그에 아래 세 줄이 순서대로 보이면 MySQL이 꺼진 것이다.

```
HikariPool-1 - Starting...
Communications link failure ... Caused by: java.net.ConnectException: Connection refused: getsockopt
Failed to initialize JPA EntityManagerFactory: ... Unable to determine Dialect without JDBC metadata
```

고치는 법: 관리자 PowerShell에서 `net start MySQL80` (또는 서비스 앱에서 MySQL80 시작) → 다시 `scripts\start.ps1`. 비밀번호가 틀리면 같은 자리에 `Access denied for user 'root'@'localhost'`가 나온다 → `.env`의 `DB_PASSWORD` 확인.

## 지금까지 되는 것 (2026-11-01)

| API | 상태 |
|---|---|
| `GET /api/health` | 동작 |
| `GET /api/event` 공연 조회 (가상 공연 1건이 서버 시작 시 자동 투입) | 동작 |
| `GET` / `PUT /api/admin/event` 관리자 조회·저장 (HTTP Basic, 아이디 `admin`, 비밀번호 `.env`의 `ADMIN_PASSWORD`) | 동작 |
| `POST /api/check` 비교(확인사항 계산, 규칙 엔진) | 동작 |
| `POST /api/extract` AI 추출 (연락처 마스킹 → AI, 실패하면 `fallback`) | 동작 (공급자는 `.env`의 `AI_PROVIDER`, 1차 Gemini 무료 티어) |
| 화면 ⓪ 홈 → ① 판매글 입력 → ② 읽은 값 확인 → ③ 확인사항 결과, ⑤ 관리자(`#/admin`) | 동작 (5단계, 다크 모드 1차. AI가 꺼져 있어도 ②가 빈 폼으로 열려 끝까지 진행) |

직접 눌러 보기 (서버가 떠 있을 때, PowerShell):

```powershell
Invoke-RestMethod http://localhost:8080/api/event | ConvertTo-Json -Depth 5
```

```powershell
Invoke-RestMethod -Method Post -ContentType "application/json; charset=utf-8" -Uri http://localhost:8080/api/check -Body '{"eventId":1,"pricePerTicket":250000,"seat":"VIP석 A구역","quantity":2,"deliveryMethod":"CAPTURE_QR","prepaymentRequired":true}' | ConvertTo-Json -Depth 5
```

```powershell
curl.exe -s -F "eventId=1" -F "text=VIP석 A구역 2장 양도, 장당 25만, 선입금 후 QR 캡처 전송" http://localhost:8080/api/extract
```

규칙 엔진 기준값(정가 배율 1.5, 수량 4장)은 `server/src/main/resources/application.properties`의 `ticket-radar.rules.*`에 있다. 계약(요청·응답 모양)은 `docs/api-contract.md`.

## 테스트

```powershell
cd server; .\mvnw.cmd test
```

테스트는 메모리 DB(H2, MySQL 모드)로 돌아가므로 로컬 MySQL이 없어도 된다. 실제 Gemini를 한 번 불러 보는 연기 테스트는 키가 환경 변수에 있을 때만 돈다: `GEMINI_API_KEY=... AI_MODEL=gemini-3.5-flash-lite ./mvnw test -Dtest=GeminiLiveSmokeTest`. 수용 기준(정상 양도 글 2건 → 확인사항 0건, 시연 글 → 정확히 3건, 기준값 변경 시 결과 변화, 같은 입력 → 같은 결과, 금지 단어 없음)이 `RuleEngineTest`·`ForbiddenWordsTest`에 자동 테스트로 들어 있다.

화면 테스트는 Node 내장 테스트 도구로 돌린다(새 라이브러리 없음, Node 22.18 이상 필요). 화면 문구 금지 단어 검사, ② 값 변환(고친 칸의 근거를 `null`로), ⑤ 주소 형식 검사가 들어 있다.

```powershell
cd client; npm test
```

## 폴더

- `server/` Spring Boot (Java 17, Maven). Controller / Service / Repository
- `client/` React + Vite + TypeScript
- `docs/` 기획서·아키텍처·화면 명세·디자인 토큰·API 계약서
- `scripts/` 실행 스크립트
