# Step 4: 플레이어 공격 전투 transaction 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/MonsterStats.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStatsCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCharacterRepositoryTest.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

실제 생성 경로를 확인해 도메인 `CombatRepository` 계약과 `data/mapper`, `data/repository/RoomCombatRepository`를 구현한다. Repository 테스트를 먼저 작성한다.

공개 계약은 최소한 다음 기능을 제공한다.

```kotlin
interface CombatRepository {
    fun observeCombat(): Flow<CombatSnapshot>
    suspend fun processPlayerAttack(taskId: Long, occurrenceDate: LocalDate): PlayerAttackResult
    suspend fun processPendingPlayerAttacks(): Int
    suspend fun reconcileOverdue(now: Instant): CombatReconciliationResult
}
```

이 step에서는 observe, 초기화, player attack 처리와 pending drain만 구현하고 overdue monster attack은 다음 step에서 완성해도 된다.

첫 접근 시 Room transaction 안에서 다음을 수행한다.

- character profile/current state를 기존 default 정책으로 원자 생성 또는 로드한다.
- combat progress가 없으면 현재 player level을 stageLevel로 잠그고 Stage 1 encounter 1의 NORMAL monster를 full HP로 만들며 `lastReconciledAtEpochMillis = clock.now()`로 설정한다.
- definition/type/grade/level에서 최종 MonsterStats를 계산하며 max HP·damage·defense snapshot은 DB에 쓰지 않는다.

pending player attack 처리 transaction은 unique occurrence row를 다시 확인하고 다음 순서를 고정한다.

1. 저장된 source attack·critical·MOMENTUM snapshot으로 raw damage를 계산한다. MOMENTUM을 attack에 bp로 적용한 뒤 저장된 seed에서 `0..9999` roll을 만들고 critical이면 기존 치명타 raw damage 내림을 적용한다.
2. 활성 몬스터의 계산된 defense에 기존 `CombatCalculator.damageAfterDefense`를 적용한다.
3. current HP를 `max(0, currentHp - finalDamage)`로 저장하고 event에 모든 operand/result, HP 전후와 처리 시각을 확정한다.
4. HP가 0이면 기존 인스턴스를 보존하고 player `HP_RECOVERY`를 현재 max HP까지 한 번 적용한다.
5. encounter 1~9 처치면 같은 stageLevel로 다음 slot을 만들고, 10 처치면 Stage를 증가시키고 공격 event의 source player level을 새 stageLevel로 잠가 encounter 1을 만든다.
6. active pointer와 character current HP 변경을 같은 transaction에서 저장한다.

seed source는 interface로 주입해 테스트에서 고정한다. 실제 event에는 seed와 확정 roll·결과를 저장하므로 Kotlin Random 구현 변경 뒤에도 과거 결과를 재계산하지 않는다. 이미 `APPLIED`인 event는 상태와 HP를 변경하지 않고 기존 결과를 반환한다. malformed/unknown definition이나 version은 조용히 덮어쓰지 말고 명확히 실패시켜 pending을 보존한다.

Repository 테스트는 앱 재시작 복원, 중복/동시 처리, normal/critical, defense, monster 처치, HP recovery, encounter 전진, boss 처치 Stage 증가·새 level lock, pending 순서와 파생 스탯 비저장을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest"
git diff --check
```

## 검증 절차

1. Repository 테스트를 실행한다.
2. player attack event, monster HP, progress와 character recovery가 하나의 transaction인지 확인한다.
3. phase index의 step 4를 완료 처리하고 Repository·Stage 전이를 한국어 `summary`로 기록한다.

## 금지사항

- Compose나 ViewModel을 추가하지 마라. 이유: backend-only 범위다.
- 처리된 event를 현재 config로 소급 재계산하지 마라. 이유: 확정 ledger 결과를 보존해야 한다.
- 몬스터 처치 보상으로 XP·골드를 추가하지 마라. 이유: 경제 정책은 후속 범위다.
- 기존 테스트를 깨뜨리지 마라.
