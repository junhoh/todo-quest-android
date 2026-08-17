# Step 2: 순수 Kotlin 몬스터 도메인 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterStats.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatCalculator.kt`
- `/app/src/test/java/com/todoquest/domain/CombatCalculatorTest.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

CRITICAL 테스트 우선 순서를 지킨다. 먼저 실패하는 순수 Kotlin unit test를 작성하고 다음 도메인 계약을 구현한다.

`domain/model`에 다음을 추가한다.

```kotlin
enum class MonsterType { BALANCED, ATTACK, DEFENSE, BOSS }
enum class MonsterGrade { NORMAL, ELITE, BOSS }

data class MonsterDefinition(
    val id: String,
    val nameKey: String,
    val type: MonsterType,
    val baseMaxHp: Int,
    val baseDamage: Int,
    val baseDefense: Int,
    val hpGrowthPerLevel: Int,
    val damageGrowthPerLevel: Int,
    val defenseGrowthPerLevel: Int,
)

data class MonsterInstance(
    val id: Long,
    val definitionId: String,
    val grade: MonsterGrade,
    val stageNumber: Int,
    val encounterNumber: Int,
    val level: Int,
    val currentHp: Int,
    val balanceVersion: Int,
) {
    val isDefeated: Boolean get() = currentHp == 0
}

data class MonsterStats(val maxHp: Int, val damage: Int, val defense: Int)
```

`MonsterBalanceConfig(version = 1)`에 level `1..55`, definition source 범위, 최종 범위, 기준 base/growth, 유형·등급 bp map, 결합 상한, grade level offset, Stage 10칸, 부활 `2,500bp`, 향후 reward bp `10,000/20,000/40,000`을 한곳에 둔다. map은 외부 변경이 불가능한 복사본으로 노출하고 모든 enum key가 존재하는지 검증한다.

`MonsterStatsCalculator.calculate(definition, grade, level, config)`는 `Math.multiplyExact`/`addExact`와 `Long`을 사용하고 문서의 한 번 내림·최종 clamp를 구현한다. definition id/nameKey 비어 있음, base 최소, growth 음수, level 범위, current HP 불변식을 명시적으로 거부한다.

`MonsterStagePolicy`는 Stage/encounter validation, grade와 유형 순환, `stageLevel + 0/+1/+2`와 상한 55를 결정한다. 기본 catalog는 네 유형 definition을 제공하되 사용자 표시 문자열을 domain에 하드코딩하지 않고 안정적인 `nameKey`만 사용한다.

`CombatCalculator`의 기존 비율 피해를 재사용한다. 별도 순수 정책으로 현재 HP 피해, 몬스터 처치 clamp, 플레이어 치명 피해 시 25% 즉시 부활, 몬스터 처치 후 `HP_RECOVERY` clamp를 제공한다. 기존 character 피해 공식과 반올림 순서를 바꾸지 않는다.

테스트는 최소한 다음을 포함한다.

- 기준 레벨 `1/10/30/50/55`와 네 유형·세 등급, 결합·절대 상한.
- Stage 10칸 grade, 3유형 순환, Stage offset, 몬스터 레벨 `51/52/55` 경계.
- 현재 균형 플레이어 기준 NORMAL `3~5`, ELITE `6~9`, BOSS `12~18`의 ceil 공격 수 골든 값.
- 몬스터 공격이 플레이어 최대 HP 목표 범위에 드는 대표 Lv1/10/30/50 사례.
- 최소 피해, defense 200, 순서 고정, 매우 큰 허용 입력에서 Long 비초과.
- `currentHp == 0` 파생 패배와 25% 부활의 floor/min 1.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.Monster*" --tests "com.todoquest.domain.CombatCalculatorTest"
git diff --check
```

## 검증 절차

1. 테스트가 구현 전 실패하고 구현 후 통과했는지 확인한다.
2. Android, Room, 시계, 난수 의존성이 도메인 계산기에 없는지 확인한다.
3. phase index의 step 2를 완료 처리하고 생성 모델·계산기·정책을 한국어 `summary`로 기록한다.

## 금지사항

- Room entity나 Repository를 추가하지 마라. 이유: 이 step은 순수 도메인만 담당한다.
- 별도 공격력, 몬스터 치명타나 스킬 배율을 추가하지 마라. 이유: 세 능력치 제한을 지켜야 한다.
- 전투 공식에 부동소수점을 사용하지 마라. 이유: seed와 무관하게 결과가 결정적이어야 한다.
- 기존 테스트를 깨뜨리지 마라.
