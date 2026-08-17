# Step 6: integrate-combat-reward-v2

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt
- /app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt
- /app/src/main/java/com/todoquest/data/local/CombatEntities.kt
- /app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt
- /app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt
- /app/src/test/java/com/todoquest/domain/CombatUseCaseTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step5.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

Repository test를 먼저 작성한다. RoomTaskRepository가 신규 COMBAT_ATTACK completion의 player attack outbox에 CURRENT_VERSION 2를 snapshot한다. RoomCombatRepository는 v0이면 무보상, v1이면 old balance, v2이면 new balance를 event의 combatRewardVersion으로 선택한다. 현재 config version만 허용하는 기존 check를 supported catalog version check로 교체한다.

기존 PENDING v1 event는 update 후에도 1/10/5 공식으로 적용하고, 신규 v2 event는 3/20/15 공식으로 적용한다. APPLIED event는 hitXpAward, killBonusXpAward, killGoldAward와 operand snapshot만 반환하고 Character·Monster·Stage를 다시 변경하거나 transition을 replay하지 않는다. 알 수 없는 version은 event를 PENDING으로 남기며 원천 상태를 부분 갱신하지 않는다.

XP·gold·level/unspent points·old/new MAX_HP ratio·victory recovery·monster HP·Stage·event snapshot은 기존 하나의 Room transaction을 유지한다. schema와 migration은 변경하지 않는다. 같은 occurrence 재처리, concurrent reconciliation과 process recreation을 검증한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.CombatUseCaseTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 신규 v2, 기존 v1 PENDING, APPLIED idempotency test를 먼저 실패시킨다.
2. event creation과 version별 적용을 구현한다.
3. transaction rollback·reconciliation test를 포함해 AC를 실행한다.
4. step 6을 completed와 한국어 summary로 갱신한다.

## 금지사항

- 기존 PENDING v1 row를 v2로 migration하지 마라. 이유: event version은 생성 시 확정된 경제 계약이다.
- APPLIED reward를 재계산하지 마라. 이유: 저장 snapshot이 멱등 원천이다.
- 보상 실패를 일정 완료 rollback으로 연결하지 마라. 이유: completion과 combat outbox의 best-effort 경계를 유지해야 한다.
- Room version을 올리지 마라. 이유: 필요한 reward version column은 이미 존재한다.
- 기존 테스트를 깨뜨리지 마라.
