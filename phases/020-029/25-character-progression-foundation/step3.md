# Step 3: 캐릭터 저장소 v3 마이그레이션

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoTaskEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterProfileEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerEntity.kt`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/2.json`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- step 1과 2에서 생성한 캐릭터·보상 도메인 계약
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

Robolectric migration/DAO unit test를 먼저 작성해 실패를 확인한 다음 Room schema를 version 3으로 올리고 수동 `MIGRATION_2_3`을 구현한다. 기존 `MIGRATION_1_2`와 연결해 v1→2→3 및 v2→3 경로 모두 사용자 데이터를 보존한다.

schema v3은 다음 원천 상태와 확정 기록만 저장한다.

- `todo_tasks`에 non-null `recurrenceSeriesId: Long`을 추가한다. 기존 행은 자기 `id`, 새 일정은 최초 생성 id, 반복 원본 분할로 생긴 행은 기존 series id를 유지한다.
- `character_profile`은 저장 `level`을 제거하고 `id`, `totalXp: Long`, `currentGold: Long`, 네 기본 스탯, `unspentStatPoints`, `hasUsedFreeStatReset`을 저장한다.
- `character_current_state`를 추가해 `characterId`, `currentHp`, `balanceVersion`, `updatedAtEpochMillis`를 저장한다.
- `reward_ledger`는 unique `(taskId, occurrenceDateEpochDay)`를 보존하고 `recurrenceSeriesId`, 실제 `xpAward/goldAward: Long`, `rewardLocalDateEpochDay`, `onTime`, `onTimeMultiplierBp`, `rewardEfficiencyBp`, `repeatOrdinal`, `dailyOrdinal`, `goldGainBonusBp`, `combatEligible`, `balanceVersion`, 지급 시각을 저장한다.
- 일일/series 순번과 정시 occurrence 날짜 조회에 필요한 index와 DAO count/observe query를 추가한다.

기존 profile migration은 XP·gold를 그대로 복사하고 capped level을 XP로 다시 계산한다. 기본 스탯은 각각 5, 미배분 포인트는 `2 × (level - 1)`, 무료 초기화는 미사용, current HP는 해당 level의 무장비 MAX_HP, balance version은 1이다.

기존 ledger는 지급량과 unique key를 보존하되 history를 소급 해석하지 않는다. `recurrenceSeriesId`는 task의 migration 값 또는 task가 없으면 task id, `rewardLocalDateEpochDay`는 occurrence 날짜, multiplier·efficiency는 10,000bp, ordinal은 0, `onTime=false`, `combatEligible=false`, `balanceVersion=0`으로 이전한다. 이후 새 순번 query에서는 기존 행도 count하여 같은 날짜에 추가 보상을 우회하지 못하게 한다.

새 profile 행이 없는 설치는 repository가 기본 character/current state를 원자적으로 생성할 수 있게 DAO를 제공한다. app database builder에 두 migration을 모두 등록하고 schema 3 JSON을 생성·커밋한다.

테스트 클래스는 `TodoQuestDatabaseMigrationTest`, `CharacterDaoTest`, `RewardLedgerDaoTest`로 분리한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.CharacterDaoTest" --tests "com.todoquest.data.local.RewardLedgerDaoTest"
.\gradlew.bat assembleDebug
if (-not (Test-Path -LiteralPath 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\3.json')) { throw 'Room schema 3 was not exported' }
git diff --check
```

## 검증 절차

1. v2 sample DB와 profile, task, completion, reward 행을 만드는 실패 테스트를 먼저 추가한다.
2. AC를 실행하고 v1→3 및 v2→3에서 일정·완료·보상·XP·gold가 보존되는지 확인한다.
3. schema 3에 level이나 파생 스탯 snapshot이 없는지 확인한다.
4. task index step 3을 `completed`로 변경하고 migration과 DAO 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- destructive migration 또는 fallback을 사용하지 마라. 이유: 기존 일정과 성장 기록을 잃을 수 있다.
- 기존 reward ledger를 정시·연속일·전투 event로 소급 변환하지 마라. 이유: 당시 판정 입력을 복원할 수 없다.
- level이나 8개 파생 스탯을 Room에 저장하지 마라. 이유: versioned 원천 상태에서 계산해야 한다.
- 장비나 generic temporary effect 테이블을 만들지 마라. 이유: 이번 phase 승인 범위 밖이다.
- 기존 테스트를 깨뜨리지 마라.

