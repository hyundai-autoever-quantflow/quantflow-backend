# 토스 매수 가능 금액·종목 현재가 조회

향후 주문 가능 여부 확인과 매수 수량 계산에 사용할 조회 API다. 현재 기능은 토스 조회 결과를 반환하며, 주문 수량 계산·예산 예약·환전·주문 실행은 수행하지 않는다. DB 스키마 변경은 없다.

## Swagger 테스트

1. 백엔드를 재시작한다.
2. Swagger에서 서비스 Access Token으로 인증한다.
3. `GET /api/v1/broker-connections`에서 본인의 CONNECTED 토스 연결 ID를 확인한다.
4. **매수 준비 조회** 태그에서 아래 API를 실행한다. connectionId는 QUANTFLOW 연결 ID이며 토스 accountSeq가 아니다.

### 매수 가능 금액

```http
GET /api/v1/broker-connections/1/buying-power?currency=KRW
Authorization: Bearer {서비스 Access Token}
```

currency는 필수이며 KRW 또는 USD다. 소문자는 대문자로 정규화한다. 통화별로 한 번씩 호출하며 서버가 두 통화를 합산하거나 환산하지 않는다.

응답 예시:

```json
{
  "connectionId": "1",
  "fetchedAt": "2026-11-13T01:00:00Z",
  "buyingPower": {
    "currency": "KRW",
    "cashBuyingPower": "5000000"
  }
}
```

cashBuyingPower는 미수거래를 제외한 현금 기반 매수 가능 금액이며 0도 정상이다. KRW는 원 단위, USD는 달러 단위다. Java에서는 BigDecimal로 처리하고 JSON에는 문자열로 반환한다. 조회 결과는 금액을 예약하지 않으므로 실제 주문 시점의 가용 금액과 같다고 가정하면 안 된다.

### 종목 현재가

```http
GET /api/v1/broker-connections/1/prices?symbols=005930,AAPL
Authorization: Bearer {서비스 Access Token}
```

symbols는 필수이며 쉼표로 구분해 최대 200개 항목을 받는다. 각 항목은 영문·숫자·점·하이픈으로 1~30자다. 앞뒤 공백 제거와 대문자 변환 후 중복을 제거한다. 200개 제한은 중복 제거 전 요청 항목 수 기준이다. 빈 항목이나 `AAPL,`처럼 마지막 쉼표가 있는 입력은 400이다.

응답 예시:

```json
{
  "connectionId": "1",
  "fetchedAt": "2026-11-13T01:00:00Z",
  "prices": [
    {
      "symbol": "005930",
      "timestamp": "2026-11-13T00:59:59Z",
      "lastPrice": "72000",
      "currency": "KRW"
    },
    {
      "symbol": "AAPL",
      "timestamp": null,
      "lastPrice": "185.70",
      "currency": "USD"
    }
  ]
}
```

lastPrice는 종목의 거래 통화 기준 소수 문자열이다. timestamp는 토스가 제공한 가격 데이터 시각이며 UTC로 정규화해 반환한다. 시각이 없으면 null이다. fetchedAt은 서버가 응답을 받은 시각으로, 가격 데이터 시각과 다르다. 시세 조회는 실시간 체결 가능 가격을 보장하지 않는다.

현재가 자체는 회원별·계좌별 데이터가 아니다. 이 프로젝트에서는 저장된 개인 토스 인증정보를 선택하기 위해 본인 연결 ID를 받는다. 실제 토스 현재가 요청에는 계좌 헤더를 보내지 않는다.

목록과 순서는 토스가 반환한 값을 유지한다. 반환되지 않은 종목을 0원으로 채우지 않으며 빈 목록도 그대로 반환한다. 요청하지 않은 종목이 반환되면 502로 처리한다. 매수 수량 계산을 추가할 때는 필요한 종목의 가격 존재 여부·통화·데이터 시각을 별도로 확인해야 한다.

## 구현 흐름

```text
BrokerTradingQueryController
  → BrokerTradingQueryService: 연결 ID·통화·종목 목록 검증
  → TossAccountQueryService: 기존 계좌 검증·복호화·토큰 캐시·조회 재시도 재사용
  → TossTradingQueryClient
      매수 가능 금액: GET /api/v1/buying-power?currency=...
          Authorization + X-Tossinvest-Account
      현재가: GET /api/v1/prices?symbols=...
          Authorization만 전달
```

두 경로 모두 본인 소유의 CONNECTED 토스 연결이어야 한다. 없는 연결과 타 회원 연결은 동일하게 404로 처리하며, 인증정보 복호화나 토스 호출 전에 차단한다. 기존의 짧은 읽기 전용 DB 트랜잭션이 끝난 후 외부 HTTP 호출을 실행한다.

토스 조회가 401이면 캐시 무효화·토큰 갱신 후 한 번만 재조회한다. 계속 401이면 거절된 캐시를 삭제하고 422를 반환한다. 토큰 발급 자체의 잘못된 Client ID/Secret은 반복 시도하지 않는다. 403이나 429·통신 오류는 자동 재시도하지 않는다.

응답 DTO는 필수 필드·숫자·일시를 검증한다. 매수 가능 금액의 반환 통화가 요청 통화와 다르면 502다. 외부 오류 본문이나 인증정보를 사용자 응답에 포함하지 않으며 Cache-Control: no-store를 설정한다.

## 오류

| 상태 | 의미 |
|---|---|
| 400 | 입력 형식 오류·필수 파라미터 누락·토스 조회 조건 거절 |
| 401 | 서비스 인증 실패 또는 회원 없음 |
| 404 | 없는 연결·타 회원 연결 또는 토스 조회 대상 없음 |
| 409 | 연결 미완료·다른 증권사·인증정보 누락 |
| 422 | 토스 인증정보·허용 IP·접근 권한 확인 필요 |
| 502 | 토스 응답 형식·필수 필드·통화·종목 불일치 |
| 503 | 토스·Redis 장애, 요청 한도, 토큰 발급 중 또는 복호화 불가 |

## 테스트

```sh
python3 scripts/test-toss-integration.py
```

임시 PostgreSQL·인증이 필요한 Redis와 가짜 토스 HTTP 서버로 전체 테스트를 실행한다. 개발 DB나 .env의 실제 키를 사용하지 않는다.

정상·0원·달러 소수 정밀도, 다건 시세·nullable timestamp, 200개 제한, 통화·종목 정규화, 회원 소유 관계·연결 상태, 계좌 헤더 유무, 토큰 재발급·재시도 한도, 응답 형식과 오류 처리, Swagger 경로, 주문·Position 미저장을 확인한다. 실제 토스 계좌로의 조회는 별도 확인이 필요하다.

공식 규격과 fixture 출처:

- https://developers.tossinvest.com/docs/order-info
- https://developers.tossinvest.com/docs/market-data
- https://openapi.tossinvest.com/openapi-docs/latest/openapi.json
