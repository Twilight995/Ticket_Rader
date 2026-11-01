# 개발 일지

세션이 끝날 때마다 "어디까지 됐고, 다음은 무엇이고, 무엇이 막혔는지"를 날짜와 함께 한 줄씩 적는다.

## 2026-11-01 (첫 개발 세션)

- 0단계 환경: `server/`(Spring Boot 4.1.2, Java 17, Maven wrapper) + `client/`(Vite 8, React 19, TS) 뼈대. `GET /api/health` 테스트 통과(H2). `.env.example`, `.gitignore`, `scripts/start.ps1`, `README.md`.
- 가안으로 정한 것: 빌드 도구 Maven(팀이 수업에서 쓴 쪽), 패키지 `com.wol32.ticketradar`, 테스트는 H2 MySQL 모드(로컬 MySQL 없이도 돌게), `.env`는 Spring의 `spring.config.import`로 읽음(라이브러리 추가 없음).
- 막힌 것: 이 세션에서는 MySQL80 서비스가 꺼져 있고 관리자 권한이 없어 켜지 못함. **실제 MySQL 연결 확인(0단계 완료 기준 일부)은 팀원이 `net start MySQL80` + `.env`의 `DB_PASSWORD` 입력 후 `scripts/start.ps1`로 직접 확인해야 한다.** MySQL 꺼짐 오류 문구는 README에 적었다.
- 1단계 계약서: `docs/api-contract.md`에 Must API 5장(9주차 양식). 가안: 오류 봉투 `error: { message, fields }`, 공연에 `officialUrl` 추가(아키텍처 STEP 3에 반영), 확인사항·전달 방식·규정 종류 영문 코드와 한국어 표시 표, 추출 응답에 `maskedText`(연락처 지운 글, 화면 ②③의 "다시 보기"용).
- 2단계 공연 데이터·관리자 API: 엔티티 3개(`events`·`price_tiers`·`event_rules`), seed 자동 투입(두 번 실행해도 1건), `GET /api/event`, `GET/PUT /api/admin/event`(HTTP Basic, 인터셉터 한 곳, SHA-256 다이제스트 상수시간 비교, 비밀번호 비어 있으면 닫힘), 오류 봉투 한 곳(`ApiExceptionHandler`). 테스트 20개(H2). 코드 리뷰에서 필터 경로 우회(`/api/admin;x=1/event`)와 404/405/415→500 문제를 찾아 고침.
- 3단계 비교 API(규칙 엔진): `POST /api/check`. `RuleEngine`은 순수 함수(입력 + 공연 + 기준값 → 확인사항 목록), 기준값은 `ticket-radar.rules.*`. 확인사항 6종, 종류당 1장, 심각도 순 정렬, 템플릿 설명. 테스트 41개 전부 통과(수용 기준 2·3 관련 테스트 포함). AI 설명(선택)은 4단계 이후.
- 브랜치: `feat/stage0-env` → `feat/stage1-api-contract` → `feat/stage2-event-admin-api` → `feat/stage3-rule-engine` 순서로 쌓여 있다(각 단계 커밋 1개). 리뷰 후 순서대로 `master`에 합치면 된다.
- **다음 세션**: ① 팀원이 MySQL80 켜고 `.env` 채워 `scripts/start.ps1`로 실제 MySQL 연결 확인(0단계 완료 기준 마무리, 9주차 점검 ①③⑤를 실제 요청으로 한 번 더) ② 4단계 AI 추출(`POST /api/extract`, Claude Java SDK, 키 필요) ③ 5단계 화면.
- 이 세션에서 알게 된 것: Spring Boot 4에서는 테스트 애너테이션 패키지가 바뀌었다(`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `AutoConfigureMockMvc`), Jackson이 3.x(`tools.jackson.*`)다. Initializr 버전 문자열 `4.1.2.RELEASE`는 Maven 중앙에 없어 `4.1.2`로 고쳐야 한다.
- 2026-11-01 저녁, 실제 MySQL 연결 확인 완료: 팀원이 MySQL80을 켜고 `.env`를 채운 뒤 `scripts/start.ps1`로 서버 기동. 떠 있는 서버에 직접 요청해 health / 공연 조회(seed가 MySQL에 들어감) / 비밀번호 없는 관리자 요청 401 / 음수 가격 400 / 없는 주소 404 / 시연 글 3건 / 정상 글 0건 모두 확인 → 0단계 완료 기준과 9주차 점검 ①③⑤ 실서버 통과. (주의: Git Bash에서 curl로 한글을 보내면 인코딩이 깨져 400이 난다. 브라우저·React 요청은 UTF-8이라 문제없음.)
- 4단계 AI 추출(같은 날 저녁, 팀원 결정 "AI 부품 교체 가능 + 1차 Gemini 무료 티어"): ADR 5 추가, `POST /api/extract`. 순서 = 입력 검증(글 5,000자·이미지 바이트로 PNG/JPG·5MB) → 분당 한도(접속별 10, 전체 14) → 공연 확인 → 연락처 마스킹 `[삭제]` → `Extractor`(Gemini REST, JSON 스키마 고정, `<listing>` 태그로 자료 취급, 20초) → 실패·시간 초과·형식 불일치는 200 + `fallback`. 공급자는 `ExtractorConfig`가 `AI_PROVIDER`로 고름(claude 는 자리만, 미구현). 테스트 61개.
- 알게 된 것: 이 환경의 Bash 도구는 역슬래시 두 개를 하나로 줄인다(정규식·`
`이 깨짐). 역슬래시가 있는 파일은 Write 도구로 써야 한다.
- 4단계 실제 키 확인(2026-11-01 저녁): 모델 코드는 `gemini-3.5-flash-lite`(AI Studio 표시 이름이 아니라 코드를 `.env`의 `AI_MODEL`에 적어야 함). 처음엔 4회 중 1회꼴로 `fallback`이 났는데, 원인은 모델이 `evidence.seat`(좌석 근거) 칸에 판매글 전체를 반복해 쓰다 출력 한도에 걸리는 것이었다(생각 토큰 아님, 직접 호출로 확인). 좌석 근거 칸을 스키마에서 빼고(좌석 값 자체가 근거) 근거는 20자 이내로 지시, 한도를 넘으면 1회 재시도, 출력 한도 1024로 조정 → 가상 글 3건×2회 + 가상 캡처 2회 = 8/8 성공, 값 전부 정확, 응답 어디에도 연락처 없음. 남은 품질 이슈: `evidence.quantity`에 문장 뒷부분이 길게 딸려 올 때가 있다(120자에서 자름, 화면에는 작은 글씨라 영향 적음).
- 연기 테스트 `GeminiLiveSmokeTest`: `GEMINI_API_KEY`·`AI_MODEL` 환경 변수가 있을 때만 실제 API를 한 번 부른다(없으면 자동 건너뜀). 실행: `GEMINI_API_KEY=... AI_MODEL=gemini-3.5-flash-lite ./mvnw test -Dtest=GeminiLiveSmokeTest`.
- 다음: 5단계 화면(⓪①②③⑤). 4단계 fallback 경로(키 제거)는 단위 테스트와 `AI_PROVIDER=none` 설정으로 보장되며, 실서버에서는 서버 시작 로그에 "extraction will always fall back"이 찍히는지로 확인한다.
