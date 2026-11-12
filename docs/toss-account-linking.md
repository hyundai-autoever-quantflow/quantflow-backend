# Android 토스증권 실계좌 연결

사용자 개인의 토스 Open API Client ID/Secret을 검증하는 방식이다. Google 로그인이나 토스 로그인 리디렉션과는 별개다. 사용자 키를 위탁받는 다중 사용자 서비스 운영 가능 여부는 토스와 확인해야 한다. 이 구현은 API 키의 계좌 접근 권한을 확인하며, 서비스 회원과 계좌 명의자의 동일성을 증명하지 않는다.

## 로컬 설정

기존 DB·Redis·JWT·Google 설정 외에 `.env`에 다음 키가 필요하다.

```properties
BROKER_ENCRYPTION_KEY_BASE64=<32바이트 난수를 Base64로 인코딩한 값>
```

키 생성 예: `openssl rand -base64 32`. 실제 키를 Git·로그·Swagger에 올리지 않는다. 현재 로컬 `.env`에는 별도의 난수 키를 추가했다. JWT 서명 키와는 별개이며 인증정보 복호화와 계좌 HMAC 식별에 사용하므로 안정적으로 백업해야 한다. 키를 단순 교체하면 기존 인증정보 복호화와 계좌 중복 식별이 깨진다. 운영 키 회전에는 데이터 재암호화·식별값 재생성 절차가 필요하다.

키를 설정하지 않아도 기존 로그인 API는 기동하며, 증권사 연결은 503으로 거절한다. 잘못된 형식의 키는 설정 오류로 기동을 중단한다.

`app.broker.toss`에는 서버 URL과 제한시간만 둔다. `.env`의 기존 `TOSS_CLIENT_ID`/`TOSS_CLIENT_SECRET`은 이 연결 흐름에서 사용하지 않는다. 사용자별 키는 API 요청으로 받아 서버에서 암호화한다. 토스 허용 IP에는 Android 단말 IP가 아닌 백엔드 출발지 공인 IP를 등록한다. 운영 요청은 HTTPS로 전달해야 하며 HTTP 본문 로깅/APM 수집 대상에서 인증정보와 티켓을 제외해야 한다.

앱 재시작 시 Flyway V5가 `broker_credentials`, 티켓 중복 방지 제약과 활성 계좌 유니크 인덱스를 적용한다. 기존 활성 중복 계좌가 있다면 먼저 정리해야 하며, 마이그레이션이 임의 삭제하지 않는다. 기존 V1~V4 파일은 변경하지 않는다.

## Swagger에서 테스트

1. Google 로그인 후 서비스 Access Token을 Swagger `Authorize`에 입력한다.
2. `POST /api/v1/broker-connections/toss/verify`에 사용자 개인의 토스 API 키를 전달한다.

```json
{
  "clientId": "사용자 토스 Client ID",
  "clientSecret": "사용자 토스 Client Secret"
}
```

응답에는 `verificationTicket`, `expiresIn: 300`, `accounts`가 있다. 계좌번호는 마스킹하며, `accountSeq`는 Android에서 정밀도 문제가 없도록 문자열로 전달한다. 토스가 BROKERAGE 이외의 새 계좌 유형을 반환하면 연결 목록에서 제외한다. 빈 목록은 422다.

3. 5분 이내에 `POST /api/v1/broker-connections/toss`를 호출한다. `Idempotency-Key` 헤더에 UUID를 입력한다.

```json
{
  "verificationTicket": "직전 응답의 티켓",
  "accountSeq": "선택한 계좌의 accountSeq"
}
```

응답은 201이며 연결 ID, 증권사, 마스킹 계좌번호, CONNECTED 상태, 마지막 검증 시각을 포함한다. 자동매수는 활성화되지 않으며 주문 API도 호출하지 않는다.

4. `GET /api/v1/broker-connections`에서 현재 회원의 연결 목록을 확인한다.

## 저장 및 동시성

- Redis 검증 티켓: 회원 ID, 개인 키, 계좌 목록을 AES-256-GCM으로 암호화해 5분간 보관한다. Redis 키에는 티켓의 HMAC만 남는다. 다른 회원은 사용할 수 없다.
- 최종 저장 직전 토스 계좌 목록을 다시 조회해 선택한 계좌 접근을 재확인한다. 외부 호출은 SQL 트랜잭션 밖에서 처리한다.
- `broker_credentials`: 개인 키와 후속 계좌 호출용 accountSeq의 암호문을 영속 저장한다. `BrokerConnection.credentialRef`는 UUID 참조다. DB 데이터와 암호화 키의 접근 권한은 분리해야 한다.
- 토스 액세스 토큰: Client ID/Secret 조합별 암호화 Redis 캐시. 만료 30초 전부터 재사용하지 않는다. Client ID별 Redis 잠금으로 중복 발급을 막는다. 발급 처리 중인 요청에는 재시도 가능한 503을 반환한다.
- 토스 토큰은 Client당 하나이므로 다른 프로그램에서 같은 키로 재발급하면 기존 캐시가 무효화될 수 있다. 계좌 조회에서 인증 실패를 감지하면 캐시를 삭제하며 사용자가 다시 검증하도록 422를 반환한다. Refresh Token은 없다.
- `(user_id, request_key)`: 같은 키/본문의 재시도는 기존 연결을 반환한다. 티켓이 만료된 뒤에도 동일 요청 재시도는 가능하다. 다른 본문에 같은 키를 재사용하면 409다.
- 티켓 해시 유니크 제약: 하나의 티켓으로 두 연결 생성 불가. 티켓은 TTL까지 Redis에 남지만 DB 제약으로 재사용을 차단한다.
- `(broker, account_fingerprint)` 활성 계좌 부분 유니크 인덱스: 동시에 또는 다른 회원이 같은 계좌를 연결해도 한 건만 저장한다. 실패한 SQL 트랜잭션에서는 인증정보 삽입도 롤백된다.
- permissions는 계좌 목록 조회로 확인한 `accountRead: true`만 저장한다. 주문 권한이나 잔고 동기화 성공은 추정하지 않으며 reconciliation 상태는 PENDING이다.

현재 범위는 검증·연결·목록 조회다. 연결 해제, 인증정보 교체, 잔고 동기화, 주문 실행은 별도 기능이다. 기존 연결 데이터는 자동으로 새로운 인증정보 저장소로 변환하지 않는다.

## 오류

| 상태 | 의미 |
|---|---|
| 400 | 요청 오류, 만료·다른 회원의 티켓, 목록에 없는 계좌 |
| 401 | 서비스 JWT 오류 또는 회원 없음 |
| 409 | 중복 연결, 티켓 재사용, 요청 키 충돌 |
| 422 | 토스 키·허용 IP 문제, 연결 가능 계좌 없음, 계좌 접근 상실 |
| 502 | 토스 응답 형식 오류 |
| 503 | 토스·Redis 장애, 요청 한도, 토큰 발급 중, 암호화 키 미설정 |

외부 API 오류 본문과 원본 예외를 사용자 응답에 포함하지 않는다. 응답에는 `Cache-Control: no-store`를 설정한다.

## 자동 테스트

Docker를 실행하고 프로젝트 루트에서 다음을 실행한다.

```sh
python3 scripts/test-toss-integration.py
```

테스트는 임시 PostgreSQL 16/Redis 7.4를 랜덤 포트로 띄우고, 로컬 가짜 토스 HTTP 서버를 사용한다. `.env`의 실제 키와 개발 DB는 사용하지 않는다. Flyway V1~V5, JPA validate, 인증, 타 회원 티켓 차단, 동시 요청, 계좌 중복, 암호화 저장, 롤백, 만료 후 멱등 재시도, 회원별 목록, Swagger 스키마를 확인하고 컨테이너를 제거한다. 실제 토스 키와 주문은 사용하지 않는다.

통합 테스트 클래스는 `RUN_TOSS_INTEGRATION_TESTS=true`일 때만 실행되며 위 스크립트가 이를 설정한다. 일반 `./gradlew test`는 기존 컨텍스트 테스트의 DB·Redis 설정이 필요하다.

## 공식 규격

- https://developers.tossinvest.com/docs/auth
- https://developers.tossinvest.com/docs/account
- https://openapi.tossinvest.com/openapi-docs/latest/openapi.json

공식 스키마의 토큰 응답은 OAuth 표준 최상위 필드이며, 계좌 응답은 `result` 배열이다. 계좌 목록 조회에는 선택 계좌 헤더가 필요하지 않으며, 응답 accountSeq는 후속 계좌 API의 `X-Tossinvest-Account`로 사용한다.
