# Step 6: wire-character-stat-guide-dependencies

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterGuideUseCases.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step5.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

application composition wiring을 테스트 먼저 추가한다.

- `TodoQuestAppContainer`에 주입 가능한 `CharacterGuideRepository`를 추가하고 기본값은 항상 automatic ineligible이며 acknowledge가 성공하는 비활성 구현으로 둔다.
- container가 `PrepareCharacterStatGuideUseCase`와 `AcknowledgeCharacterStatGuideUseCase`를 application-scoped로 제공한다.
- production `TodoQuestAppContainer.create(context)`에서 `Room.databaseBuilder`를 호출하기 전에 `context.getDatabasePath(DATABASE_NAME).exists()`를 캡처한다. DB가 없으면 `eligibleOnFirstInitialization=true`, 있으면 false로 실제 `SharedPreferencesCharacterGuideRepository`를 만든다.
- preference가 이미 eligibility를 저장했다면 repository가 이 초기 후보를 무시하므로 process 재시작에 의해 재분류되지 않는다.
- `TodoQuestApp` → `AppNavigation` → `characterViewModelFactory`로 두 UseCase의 `::invoke` 함수를 전달하고 step 5의 ViewModel constructor에 주입한다.
- 기존 named-argument test container와 notification wiring을 깨뜨리지 않는다.

`TodoQuestDatabaseIsolationTest` 또는 적절한 application test에 injected repository 보존, DB 파일 존재 전 판별 순서, 비활성 기본 구현을 검증하는 테스트를 추가한다. 필요하면 pure internal helper로 DB 존재 판별을 분리하되 PackageManager install time이나 Room query로 추측하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.SharedPreferencesCharacterGuideRepositoryTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. application wiring 테스트를 먼저 추가한다.
2. AC를 실행한다.
3. DB 생성 이전에 신규 여부를 캡처하는지 코드 순서를 확인한다.
4. phase index step 6을 완료 처리하고 신규 설치 판별·DI 경계를 한국어로 요약한다.

## 금지사항

- Room을 연 뒤 DB 존재 여부를 신규 설치 판별에 사용하지 마라. 이유: 새 설치도 이미 기존으로 오분류된다.
- PackageManager timestamp를 추가 판별 기준으로 사용하지 마라. 이유: 승인된 계약은 앱 데이터의 DB 존재 여부다.
- 기존 설치를 자동 eligible로 migration하지 마라. 이유: 사용자가 기존 사용자 자동 안내를 제외했다.
- UI에서 repository를 직접 주입받게 하지 마라. 이유: ViewModel·UseCase 경계를 유지한다.
- 기존 테스트를 깨뜨리지 마라.

