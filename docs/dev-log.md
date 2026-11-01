# 개발 일지

세션이 끝날 때마다 "어디까지 됐고, 다음은 무엇이고, 무엇이 막혔는지"를 날짜와 함께 한 줄씩 적는다.

## 2026-11-01 (첫 개발 세션)

- 0단계 환경: `server/`(Spring Boot 4.1.2, Java 17, Maven wrapper) + `client/`(Vite 8, React 19, TS) 뼈대. `GET /api/health` 테스트 통과(H2). `.env.example`, `.gitignore`, `scripts/start.ps1`, `README.md`.
- 가안으로 정한 것: 빌드 도구 Maven(팀이 수업에서 쓴 쪽), 패키지 `com.wol32.ticketradar`, 테스트는 H2 MySQL 모드(로컬 MySQL 없이도 돌게), `.env`는 Spring의 `spring.config.import`로 읽음(라이브러리 추가 없음).
- 막힌 것: 이 세션에서는 MySQL80 서비스가 꺼져 있고 관리자 권한이 없어 켜지 못함. **실제 MySQL 연결 확인(0단계 완료 기준 일부)은 팀원이 `net start MySQL80` + `.env`의 `DB_PASSWORD` 입력 후 `scripts/start.ps1`로 직접 확인해야 한다.** MySQL 꺼짐 오류 문구는 README에 적었다.
