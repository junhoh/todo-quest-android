# Step 4: persist-character-stat-guide-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterGuide.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/notification/SharedPreferencesFirstLaunchNotificationPromptStore.kt`
- `/app/src/test/java/com/todoquest/domain/FirstLaunchNotificationPermissionUseCaseTest.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step3.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

테스트를 먼저 작성하고 `/app/src/main/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepository.kt`를 구현한다.

- public constructor는 application `Context`와 `eligibleOnFirstInitialization: Boolean`을 받는다. 테스트용 internal constructor는 `SharedPreferences`와 같은 boolean을 받는다.
- preference 이름은 `todo_quest_character_guides`다.
- versioned key는 `stat_allocation_auto_eligible_v1`, `stat_allocation_acknowledged_v1`로 분리한다.
- process-wide lock 안에서 eligibility key가 없을 때만 전달받은 boolean을 synchronous `commit()`으로 저장한다. 이미 key가 있으면 DB 상태나 새 인수로 재분류하지 않는다.
- eligibility 최초 저장이 실패하면 자동 표시를 fail-closed(false)로 반환한다. Character 기능이나 앱 시작을 예외로 중단하지 않는다.
- `acknowledgeStatAllocationGuide()`는 acknowledged=true를 synchronous commit하고 이미 true인 경우 멱등 성공한다.
- status read는 eligibility와 acknowledged를 독립적으로 반환한다. acknowledged는 수동 도움말 열기를 금지하는 값이 아니다.

`/app/src/test/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepositoryTest.kt`에서 신규 eligible, 기존 설치 ineligible, repository 재생성 후 eligibility 유지, 최초 확인, 중복 확인, 앱 데이터에 해당하는 빈 preference 초기화를 검증한다. Room schema와 database는 수정하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.SharedPreferencesCharacterGuideRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterGuideUseCasesTest" --console=plain
git diff --check
```

## 검증 절차

1. repository 테스트를 먼저 작성해 구현 부재 실패를 확인한다.
2. AC를 실행한다.
3. eligibility가 한 번 저장된 뒤 재분류되지 않는지 확인한다.
4. phase index step 4를 완료 처리하고 preference key·멱등 계약을 한국어로 요약한다.

## 금지사항

- UI나 ViewModel에서 SharedPreferences를 직접 읽지 마라. 이유: Repository 경계를 지켜야 한다.
- Room table이나 migration을 추가하지 마라. 이유: 가이드 상태는 앱 preference다.
- 기존 설치를 자동 eligible로 만들지 마라. 이유: 사용자가 신규 설치만 자동 안내하도록 선택했다.
- preference 실패로 Character 기능을 실패 처리하지 마라. 이유: 가이드는 비핵심 보조 기능이다.
- 기존 테스트를 깨뜨리지 마라.

