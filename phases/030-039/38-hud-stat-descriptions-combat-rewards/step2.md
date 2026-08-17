# Step 2: migrate-combat-reward-ledgers-room-v9

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/model/CompletionResult.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/mapper/CombatEntityMapper.kt`
- `/app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt`
- `/app/src/test/java/com/todoquest/data/local/CombatDaoTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Room migration과 DAO 테스트를 먼저 작성하고 database를 v9로 올린다. 이 step은 `data/local` entity·DAO·mapper·schema에 한정하며 production application builder wiring은 step 8에서 수행한다.

`reward_ledgers`에 `rewardMode TEXT NOT NULL DEFAULT 'TODO_COMPLETION'`을 추가한다. `player_attack_events`에는 `combatRewardVersion INTEGER NOT NULL DEFAULT 0`, `hitXpAward INTEGER NOT NULL DEFAULT 0`, `killBonusXpAward INTEGER NOT NULL DEFAULT 0`, `killGoldAward INTEGER NOT NULL DEFAULT 0`, `rewardGradeMultiplierBp INTEGER NOT NULL DEFAULT 0`, `rewardGoldGainBonusBp INTEGER NOT NULL DEFAULT 0`을 추가한다. Kotlin entity에서는 경제 값에 `Long`, bp와 version에 `Int`를 사용한다.

`MIGRATION_8_9`는 ALTER TABLE만 사용해 기존 v8 row를 default로 보존한다. 기존 APPLIED와 PENDING attack 모두 reward version `0`과 award `0`이어야 하며 character profile, reward ledger award, monster HP·Stage·failure source를 수정하지 않는다. v1부터 v9까지 전체 migration chain, v8→v9 direct migration과 재개방을 테스트하고 `app/schemas/com.todoquest.data.local.TodoQuestDatabase/9.json`을 생성한다.

`CombatDao.markPlayerAttackApplied(...)`와 mapper가 새 snapshot 필드를 저장·복원하게 한다. PENDING version 0/1과 APPLIED result의 nullability·default invariant를 명시적으로 검증한다. reward mode와 version의 unknown 값은 mapper/repository 경계에서 조용히 대체하지 않고 실패시킨다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --tests "com.todoquest.data.local.CombatDaoTest" --console=plain
Test-Path 'app\schemas\com.todoquest.data.local.TodoQuestDatabase\9.json'
git diff --check
```

## 검증 절차

1. v8 fixture에 TODO reward와 APPLIED/PENDING attack을 넣는 migration test를 먼저 작성해 새 column 부재 실패를 확인한다.
2. AC를 실행해 schema identity, default 값, DAO update와 mapper round-trip을 검증한다.
3. 기존 schema `1..8.json`을 수정하지 않고 새 `9.json`만 추가됐는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 Room v9 column과 비소급 default를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 row의 XP·gold·combat status를 UPDATE하지 마라. 이유: migration은 과거 경제·전투 결과를 재작성하면 안 된다.
- reward를 파생 계산만으로 복원하지 마라. 이유: 확정 event에는 실제 지급 snapshot이 남아야 한다.
- destructive migration이나 table drop을 사용하지 마라. 이유: 사용자 진행을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
