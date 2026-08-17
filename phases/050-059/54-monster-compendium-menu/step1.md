# Step 1: project-monster-discovery-history

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/data/local/CombatEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterDiscoveryPolicy.kt`
- `/app/src/test/java/com/todoquest/data/local/CombatDaoTest.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/phases/050-059/54-monster-compendium-menu/step0.md`
- `/phases/050-059/54-monster-compendium-menu/index.json`

## 작업

보존된 전투 이력을 발견 종족 Flow로 투영하는 DAO/Repository 테스트를 먼저 작성해 실패를 확인한 뒤 data layer를 구현한다.

`CombatDao`에 다음 query를 추가한다.

```kotlin
fun observeMonsterInstances(): Flow<List<MonsterInstanceEntity>>
```

query는 `monster_instances` 전체를 `stageNumber`, `encounterNumber` 순으로 관찰한다. 현재 활성 row뿐 아니라 HP가 0인 과거 row도 제외하지 않는다.

`RoomCombatRepository.observeDiscoveredMonsterSpecies()`는 Flow 수집 시작 전에 기존 `commandMutex`와 `initializeCombatLocked()` 경계로 초기 활성 몬스터 생성을 보장하고, DAO Flow의 모든 row를 domain `MonsterInstance`로 변환한 뒤 Step 0의 `MonsterDiscoveryPolicy`에 전달한다. current `MonsterBalanceConfig` v1과 저장된 instance version의 기존 검증을 유지한다. 새 monster instance가 victory transaction에서 생성되면 Room invalidation으로 발견 set이 즉시 갱신되어야 한다.

테스트는 다음을 포함한다.

- fresh database에서 초기 활성 몬스터가 첫 수집부터 발견된다.
- HP가 0인 과거 row와 현재 활성 row가 모두 반영된다.
- 같은 종족의 여러 row는 하나로 축약된다.
- Repository/collector 재생성 뒤 기존 row가 소급 복원된다.
- 수집 중 새 monster row가 생기면 별도 새로고침 없이 Flow가 갱신된다.
- 기존 Database version 14와 export schema 파일은 변하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.CombatDaoTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. DAO와 Repository 테스트를 먼저 추가하고 구현 전 실패를 확인한다.
2. 최소 구현 후 AC 명령을 실행한다.
3. `app/schemas/com.todoquest.data.local.TodoQuestDatabase/14.json`과 database version이 수정되지 않았는지 확인한다.
4. UI가 DAO를 직접 호출하지 않도록 Repository 경계를 확인한다.
5. task index의 step 1을 `completed`로 바꾸고 발견 이력 projection과 무 migration 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 별도 discovery table, entity 또는 Room v15 migration을 만들지 마라. 이유: `monster_instances`가 모든 현재·과거 조우의 결정적 원천 이력이다.
- 과거 HP 0 row를 삭제하거나 query에서 제외하지 마라. 이유: 기존 사용자 발견 진행을 소급 보존해야 한다.
- UI 또는 ViewModel에서 Room entity를 노출하지 마라. 이유: AGENTS.md의 Repository 경계를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
