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
- 데이터베이스 `ticket_radar`는 서버가 처음 붙을 때 없으면 만든다(JDBC URL의 `createDatabaseIfNotExist`).

화면(React) 개발 서버는 따로 띄운다. `/api` 요청은 Vite가 8080으로 넘겨 준다.

```powershell
cd client; npm install; npm run dev
```

## `.env` 칸 설명

| 칸 | 뜻 | 언제부터 |
|---|---|---|
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | 로컬 MySQL 접속 정보. 기본 `127.0.0.1:3306`, DB 이름 `ticket_radar` | 0단계 |
| `SERVER_PORT` | 서버 포트. 기본 8080 | 0단계 |
| `ADMIN_PASSWORD` | 관리자 화면(⑤) HTTP Basic 비밀번호 | 2단계 |
| `ANTHROPIC_API_KEY` `CLAUDE_MODEL` | Claude API 키와 모델 ID. 비워 두면 AI 추출 없이 `fallback`으로 동작 | 4단계 |

## MySQL이 꺼져 있으면

서버가 **뜨지 않고 바로 종료**된다(2026-11-01 관찰, Spring Boot 4.1.2). 로그에 아래 세 줄이 순서대로 보이면 MySQL이 꺼진 것이다.

```
HikariPool-1 - Starting...
Communications link failure ... Caused by: java.net.ConnectException: Connection refused: getsockopt
Failed to initialize JPA EntityManagerFactory: ... Unable to determine Dialect without JDBC metadata
```

고치는 법: 관리자 PowerShell에서 `net start MySQL80` (또는 서비스 앱에서 MySQL80 시작) → 다시 `scripts\start.ps1`. 비밀번호가 틀리면 같은 자리에 `Access denied for user 'root'@'localhost'`가 나온다 → `.env`의 `DB_PASSWORD` 확인.

## 테스트

```powershell
cd server; .\mvnw.cmd test
```

테스트는 메모리 DB(H2, MySQL 모드)로 돌아가므로 로컬 MySQL이 없어도 된다.

## 폴더

- `server/` Spring Boot (Java 17, Maven). Controller / Service / Repository
- `client/` React + Vite + TypeScript
- `docs/` 기획서·아키텍처·화면 명세·디자인 토큰·API 계약서
- `scripts/` 실행 스크립트
