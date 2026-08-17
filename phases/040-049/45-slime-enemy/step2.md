# Step 2: implement-deterministic-species-schedule

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStagePolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStatsCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatRewardPolicy.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterSpeciesPolicyTest.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterStagePolicyTest.kt`
- `/docs/art/monster/todo-quest-slime-front-idle-spec.json`
- `/docs/art/monster/todo-quest-slime-front-idle.png`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

순수 Kotlin 테스트를 먼저 작성해 기존 type/grade 고정 매핑의 실패를 확인한 뒤 `MonsterSpecies.SLIME`과 재시작에 안정적인 결정적 종족 스케줄을 도메인 계층에 구현한다. Repository와 Android resource는 수정하지 않는다.

공개 인터페이스를 다음으로 변경한다.

```kotlin
enum class MonsterSpecies {
    GOBLIN_SCOUT,
    SKELETON_SOLDIER,
    CORRUPTED_TREE_SPIRIT,
    HARPY,
    SLIME,
}

object MonsterSpeciesPolicy {
    fun speciesFor(
        stageNumber: Int,
        encounterNumber: Int,
        grade: MonsterGrade,
        encounterCount: Int,
        balanceVersion: Int,
    ): MonsterSpecies
}
```

기존 `speciesFor(type, grade)`는 다음 Repository 전환 step까지 컴파일 호환 overload로 유지하고 기존 네 종족 매핑을 그대로 반환하게 한다. `@Deprecated`와 새 5-인자 API 사용 안내를 붙이며 step 5에서 Repository 호출을 전환한 뒤 제거한다. `MonsterType`은 계속 definition과 능력치 계산의 입력이며 최종 종족 스케줄에는 사용하지 않는다.

`SLIME` enum 추가로 `BattleMonsterVisualCatalog.forSpecies`의 exhaustive `when`이 컴파일되지 않으므로 `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`에 다음 compile-only 분기만 추가한다.

```kotlin
MonsterSpecies.SLIME -> error("SLIME presentation is registered in step 4")
```

이 분기는 Repository가 아직 기존 호환 overload를 사용하므로 production snapshot에서 도달할 수 없다. drawable, 문자열, Preview, controller와 UI는 수정하지 않으며 step 4에서 실제 resource mapping으로 교체한다.

결정적 스케줄 계약은 다음과 같다.

- enum ordinal에 의존하지 않는 명시적 종족 목록은 `[GOBLIN_SCOUT, SLIME, CORRUPTED_TREE_SPIRIT, SKELETON_SOLDIER, HARPY]`다.
- grade code는 `NORMAL=1`, `ELITE=2`, `BOSS=3`으로 명시하고 ordinal을 사용하지 않는다.
- seed는 `0x544F444F51554553L`에서 시작해 `balanceVersion`, `stageNumber`, grade code 순서로 각각 `seed = seed * 31L + value`를 적용한다. JVM `Long` overflow와 `java.util.Random(seed)`·`java.util.Collections.shuffle(list, random)`의 순서를 계약으로 고정한다.
- 공통 입력은 `stageNumber > 0`, `balanceVersion > 0`, `encounterCount > 0`, `encounterNumber in 1..encounterCount`를 요구한다.
- `NORMAL`은 `encounterCount >= 5`를 추가로 요구한다. 기본 pool에 다섯 종족을 한 번씩 넣고, pool이 encounter count에 도달할 때까지 같은 `Random` 인스턴스로 매번 새 다섯 종족 목록을 셔플해 필요한 prefix를 추가한다. 마지막에 pool 전체를 같은 `Random`으로 한 번 더 셔플하고 encounter의 1-based 위치를 반환한다.
- 현재 8개 encounter에서는 모든 종족이 최소 1회, 정확히 세 종족이 2회 등장해 종족별 횟수 차이가 최대 1이다.
- `ELITE`와 `BOSS`는 현재 `encounterCount == 1`, `encounterNumber == 1`을 요구한다. 명시적 다섯 종족 목록을 같은 seed 규칙으로 셔플하고 첫 종족을 반환한다.

balance version 1의 golden vector를 테스트에 고정한다.

- Stage 1 NORMAL: `[SKELETON_SOLDIER, HARPY, GOBLIN_SCOUT, SKELETON_SOLDIER, SLIME, HARPY, CORRUPTED_TREE_SPIRIT, SLIME]`.
- Stage 2 NORMAL: `[CORRUPTED_TREE_SPIRIT, GOBLIN_SCOUT, SLIME, SKELETON_SOLDIER, GOBLIN_SCOUT, SKELETON_SOLDIER, HARPY, HARPY]`.
- Stage 5 ELITE: `CORRUPTED_TREE_SPIRIT`.
- Stage 10 BOSS: `HARPY`.

테스트는 반복 호출의 동일 결과, Stage 1~4·6~9 각 NORMAL Stage의 다섯 종족 포함과 1~2회 균형, 서로 다른 Stage의 순서 차이, 특수 Stage 단일 선택, 모든 invalid input을 포함한다. 기존 능력치·피해·hit XP·kill XP·gold 계산은 종족 스케줄 호출 전후 완전히 같아야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterSpeciesPolicyTest" --tests "com.todoquest.domain.MonsterStagePolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterStatsCalculatorTest" --tests "com.todoquest.domain.CombatCalculatorTest" --tests "com.todoquest.domain.CombatRewardPolicyTest"
git diff --check
```

## 검증 절차

1. 새 정책 테스트를 먼저 작성하고 기존 구현에서 예상대로 실패하는지 확인한다.
2. enum과 정책을 구현하고 AC 명령을 실행한다.
3. `MonsterStagePolicy`의 기존 type·grade·encounter count와 전투 계산 공식이 변경되지 않았는지, compile-only presentation 분기가 production snapshot에서 도달 불가능한지 확인한다.
4. AGENTS.md의 테스트 우선, 보상 멱등성과 occurrence 분리 규칙을 확인한다.
5. task index의 step 2를 `completed`로 변경하고 새 enum, seed·셔플 계약과 능력치·보상 불변을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `kotlin.random.Random`, 현재 시각, 시스템 entropy 또는 실행 순서에 의존하지 마라. 이유: 앱 재시작 뒤 같은 Stage/encounter가 같은 종족으로 복원되어야 한다.
- enum ordinal로 seed나 목록 순서를 만들지 마라. 이유: enum 선언 변경이 기존 스케줄을 암묵적으로 바꾸면 안 된다.
- `MonsterType`, 능력치 배율, 보상 공식 또는 encounter count를 변경하지 마라. 이유: 이번 정책은 종족 표시 스케줄만 바꾼다.
- Room entity, DAO, migration 또는 Repository를 수정하지 마라. 이유: Repository는 step 5에서 새 API로 원자적으로 전환한다.
- compile-only `SLIME` 분기에 기존 몬스터 drawable이나 사용자 문구 fallback을 넣지 마라. 이유: 잘못된 종족 표시를 허용하지 말고 실제 resource가 준비되는 step 4까지 도달 불가능 상태를 유지해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 CRITICAL 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
