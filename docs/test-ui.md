# 가입·계좌 연결 테스트 화면

백엔드와 같은 주소에서 제공하는 HTML/JavaScript 화면이다. 별도 프론트 서버나 빌드가 필요 없다. Android에서 사용하는 ID 토큰 전달 API를 그대로 호출한다.

## 실행

1. PostgreSQL·Redis와 기존 로그인 환경변수를 준비하고 백엔드를 재시작한다.
2. 로컬 .env에는 TEST_UI_ENABLED=true를 추가했다. http://localhost:8080/test-ui/index.html 로 접속한다.
3. Google Cloud Console에서 기존 **웹 애플리케이션 유형** OAuth 클라이언트를 선택한다. GOOGLE_WEB_CLIENT_ID와 같은 클라이언트다.
4. **승인된 JavaScript 원본**에 http://localhost 와 http://localhost:8080 을 등록한다. 서버 포트를 바꿨으면 해당 포트도 등록한다. Android 클라이언트의 SHA-1 설정과는 별개다. 이 화면의 팝업 콜백 방식에는 리디렉션 URI가 필요하지 않다.
5. 구글 로그인 버튼을 누른다. 최초 사용자는 자동 가입된다. OAuth 동의 화면이 테스트 모드이면 로그인할 계정을 테스트 사용자로 등록해야 할 수 있다.
6. 개인 토스 Open API Client ID/Secret 입력 → **계좌 조회** → 계좌 선택 → **선택한 계좌 연결** 순서로 진행한다.

토스 허용 IP에는 백엔드 서버의 공인 출발지 IP를 등록한다. 외부 PC에서 접속하려면 백엔드의 접속 가능한 주소를 사용하고 그 원본을 Google에 등록해야 한다. localhost 이외의 브라우저 로그인은 HTTPS를 사용한다.

## 동작

- Google ID 토큰을 기존 /api/v1/auth/oauth2로 전달하고, 서비스 토큰으로 /api/v1/users/me를 조회한다.
- Secret 입력란은 계좌 조회 요청 후 비운다. 인증정보를 브라우저 저장소·URL에 기록하지 않는다.
- 토큰과 검증 티켓은 메모리에만 보관한다. 새로고침·탭 종료 시 초기화된다. 새로고침은 서버 Refresh Token을 폐기하지 않으므로 명시적 로그아웃은 버튼을 사용한다.
- 연결 재시도에는 같은 Idempotency-Key와 본문을 재사용한다.
- 로그인 만료 시 다시 구글 로그인한다. 토큰 자동 재발급과 자동매수는 추가하지 않았다.
- 회원·연결 결과 영역과 별도로 **Swagger용 토큰 확인·복사** 영역에서 Google ID Token과 서비스 Access/Refresh Token을 확인할 수 있다. Google ID Token은 로그인 API의 idToken 필드에, Access Token은 Swagger Authorize에 사용한다. 로그인 요청 JSON 전체 복사도 지원한다.
- Google ID Token은 서버 로그인 실패 시에도 확인할 수 있다. 토큰은 브라우저 저장소·URL·로그에 기록하지 않으며 새로고침·로그아웃 시 지운다. 토스 Secret은 결과 영역에 출력하지 않는다.
- 테스트 화면은 app.test-ui.enabled 기본값 false이고, 로컬 .env에서만 활성화했다. 비활성화하면 로그인한 사용자에게도 /test-ui/** 접근을 거절하고 설정 컨트롤러를 등록하지 않는다.
- /test-ui/config는 공개 식별자인 Google Web Client ID만 반환한다.

## Google 로그인 오류

원본 관련 오류는 브라우저 주소의 프로토콜·호스트·포트와 승인된 JavaScript 원본이 같은지 확인한다. localhost와 127.0.0.1은 다른 원본이다. 여기서는 http://localhost:8080 을 사용한다.

공식 설정 문서: https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid

## 로그인 500과 Redis 인증

로그인 성공 시 Refresh Token을 Redis에 저장하므로, Redis가 비밀번호를 요구하면 .env의 REDIS_PASSWORD도 맞춰야 한다. 설정 변경 후 서버를 재시작한다. `/actuator/health`가 DOWN이면 DB·Redis 연결을 확인한다. 토큰이나 비밀번호를 오류 로그에 붙여 넣지 않는다.

테스트 화면에는 Google 팝업 통신을 위해 Cross-Origin-Opener-Policy: same-origin-allow-popups 헤더를 적용한다. 이 헤더는 /test-ui/ 경로에만 적용한다.
