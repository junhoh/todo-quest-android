# Step 3: add-monster-species-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStagePolicy.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterStagePolicyTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle.png`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

도메인과 Repository 테스트를 먼저 작성해 실패를 확인한 뒤 전투 능력치 유형과 독립된 표시 종족 메타데이터를 추가한다. Room schema와 balance version은 변경하지 않는다.

공개 인터페이스는 다음 의미로 구현한다.

```kotlin
enum class MonsterSpecies {
    GOBLIN_SCOUT,
    SKELETON_SOLDIER,
}

object MonsterSpeciesPolicy {
    fun speciesFor(type: MonsterType, grade: MonsterGrade): MonsterSpecies
}

data class CombatSnapshot(
    // existing fields
    val activeMonsterSpecies: MonsterSpecies,
)
```

정책 행렬은 다음으로 고정한다.

- `MonsterType.ATTACK + MonsterGrade.NORMAL`만 `SKELETON_SOLDIER`다.
- `ATTACK + ELITE`, `ATTACK + BOSS`와 다른 모든 type/grade 조합은 `GOBLIN_SCOUT`다.
- 현재 10칸 순환에서 2·8번째 일반 encounter가 해골 병사이고 5번째 elite는 고블린이다.
- 종족은 저장된 `definitionId`로 찾은 `MonsterDefinition.type`과 `MonsterInstance.grade`에서 결정적으로 파생한다.

`RoomCombatRepository`는 snapshot 생성 시 기존 definition lookup을 재사용해 `activeMonsterSpecies`를 채운다. 기존 `monster_balanced_v1`, `monster_attack_v1`, `monster_defense_v1`, `monster_boss_v1` id와 `monster_instances` row를 그대로 유지한다. 이미 활성화된 `monster_attack_v1 + NORMAL` 인스턴스도 앱 업데이트 뒤 해골 병사로 해석하며 HP, Stage, attack event, reward 결과를 변경하지 않는다.

테스트는 다음을 포함한다.

- 모든 type/grade 조합의 종족 매핑 행렬.
- encounter 2·8은 해골, 5 elite와 1·3·10은 고블린이라는 현행 순환 결과.
- Repository 재시작과 기존 stored attack-normal 인스턴스가 같은 종족을 반환한다.
- 종족 추가 전후 같은 definition·grade·level의 `MonsterStats`, HP, 공격과 보상 계산이 동일하다.
- unknown definition, balance version과 기존 slot validation 실패 동작은 바뀌지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterSpeciesPolicyTest" --tests "com.todoquest.domain.MonsterStagePolicyTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
git diff --check
```

## 검증 절차

1. 정책·Repository 테스트를 먼저 작성하고 구현 전 실패를 확인한다.
2. 도메인 타입·정책과 snapshot 매핑을 구현하고 AC 명령을 실행한다.
3. Room entity, DAO, database version과 migration 파일에 변경이 없는지 확인한다.
4. AGENTS.md의 전투 멱등성·occurrence 분리·권한 독립 CRITICAL 규칙을 확인한다.
5. task index의 step 3을 `completed`로 변경하고 신규 타입, mapping 행렬과 무 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `monster_instances`에 species 컬럼을 추가하지 마라. 이유: 기존 type과 grade에서 결정적으로 파생할 수 있어 중복 source가 된다.
- 몬스터 능력치 배율, 보상 공식 또는 balance version을 바꾸지 마라. 이유: 이번 변경은 종족 표시 통합이지 밸런스 변경이 아니다.
- elite와 boss를 해골 병사로 매핑하지 마라. 이유: 승인 범위는 일반 공격형 encounter뿐이다.
- 기존 테스트를 깨뜨리지 마라.
