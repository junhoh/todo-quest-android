# Step 4: implement-soundpool-battle-player

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/build.gradle.kts`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/domain/repository/BattleSfxSettingsRepository.kt`
- `/app/src/main/res/raw/sfx_player_attack.wav`
- `/app/src/main/res/raw/sfx_monster_attack.wav`
- `/app/src/main/res/raw/sfx_player_hit.wav`
- `/app/src/main/res/raw/sfx_monster_hit.wav`
- `/app/src/main/res/raw/sfx_monster_defeated.wav`
- `/app/src/main/res/raw/sfx_player_defeated.wav`
- `/phases/060-069/61-battle-sound-effects/step2.md`
- `/phases/060-069/61-battle-sound-effects/step3.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

Android와 독립적으로 fake를 주입할 수 있는 audio 계약과 Android SoundPool adapter를 테스트 우선으로 구현한다.

```kotlin
enum class BattleSfx {
    PLAYER_ATTACK, MONSTER_ATTACK, PLAYER_HIT,
    MONSTER_HIT, MONSTER_DEFEATED, PLAYER_DEFEATED,
}

data class SfxPlaybackKey(val eventId: String, val effect: BattleSfx)

interface BattleSfxPlayer {
    fun play(effect: BattleSfx, eventId: String)
    fun release()
}
```

`AndroidBattleSfxPlayer`는 application context만 보관하고 `AudioAttributes.USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `SoundPool.Builder.setMaxStreams(6)`를 사용한다. constructor/init에서 여섯 resource를 정확히 한 번 load하고 effect별 sample id와 load 성공 여부를 보관한다. load 전, load 실패, release 이후, app foreground가 아닌 시점의 요청은 queue하지 말고 무해하게 폐기한다. `play()`의 exception과 반환 stream id 0은 logging만 하고 호출자에게 throw하지 않는다. loop 0, rate 1f로 재생하며 AudioManager volume, ringer, DND, audio focus를 변경하지 않는다.

foreground 여부는 application `ActivityLifecycleCallbacks`의 resumed activity count를 thread-safe하게 추적하거나 같은 동작을 하는 주입 가능한 gate로 구현한다. background 진입 뒤 pending sound를 보존하지 않는다. `release()`는 idempotent하게 SoundPool을 release하고 callback을 해제한다. process 종료는 OS가 native resource를 회수하지만 test/application teardown에서 명시 release할 수 있어야 한다.

application-scope decorator `ConfiguredBattleSfxPlayer`는 `BattleSfxSettingsRepository.isEnabled.value`와 insertion-order 256개 bounded `SfxPlaybackKey` cache를 소유한다. blank event id를 재생하지 않는다. key는 setting check와 delegate 호출 전에 소비 처리하여 muted, background 또는 실패한 과거 이벤트가 설정 변경/복귀 뒤 replay되지 않게 한다. cache eviction은 오래된 key만 제거하며 시간 debounce를 사용하지 않는다. delegate가 잘못 throw해도 decorator가 격리한다. `NoOpBattleSfxPlayer`도 제공한다.

Robolectric에서 real speaker를 쓰지 않도록 SoundPool backend/factory와 foreground gate를 internal injection point로 둔다. test fake backend로 attributes/maxStreams, six preload, load 전 drop, loaded play mapping, load/play failure, background drop, idempotent release를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.audio.AndroidBattleSfxPlayerTest" --tests "com.todoquest.audio.ConfiguredBattleSfxPlayerTest" --console=plain
git diff --check
```

## 검증 절차

1. fake backend/player로 설정, dedup, bounded eviction, lifecycle와 failure tests를 먼저 작성한다.
2. AC 명령을 실행한다.
3. 여섯 enum이 여섯 raw resource에 일대일 mapping되고 attack마다 reload하지 않는지 확인한다.
4. task index의 step 4를 `completed`로 바꾸고 audio 계약, implementation과 lifecycle 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Composable 또는 공격마다 SoundPool을 생성·load하지 마라. 이유: latency와 native resource leak이 발생한다.
- load 전 요청을 나중에 자동 재생하지 마라. 이유: 화면 복귀 때 과거 전투음이 갑자기 들릴 수 있다.
- 시스템 media volume이나 DND를 우회하지 마라. 이유: 사용자 기기 정책을 존중해야 한다.
- audio focus를 독점하지 마라. 이유: 짧은 게임 sonification에 불필요하다.
- 실제 스피커 출력을 unit test assertion으로 사용하지 마라. 이유: test는 결정적 fake 요청을 검증해야 한다.
- 기존 테스트를 깨뜨리지 마라.
