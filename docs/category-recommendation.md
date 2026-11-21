# 관심 카테고리별 추천

앱은 목록에서 선택한 카테고리 코드를 저장하거나 추천 요청에 전달한다.

| 요청 | 동작 |
|---|---|
| `GET /api/v1/categories` | 카테고리 코드, 이름, 계층과 상위 코드를 반환한다. |
| `GET /api/v1/users/me/preferred-categories` | 로그인한 사용자의 선택 목록과 버전을 반환한다. |
| `PUT /api/v1/users/me/preferred-categories` | `preferredCategories` 배열과 조회한 `preferencesVersion`으로 선택을 교체한다. 빈 배열은 선택 해제이며, 이전 버전으로 변경하면 `409`가 발생한다. |
| `POST /api/v1/analyses/recommendation/category?categoryCode=100` | 해당 코드의 뉴스와 거래량·순매수 순위를 이용해 후보 중 한 종목을 추천한다. `categoryCode`를 생략하면 저장된 오늘의 공통 추천을 반환한다. |

카테고리의 코드와 이름은 토스의 섹터 목록에서 한국 시간 기준 하루에 한 번 동기화한다. 추천을 요청한 카테고리의 구성 종목도 하루 한 번 조회하고, 저장된 코스피·코스닥 보통주와 대조해 `stocks.category_codes`에 반영한다. 이 조회에는 기존 `STOCK_SYNC_TOSS_CLIENT_ID`, `STOCK_SYNC_TOSS_CLIENT_SECRET` 설정을 사용한다. [토스증권 섹터 API](https://openapi.tossinvest.com/openapi-docs/latest/openapi.json)

추천 후보와 관련 뉴스가 부족하면 종목 없이 보류 사유를 반환한다. 자동매수에는 저장된 오늘의 공통 추천이 사용된다.
