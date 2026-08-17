# Step 1: model-battle-effect-events

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/phases/060-069/61-battle-sound-effects/step0.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 fresh `CombatTransition`이 효과음과 animation이 공통 소비할 ordered domain effect event를 제공하도록 한다. `Combat.kt`에 `BattleEntityKind`, attacker/target의 안정적 typed reference와 다음 sealed event를 추가한다.

```kotlin
sealed interface BattleEffectEvent {
    val eventId: String
    val attackEventKey: CombatEventKey
    val attacker: BattleEntityRef
    val target: BattleEntityRef
    val damage: Int
    val monsterId: Long
    val isTerminal: Boolean

    data class PlayerAttackStarted(/* 공통 metadata */) : BattleEffectEvent
    data class MonsterAttackStarted(/* 공통 metadata */) : BattleEffectEvent
    data class EntityHit(/* 공통 metadata */) : BattleEffectEvent
    data class MonsterDefeated(/* 공통 metadata */) : BattleEffectEvent
    data class PlayerDefeated(/* 공통 metadata와 source lifecycle id */) : BattleEffectEvent
}
```

single-player identity는 문자열을 산재시키지 말고 `BattleEntityRef.Player` 같은 typed singleton으로 표현하고 monster는 실제 `MonsterInstance.id`를 보존한다. `eventId`는 `CombatEventKey`에서 결정적으로 생성한 `combat:{kind}:{taskId}:{occurrenceDateEpochDay}`를 한 공격의 started/hit/defeat가 공유한다. `PlayerDefeated`에는 이를 증명한 기존 `CombatLifecycleEvent.PlayerDefeated.eventId`도 별도 필드로 보존한다.

`CombatTransition`은 `val effectEvents: List<BattleEffectEvent>`를 노출한다. player nonlethal은 `PlayerAttackStarted, EntityHit`, lethal은 뒤에 `MonsterDefeated`를 추가한다. monster nonlethal은 `MonsterAttackStarted, EntityHit`, lethal은 정확히 세 severe-injury lifecycle 중 첫 `CombatLifecycleEvent.PlayerDefeated`가 검증된 경우에만 `PlayerDefeated`를 추가한다. target HP는 기존 policy대로 0에 clamp되므로 초과 피해도 terminal event 하나만 만든다. outgoing monster id를 사용하고 lethal player attack의 incoming monster id를 defeat event에 섞지 않는다.

event factory는 transition의 공격 snapshot, before/after snapshot과 lifecycle의 불일치에서 즉시 실패해 잘못된 presentation metadata를 조용히 만들지 않는다. Repository transaction, entity, DAO와 Room schema는 변경하지 않는다. 기존 repository replay-0 tests에는 fresh attack당 transition 한 개와 올바른 effect event sequence, APPLIED 재처리에는 새 transition/event 없음 assertion을 보강한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.BattleEffectEventTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. player/monster 각각 nonlethal·lethal·overkill·duplicate 테스트를 먼저 작성해 기존 코드에서 실패하는지 확인한다.
2. AC 명령을 실행하고 이벤트의 type, 순서, 공통 event id, attacker/target, damage, monster id와 terminal flag를 확인한다.
3. occurrence attack key와 기존 reward·spawn·severe injury 결과가 바뀌지 않았는지 확인한다.
4. task index의 step 1을 `completed`로 바꾸고 새 domain event 파일/결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- UI 또는 Android 타입을 domain event에 넣지 마라. 이유: event 생성은 순수 Kotlin 전투 결과 계약이어야 한다.
- 별도 Room event table이나 migration을 추가하지 마라. 이유: 기존 attack row와 replay 없는 transition이 멱등성 source다.
- wall-clock timestamp나 debounce를 identity로 사용하지 마라. 이유: 빠른 정상 공격을 누락시키고 재현성이 사라진다.
- incoming monster를 처치 대상으로 기록하지 마라. 이유: defeat는 outgoing instance에 귀속된다.
- 기존 테스트를 깨뜨리지 마라.
