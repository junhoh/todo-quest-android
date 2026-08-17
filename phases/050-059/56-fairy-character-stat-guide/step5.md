# Step 5: integrate-character-stat-guide-viewmodel

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterGuideUseCases.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step4.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

Character ViewModel의 가이드 상태를 테스트 먼저 추가한다.

- `CharacterUiState`에 `isStatAllocationGuideVisible: Boolean = false`를 추가한다.
- `CharacterViewModel` constructor에 테스트 가능한 함수 경계를 추가한다. 기존 call site가 step 6 전에도 컴파일되도록 안전한 기본값을 둔다.

```kotlin
prepareCharacterStatGuide: () -> Boolean = { false }
acknowledgeCharacterStatGuide: () -> Boolean = { true }
```

- command state 내부에는 자동/수동 origin을 구분하는 private 상태와 ViewModel 수명 동안 prepare를 한 번만 수행하는 flag를 둔다.
- `onScreenEntered()`는 기존 날짜·상태이상 처리를 유지하면서 최초 한 번 prepare를 호출한다. true이면 자동 origin을 준비한다.
- 자동 Dialog는 Character snapshot이 성공적으로 Loaded인 경우에만 `uiState`에 visible로 노출한다. Loading·Failed에서는 표시하지 않는다.
- `showStatAllocationGuide()`는 eligibility·acknowledged와 무관하게 manual origin으로 같은 Dialog를 연다.
- `dismissStatAllocationGuide()`는 automatic origin일 때만 acknowledge를 best-effort로 호출한 뒤 항상 현재 Dialog를 닫는다. manual origin은 preference를 쓰지 않는다.
- prepare 또는 acknowledge 예외는 취소 예외를 삼키지 않는 기존 coroutine 규칙을 존중하면서 Character 로드·draft·저장·초기화 상태와 분리한다. synchronous lambda 예외는 Dialog를 표시하지 않거나 닫는 방향으로 격리한다.
- `onLifecycleResumed()`는 자동 guide를 다시 prepare하지 않는다.

`CharacterViewModelTest`에 eligible 첫 진입 1회, ineligible, loading 후 표시, load failure 미표시, dismiss acknowledge, 재진입·resume 미반복, manual reopen, manual dismiss 무기록, prepare/acknowledge 실패 격리 테스트를 추가한다. 기존 능력치 draft·저장·초기화 테스트를 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterGuideUseCasesTest" --console=plain
git diff --check
```

## 검증 절차

1. ViewModel 테스트를 먼저 작성해 새 state·method 부재 실패를 확인한다.
2. AC를 실행한다.
3. 기존 stat allocation state와 guide state가 서로 덮어쓰지 않는지 확인한다.
4. phase index step 5를 완료 처리하고 자동/수동 origin 및 오류 격리를 한국어로 요약한다.

## 금지사항

- 표시용 한국어 문장을 ViewModel에 넣지 마라. 이유: strings.xml과 Compose가 사용자 문구를 소유한다.
- ViewModel에서 Context나 SharedPreferences를 사용하지 마라. 이유: UseCase 함수 경계를 지켜야 한다.
- lifecycle resume마다 자동 가이드를 다시 열지 마라. 이유: 최초 진입 1회 계약을 위반한다.
- 가이드 실패로 stat command를 비활성화하지 마라. 이유: 핵심 성장 기능과 독립적이어야 한다.
- 기존 테스트를 깨뜨리지 마라.

