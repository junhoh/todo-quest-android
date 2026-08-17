# Step 6: build-battle-sfx-settings-screen

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/repository/BattleSfxSettingsRepository.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/phases/060-069/61-battle-sound-effects/step2.md`
- `/phases/060-069/61-battle-sound-effects/step5.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

`feature/settings` 모듈 범위에서 테스트를 먼저 작성하고 설정 UI state, ViewModel과 Compose screen을 만든다.

```kotlin
data class SettingsUiState(
    val battleSfxEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

class SettingsViewModel(
    repository: BattleSfxSettingsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel {
    fun setBattleSfxEnabled(enabled: Boolean)
    fun clearSaveError()
}
```

ViewModel은 Repository의 StateFlow를 source로 렌더링하고 switch 요청을 직렬화한다. 저장 성공 뒤 바로 flow 값을 표시하고 실패하면 마지막 persisted value로 복귀하며 raw exception text 대신 typed `saveFailed`를 노출한다. 빠른 중복 tap이 저장 순서를 역행하지 않게 단일 command job/mutex를 사용한다.

`SettingsScreen`은 Material 3 fixed top app bar 제목 `설정`과 scroll 가능한 content에 `효과음` label, 현재 `켜짐`/`꺼짐` 상태 설명, Switch를 하나 제공한다. row와 switch는 최소 48dp touch target이고 row click도 같은 command로 연결하되 한 gesture가 두 번 toggle되지 않게 한다. TalkBack에는 label과 on/off 상태를 한국어로 제공한다. 저장 실패는 한국어 `효과음 설정을 저장하지 못했습니다.` snackbar 또는 동등한 일회성 표시로 제공한다. 모든 사용자 문구는 `strings.xml`에 둔다.

screen은 audio player, SoundPool 또는 combat repository를 직접 호출하지 않는다. off에서도 animation·damage·HP·status UI를 변경하지 않는다는 설명은 필요한 경우 보조 text로만 제공한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.settings.SettingsViewModelTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.settings.SettingsScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. 기본 on, off/on command, persistence failure rollback, rapid toggle tests를 먼저 작성한다.
2. Compose test에서 한국어 title/label/state, minimum target, semantics, switch 동작과 320dp/font scale 2.0 도달성을 검증한다.
3. AC 명령을 실행한다.
4. task index의 step 6을 `completed`로 바꾸고 settings UI/ViewModel 파일과 UX를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- SettingsScreen에서 SharedPreferences나 SoundPool을 직접 호출하지 마라. 이유: UI는 ViewModel state와 command만 사용해야 한다.
- 영문 표시 문구를 하드코딩하지 마라. 이유: AGENTS.md의 한국어 resource 규칙을 위반한다.
- switch off로 battle animation/state를 숨기지 마라. 이유: 설정은 audio만 제어한다.
- 기존 테스트를 깨뜨리지 마라.
