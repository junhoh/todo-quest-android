# Step 4: apply-difficulty-player-attacks

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/mapper/CombatEntityMapper.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatRewardPolicy.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCombatRepositoryTest.kt`
- `/app/src/test/java/com/todoquest/domain/CombatRewardPolicyTest.kt`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step1.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step2.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/step3.md`
- `/phases/050-059/52-notification-difficulty-calendar-polish/index.json`

## 작업

전투 Repository 테스트를 production보다 먼저 작성한다. `RoomCombatRepository`, `CombatEntityMapper`와 attack snapshot 경계만 수정한다.

PENDING player attack 처리에서 `taskDifficultyBalanceVersion`을 검증한다. version `0`은 source difficulty가 null 또는 역사적 값이어도 100% 중립으로 처리한다. version `1`은 valid EASY/MEDIUM/HARD를 요구하고 쉬움/보통/어려움 `100%/150%/200%`를 적용한다. 알 수 없는 version이나 잘못된 difficulty는 전체 Room transaction을 rollback해 PENDING source와 character/monster 상태를 보존한다.

피해 계산 순서는 다음으로 고정한다.

1. event의 `sourceAttack`에 `sourceMomentumBp`를 기존 정수 공식으로 적용한다.
2. 결과에 `TaskDifficultyCombatPolicy.scaleDamage`를 적용한다.
3. critical이면 difficulty-scaled attack에 critical damage bp를 적용한다.
4. 기존 `CombatCalculator.damageAfterDefense`를 적용한다.

Combat Reward v0/v1/v2 base와 monster level/grade 계산은 바꾸지 않는다. combat reward version이 0이면 계속 무보상이다. 보상이 있는 새 difficulty version 1 attack은 base `hitXpAward`와 `killBonusXpAward` 각각에 `scaleXp`를 적용한 값을 character와 applied event에 저장한다. kill gold와 `rewardGoldGainBonusBp` 결과는 난이도로 변경하지 않는다. badge와 Character Flow는 저장된 scaled snapshot을 그대로 사용한다.

`PlayerAttackSnapshot`과 mapper에는 source difficulty와 difficulty balance version을 노출하되 source-compatible 기본값을 제공한다. 이미 APPLIED인 row는 저장된 raw/final damage와 XP/gold snapshot을 반환하고 새 정책으로 재계산하지 않는다. target monster/Stage advance, HP ratio, kill recovery와 equipment/status modifier transaction은 그대로 유지한다.

golden test는 다음을 포함한다.

- level 1 NORMAL nonlethal hit XP: EASY `3`, MEDIUM `4`, HARD `6`.
- level 1 NORMAL kill total XP: EASY `23`, MEDIUM `34`, HARD `46`; kill gold는 모두 `15`(장비 bonus 없음).
- source attack 100, momentum 0, non-critical, defense 0: raw/final damage `100/150/200`.
- APPLIED 재호출은 XP, gold, damage, HP, Stage를 다시 변경하지 않는다.
- legacy difficulty version 0 PENDING은 task 난이도와 무관하게 기존 결과다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.domain.TaskDifficultyCombatPolicyTest" --tests "com.todoquest.domain.CombatRewardPolicyTest" --tests "com.todoquest.domain.CombatCalculatorTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 세 난이도 damage/reward와 legacy/APPLIED test를 먼저 실패시킨다.
2. PENDING transaction과 mapper를 최소 수정한다.
3. character·monster·event가 같은 transaction에서 한 번만 바뀌는지 확인하고 AC를 실행한다.
4. step 4를 `completed`와 한국어 summary로 갱신한다.

## 금지사항

- kill gold에 난이도 배율을 적용하지 마라. 이유: 사용자가 선택한 범위는 피해와 XP이며 경제 gold 공식은 불변이다.
- monster stats, grade multiplier, critical chance 또는 equipment modifier를 재조정하지 마라. 이유: 난이도 source 이외의 balance는 범위 밖이다.
- APPLIED event를 재계산하지 마라. 이유: 저장 snapshot과 멱등성 계약을 위반한다.
- unsupported version을 neutral fallback으로 처리하지 마라. 이유: 알 수 없는 의미로 XP나 피해를 확정하면 데이터 무결성이 깨진다.
- 기존 테스트를 깨뜨리지 마라.
