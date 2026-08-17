# Step 1: 캐릭터 스탯 엔진 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterProfile.kt`
- `/phases/020-029/25-character-progression-foundation/index.json`
- `/docs/PRD.md`와 `/docs/ADR.md`의 step 0 변경 내용

## 작업

순수 Kotlin unit test를 먼저 작성해 실패를 확인한 다음 Android·Room·시계·난수에 의존하지 않는 캐릭터 스탯 엔진을 구현한다.

다음 시그니처 수준의 도메인 계약을 `domain/model`과 `domain/usecase`에 둔다.

- `PlayerCharacter(id: Long, totalXp: Long, currentGold: Long, baseStats: CharacterBaseStats, unspentStatPoints: Int, hasUsedFreeStatReset: Boolean)`
- `CharacterBaseStats(strength: Int, vitality: Int, focus: Int, willpower: Int)`
- `CharacterCurrentState(characterId: Long, currentHp: Int, balanceVersion: Int, updatedAtEpochMillis: Long)`
- `DerivedStats`는 설계 문서의 고정형 4개와 bp 4개를 모두 포함한다.
- `StatType`, `DerivedStatType`, `StatTarget`, `ModifierType`, `EquipmentSlot`, `EquipmentRarity`를 문서 계약대로 정의한다.
- `EquipmentStatModifier`, `TemporaryStatEffect`, `StatCalculationInput`, `CharacterStatBalanceConfig`를 정의한다.
- `DerivedStatsCalculator.calculate(input, config): DerivedStats`와 modifier/부위/등급 validation을 구현한다.
- `CombatCalculator`는 일반·치명타 피해, 상태 적용 확률, 정수 roll 판정과 MAX_HP 변경 시 HP 비율 유지를 제공한다.

모든 계수와 상한은 immutable `CharacterStatBalanceConfig.version = 1`에 둔다. 기본 스탯 기여, 장비 고정값, 장비/패시브/일시 효과 비율 버킷, 최종 clamp와 내림 순서는 문서와 동일해야 한다. `Long` 중간값의 오버플로 가능성을 테스트하고 입력 상한을 벗어난 source는 명시적으로 거부한다.

테스트 클래스는 최소한 다음을 포함한다.

- `DerivedStatsCalculatorTest`: Lv1/Lv10/Lv30/Lv50 골든 수치, 버킷 합산, 상한·디버프, 회복 동적 상한
- `CombatCalculatorTest`: 방어 점감, 최소 피해, 치명타 단계별 내림, 상태 확률, HP 비율과 0 HP 유지
- `EquipmentModifierValidatorTest`: 부위·등급·affix 수, target/type 허용 조합, 펫 축소 범위

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.domain.DerivedStatsCalculatorTest" --tests "com.todoquest.domain.CombatCalculatorTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest"
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. 테스트를 구현보다 먼저 추가하고 대상 테스트가 실패하는지 확인한다.
2. AC를 실행하고 설계 문서의 골든 수치를 독립 재계산한다.
3. Android/Room import와 `Float`/`Double` 계산이 도메인 엔진에 없는지 확인한다.
4. task index step 1을 `completed`로 변경하고 생성 모델과 계산기를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 파생 능력치를 저장 가능한 entity로 만들지 마라. 이유: 원천 상태 변경 뒤 snapshot 불일치가 생긴다.
- 전투 공식에 `Float`, `Double` 또는 플랫폼 기본 난수를 사용하지 마라. 이유: 정수 재현성과 확정 ledger 계약을 지켜야 한다.
- 장비 Room 테이블이나 장착 UI를 만들지 마라. 이유: 이번 phase는 장비 도메인 계약까지만 승인됐다.
- 골든 수치를 설명 없이 바꾸지 마라. 이유: 계산 계약의 실행 가능성 기준이다.
- 기존 테스트를 깨뜨리지 마라.

