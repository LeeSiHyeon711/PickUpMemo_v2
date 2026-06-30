# PickUpMemo (v3)

배달 라이더가 **배민커넥트에서 신규 배차를 받을 때**, 미리 저장해 둔 **가게 메모**와 **픽업지→전달지 실제 경로 거리·예상 소요시간**을 화면 위 **단일 팝업**으로 자동 표시해 주는 Android 앱.
실제 사용자(배달) 문제에서 출발한 **개인용 기술검증** 프로젝트로, 접근성 서비스·오버레이를 이용해 *"화면 텍스트에서 상호/주소를 인식하고 → 저장된 메모 매칭 + 지도 API 경로 조회를 합쳐 → 하나의 팝업으로 띄우는 것이 가능한가"* 를 검증한다.

> **버전 흐름**: v2 = 화면 텍스트 인식 → 메모 매칭 → 팝업(완료). **v3 = 그 팝업 안에 실제 경로 거리·예상 시간 통합**(본 문서). v3.1 = 오르막 주의(골격만 선반영).
> 본 README는 현재 repo에 **실제 구현된 코드**만을 기준으로 작성되었다.

---

## 기술 스택 (실제 설정 기준)
- 언어: **Kotlin**, UI: **Android Views**(AppCompat / Material / ConstraintLayout / RecyclerView) — *Jetpack Compose 미사용*
- 로컬 DB: **Room 2.6.1** (KSP), 비동기: Kotlin Coroutines
- 네트워크: **OkHttp 4.12.0** + 안드로이드 내장 `org.json` (별도 JSON 라이브러리 미사용)
- 지도 API: **카카오 Local API**(주소/장소명→좌표) + **카카오모빌리티 길찾기 API**(경로 거리·소요시간)
- `applicationId` / `namespace`: `com.itmakesome.pickupmemo2`
- `minSdk = 26` (Android 8.0+), `targetSdk = 34`, `compileSdk = 34`
- 앱 표시명: **PickUpMemo**

## 권한
- **접근성 서비스** (`BIND_ACCESSIBILITY_SERVICE`) — OS 설정에서 사용자가 직접 활성화
- **다른 앱 위에 표시 / 오버레이** (`SYSTEM_ALERT_WINDOW`)
- **인터넷** (`INTERNET`) — v3 지도 API 호출용(일반 권한, 런타임 요청 불필요)
> 접근성·오버레이는 시스템 권한이라 앱이 직접 켤 수 없고, 활성 여부 판정 + 설정 화면 이동만 제공한다(`util/PermissionChecker`).

---

## 구현된 기능

### 1. 가게 메모 관리 (CRUD) — v2
- `data/Memo` 엔티티: `storeName`, `branchName`, `content`, `tag?`, `updatedAt` (테이블 `memos`)
- `MemoDao` / `MemoRepository`, 화면: `ui/MemoListActivity`·`ui/MemoEditActivity`

### 2. 배민커넥트 화면 인식 (접근성 서비스) — v2
- `service/PickupAccessibilityService` — 대상 패키지를 **배민커넥트(`com.woowahan.bros`) 단독**으로 한정
- `matcher/StoreExtractor` — 화면 텍스트에서 **"픽업지" ~ "전달지" 사이 상호 텍스트**를 추출(메모 매칭용). *v3에서 변경 없음(회귀 방지).*

### 3. 메모 매칭 & 중복 억제 — v2
- `matcher/MemoMatcher` — 추출 상호에 메모의 **`storeName`·`branchName`이 모두 포함**될 때 매칭
- `matcher/DedupGuard` — **30초 윈도우 내 재팝업 억제**. *v3에서 메모 없는 케이스용 `shouldShow(key: String)` 오버로드 추가(기존 메모 id 기준 유지).*

### 4. 단일 오버레이 팝업 (메모 + 경로 통합) — **v3 핵심**
- `overlay/MemoPopupController` — **하나의 오버레이 팝업** 안에 메모 영역 + **경로 영역(`약 X.Xkm · 약 N분`)**을 함께 표시(별도 팝업 컨트롤러를 만들지 않음).
- 팝업 표시 후 경로 정보는 **비동기로 도착하는 대로 같은 팝업의 경로 영역만 갱신**한다. 토큰 세대(`AtomicLong`) 검증으로 자동 닫힘/교체와의 경합을 방지.
- 조회 실패 시 같은 팝업에 **`거리 정보 확인 불가`** 표시.

### 5. 경로 거리·예상 시간 조회 — **v3 신규**
- `matcher/AddressExtractor` — 화면 텍스트에서 **픽업지/전달지 주소**를 추출(StoreExtractor와 별개 신규 모듈). 소스 A(분리 토큰열의 "픽업지/전달지" 다음 값) 우선, 실패 시 소스 B(신규배차_카드 `desc` 한 줄 콤마 분할) fallback.
- `route/RouteProvider`(인터페이스) + `route/KakaoRouteProvider`(구현체) — 제공자 교체 가능 구조. 픽업지=**키워드 검색**, 전달지=**주소 검색**으로 좌표화한 뒤 길찾기로 거리·시간 조회.
- `route/RouteService` — 캐싱 + **전체 5초 타임아웃(`withTimeoutOrNull`)** + 전달지 **4단계 geocode fallback**(원문 → `****`·괄호동 제거 → 도로명 앞부분 → 키워드). 실패 원인은 `RouteResolveOutcome`/`RouteFailure`로 구분(키없음·픽업/전달 좌표 실패·길찾기 실패·타임아웃).
- 모델: `route/GeoPoint`, `route/RouteInfo`(`distanceMeters`·`durationSeconds`·`summaryText`·`rawProvider`).

### 6. 배민 로그 보관함 (수집·조회·내보내기) — v2
- `data/BaeminLog`(테이블 `baemin_logs`), **최대 1000건 링버퍼**, 화면 `ui/BaeminLogActivity`, `util/BaeminLogExporter`(FileProvider 공유)

### 7. 권한 안내 허브 & 검증 화면
- `MainActivity` — 메인 허브, 유틸 `util/PermissionChecker`·`Packages`·`TimeFormat`
- `ui/TestActivity` — 팝업 검증 + **v3 경로 수동 테스트(모드 C)**: 픽업지/전달지를 직접 입력해 좌표 변환→경로 조회→팝업 표시 전 과정을 배민 앱 없이 재현.

### 8. (v3.1 선반영) 오르막 주의 — **골격만, 동작 없음**
- `uphill/UphillDetector`(인터페이스) + `NoopUphillDetector`(항상 `null`) + `uphill/HillRoadList`(빈 리스트) + 팝업 내 오르막 자리(기본 숨김). 실제 감지/매칭/표시/서비스 결선은 v3.1에서 구현 예정.

### 데이터베이스
- `data/AppDatabase` — `@Database(entities = [Memo, BaeminLog], version = 2)`

---

## 동작 흐름 (v3)
```
배민커넥트 신규 배차 화면
  → PickupAccessibilityService 가 화면 텍스트 수집 (baemin_logs 보관)
  → StoreExtractor → MemoMatcher : 저장된 메모 매칭(있을 수도, 없을 수도)
  → AddressExtractor : 픽업지/전달지 주소 추출
  → (메모 또는 주소가 있으면) 단일 팝업 표시  [트리거 승격]
  → RouteService 가 비동기로 경로 거리·시간 조회 → 같은 팝업의 경로 영역 갱신
       (실패 시 "거리 정보 확인 불가")
```

**표시 케이스**
| 메모 | 경로 조회 | 표시 |
|------|-----------|------|
| 있음 | 성공 | 메모 + `약 X.Xkm · 약 N분` |
| 있음 | 실패 | 메모 + `거리 정보 확인 불가` |
| 없음 | 성공 | `등록된 메모 없음` + `약 X.Xkm · 약 N분` |
| 없음 | 실패 | **debug 빌드**: `거리 정보 확인 불가` 표시 / **release 빌드**: 미표시(실배달 방해 방지) |

---

## 지도 API 키 설정
카카오 REST API 키는 **코드에 하드코딩하지 않으며**, `local.properties`(git 미추적)에서 읽어 `BuildConfig`로 주입한다.
```properties
# local.properties (커밋되지 않음)
KAKAO_REST_API_KEY=발급받은_REST_API_키
```
- 키가 없어도 **빌드는 성공**한다(빈 문자열 주입). 이 경우 경로 조회는 `거리 정보 확인 불가`로 처리되고 메모 팝업 등 나머지 기능은 정상 동작한다.
- 실제 거리/시간 표시는 위 키를 입력한 뒤 동작한다.

## 빌드 / 실행
```bash
# JDK 17~21 필요. 기본 JDK가 Java 25이면 Kotlin 플러그인과 호환되지 않으므로
# Android Studio 번들 JBR(21)을 지정해 빌드한다.
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug      # 디버그 APK
./gradlew :app:assembleRelease    # 릴리스 APK (미서명)
```
산출물: `app/build/outputs/apk/{debug,release}/`. 설치 후 **OS 설정에서 접근성 + 오버레이 권한을 수동 허용**해야 팝업이 동작한다(앱 내 안내 제공).

---

## 비고
- 대상 앱(배민커넥트) 인식은 **본인 단말에서의 개인용 기술검증** 목적이다.
- 화면 텍스트/로그는 **기기 로컬 Room DB(`baemin_logs`)**에만 저장된다. 별도 서버 전송 기능은 없으며, v3에서 추가된 외부 통신은 **지도 API 호출(거리/시간 조회)뿐**이다.
- v3는 v2의 3대 기능(로그 수집 / 메모 CRUD / 메모 팝업)을 **회귀 없이 유지**한 채 확장했다.
