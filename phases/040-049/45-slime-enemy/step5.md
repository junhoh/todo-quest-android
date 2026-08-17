# Step 5: integrate-slime-species-repository

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
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterSpeciesPolicyTest.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterStagePolicyTest.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

Repository 테스트를 먼저 작성해 기존 `definition type + grade` 호환 overload 호출 경계의 실패를 확인한 뒤 `RoomCombatRepository`가 저장된 active monster 원천 상태로 새 `MonsterSpeciesPolicy`를 호출하게 한다. 전환이 끝나면 step 2가 임시로 유지한 `speciesFor(type, grade)` overload와 `@Deprecated` 표시를 제거한다. UI와 Android resource는 수정하지 않는다.

`combatSnapshot`의 종족 파생 입력을 다음으로 고정한다.

```kotlin
MonsterSpeciesPolicy.speciesFor(
    stageNumber = stored.activeMonster.stageNumber,
    encounterNumber = stored.activeMonster.encounterNumber,
    grade = stored.activeMonster.grade,
    encounterCount = MonsterStagePolicy.encounterCount(
        stored.activeMonster.stageNumber,
        monsterBalanceConfig,
    ),
    balanceVersion = stored.activeMonster.balanceVersion,
)
```

- `CombatSnapshot.activeMonsterSpecies`의 타입과 역할은 유지한다.
- 종족은 `monster_instances`에 저장하지 않고 관찰 시 파생한다.
- NORMAL Stage의 저장된 encounter를 각각 조회하면 도메인 golden vector와 같은 종족을 반환해야 한다.
- 앱 재시작과 Repository 재생성 뒤 같은 active monster는 같은 종족을 반환해야 한다.
- 치명 공격으로 다음 encounter·Stage가 활성화되면 새 저장 위치의 스케줄 종족을 반환하고 before/death snapshot의 이전 종족과 after/spawn snapshot의 새 종족을 보존한다.
- 기존 저장 인스턴스는 업데이트 후 새 결정적 종족 스케줄로 재해석될 수 있지만 id, definition, type, grade, level, HP, Stage, encounter와 balance version은 변경하지 않는다.
- 종족 조회 전후 같은 인스턴스의 `MonsterStats`, 플레이어·몬스터 피해, hit XP, kill XP와 gold는 동일해야 한다.

Repository 테스트는 Stage 1·2 NORMAL golden vector의 대표 encounter, Stage 5 ELITE, Stage 10 BOSS, 재시작 동일성, 처치 후 종족 전환, unknown definition과 잘못된 저장 상태의 기존 실패 동작을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.MonsterSpeciesPolicyTest" --tests "com.todoquest.domain.MonsterStagePolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
git diff --check
```

## 검증 절차

1. Repository 재시작·전환 테스트를 먼저 작성하고 호환 overload 호출 경계에서 예상된 실패를 확인한다.
2. snapshot 종족 파생을 전환하고 호환 overload를 제거한 뒤 AC 명령을 실행한다.
3. Room entity, DAO, database version, schema export와 migration 파일에 변경이 없는지 확인한다.
4. 전투·보상 transaction과 occurrence 멱등 key가 변경되지 않았는지 확인한다.
5. task index의 step 5를 `completed`로 변경하고 저장된 Stage/encounter 기반 파생, 재시작 안정성과 무 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- species, schedule 또는 random seed 컬럼을 Room에 추가하지 마라. 이유: 사용자가 결정적 파생 방식을 선택했고 저장된 원천 상태로 충분하다.
- Repository에서 현재 시각이나 별도 난수 source를 호출하지 마라. 이유: 재시작 뒤 종족이 달라지면 데이터 표현이 불안정해진다.
- monster HP, Stage 진행, 공격 event, 보상 ledger 또는 balance version을 수정하지 마라. 이유: 이번 변경은 종족 presentation metadata다.
- UI, Compose 또는 Android resource를 수정하지 마라. 이유: 이 step은 data/repository 경계와 임시 호환 API 제거만 다룬다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 CRITICAL 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
