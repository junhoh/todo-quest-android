# Step 5: coordinate-sfx-with-battle-timeline

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/audio/BattleSfxPlayer.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/060-069/61-battle-sound-effects/step1.md`
- `/phases/060-069/61-battle-sound-effects/step4.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

가상 시간 tests를 먼저 확장하고 `BattleAnimationController`의 기존 single buffered actor가 animation과 SFX를 같은 ordered effect event에서 순차 조정하도록 한다. constructor에 `BattleSfxPlayer`를 주입하되 기본값은 `NoOpBattleSfxPlayer`로 두어 preview와 기존 test가 Android audio를 요구하지 않게 한다.

player transition은 `PlayerAttackStarted`를 확인한 뒤 `PLAYER_ATTACK`을 호출하고 `PLAYER_ATTACKING` phase를 활성화한다. `advanceMillis` 뒤 `EntityHit(target=MONSTER)`로 `MONSTER_HIT`을 호출한 다음 MONSTER_HIT phase와 HP/damage scene을 표시한다. lethal인 경우 기존 `hitMillis`가 끝난 뒤 `MonsterDefeated`로 `MONSTER_DEFEATED`를 호출하고 MONSTER_DYING phase를 시작한다. spawn alert와 new monster spawn에는 defeat 음을 다시 호출하지 않는다.

monster transition은 `MonsterAttackStarted`로 `MONSTER_ATTACK`을 호출한 뒤 MONSTER_ATTACKING phase, `advanceMillis` 뒤 player `EntityHit`로 `PLAYER_HIT`과 PLAYER_HIT phase를 진행한다. lethal인 경우 `hitMillis` 뒤 실제 effect event `PlayerDefeated`와 기존 severe lifecycle의 source id 일치를 검증하고 `PLAYER_DEFEATED`를 호출한 뒤 PLAYER_DYING phase를 시작한다. 뒤의 PLAYER_DEFEATED label, STATUS_EFFECT_APPLYING/REFRESHING, PLAYER_EMERGENCY_RECOVERING과 STATUS_EFFECT_REMOVING에는 SFX 호출을 추가하지 않는다.

각 audio 호출은 잘못된 fake/delegate가 throw해도 timeline coroutine이 계속 진행하도록 controller에서도 최종 방어로 격리한다. animation state와 audio가 별도 Flow collector로 같은 event를 경쟁 소비하지 않게 한다.

기존 무한 `consumedEventIds`는 combat transition용 `CombatEventKey`와 status removal id를 구분해 insertion-order 256개 bounded cache로 교체한다. 같은 key가 queue 또는 cache에 있으면 전체 transition을 거절하고 서로 다른 key의 빠른 연속 공격은 모두 queue한다. `SfxPlaybackKey` dedup은 application-scope player가 담당한다.

`CalendarViewModel` constructor/factory에는 player injection point를 추가하고 controller 생성에 전달한다. repository event collector는 그대로 transition 한 번만 enqueue하며 HP state를 관찰해 음향을 만들지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --console=plain
git diff --check
```

## 검증 절차

1. `FakeBattleSfxPlayer`로 six basic requests, lethal ordering, nonlethal exclusion, overkill once, duplicate key, distinct rapid events, cache bound, re-subscription, spawn, severe injury refresh와 throwing player tests를 먼저 작성한다.
2. 실제 delay 대신 test dispatcher virtual time으로 attack/hit/defeat 호출 시점을 검증한다.
3. AC 명령을 실행하고 기존 phase duration, HP scene, reward badge와 input lock이 유지되는지 확인한다.
4. task index의 step 5를 `completed`로 바꾸고 controller/audio 조정 및 bounded cache를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Composable이나 `CombatSnapshot` HP Flow에서 SFX를 추론하지 마라. 이유: 회전·재구성과 recovery에서 replay될 수 있다.
- hit와 defeat를 같은 phase 진입에서 동시에 호출하지 마라. 이유: 두 음향을 자연스럽게 구분해야 한다.
- 중상 또는 응급 회복 phase에 SFX를 연결하지 마라. 이유: death/revive 반복 재생을 금지한 요구사항이다.
- 실제 delay나 sleep으로 test하지 마라. 이유: virtual-time 순서를 결정적으로 검증해야 한다.
- 기존 테스트를 깨뜨리지 마라.
