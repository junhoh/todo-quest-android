# Step 5: version-combat-reward-policy

## 읽어야 할 파일

- /AGENTS.md
- /docs/game-design/character-stats/todo-combat-rewards.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/domain/model/Monster.kt
- /app/src/main/java/com/todoquest/domain/usecase/CombatRewardPolicy.kt
- /app/src/test/java/com/todoquest/domain/CombatRewardPolicyTest.kt
- /app/src/test/java/com/todoquest/domain/MonsterSpeciesPolicyTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step0.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

순수 Kotlin test를 먼저 작성해 reward version 1과 2를 동시에 고정한다. CombatRewardBalanceConfig와 CombatRewardBalanceCatalog를 domain에 추가한다. catalog version 1은 base 1/10/5, version 2는 3/20/15이고 두 version 모두 level band 10/5/10이다. CURRENT_VERSION은 2다. 알 수 없는 version은 IllegalArgumentException으로 거부한다.

MonsterBalanceConfig의 combatRewardVersion 기본값을 2로 바꾸되 monster stats balance version 1, grade reward multiplier와 level/stage 계약은 변경하지 않는다. CombatRewardPolicy는 명시적 reward version 또는 CombatRewardBalanceConfig를 받는 overload를 추가하고 기존 호출 signature는 current version으로 위임해 step 6 전에도 전체 source가 compile되게 한다.

v2 golden은 level 1 NORMAL nonlethal 3 XP, lethal hit 3 + kill 20 = 23 XP와 15골드, level 55 BOSS hit 8 + kill 120 = 128 XP와 80골드다. 최대 5000bp gold bonus는 처치 골드만 50% 올린다. v1 golden 1/11/5와 86/40을 그대로 유지한다. exact arithmetic, invalid level, version과 overflow 경계를 검증한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CombatRewardPolicyTest" --tests "com.todoquest.domain.MonsterSpeciesPolicyTest" --console=plain
git diff --check
~~~

## 검증 절차

1. v1/v2 golden test를 먼저 실패시킨다.
2. versioned config/catalog와 compatible overload를 구현한다.
3. monster 능력치·종족 조회 전후 reward 불변 test를 실행한다.
4. step 5를 completed와 한국어 summary로 갱신한다.

## 금지사항

- v1 값을 v2로 교체하고 version 구분을 제거하지 마라. 이유: 업데이트 시 PENDING v1의 의미를 보존해야 한다.
- monster HP·damage·defense 또는 grade multiplier를 바꾸지 마라. 이유: 요청 범위는 경제 보상뿐이다.
- floating point로 골드를 계산하지 마라. 이유: snapshot과 재시도의 결정성을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
