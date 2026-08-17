# Step 1: persist-status-effects-room-v13

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/DEVELOPMENT.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step0.md
- /app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt
- /app/src/main/java/com/todoquest/data/local/CharacterEntities.kt
- /app/src/main/java/com/todoquest/data/local/TaskEntities.kt
- /app/src/main/java/com/todoquest/data/local/CombatEntities.kt
- /app/src/main/java/com/todoquest/data/local/CharacterDao.kt
- /app/src/main/java/com/todoquest/data/local/TaskDao.kt
- /app/src/test/java/com/todoquest/data/local/TodoQuestDatabaseMigrationTest.kt
- /app/src/androidTest/java/com/todoquest/app/TodoQuestDatabaseIsolationTest.kt

## 작업

테스트를 먼저 작성하고 Room schema를 13으로 올려 상태이상과 완료 occurrence credit을 영구 저장한다.

1. 기존 local 패키지 구성과 명명법을 따라 character_status_effects entity/DAO를 추가한다. (characterId, effectType)는 유일해야 하며 definition version, appliedAt, expiresAt, remainingRecoveryCompletions, active, revision, lastMutationId를 저장한다.
2. status_effect_recovery_occurrences entity/DAO를 추가한다. (characterId, effectType, revision, taskId, occurrenceDate) 복합 키 또는 동등한 unique index로 같은 중상 revision에서 같은 occurrence가 한 번만 회복 credit을 얻도록 한다. 반복 일정의 서로 다른 날짜 occurrence는 각각 셀 수 있어야 한다.
3. 활성 상태 조회, apply/refresh upsert, 조건부 occurrence credit 삽입, 남은 횟수 감소, 조건부 만료/해제에 필요한 DAO 연산을 트랜잭션 친화적으로 제공한다. enum/string 변환은 기존 converter 정책을 따른다.
4. TodoQuestDatabase version을 13으로 올리고 MIGRATION_12_13을 명시적으로 등록한다. 두 테이블과 인덱스만 생성하며 기존 사용자 데이터는 보존하고 기존 사용자에게 활성 상태이상이 없는 기본 상태를 제공한다.
5. 기존 물리 컬럼 monster_attack_events.revivedHp는 테이블 재작성 없이 유지할 수 있다. Kotlin entity에서는 필요하면 @ColumnInfo(name = "revivedHp")로 emergencyRecoveredHp에 매핑하되 이전 attack row를 재생하거나 상태이상으로 변환하지 않는다.
6. 기존 Room 테스트 관례에 맞춰 DAO idempotency 테스트와 12→13 migration 테스트를 추가한다. migration 후 기존 task, completion, character, combat 데이터가 유지되고 새 테이블이 비어 있으며 unique 제약이 동작하는지 검증한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.StatusEffectDaoTest" --tests "com.todoquest.data.local.TodoQuestDatabaseMigrationTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
~~~

## 검증 절차

1. migration 테스트를 먼저 추가하고 version 12 schema에서 실패하는지 확인한다.
2. SQLite master 및 Room schema 검증으로 두 새 테이블의 PK/index/not-null/default를 확인한다.
3. 동일 occurrence credit 두 번 삽입은 첫 번째만 성공하고, 같은 task의 다른 occurrenceDate와 새 revision은 성공하는지 확인한다.
4. AC를 실행하고 phase index의 step 1을 completed와 한국어 summary로 갱신한다.

## 금지사항

- destructive migration, fallbackToDestructiveMigration, DROP TABLE을 사용하지 마라. 이유: 기존 사용자 데이터 보존이 필수다.
- 기존 완료 로그나 combat event를 중상 데이터로 소급 변환하지 마라. 이유: 과거 이벤트를 재처리하면 중복 효과가 발생한다.
- UI 또는 ViewModel이 DAO를 직접 호출할 API를 만들지 마라. 이유: 저장 변경은 Repository/UseCase 경계를 지켜야 한다.
- 기존 schema JSON이나 migration 테스트를 삭제하지 마라.
