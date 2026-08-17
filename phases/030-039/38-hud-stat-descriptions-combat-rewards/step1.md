# Step 1: define-level-scaled-combat-reward-policy

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/model/CompletionResult.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterProgressionPolicy.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 step 0의 전투 보상 공식을 고정한 뒤 domain model과 policy를 구현한다.

`CombatReward`는 `hitXpAward: Long`, `killBonusXpAward: Long`, `killGoldAward: Long`을 갖고 `totalXpAward`를 exact addition으로 제공한다. `CombatRewardPolicy.rewardFor(monsterLevel, monsterGrade, isKill, goldGainBonusBp, monsterConfig, characterConfig): CombatReward`를 추가한다. 대상 level `1..55`, 세 grade, gold bonus `0..5,000bp`를 검증하고 `MonsterBalanceConfig`의 기존 `gradeRewardMultipliersBp`를 재사용한다. level band와 base 값은 config의 versioned balance 값으로 노출하되 기본값은 승인 공식과 정확히 같아야 한다.

`CompletionRewardMode`는 최소 `TODO_COMPLETION`, `COMBAT_ATTACK`을 갖고 `CompletionResult`가 mode를 노출하게 한다. 기존 호출부가 명시적으로 적응하도록 모호한 Boolean으로 대체하지 않는다.

`PlayerAttackSnapshot`에는 지급 당시의 `hitXpAward`, `killBonusXpAward`, `killGoldAward`, `rewardGradeMultiplierBp`, `rewardGoldGainBonusBp`, `combatRewardVersion`을 추가한다. `combatRewardVersion == 0`은 legacy 무보상 attack이며, 현재 version은 `1`이다. snapshot의 total XP는 두 XP 필드의 합으로 계산한다.

테스트는 level `1/10/11/50/51/55`, NORMAL/ELITE/BOSS, nonlethal/lethal, gold bonus `0/5,000bp`, 최대값과 잘못된 입력을 포함한다. level 1 NORMAL 처치는 hit `1`, 추가 XP `10`, gold `5`; level 55 BOSS 처치는 hit `6`, 추가 XP `80`, bonus 전 gold `40`이어야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CombatRewardPolicyTest" --tests "com.todoquest.domain.CharacterProgressionPolicyTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterStatsCalculatorTest" --console=plain
git diff --check
```

## 검증 절차

1. policy unit test를 production 코드보다 먼저 작성하고 기존 코드에서 실패하는지 확인한다.
2. AC를 실행해 정수 내림, grade 배율, gold bonus, total XP exact 합과 기존 성장 invariant를 확인한다.
3. step 0 문서 공식과 public domain 시그니처가 일치하는지 검토한다.
4. task index의 step 1을 `completed`로 바꾸고 새 model·policy와 골든 값을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `Float`나 `Double`로 보상을 계산하지 마라. 이유: 플랫폼과 재처리 시 동일한 정수 결과가 필요하다.
- task 난이도·정시 multiplier·일일 효율을 전투 보상 공식에 섞지 마라. 이유: 사용자가 몬스터 level·grade 기반 대체 보상을 선택했다.
- 캐릭터나 Room 상태를 policy에서 변경하지 마라. 이유: 이 step은 Android 비의존 순수 domain 계산만 담당한다.
- 기존 테스트를 깨뜨리지 마라.
