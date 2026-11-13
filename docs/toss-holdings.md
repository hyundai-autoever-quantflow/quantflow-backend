# 토스 계좌 보유 주식 조회

연결 완료된 토스 계좌에서 국내·미국 주식의 보유량과 평가금액·손익을 조회한다. DB의 Position을 갱신하거나 주문을 실행하지 않는다. 조회 결과는 토스 응답 시점의 값이며 응답에 매수 가능 금액이나 현금 잔고는 포함하지 않는다.

## Swagger 테스트

1. 서버를 재시작하고 Swagger의 Authorize에 서비스 Access Token을 입력한다.
2. `GET /api/v1/broker-connections`에서 자신의 연결 ID인 connectionId를 확인한다.
3. **보유 주식 → 내 계좌 보유 주식 조회**에서 connectionId를 입력한다. 이 값은 토스 accountSeq와 다르다.
4. symbol을 생략하면 전체, `005930` 또는 `AAPL` 등을 입력하면 해당 종목만 조회한다. 미보유 종목은 빈 목록과 토스가 계산한 요약을 반환한다.

```http
GET /api/v1/broker-connections/1/holdings
Authorization: Bearer {서비스 Access Token}
```

```http
GET /api/v1/broker-connections/1/holdings?symbol=005930
Authorization: Bearer {서비스 Access Token}
```

symbol은 영문 대소문자·숫자·점·하이픈으로 1~30자다. 빈 문자열은 허용하지 않으므로 전체 조회 시 파라미터 자체를 생략한다. 요청 본문은 없다.

응답은 다음 필드를 포함한다.

| 필드 | 의미 |
|---|---|
| connectionId | QUANTFLOW 계좌 연결 ID 문자열 |
| fetchedAt | 서버가 응답을 수신한 UTC 시각. 종목 시세 기준 시각이 아님 |
| holdings.totalPurchaseAmount | 통화별 매입금액 |
| holdings.marketValue | 비용 공제 전·후 평가금액 |
| holdings.profitLoss | 비용 공제 전·후 손익금액과 손익률 |
| holdings.dailyProfitLoss | 일간 손익금액과 손익률 |
| holdings.items | 종목별 수량·가격·평가금액·손익·비용 |

금액·수량·비율은 Java에서 BigDecimal로 처리하고 JSON에는 문자열로 반환한다. KRW와 USD 합계는 각 통화별 금액이며 서버가 합산하거나 환산하지 않는다. 계좌 전체 손익률은 토스가 제공한 전체 자산 원화 환산 기준 값을 유지한다. 비율 `0.1077`은 `10.77%`이고 서버가 100을 곱하지 않는다. 소수 수량도 보존한다.

미국 주식이 없으면 요약의 usd는 null일 수 있다. 비용 cost.tax도 null일 수 있으며, 이를 임의로 0으로 바꾸지 않는다. 미보유 시 items는 빈 배열이다. 자세한 전체 스키마와 각 중첩 필드는 Swagger에 문서화했다.

## 호출 구조

```text
BrokerHoldingsController
  → BrokerHoldingsService: 경로 ID·symbol 형식 검사
  → TossAccountQueryService: 계좌 컨텍스트 + 조회 전용 인증 재시도
      → BrokerAccountAccessService: 회원·소유 관계·연결 상태·저장 인증정보 조회
      → TossTokenService: 암호화 Redis 캐시에서 토스 토큰 준비
      → TossHoldingsClient: GET /api/v1/holdings
          Authorization: Bearer {토스 토큰}
          X-Tossinvest-Account: {복호화한 accountSeq}
```

소유 관계는 `findByConnectionIdAndUserMemberId`로 SQL 조회부터 제한한다. 타 회원 계좌와 존재하지 않는 계좌는 모두 404이며, 인증정보 조회나 외부 API 호출까지 진행하지 않는다. 현재 토스증권의 CONNECTED 계좌만 지원한다.

BrokerCredentialData는 기존 암호화 JSON의 clientId·clientSecret·accountSeq 구조를 유지한 공통 내부 DTO다. 기존 연결의 암호문과 호환되며 마이그레이션은 필요 없다. 데이터 조회·복호화의 짧은 읽기 전용 트랜잭션이 끝난 뒤 토스 HTTP 호출을 수행한다.

토스 보유 주식 요청에서 401이 발생하면 거절된 캐시를 삭제하고 토큰을 새로 준비해 한 번만 다시 조회한다. 재시도도 401이면 캐시를 다시 삭제하고 422를 반환한다. 토큰 발급 자체에서 invalid_client가 발생하면 같은 인증정보로 반복 발급하지 않는다. 403·429·통신 오류는 자동 재시도하지 않는다. 기존 Client별 발급 잠금 정책은 유지한다.

TossAccountQueryService는 읽기 요청 전용이다. 주문 생성에는 이 자동 재시도 방식을 그대로 사용하면 안 된다.

## 오류와 보안

| 상태 | 의미 |
|---|---|
| 400 | 잘못된 연결 ID·종목 코드 |
| 401 | 서비스 JWT 오류 또는 탈퇴한 회원 |
| 404 | 없는 연결·타 회원 연결 또는 토스에서 종목을 찾지 못함 |
| 409 | CONNECTED가 아닌 연결·다른 증권사·저장된 인증정보 누락 |
| 422 | 토스 인증 실패가 지속되거나 허용 IP·접근 권한 확인 필요 |
| 502 | 토스 응답 envelope·필수 필드·숫자 형식 오류 |
| 503 | 토스·Redis 장애, 토큰 발급 중, 요청 한도, 인증정보 복호화 불가 |

토스 오류 본문은 그대로 노출하지 않는다. 응답에는 Client ID·Secret·토스 토큰·accountSeq·credentialRef를 포함하지 않고 Cache-Control: no-store를 적용한다. 인증 실패를 근거로 연결 상태를 DB에서 자동 변경하지는 않는다.

## 검증

```sh
python3 scripts/test-toss-integration.py
```

기존 스크립트가 임시 PostgreSQL·인증이 필요한 Redis와 로컬 가짜 토스 서버를 사용한다. 실제 .env와 개발 DB를 사용하지 않는다.

검증 범위는 소유 관계·연결 상태, 저장 인증정보 복호화, 토스 인증·계좌 헤더, 국내/미국 혼합 응답, 소수 수량과 문자열 직렬화, 빈 목록·nullable 금액, 종목 필터, 필수 필드 누락, 토큰 재발급과 재시도 한도, IP 제한·요청 한도·타임아웃, Swagger 스키마와 Position 미저장이다. 실제 토스 계좌를 호출하거나 주문하지 않는다.

## 공식 규격

- https://developers.tossinvest.com/docs/asset
- https://openapi.tossinvest.com/openapi-docs/latest/openapi.json

테스트 fixture는 공식 OpenAPI의 보유 주식 응답 예시를 사용한다.
