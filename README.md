# PickUpMemo (v2)

배달 라이더가 **배민커넥트에서 신규 배차를 받을 때**, 미리 저장해 둔 **가게 메모를 화면 위 팝업으로 자동 표시**해 주는 Android 앱.
실제 사용자(배달) 문제에서 출발한 **개인용 기술검증(V2)** 프로젝트로, 접근성 서비스·오버레이를 이용해 *"화면 텍스트에서 상호를 인식하고 → 저장된 메모와 매칭해 → 팝업으로 띄우는 것이 가능한가"* 를 검증한다.

> 본 README는 현재 repo에 **실제 구현된 코드**만을 기준으로 작성되었다.

---

## 기술 스택 (실제 설정 기준)
- 언어: **Kotlin**, UI: **Android Views**(AppCompat / Material / ConstraintLayout / RecyclerView) — *Jetpack Compose 미사용*
- 로컬 DB: **Room 2.6.1** (KSP), 비동기: Kotlin Coroutines
- `applicationId` / `namespace`: `com.itmakesome.pickupmemo2`
- `minSdk = 26` (Android 8.0+), `targetSdk = 34`, `compileSdk = 34`
- 앱 표시명: **PickUpMemo**

## 권한 (수동 허용 필요)
- **접근성 서비스** (`BIND_ACCESSIBILITY_SERVICE`) — 코드로 켤 수 없어 OS 설정에서 사용자가 직접 활성화
- **다른 앱 위에 표시 / 오버레이** (`SYSTEM_ALERT_WINDOW`)
> 두 권한은 시스템 권한이라 앱이 직접 켤 수 없고, 활성 여부 판정 + 설정 화면 이동만 제공한다(`util/PermissionChecker`).

---

## 구현된 기능

### 1. 가게 메모 관리 (CRUD)
- `data/Memo` 엔티티: `storeName`, `branchName`, `content`, `tag?`, `updatedAt` (테이블 `memos`)
- `MemoDao` / `MemoRepository`
- 화면: `ui/MemoListActivity`(+`MemoAdapter`) 목록·삭제, `ui/MemoEditActivity` 추가·수정

### 2. 배민커넥트 화면 인식 (접근성 서비스)
- `service/PickupAccessibilityService` — 대상 패키지를 **배민커넥트(`com.woowahan.bros`) 단독**으로 한정(노이즈·부하 감소)
- 감지 이벤트: `typeWindowStateChanged | typeWindowContentChanged | typeViewTextChanged`, `canRetrieveWindowContent=true`
- `matcher/StoreExtractor` — 화면 텍스트에서 **"픽업지" ~ "전달지" 사이 구간**의 상호 텍스트를 추출(`신규배차_카드`/픽업지·전달지 키 기반, id/desc 등 노이즈 제거)

### 3. 메모 매칭 & 중복 억제
- `matcher/MemoMatcher` — 추출된 상호 텍스트에 저장된 메모의 **`storeName`과 `branchName`이 모두 포함**될 때 매칭
- `matcher/DedupGuard` — 같은 메모는 **30초 윈도우 내 재팝업 억제**(메모 id 기준)

### 4. 오버레이 메모 팝업
- `overlay/MemoPopupController` — 매칭된 메모 내용을 **다른 앱 위 오버레이 팝업**으로 표시(`SYSTEM_ALERT_WINDOW`)

### 5. 배민 로그 보관함 (수집·조회·내보내기)
- `data/BaeminLog` 엔티티: `capturedAt`, `packageName`(항상 `com.woowahan.bros`), `eventType`, `text` (테이블 `baemin_logs`)
- **최대 1000건 링버퍼**(초과분 `BaeminLogRepository.save()`에서 자동 삭제)
- 화면: `ui/BaeminLogActivity`(+`BaeminLogAdapter`) 조회
- `util/BaeminLogExporter` — `cacheDir/exports/baemin_log_YYYYMMDD_HHmmss.txt`로 기록 후 **FileProvider URI 공유**(authority `${applicationId}.fileprovider`)

### 6. 권한 안내 허브 & 검증 화면
- `MainActivity` — 메인 허브(권한 상태 안내·각 화면 진입)
- `ui/TestActivity` — 팝업 동작 검증용 테스트 화면
- 유틸: `util/PermissionChecker`(접근성·오버레이 활성 판정 + 설정 이동), `util/Packages`, `util/TimeFormat`

### 데이터베이스
- `data/AppDatabase` — `@Database(entities = [Memo, BaeminLog], version = 2)`

---

## 동작 흐름
```
배민커넥트 신규 배차 화면
  → PickupAccessibilityService 가 화면 텍스트 수집
  → StoreExtractor 가 픽업지~전달지 구간에서 상호 추출
  → MemoMatcher 가 저장된 메모(상호+지점)와 매칭
  → DedupGuard 통과 시 (30초 내 중복 아님)
  → MemoPopupController 가 오버레이 팝업으로 메모 표시
  (수집된 화면 텍스트는 baemin_logs 에 최대 1000건 보관·조회·내보내기)
```

## 빌드 / 실행
```bash
./gradlew :app:assembleDebug      # 디버그 APK 빌드
```
설치 후 **OS 설정에서 접근성 서비스 + 오버레이 권한을 수동 허용**해야 팝업이 동작한다(앱 내 안내 제공).

---

## 비고
- 대상 앱(배민커넥트) 인식은 **본인 단말에서의 개인용 기술검증** 목적이다.
- 수집되는 화면 텍스트/로그는 **기기 로컬 Room DB(`baemin_logs`)**에만 저장되며, 별도 서버 전송 기능은 없다.
- `app/src/main/AndroidManifest.xml`의 Activity·Service·Provider 등록 및 `res/xml/accessibility_service_config.xml`이 위 구성의 근거다.
