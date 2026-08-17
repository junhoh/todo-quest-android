# Step 3: extend-harpy-species-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStagePolicy.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterSpeciesPolicyTest.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterStagePolicyTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/docs/art/monster/todo-quest-harpy-front-idle.png`
- `/phases/040-049/42-harpy-enemy/index.json`

## 작업

도메인과 Repository 테스트를 먼저 작성하고 구현 전 예상된 실패를 확인한 뒤 네 번째 표시 종족을 추가한다. Room schema와 balance version은 변경하지 않는다.

공개 인터페이스는 다음 의미로 확장한다.

```kotlin
enum class MonsterSpecies {
    GOBLIN_SCOUT,
    SKELETON_SOLDIER,
    CORRUPTED_TREE_SPIRIT,
    HARPY,
}

object MonsterSpeciesPolicy {
    fun speciesFor(type: MonsterType, grade: MonsterGrade): MonsterSpecies
}
```

정책 행렬을 다음으로 고정한다.

- `BALANCED + NORMAL`은 `CORRUPTED_TREE_SPIRIT`이다.
- `ATTACK + NORMAL`은 `SKELETON_SOLDIER`다.
- `DEFENSE + NORMAL`은 `HARPY`다.
- `BOSS + NORMAL`, 모든 `ELITE`, 모든 `BOSS` grade 조합은 `GOBLIN_SCOUT`다.
- Stage 1의 8개 일반 encounter는 `나무 정령 → 해골 → 하피 → 나무 정령 → 해골 → 하피 → 나무 정령 → 해골`이다. 다른 NORMAL Stage도 기존 `[BALANCED, ATTACK, DEFENSE]` 순환에 같은 행렬을 적용한다.
- 종족은 저장된 `definitionId`로 복원한 `MonsterDefinition.type`과 `MonsterInstance.grade`에서 결정적으로 파생한다.

기존 활성 `monster_defense_v1 + NORMAL` 인스턴스도 업데이트 후 하피로 해석한다. HP, Stage, encounter, attack event, reward 결과는 바뀌지 않는다. `MonsterStatsCalculator`, `CombatCalculator`, `CombatRewardPolicy`에는 종족을 새 입력으로 전달하지 않는다.

테스트는 모든 type/grade 조합, Stage 순환, Repository 재시작과 저장된 defense-normal 인스턴스, 종족 조회 전후 능력치·피해·hit/kill XP·gold 불변, unknown definition과 기존 validation 실패 동작을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterSpeciesPolicyTest" --tests "com.todoquest.domain.MonsterStagePolicyTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
git diff --check
```

## 검증 절차

1. 정책·Repository 테스트를 먼저 작성하고 enum·정책 수정 전 예상된 실패를 확인한다.
2. enum과 정책을 구현하고 AC 명령을 실행한다.
3. Room entity, DAO, database version, migration, 능력치·보상 공식을 변경하지 않았는지 확인한다.
4. task index의 step 3을 `completed`로 변경하고 신규 enum, 매핑 행렬과 무 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `monster_instances`에 species, element 또는 biome 컬럼을 추가하지 마라. 이유: 기존 type과 grade에서 결정적으로 파생할 수 있다.
- `MonsterType.AGILE`을 신설하거나 DEFENSE 배율을 바꾸지 마라. 이유: 사용자가 기존 DEFENSE+NORMAL의 표시 종족 교체를 선택했다.
- 능력치, 보상 공식 또는 balance version을 바꾸지 마라. 이유: 이번 변경은 presentation species 추가다.
- 바람 공격, 비행 회피, 상성, 상태 효과, 스킬 또는 전리품을 추가하지 마라. 이유: 속성은 이번 phase에서 시각 metadata다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
