# Android 소셜 인증

Android의 Google SDK/Credential Manager에서 받은 ID 토큰을 백엔드 API로 전달합니다.
백엔드는 Google 서명·발급자·대상 클라이언트·만료를 검증한 뒤 회원을 조회하거나 생성합니다.
일반 회원가입, 서버 OAuth 리디렉션, Google Client Secret은 사용하지 않습니다.

## 실행 설정

IntelliJ 실행 구성 또는 프로세스 환경변수에 설정합니다. `.env` 파일만 만드는 것으로 Spring Boot가 자동으로 읽지는 않습니다.

- `GOOGLE_WEB_CLIENT_ID`: Android의 serverClientId와 같은 웹 유형 Google 클라이언트 ID
- `JWT_SECRET_BASE64`: 최소 32바이트 난수를 Base64로 인코딩한 JWT 서명키
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`: PostgreSQL 연결 정보
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`: Redis 연결 정보

서명키 생성 예시: `openssl rand -base64 32`
Google ID 토큰 검증용 클라이언트 ID와 서비스 JWT 서명키는 서로 다른 값입니다.
서명키는 Android 앱에 넣지 않습니다.

## API

| 메서드 | 경로 | 요청 |
|---|---|---|
| POST | `/api/v1/auth/oauth2` | `{"provider":"GOOGLE","idToken":"..."}` |
| POST | `/api/v1/auth/reissue` | `{"refreshToken":"..."}` |
| POST | `/api/v1/auth/logout` | `{"refreshToken":"..."}` |
| GET | `/api/v1/users/me` | `Authorization: Bearer {accessToken}` |

로그인과 재발급은 `{"accessToken":"...","refreshToken":"..."}`을 반환합니다.
로그아웃은 204를 반환합니다. 앱에서도 보관한 토큰을 제거해야 합니다.
미구현 소셜 제공자·잘못된 요청은 400, 유효하지 않은 토큰은 401입니다.
Google 공개키 확인 등 인증 인프라 실패는 503입니다.

## 토큰 정책

- 액세스 토큰: 기본 15분, 일반 API 호출에 사용
- 리프레시 토큰: 기본 14일, 재발급 시 두 토큰을 모두 새 값으로 교체
- Redis: 회원당 리프레시 토큰 해시 한 개를 TTL과 함께 보관
- 새 로그인은 이전 리프레시 토큰을 대체
- 동일 토큰의 동시 재발급은 Redis Lua로 한 요청만 성공
- 이전 토큰의 재발급 실패나 로그아웃은 최신 로그인 정보를 삭제하지 않음
- 로그아웃은 리프레시 토큰을 폐기하며, 기존 액세스 토큰의 즉시 폐기는 구현하지 않음

Android는 동시 재발급 요청을 하나로 묶고 새 토큰 쌍을 함께 교체해야 합니다.
리프레시 토큰이나 Google ID 토큰을 일반 API의 Bearer 토큰으로 사용하지 않습니다.

## 회원과 마이그레이션

`V4__configure_oauth2_users.sql`이 소셜 식별자 복합 UNIQUE를 추가합니다.
이메일·이름은 중복을 허용하고, 이메일은 선택값으로 저장합니다.
같은 이메일의 다른 소셜 계정을 자동으로 연결하지 않습니다.
관심 카테고리는 미선택 시 빈 배열입니다.

기존 소셜 식별자가 NULL이거나 중복인 회원은 V4 적용 전에 검토해야 합니다.
V1~V3은 수정하지 않습니다.

## 검증

`./gradlew test`에는 외부 PostgreSQL과 Redis, 위 인증 환경변수가 필요합니다.
기본 컨텍스트 테스트가 Flyway를 실행하므로 별도의 테스트용 DB를 사용합니다.
토큰 종류 분리와 재발급 실패 시 최신 세션 보존을 검사하는 테스트도 포함합니다.

적용 시 격리 PostgreSQL 16·Redis 7.4에서 전체 테스트와 실제 HTTP 로그인·재발급·로그아웃을 확인했습니다.
Google 검증은 로컬 테스트 서명키/JWKS를 사용했으며, 실제 Google 계정 및 Android 실기기 연동은 별도 확인이 필요합니다.
