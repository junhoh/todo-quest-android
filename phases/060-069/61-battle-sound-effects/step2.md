# Step 2: persist-battle-sfx-setting

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterGuideRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/SharedPreferencesCharacterGuideRepositoryTest.kt`
- `/phases/060-069/61-battle-sound-effects/step0.md`
- `/phases/060-069/61-battle-sound-effects/step1.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

테스트를 먼저 작성하고 Room과 독립된 application preference 경계를 추가한다.

```kotlin
interface BattleSfxSettingsRepository {
    val isEnabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean): Boolean
}
```

Android 구현은 `SharedPreferencesBattleSfxSettingsRepository`로 두고 application context의 전용 파일 `todo_quest_audio_settings`, key `battle_sfx_enabled_v1`을 사용한다. key가 없으면 `true`를 반환하되 단순 read 때문에 파일을 쓸 필요는 없다. `setEnabled`는 process lock 안에서 같은 값은 멱등 성공으로 처리하고 다른 값은 `commit()` 성공 후에만 `StateFlow`를 갱신한다. commit 실패 시 preference와 flow 모두 마지막 확정값을 유지한다. repository 재생성은 저장된 값을 즉시 initial state로 읽어 앱 재실행을 모델링한다.

공유 설정 값이 이후 전투부터 즉시 사용될 수 있도록 성공한 write와 `StateFlow.value` 갱신 사이에 비동기 지연을 만들지 않는다. UI, SoundPool, Activity lifecycle을 이 step에서 구현하지 않는다. 기존 SharedPreferences 파일이나 key를 재사용하지 않아 notification/character guide 상태와 결합하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.SharedPreferencesBattleSfxSettingsRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. 비어 있는 preference 기본 켜짐, on/off 전환, 같은 값 중복, repository 재생성, commit 실패 테스트를 먼저 작성한다.
2. AC 명령을 실행한다.
3. Room schema와 기존 preference 파일이 변경되지 않았는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 설정 interface와 persistence key를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- DataStore dependency를 새로 추가하지 마라. 이유: 프로젝트에는 기존 DataStore 구조가 없고 SharedPreferences가 최소 영향 경계다.
- 설정을 Room에 저장하지 마라. 이유: audio preference는 gameplay 원천 데이터가 아니다.
- commit 실패 뒤 UI flow만 새 값으로 유지하지 마라. 이유: 표시값과 재실행 후 값이 달라진다.
- 기존 테스트를 깨뜨리지 마라.
