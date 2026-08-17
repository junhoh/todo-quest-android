# Step 0: define-status-effect-domain

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /docs/game-design/character-stats/character-stat-formulas.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /app/src/main/java/com/todoquest/core/AppClock.kt
- /app/src/main/java/com/todoquest/domain/model/CharacterStats.kt
- /app/src/main/java/com/todoquest/domain/model/Monster.kt
- /app/src/main/java/com/todoquest/domain/usecase/DerivedStatsCalculator.kt
- /app/src/main/java/com/todoquest/domain/usecase/MonsterCombatPolicy.kt
- /app/src/test/java/com/todoquest/domain/DerivedStatsCalculatorTest.kt
- /app/src/test/java/com/todoquest/domain/MonsterCombatPolicyTest.kt

## 작업

테스트를 먼저 작성한 뒤, 다른 상태이상을 추가할 수 있는 일반화된 도메인 모델과 중상 정책을 구현한다.

1. StatusEffectType.SEVERE_INJURY와 활성 상태를 표현하는 CharacterStatusEffect를 추가한다. 모델에는 정의 버전, 적용 시각, 만료 시각, 남은 정상 완료 횟수, 활성 여부, 갱신 revision, 마지막 mutation/event 식별자가 포함되어야 한다. 시간 값은 프로젝트의 Instant/epoch 변환 관례를 따른다.
2. 상태이상별 규칙을 한 곳에 두는 버전형 definition catalog 또는 StatusEffectPolicy를 추가한다. 중상의 정의는 24시간, 서로 다른 occurrence 정상 완료 3회, 최대 체력과 공격력 각각 -2_000bp, 응급 회복률 5_000bp이다.
3. TemporaryStatEffect가 만료 시각과 남은 trigger를 동시에 가질 수 있도록 불변조건을 확장한다. 둘 다 없는 효과는 허용하지 않고, 이미 사용하는 단일 조건 효과와의 호환성을 유지한다.
4. 중상 definition을 MAX_HP와 ATTACK의 서로 다른 stacking key를 가진 temporary modifier로 변환한다. DerivedStatsCalculator의 기존 basis-point 정수 계산 및 내림 정책을 재사용하며 결과는 기존 max HP/attack 최소값 1을 보장한다. 기본 스탯과 장비 modifier는 변경하지 않는다.
5. MonsterCombatPolicy.playerHpAfterDamage는 치명 피해 시 HP를 0으로 clamp하고 wasLethal=true만 반환하도록 바꾼다. 기존 revivedHp와 25% 즉시 회복 정책은 제거하고, 응급 회복 계산은 상태이상 정책에서 max(1, effectiveMaxHp * 50 / 100)로 제공한다.
6. 정확히 0, 0 미만, 낮은 스탯, 20% 정수 내림, 기본 스탯 불변, 이미 중상인 상태의 modifier 비중첩을 순수 Kotlin 단위 테스트로 고정한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.StatusEffectPolicyTest" --tests "com.todoquest.domain.DerivedStatsCalculatorTest" --tests "com.todoquest.domain.MonsterCombatPolicyTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 새 테스트가 기존 부활 동작을 기대할 때 먼저 실패하는지 확인한 후 구현한다.
2. 중상 modifier를 넣은 계산과 넣지 않은 계산의 CharacterBaseStats 및 입력 장비 modifier가 동일한지 단언한다.
3. 최대 체력과 공격력의 80% 계산 및 응급 회복 50% 계산이 Long 중간값과 프로젝트의 기존 내림 정책을 사용하며 최소 1인지 확인한다.
4. AC를 실행하고 phase index의 step 0을 completed와 한국어 summary로 갱신한다.

## 금지사항

- 중상 수식을 Repository나 UI에 복제하지 마라. 이유: 상태이상 정의와 유효 스탯 계산이 단일 진실 공급원이어야 한다.
- PlayerCharacter.baseStats나 장비 modifier를 20% 낮춘 값으로 저장하지 마라. 이유: 중상 해제 시 원래 스탯을 손실 없이 복원해야 한다.
- 경험치, 골드, 할 일 보상 modifier를 중상에 추가하지 마라. 이유: 요구사항은 최대 체력과 공격력에만 영향을 준다.
- 사용자 노출 문구에 부활 또는 되살아남을 새로 추가하지 마라.
- 기존 테스트를 삭제하거나 완화하지 마라.
