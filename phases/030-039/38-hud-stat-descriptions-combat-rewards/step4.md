# Step 4: award-combat-rewards-transactionally

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatRewardPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterProgressionPolicy.kt`
- `/app/src/main/java/com/todoquest/data/local/CombatDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

Repository 테스트를 먼저 추가한 뒤 player attack 적용 transaction에 전투 보상을 연결한다.

PENDING event의 `combatRewardVersion`이 `0`이면 기존 피해·처치 회복·Stage 전진만 처리하고 reward snapshot은 0으로 남긴다. 현재 version `1`이면 대상 몬스터의 저장 level·grade와 공격 처리 직전 실제 장착 modifier에서 계산한 `GOLD_GAIN_BONUS`로 `CombatRewardPolicy`를 호출한다. 알 수 없는 양수 version은 event를 PENDING으로 보존한 채 명확히 실패시킨다.

version 1 공격은 nonlethal에도 hit XP를 지급한다. lethal이면 hit XP와 kill bonus XP·gold를 함께 지급한다. `CharacterProgressionPolicy.awardXp`로 level과 미배분 포인트 invariant를 유지하고 gold를 exact addition한다. XP로 MAX_HP가 달라지면 공격 직전 old/new 파생값으로 현재 HP 비율을 한 번 보존한다. lethal에서는 그 결과에 새 파생 `HP_RECOVERY`를 적용하고 최대 HP로 clamp한다.

monster HP update, event APPLIED와 reward operand/result snapshot, character profile/current state, 처치 시 next monster와 Stage progress를 하나의 Room transaction에서 저장한다. `CombatTransition.PlayerAttack.before`는 지급 전 상태, `after`는 지급·HP 비율·회복·Stage 전진이 모두 반영된 상태다. APPLIED 중복 조회는 저장된 snapshot만 반환하고 캐릭터·HP·Stage를 다시 변경하거나 transition을 방출하지 않는다.

테스트는 nonlethal hit, NORMAL/ELITE/BOSS kill, level-up과 stat point, nonlethal HP ratio, lethal ratio 후 recovery, max level total XP, gold bonus, 동시·반복 요청, processPending 순서, legacy version 0, unknown version, application 재시작을 포함한다. SQLite abort trigger 등으로 character profile write를 강제 실패시켜 event·monster HP·profile·current HP·Stage 전체 rollback과 재시도 1회 지급도 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.CombatRewardPolicyTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --tests "com.todoquest.domain.CharacterProgressionPolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. 각 reward·level-up·rollback 테스트를 production transaction 변경보다 먼저 작성하고 실패를 확인한다.
2. AC를 실행해 공격 key당 XP·gold·HP recovery가 정확히 한 번인지 확인한다.
3. AGENTS.md의 occurrence 멱등성과 UI→Repository 경계, 파생 stat 비저장 원칙을 검토한다.
4. task index의 step 4를 `completed`로 바꾸고 transaction 순서와 idempotency 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- combat reward를 `RewardLedger`에 두 번째 occurrence reward로 삽입하지 마라. 이유: player attack event가 별도 멱등 ledger다.
- APPLIED event를 현재 balance config로 재계산하지 마라. 이유: 저장된 지급 snapshot이 확정 결과다.
- level-up MAX_HP 변경과 처치 회복을 서로 다른 transaction으로 나누지 마라. 이유: 중간 실패 시 HP와 경제 상태가 불일치한다.
- 기존 테스트를 깨뜨리지 마라.
