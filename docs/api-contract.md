# 티켓레이더(가칭) API 계약서 (9주차 양식)

- 작성일: 2026-11-01 (1단계). 근거: `docs/architecture-ticket-radar.md` STEP 3·4, `docs/screen-spec-ticket-radar.md`
- 이 문서가 곧 AI 지시문이다. 화면 담당은 이 문서의 "성공 시 돌려주는 것"을 가짜 응답으로 써서 서버 없이 화면을 만든다. 서버 담당은 화면 없이 요청만 보내며 만든다.
- 담당(A)은 기획서 RACI에서 옮긴 **가안**이다(서버 API 전부 서버 담당, 화면 화면 담당). 팀이 다르게 정하면 이 칸만 고친다.
- 계약을 바꾸려면 코드가 아니라 **이 문서를 먼저** 고친다.

## 0. 모든 API가 공통으로 지키는 것

### 응답 봉투 (한 모양)

```json
{ "ok": true,  "data": { ... }, "error": null }
{ "ok": false, "data": null,    "error": { "message": "사람이 읽는 한국어 한 문장", "fields": { "칸이름": "그 칸의 이유" } } }
```

- `error.message`는 항상 있다. `error.fields`는 어느 칸이 틀렸는지 알려 줄 수 있을 때만 있고(400), 아니면 `null`.
- 칸 이름은 요청 JSON 경로 그대로 (`pricePerTicket`, `priceTiers[1].sourceUrl`).
- 응답에 스택·파일 경로·테이블 이름·SQL·API 키를 절대 넣지 않는다. 500은 항상 `"잠시 문제가 생겼어요. 다시 시도해 주세요"` 한 문장.

### 상태 코드

| 코드 | 뜻 | 이 서비스에서 |
|---|---|---|
| 200 | 성공 | 조회·추출·비교·저장 모두 200 (PUT은 바꾼 결과 전체를 돌려주므로 201을 쓰지 않는다) |
| 400 | 보낸 값이 잘못됨 | 형식·범위·필수 누락. `fields`에 칸별 이유 |
| 401 | 누구인지 모름 | 관리자 API에 비밀번호 없음·틀림. `WWW-Authenticate: Basic realm="ticket-radar-admin"` 헤더 포함 |
| 404 | 없음 | 요청한 공연이 없음 |
| 429 | 너무 자주 부름 | 추출 API 분당 10회 초과 |
| 500 | 서버 잘못 | DB 연결 실패 등. 사용자 문구만 |

- 403은 쓰지 않는다(계정이 없어 "남의 것"이 없다). AI 실패는 오류가 아니므로 **200 + `aiStatus: "fallback"`**.

### 값의 종류 (열거형). 서버는 영문 코드만 주고받고, 한국어 표시는 화면이 붙인다

| 종류 | 코드 | 화면 표시 |
|---|---|---|
| 공연 종류 `category` | `CONCERT` / `SPORTS` | 공연 / 스포츠 (③ 신고센터 링크 선택용) |
| 전달 방식 `deliveryMethod` | `ON_SITE_PICKUP` / `CAPTURE_QR` / `ACCOUNT_TRANSFER` / `MOBILE_TICKET_TRANSFER` / `OTHER` | 본인 현장 수령 / 캡처·QR 전송 / 계정 양도 / 모바일 티켓 전송 / 기타 |
| 규정 종류 `rules[].type` | `IDENTITY_CHECK_REQUIRED` / `TRANSFER_PROHIBITED` / `CAPTURE_ENTRY_NOT_ALLOWED` / `ON_SITE_PICKUP_REQUIRED` / `OTHER` | 본인 확인 필수 / 양도 금지 / 캡처 입장 불가 / 현장 수령 필수 / 기타 |
| 확인사항 종류 `findings[].type` | `PRICE_ABOVE_LIST` / `PRICE_NOT_COMPARABLE` / `PREPAYMENT_REQUESTED` / `DELIVERY_CONFLICTS_RULE` / `TRANSFER_PROHIBITED` / `QUANTITY_HIGH` | 정가보다 높음 / 정가 대조 불가 / 선입금 요구 / 전달 방식이 규정과 충돌 / 양도 금지 공연 / 수량 많음 (서버가 `title`로도 보내 준다) |
| 심각도 `severity` | `INFO` / `CAUTION` / `CHECK` | 정보 / 주의 / 확인 필요 (서버가 `severityLabel`로도 보내 준다) |
| AI 상태 `aiStatus` | `ok` / `fallback` | — |

- 날짜 `checkedOn`은 `YYYY-MM-DD`, 일시 `startsAt`은 `YYYY-MM-DDTHH:mm:ss`(한국 시간, 시간대 표기 없음). 금액은 원 단위 정수.
- 어떤 응답에도 **연락처 칸이 없다**(전화·계좌·오픈채팅·텔레그램). 추출 전에 서버가 지운다.
- 템플릿 문장·`title`·`explanation`에 금지 단어(암표·암표상·불법·사기·상습·범죄)를 쓰지 않는다. 자동 테스트로 막는다.

### 공연 한 건의 모양 `Event` (조회·관리자 조회·관리자 저장이 같이 쓴다)

```json
{
  "id": 1,
  "name": "2026 ○○ 콘서트 (가상)",
  "startsAt": "2026-12-20T18:00:00",
  "venue": "○○ 아레나 (가상)",
  "category": "CONCERT",
  "officialUrl": "https://example.com/notice",
  "priceTiers": [
    { "id": 1, "grade": "VIP", "price": 165000, "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" },
    { "id": 2, "grade": "R",   "price": 143000, "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" },
    { "id": 3, "grade": "S",   "price": 121000, "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" }
  ],
  "rules": [
    { "id": 1, "type": "ON_SITE_PICKUP_REQUIRED",   "text": "티켓은 공연 당일 현장에서 예매자 본인이 수령합니다.", "sourceUrl": "https://example.com/notice#rules", "checkedOn": "2026-10-28" },
    { "id": 2, "type": "CAPTURE_ENTRY_NOT_ALLOWED", "text": "캡처 화면이나 사진으로는 입장할 수 없습니다.",          "sourceUrl": "https://example.com/notice#rules", "checkedOn": "2026-10-28" },
    { "id": 3, "type": "IDENTITY_CHECK_REQUIRED",   "text": "입장 시 예매자 본인 확인(신분증)을 진행합니다.",         "sourceUrl": "https://example.com/notice#rules", "checkedOn": "2026-10-28" }
  ]
}
```

- `officialUrl`은 홈(⓪)과 ③ 빈 상태의 "공식 안내 원문 보기" 링크. 아키텍처 STEP 3 공연 덩어리에 2026-11-01 추가.
- 대상 공연이 정해질 때까지 이름·장소에 "(가상)"을 붙인다. 실제 공연이 정해지면 `server/src/main/resources/seed/event.json`만 바꾼다.

---

## 1. 공연 조회

| 항목 | 내용 |
|---|---|
| **이름** | 공연 조회 |
| **주소와 방식** | `GET /api/event` |
| **누가 부를 수 있나** | 누구나 (⓪ 홈, ① 판매글 입력이 쓴다) |
| **보내는 것** | 없음 |
| **서버가 확인할 것** | 없음 |
| **성공 시 돌려주는 것** | 200, `data`: `Event` 한 건 (위 모양). 등록된 공연이 없으면 `data: null` → 홈은 안내 문구 + 버튼 비활성 |
| **실패 시 돌려주는 것** | 500 DB를 못 읽음 |
| **담당 (A)** | 서버 서버 담당 / 화면 화면 담당 (가안) |

---

## 2. 판매글 추출 (AI)

| 항목 | 내용 |
|---|---|
| **이름** | 판매글 추출 |
| **주소와 방식** | `POST /api/extract` — `multipart/form-data` (이미지를 함께 보낼 수 있어야 하므로 JSON이 아니라 폼 전송) |
| **누가 부를 수 있나** | 누구나. 단, 같은 접속(IP) 분당 10회 |
| **보내는 것** | `eventId` (정수, 필수) / `text` (문자, 선택, 5,000자 이하) / `image` (파일, 선택, PNG 또는 JPG, 5MB 이하). **text와 image 중 하나 이상** |
| **서버가 확인할 것** | ① 공연 존재 ② text·image 둘 다 없으면 400 ③ text 길이 ④ image는 확장자·Content-Type이 아니라 **파일 앞부분 바이트**(PNG `89 50 4E 47`, JPG `FF D8 FF`)로 확인, 5MB 초과 400 ⑤ 분당 10회 초과 429 ⑥ AI에 보내기 **전에** 연락처 패턴(전화·계좌·오픈채팅·텔레그램)을 `[삭제]`로 바꾼다 ⑦ 판매글은 구분 태그로 감싸 "분석할 자료"로만 다룬다(글 안의 지시문을 따르지 않음) ⑧ AI 호출 20초 제한 |
| **성공 시 돌려주는 것** | 200, `data`: 아래. AI 실패·시간 초과·거부·형식 불일치도 **200**이며 `aiStatus: "fallback"`, 값은 전부 `null` → ②가 빈 폼으로 열린다. 이미지는 응답 후 메모리에서 버린다. 원문·키를 로그에 남기지 않는다 |
| **실패 시 돌려주는 것** | 400 값 잘못됨(`fields`: `text`, `image`, `eventId`) / 404 공연 없음 / 429 "요청이 너무 많아요. 잠시 후 다시 시도해 주세요" / 500 |
| **담당 (A)** | 서버 서버 담당 / 화면 화면 담당 (가안) |

```json
{
  "aiStatus": "ok",
  "extracted": {
    "pricePerTicket": 250000,
    "seat": "VIP석 A구역",
    "quantity": 2,
    "deliveryMethod": "CAPTURE_QR",
    "prepaymentRequired": true
  },
  "evidence": {
    "pricePerTicket": "장당 25만",
    "seat": "VIP석 A구역",
    "quantity": "2장",
    "deliveryMethod": "QR 캡처로 보내드려요",
    "prepaymentRequired": "선입금 확인 후"
  },
  "maskedText": "VIP석 A구역 2장 양도해요. 장당 25만. 선입금 확인 후 QR 캡처로 보내드려요. 연락 [삭제]"
}
```

- `extracted`의 각 값은 못 읽었으면 `null`. `pricePerTicket`은 **1장당** 원 단위 정수(AI가 "2장 50만"을 읽으면 25만으로 나눠 준다). `evidence`는 그 값을 읽은 판매글 구절(없으면 `null`).
- `maskedText`는 서버가 연락처를 지운 글(텍스트 입력일 때만. 이미지만 보냈으면 `null`). 화면 ②·③의 "판매글 다시 보기"는 원문이 아니라 **이 값**을 보여 준다(연락처를 다시 보여주지 않는 규칙).
- 응답에 `contacts`·전화번호 칸은 없다.

---

## 3. 비교 (확인사항 계산) — 핵심, 코드가 판단

| 항목 | 내용 |
|---|---|
| **이름** | 비교 |
| **주소와 방식** | `POST /api/check` — JSON |
| **누가 부를 수 있나** | 누구나 |
| **보내는 것** | `eventId` (정수, 필수) / `pricePerTicket` (정수, 선택, 0 이상, 1장당) / `seat` (문자, 선택, 100자 이하) / `quantity` (정수, 선택, 1 이상 100 이하) / `deliveryMethod` (코드 5개 중 하나, 선택) / `prepaymentRequired` (참·거짓, 선택) / `evidence` (객체, 선택: 위 추출 응답의 `evidence`를 그대로. 사용자가 고친 칸은 `null`로) |
| **서버가 확인할 것** | ① 공연 존재(404) ② **`pricePerTicket`과 `deliveryMethod` 중 하나 이상**(둘 다 없으면 400) ③ 각 칸의 형식·범위 (화면이 막았어도 다시 확인) ④ 좌석 등급이 정가표에 없으면 오류가 아니라 `PRICE_NOT_COMPARABLE` 확인사항 ⑤ 판단은 전부 Service의 순수 함수. 같은 입력이면 항상 같은 결과. 기준값(배율·수량)은 설정 파일 |
| **성공 시 돌려주는 것** | 200, `data`: 아래. 확인사항 0건이면 `findings: []`, `summary: "확인이 필요한 부분을 찾지 못했어요. 거래 전 예매처 규정은 꼭 확인하세요"` ("안전"이라 쓰지 않음) |
| **실패 시 돌려주는 것** | 400 (`fields`: 틀린 칸별 이유. 예 `pricePerTicket: "숫자만 입력해 주세요"`) / 404 공연 없음 / 500 |
| **담당 (A)** | 서버 서버 담당 / 화면 화면 담당 (가안) |

```json
{
  "summary": "확인이 필요한 부분 3가지를 찾았어요",
  "findings": [
    {
      "type": "PRICE_ABOVE_LIST",
      "title": "정가보다 높음",
      "severity": "CHECK",
      "severityLabel": "확인 필요",
      "listingQuote": "장당 25만",
      "official": { "text": "VIP 정가 165,000원", "sourceUrl": "https://example.com/notice#price", "checkedOn": "2026-10-28" },
      "explanation": "판매가 250,000원은 VIP 정가 165,000원의 약 1.5배예요. 정가와 차이가 나는 이유를 판매자와 예매처에 확인해 보세요.",
      "details": { "ratio": 1.52, "listPrice": 165000, "matchedGrade": "VIP" }
    },
    {
      "type": "DELIVERY_CONFLICTS_RULE",
      "title": "전달 방식이 규정과 충돌",
      "severity": "CHECK",
      "severityLabel": "확인 필요",
      "listingQuote": "QR 캡처로 보내드려요",
      "official": { "text": "캡처 화면이나 사진으로는 입장할 수 없습니다.", "sourceUrl": "https://example.com/notice#rules", "checkedOn": "2026-10-28" },
      "explanation": "판매글은 캡처·QR로 전달한다고 하지만, 공식 안내는 캡처 화면으로는 입장할 수 없다고 해요.",
      "details": { "deliveryMethod": "CAPTURE_QR", "ruleType": "CAPTURE_ENTRY_NOT_ALLOWED" }
    },
    {
      "type": "PREPAYMENT_REQUESTED",
      "title": "선입금 요구",
      "severity": "CAUTION",
      "severityLabel": "주의",
      "listingQuote": "선입금 확인 후",
      "official": null,
      "explanation": "판매글이 티켓을 받기 전에 먼저 입금하라고 해요. 예매처 밖에서 오간 돈은 예매처가 보장하지 않아요.",
      "details": null
    }
  ],
  "officialUrl": "https://example.com/notice",
  "notice": "이 결과는 참고용이며 최종 판단은 사용자가 합니다."
}
```

- 카드 한 장 = `findings[]` 한 개. **종류당 최대 1장.** 정렬은 심각도(확인 필요 → 주의 → 정보) 다음 종류 순서(위 열거형 표 순서).
- `listingQuote`: 요청의 `evidence`에 그 칸의 구절이 있으면 그것, 없으면 서버가 값으로 만든 문장(예: `"1장당 250,000원 (직접 입력)"`).
- `official`: 공식 문장 + 출처 + 확인 날짜. 정가 관련은 정가표 행에서, 규정 관련은 규정 행에서 온다. 대응하는 공식 문장이 없는 종류(선입금 요구, 수량 많음)는 `null`이고, 화면은 그 자리에 "공식 안내 원문 보기" 링크(`officialUrl`)만 둔다.
- `explanation`: 쉬운 말 1~2문장. 1차는 **템플릿**(Service 안 고정 문구). AI 설명은 선택이며 붙여도 판정·심각도는 바뀌지 않는다.
- 각 카드에 붙는 "예매처에 재확인하세요"는 모든 카드가 같으므로 화면 부품이 붙인다.
- `details`는 종류별 보조 숫자(화면은 몰라도 된다). 종류별 규칙과 기준값은 3단계 규칙 엔진 설정 파일에서 정한다. 가안: 정가 초과면 카드, 배율 1.5 이상이면 "확인 필요" 아니면 "주의" / 선입금 요구 "주의" / 전달 방식 충돌 "확인 필요" / 양도 금지 공연 "확인 필요" / 수량 4장 이상 "정보" / 정가 대조 불가 "정보".

---

## 4. 관리자 공연 조회

| 항목 | 내용 |
|---|---|
| **이름** | 관리자 공연 조회 |
| **주소와 방식** | `GET /api/admin/event` |
| **누가 부를 수 있나** | 관리자(팀원). HTTP Basic: 아이디 `admin`, 비밀번호는 환경 변수 `ADMIN_PASSWORD` |
| **보내는 것** | 헤더 `Authorization: Basic …` 만 |
| **서버가 확인할 것** | 비밀번호 (없음·틀림 → 401). 비교는 길이에 따라 시간이 달라지지 않는 방식(timing-safe). `ADMIN_PASSWORD`가 비어 있으면 관리자 API 전체를 401로 막는다(비밀번호 없는 상태로 열리지 않게) |
| **성공 시 돌려주는 것** | 200, `data`: `Event` 전체 (공개 조회와 같은 모양). 없으면 `data: null` → ⑤ 빈 상태 |
| **실패 시 돌려주는 것** | 401 "관리자 비밀번호가 필요해요" / 500 |
| **담당 (A)** | 서버 서버 담당 / 화면 화면 담당 (가안) |

---

## 5. 관리자 공연 저장

| 항목 | 내용 |
|---|---|
| **이름** | 관리자 공연 저장 (공연 1건을 통째로 바꾼다. 없으면 새로 만든다) |
| **주소와 방식** | `PUT /api/admin/event` — JSON |
| **누가 부를 수 있나** | 관리자. HTTP Basic (4번과 같음) |
| **보내는 것** | `Event`에서 `id`들을 뺀 것: `name` (문자, 필수, 100자 이하) / `startsAt` (일시, 필수) / `venue` (문자, 필수, 100자 이하) / `category` (`CONCERT`·`SPORTS`, 필수) / `officialUrl` (문자, 필수, http(s)로 시작) / `priceTiers[]` (0개 이상): `grade` (문자, 필수, 30자 이하), `price` (정수, 필수, 0 이상), `sourceUrl` (http(s)), `checkedOn` (`YYYY-MM-DD`) / `rules[]` (0개 이상): `type` (코드 5개 중 하나), `text` (문자, 필수, 500자 이하), `sourceUrl` (http(s)), `checkedOn` (`YYYY-MM-DD`) |
| **서버가 확인할 것** | ① 비밀번호(401) ② 필수 칸·길이 ③ URL은 `http://` 또는 `https://`로 시작 ④ 정가 0 이상 정수 ⑤ **등급 이름 중복 없음**(공백·대소문자 무시) ⑥ 규정 종류 5개 중 하나 ⑦ 날짜 형식 ⑧ 저장은 한 트랜잭션: 기존 정가표·규정을 지우고 새로 넣는다(부분 저장 없음) |
| **성공 시 돌려주는 것** | 200, `data`: 저장된 `Event` 전체(새 `id` 포함). 화면은 이걸로 표를 다시 그린다 |
| **실패 시 돌려주는 것** | 401 / 400 (`fields` 예: `officialUrl: "http로 시작하는 주소를 넣어 주세요"`, `priceTiers[1].grade: "같은 등급 이름이 두 번 있어요"`, `rules[0].type: "규정 종류 5개 중 하나를 골라 주세요"`) / 500 "저장하지 못했어요. 다시 시도해 주세요" |
| **담당 (A)** | 서버 서버 담당 / 화면 화면 담당 (가안) |

---

## 6. 9주차 점검 5가지와 이 계약서의 대응

| 점검 | 어떤 요청 | 와야 하는 것 |
|---|---|---|
| ① 로그인 확인 | `GET /api/admin/event` 비밀번호 없이 | 401 + `WWW-Authenticate` |
| ② 본인 확인 | 해당 없음 (회원 없음, 공연 1건) | — |
| ③ 입력값 확인 | `POST /api/check` 에 `pricePerTicket: -1` / `PUT /api/admin/event` 에 `officialUrl: "ftp://…"` / `POST /api/extract` 에 글도 이미지도 없이 / 확장자만 png 인 텍스트 파일 | 400 + 한국어 `fields` |
| ④ 비밀번호 저장 | DB·저장소를 열어 본다 | `ADMIN_PASSWORD`·API 키는 `.env`에만. DB 어디에도 없음 |
| ⑤ 오류 때 내부 정보 | MySQL을 끄고 `GET /api/event` | 500 + "잠시 문제가 생겼어요…" 한 문장. 경로·테이블 이름 없음 |

## 7. Should 가 들어올 때 추가될 계약 (지금은 만들지 않는다)

검사 기록 저장·조회(마스킹된 원문 + 연락처 해시만), 같은 연락처 집계("다른 글 N건" 숫자만). 아키텍처 STEP 4 아래 두 줄.
