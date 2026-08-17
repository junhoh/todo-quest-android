# 구현 계약과 검증

[캐릭터 스탯 설계 인덱스로 돌아가기](../character-stats-design.md)

> 문서 지위: PRD의 MVP 제외 범위를 바꾸지 않는다. Post-MVP Character Growth v1, Monster Combat v1 backend와 ADR-013 Equipment Shop and Inventory v1의 실제 구현, 그리고 아직 승인하지 않은 전투 후속 범위를 함께 기록한다.

## 구현용 데이터 계약

이 절의 public model과 계산 interface는 순수 Kotlin 계약으로 구현됐다. 도메인 모델은 `domain/`, entity와 DAO는 `data/local/`, transaction을 조정하는 구현은 `data/repository/` 또는 명확한 UseCase에 둔다는 기존 레이어 규칙을 유지한다. 장비 modifier는 Room v8에 보존된 실제 `character_equipment` source와 Character·Task·Combat 계산, Shop·Inventory 사용자 기능에 연결됐다. 기존 검증용 `EquipmentSlot.HEAD/TOP/BOTTOM/SHOES/ACCESSORY/WEAPON/PET`는 아래 일곱 canonical type/slot으로 교체했다.

```kotlin
data class PlayerCharacter(
    val id: Long,
    val totalXp: Long,
    val currentGold: Long,
    val baseStats: CharacterBaseStats,
    val unspentStatPoints: Int,
    val hasUsedFreeStatReset: Boolean,
)

data class CharacterBaseStats(
    val strength: Int,
    val vitality: Int,
    val focus: Int,
    val willpower: Int,
)

data class CharacterCurrentState(
    val characterId: Long,
    val currentHp: Int,
    val balanceVersion: Int,
    val updatedAtEpochMillis: Long,
)

data class DerivedStats(
    val maxHp: Int,
    val attack: Int,
    val defense: Int,
    val criticalChanceBp: Int,
    val criticalDamageBp: Int,
    val statusResistanceBp: Int,
    val hpRecovery: Int,
    val goldGainBonusBp: Int,
)

enum class StatType {
    STRENGTH, VITALITY, FOCUS, WILLPOWER,
}

data class StatAllocation(
    val strength: Int = 0,
    val vitality: Int = 0,
    val focus: Int = 0,
    val willpower: Int = 0,
) {
    val totalPoints: Int

    init {
        require(strength >= 0)
        require(vitality >= 0)
        require(focus >= 0)
        require(willpower >= 0)
        totalPoints = Math.addExact(
            Math.addExact(strength, vitality),
            Math.addExact(focus, willpower),
        )
    }

    fun valueOf(type: StatType): Int = when (type) {
        StatType.STRENGTH -> strength
        StatType.VITALITY -> vitality
        StatType.FOCUS -> focus
        StatType.WILLPOWER -> willpower
    }
}

enum class DerivedStatType {
    MAX_HP,
    ATTACK,
    DEFENSE,
    CRITICAL_CHANCE,
    CRITICAL_DAMAGE,
    STATUS_RESISTANCE,
    HP_RECOVERY,
    GOLD_GAIN_BONUS,
}

sealed interface StatTarget {
    data class Base(val type: StatType) : StatTarget
    data class Derived(val type: DerivedStatType) : StatTarget
}

enum class ModifierType {
    FLAT, PERCENT_ADD,
}

data class EquipmentStatModifier(
    val itemId: Long,
    val target: StatTarget,
    val type: ModifierType,
    val amount: Int,
)

data class TemporaryStatEffect(
    val effectId: Long,
    val target: StatTarget,
    val type: ModifierType,
    val amount: Int,
    val stackingKey: String,
    val startedAtEpochMillis: Long,
    val endsAtEpochMillis: Long?,
    val remainingTriggers: Int?,
)

data class StatCalculationInput(
    val level: Int,
    val baseStats: CharacterBaseStats,
    val equipmentModifiers: List<EquipmentStatModifier>,
    val passiveAndSetModifiers: List<EquipmentStatModifier>,
    val temporaryEffects: List<TemporaryStatEffect>,
)

fun interface DerivedStatsCalculator {
    fun calculate(
        input: StatCalculationInput,
        config: CharacterStatBalanceConfig,
    ): DerivedStats
}
```

`StatAllocation`은 네 stat의 0 이상 증가량과 overflow-safe 합계를 한 값으로 묶는 typed batch command다. `AllocateStatPointsUseCase.invoke(allocation)`은 이 값을 `CharacterRepository.allocateStatPoints(allocation)`에 그대로 전달한다. `StatAllocationPolicy`는 0 배분, 최신 미배분 포인트 부족, `STRENGTH → VITALITY → FOCUS → WILLPOWER` 안정 순서의 투자 상한을 순수 Kotlin으로 판정한다.

Character 화면의 `-/+`는 `CharacterViewModel`의 `StatAllocation` draft만 변경한다. draft는 Room에 저장되지 않으며 저장 전에는 확정·pending·예상 기본 능력치와 남은 포인트만 표시한다. current/max HP와 8개 파생값은 preview하지 않고 마지막으로 확정된 `CharacterSnapshot`을 유지한다. 저장 실패나 Repository 재검증 실패에는 draft를 유지하고, 저장 중 또는 pending이 있는 동안 stat reset을 차단한다.

### Equipment Shop and Inventory v1 public 타입

gameplay type과 slot은 서로 같은 일곱 값만 사용한다. `ARMOR`, `TOP`, `BOTTOM`, `HEAD`, `PET`은 canonical enum 값이 아니다.

```kotlin
enum class EquipmentType {
    WEAPON, HELMET, CHEST, LEGS, GLOVES, SHOES, ACCESSORY,
}

enum class EquipmentSlot {
    WEAPON, HELMET, CHEST, LEGS, GLOVES, SHOES, ACCESSORY,
}
```

기존 네 `StatType`과 8개 `DerivedStatType`은 변경하지 않는다. 장비 입력에서 agility·민첩 계열은 `FOCUS`, intelligence·지능 계열은 `WILLPOWER`로 정규화하며 별도 원천 스탯이나 Room 컬럼을 만들지 않는다.

`owned_equipment`는 `(characterId, equipmentId)` unique 소유 source이고 quantity가 없다. `character_equipment`는 `(characterId, slot)` unique 실제 장착 source이며, 계산 입력의 `equipmentModifiers`에는 여기서 참조한 장비만 포함한다. Room v8이 보존하는 `character_equipped_items`는 gameplay source가 아니라 외형 fallback으로 남긴다.

`UnequipEquipmentUseCase`는 target `EquipmentSlot`을 `EquipmentRepository.unequipEquipment()`에 전달한다. 성공 시 대상 `character_equipment` row만 제거해 `owned_equipment`는 보존하고, 대상 `character_equipped_items` fallback은 `WEAPON/HELMET/GLOVES/ACCESSORY → null`, `CHEST → top_default`, `LEGS → bottom_default`, `SHOES → shoes_default`로 같은 transaction에서 갱신한다. 다른 appearance slot은 유지하며 gameplay ownership과 appearance source를 합치지 않는다. 활성 상태이상을 포함한 `oldMax/newMax`가 다르면 current HP 비율을 보존하고, 빈 slot은 `UnequipEquipmentResult.AlreadyEmpty`로 source 변경 없이 멱등 성공한다.

### Monster Combat v1 public 타입과 실제 경로

초기 phase 문서가 예시로 사용한 `domain/model/MonsterStats.kt`와 `notification/CombatReconciliationWorker.kt`는 생성되지 않았다. 실제 public 타입과 패키지 경로는 다음과 같다.

| 실제 파일 | package | public 타입 |
|---|---|---|
| `app/src/main/java/com/todoquest/domain/model/Monster.kt` | `com.todoquest.domain.model` | `MonsterType`, `MonsterGrade`, `CombatEventStatus`, `MonsterAttackSkipReason`, `MonsterDefinition`, `MonsterInstance`, `MonsterStats`, `MonsterStatMultipliersBp`, `MonsterBalanceConfig`, `MonsterCatalog` |
| `app/src/main/java/com/todoquest/domain/model/Combat.kt` | `com.todoquest.domain.model` | `StageProgress`, `CombatSnapshot`, `PlayerAttackSnapshot`, `MonsterAttackSnapshot`, `MonsterAttackTrigger`, `CombatEventKey`, `CombatTransition`, 양방향 attack result, `CombatReconciliationResult` |
| `app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt` | `com.todoquest.domain.repository` | `CombatRepository` |
| `app/src/main/java/com/todoquest/domain/usecase/`의 전투 파일 | `com.todoquest.domain.usecase` | `MonsterStatsCalculator`, `MonsterStagePolicy`, `PlayerHpDamageResult`, `MonsterCombatPolicy`, `MissedOccurrencePolicy`, `ReconcileCombatUseCase` |
| `app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt` | `com.todoquest.data.repository` | `CombatSeedSource`, `RandomCombatSeedSource`, `RoomCombatRepository` |
| `app/src/main/java/com/todoquest/background/CombatReconciliationWorker.kt` | `com.todoquest.background` | `CombatReconciliationWorker`, `CombatReconciliationWork` |

`MonsterBalanceConfig(version = 1)`과 `CharacterStatBalanceConfig(version = 1)`이 계산 version을 소유하고, `TodoQuestDatabase(version = 8)`가 영속 schema version을 소유한다. `MonsterDefinition.nameKey`는 backend localization key일 뿐 사용자 표시 이름이 아니며, 현재 Battle Map은 `app/src/main/res/values/strings.xml`의 한국어 문자열 resource로 명시적으로 매핑한다.

- `PlayerCharacter.level`은 저장 필드가 아니다. `totalXp`와 주입된 `CharacterStatBalanceConfig`로 `1..50` 범위에서 계산한다. `totalXp`는 레벨 50 이후에도 누적값을 보존한다.
- `CharacterBaseStats`의 각 투자값은 생성과 갱신 경계에서 `1..60`인지 검증한다. 장비·효과를 합친 `1..99` 제한은 calculator가 별도로 적용한다.
- `DerivedStats`는 immutable 계산 결과다. 확률·배율·보너스율 필드는 이름이 `Bp`로 끝나는 `Int`이고 `10,000bp = 100%` 계약을 따른다.
- `TemporaryStatEffect`는 `endsAtEpochMillis`와 `remainingTriggers` 중 정확히 하나만 non-null이어야 하고 `remainingTriggers`는 양수여야 한다. 이 validation과 calculator 입력은 구현됐지만 효과 영속·복원은 후속 범위다.
- 기본 스탯 target에는 `FLAT`만 허용한다. 고정형 파생 target인 `MAX_HP`, `ATTACK`, `DEFENSE`, `HP_RECOVERY`에는 `FLAT`과 bp 단위 `PERCENT_ADD`를 허용한다. 확률·배율 target인 `CRITICAL_CHANCE`, `CRITICAL_DAMAGE`, `STATUS_RESISTANCE`, `GOLD_GAIN_BONUS`에는 bp 단위 `FLAT`만 허용한다. validator는 이 조합과 새 일곱 부위·등급 범위를 검증하고, 구매 transaction은 type/slot 일치까지 최신 Room source로 다시 검증한다.
- `DerivedStatsCalculator.calculate(input: StatCalculationInput, config: CharacterStatBalanceConfig): DerivedStats`는 Android, Room, 시계와 난수에 의존하지 않는 순수 Kotlin 함수다. 입력 순서에 관계없이 같은 source와 config에는 같은 결과를 반환한다.

### 저장 범위와 숫자 타입

| 분류 | 값 | 현재 계약 |
|---|---|---|
| Room v9 영속 | 기존 XP·골드·기본 스탯·`currentHp`·일정·완료·reward snapshot, appearance·equipped item id, occurrence failure와 몬스터·전투·장비 source를 보존한다. | 구현 완료. `MIGRATION_8_9`은 기존 ledger를 `TODO_COMPLETION`, 기존 player attack을 reward version `0`과 award `0`으로 보존하고 신규 combat reward snapshot column만 추가한다. level, 캐릭터 8개 파생값과 몬스터 `MAX_HP`·`DAMAGE`·`DEFENSE`는 저장하지 않는다. |
| 미승인 후속 Room 영속 | 활성 gameplay 지속 효과와 사망 디버프 | 미구현. 별도 schema와 migration 승인이 필요하다. |
| 매번 계산 | `level`, 8개 `DerivedStats`, 방어 감소율, 일반·치명타 피해 | 순수 Kotlin 계산기는 구현됐다. Character·Task·Combat Repository는 실제 `character_equipment`에서 따라간 modifier만 입력하고 소유만 한 장비와 appearance item은 제외한다. |
| 세션 전용 | 계산 cache, 현지화된 UI 문자열, 아직 확정되지 않은 현재 roll, `CombatTransition`과 animation 진행 상태 | transition은 application-scope `SharedFlow(replay = 0)`와 ViewModel-scope actor에서만 소비하고 Room replay queue로 저장하지 않는다. 재현용 seed와 확정된 roll·피해 event는 Room에 기록한다. |
| 앱 종료 후 보존 | 플레이어·몬스터 `currentHp`, Stage/활성 몬스터, occurrence failure, reconciliation cursor, pending outbox, seed·roll·피해 결과와 `wasLethal`, 장비 소유·slot 장착 | Room v8에 구현됐다. 끝나지 않은 시간형 효과와 남은 trigger 효과 복원은 미구현이다. |

XP, 골드, epoch millis와 재현 seed는 `Long`을 사용한다. 기본 스탯, 미배분 포인트, 고정형 파생값과 bp는 `Int`를 사용하되 곱셈 중간값은 `Long`으로 승격한다. 전투 공식과 확률 판정은 `Float` 또는 `Double`을 사용하지 않고 정수 나눗셈과 명시된 내림 순서를 따른다.

Room schema v3에서 `CharacterProfileEntity`와 `RewardLedgerEntity`의 XP·골드는 Kotlin `Long`으로 전환했고 저장 `level`을 제거했다. Room v4의 `MIGRATION_3_4`는 전투 원천 table을, v5는 appearance, v6은 failure, v7은 gameplay 장비를 추가했고 v8은 누락 자동 failure source만 data-only로 backfill했다. Room v9의 `MIGRATION_8_9`은 `reward_ledger.rewardMode`와 player attack reward version·operand/result snapshot을 기본값으로 추가한다. 기존 ledger·PENDING/APPLIED event·XP·gold·HP·Stage는 갱신하지 않으며 transient transition도 재생하지 않는다. 두 공격 테이블은 같은 occurrence에서도 방향별로 독립적이고, monster attack의 두 trigger는 하나의 monster event key를 공유하며, completion ledger·player attack reward ledger·장비 구매 transaction도 서로 분리된다. 단순 `CharacterProfile` 도메인 타입은 일부 호환·테스트 보조로 남아 있고, 실제 Repository 원천 모델은 `PlayerCharacter`다.

## 파생 능력치 재계산 이벤트

| 이벤트 | 다시 계산할 값 | `currentHp` 처리 | 상태 |
|---|---|---|---|
| 레벨업 | `MAX_HP`, `ATTACK`, `DEFENSE`, `HP_RECOVERY` | 변경 전·후 `MAX_HP`로 HP 비율을 유지한다. | 구현 완료 |
| typed 일괄 스탯 포인트 배분·초기화 | 힘은 `ATTACK`·`CRITICAL_DAMAGE`, 체력은 `MAX_HP`·`DEFENSE`, 집중은 `ATTACK`·`CRITICAL_CHANCE`, 의지는 `STATUS_RESISTANCE`·`HP_RECOVERY` | 최신 Room profile과 실제 장착 modifier로 전체 배분 전후를 계산한다. `MAX_HP`가 바뀌면 한 command에서 HP 비율을 한 번만 유지하고 `0 HP`는 0으로 유지한다. | 구현 완료 |
| 장착·교체 | `character_equipment`에 실제 장착된 modifier target과 기본 스탯의 transitive dependency 전체 | `MAX_HP`가 바뀌면 `RoomEquipmentRepository`의 같은 transaction에서 HP 비율을 유지한다. `0 HP`는 유지하고 저장 실패 시 slot과 HP를 함께 rollback한다. | 구현 완료 |
| 세트·버프·디버프 시작·종료 | target과 그 transitive dependency | `MAX_HP`가 바뀌면 HP 비율을 유지한다. 같은 `stackingKey`는 가장 높은 효과 하나만 활성화하고 재적용은 기본적으로 기간 또는 남은 trigger를 갱신한다. | 미구현 |
| 몬스터 처치 회복 event | 파생값 재계산 없음 | 현재 계산된 `MAX_HP`에 대해 `currentHp = min(currentHp + HP_RECOVERY, MAX_HP)`로 한 번 갱신해 공격·Stage 전진과 함께 저장한다. | 구현 완료 |
| 전투 종료 | 소비된 trigger 효과와 종료 효과를 제거한 뒤 관련 파생값, 마지막으로 `HP_RECOVERY` | 효과 제거로 `MAX_HP`가 바뀌면 먼저 비율을 유지하고, 재계산된 `HP_RECOVERY`를 적용해 `0..MAX_HP`로 clamp하여 저장한다. | 미구현 |

source와 `currentHp`가 함께 바뀌는 command는 같은 transaction 안에서 최신 source를 다시 읽고 변경 전 source의 `oldMax`, 변경 후 source의 `newMax`, 비율 유지된 `newHp`를 순서대로 계산해 함께 저장한다. 일괄 능력치 배분은 네 stat을 모두 적용한 뒤 `oldMax/newMax`를 한 번만 비교하므로 stat별 중간 HP를 저장하지 않는다. profile 또는 current state 쓰기가 실패하면 전체 배분을 rollback한다. `balanceVersion` 변경도 versioned migration에서 같은 절차를 사용한다. `0 HP`는 source 또는 config 변경만으로 전투 가능 상태가 되지 않는다. calculator 결과인 파생값 snapshot은 어떤 이벤트에서도 DB에 저장하지 않는다.

## 밸런스 설정과 확정 결과

모든 기본 공식 계수, 레벨·스탯·파생값 상한, modifier 버킷 범위, 장비 등급·affix 범위, 방어 상수 `100`(`defenseConstant`), 최소 피해율, 보상 multiplier, 반복 원본·일일 효율 구간과 일일 전투 상한은 versioned `CharacterStatBalanceConfig`에 모은다. calculator와 보상·전투 정책에는 이 config를 명시적으로 주입하며 전역 Android resource, Room entity 또는 UI 상수에서 값을 직접 읽지 않는다.

새 config는 새로운 계산과 아직 확정되지 않은 event에만 적용한다. 현재 완료·보상 ledger에는 계산에 사용한 `balanceVersion`, 정시·효율 입력과 실제 지급 결과를 기록하며, 이미 확정된 ledger는 앱 업데이트 뒤 현재 config로 소급 재계산하거나 덮어쓰지 않는다. 플레이어 공격 event도 source character/monster balance version, 재현 seed, 확정 roll과 피해 결과를 Room v8에 기록하며 다시 계산해 덮어쓰지 않는다. 장비 교체도 이미 확정된 RewardLedger나 공격 event를 덮어쓰지 않고 이후 snapshot과 현재 계산에만 반영한다. source schema나 `MAX_HP` 해석이 바뀌는 config 갱신은 명시적 versioned migration으로 처리하되 기존 ledger 결과는 그대로 둔다.

## 골든 수치 검증

예상 공격의 상대 방어는 같은 레벨 표준 몬스터의 `benchmarkDefense = 5 + 2 × level`을 사용한다. 이 값은 콘텐츠 전체를 영구히 고정하는 공식이 아니라 calculator·피해 공식의 실행 가능성을 검증하는 **공식 검증용 초기 기준**이다. 기대 피해는 `일반 피해 × (1 - 치명타 확률) + 치명타 피해 × 치명타 확률`로만 계산하고 표시 단계 전까지 정수 bp와 확정 피해를 사용한다.

| 기준 | HP | 공격 | 방어 | 치명타 | 치명타 피해 | 저항 | 회복 | 골드 보너스 | 표준 방어 | 일반 / 치명타 / 기대 피해 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Lv1, 무장 없음, `5/5/5/5` | 110 | 20 | 8 | `750bp` (7.5%) | `15,250bp` (152.5%) | `375bp` (3.75%) | 7 | `0bp` | 7 | `18 / 28 / 18.75` |
| Lv10, `10/10/9/9`, 일반 장비 | 243 | 51 | 20 | `1,100bp` (11.0%) | `15,750bp` (157.5%) | `900bp` (9.0%) | 14 | `200bp` | 25 | `40 / 64 / 42.64` |
| Lv30, `35/13/21/9`, 희귀 장비 | 484 | 166 | 45 | `2,050bp` (20.5%) | `17,700bp` (177.0%) | `1,400bp` (14.0%) | 23 | `800bp` | 65 | `100 / 177 / 115.785` |

Lv1은 `MAX_HP = 60 + 10 × 5 = 110`, `ATTACK = 5 + 2 × 5 + 5 = 20`, `DEFENSE = 3 + 5 = 8`이다. 방어 7을 상대로 일반 피해는 `floor(20 × 100 / 107) = 18`, 치명타 raw는 `floor(20 × 15,250 / 10,000) = 30`, 방어 적용 치명타는 `floor(30 × 100 / 107) = 28`이다. 따라서 기대 피해는 `18 × 0.925 + 28 × 0.075 = 18.75`다.

### Lv10 계산 전개

- HP `floor((114 + 100 + 10 + 12) × 1.03) = 243`.
- 공격 `floor((14 + 20 + 9 + 2 + 1 + 4) × 1.03) = 51`.
- 방어 `floor((7 + 10 + 1 + 2) × 1.03) = 20`.
- 치명타 `500 + 450 + 50 + 100 = 1,100bp`.
- 치명타 피해 `15,000 + 500 + 50 + 200 = 15,750bp`.
- 저항 `675 + 75 + 150 = 900bp`.
- 일반 피해 `floor(51 × 100 / 125) = 40`.
- 치명타 raw `floor(51 × 15,750 / 10,000) = 80`, 방어 적용 `floor(80 × 100 / 125) = 64`.
- 기대 `40 × 0.89 + 64 × 0.11 = 42.64`.

Lv30은 장비 적용 전 고정 합계가 HP `449`, 공격 `154`, 방어 `42`이므로 각각 `1.08` 버킷을 한 번 적용해 `484`, `166`, `45`가 된다. 방어 65를 상대로 일반 피해는 `floor(166 × 100 / 165) = 100`이고, 치명타 raw `floor(166 × 17,700 / 10,000) = 293`에 방어를 적용하면 `floor(293 × 100 / 165) = 177`이다. 기대 피해는 `100 × 0.795 + 177 × 0.205 = 115.785`다.

참고 기준인 Lv50 균형 무장 없음 `30/30/29/29`는 **HP 654**, 공격 143, 방어 57, 치명타 19.5%, 치명타 피해 165.0%, 저항 21.75%, 회복 40이다. 이는 `MAX_HP = 60 + 6 × 49 + 10 × 30`, `ATTACK = 5 + 49 + 2 × 30 + 29`, `DEFENSE = 3 + floor(49 / 2) + 30`을 그대로 적용한 값이다.

## 순수 Kotlin unit test 우선 checklist

- [x] 8개 파생 공식, 장비·패시브·일시 효과 버킷 합산 순서와 마지막 한 번 내림을 검증한다.
- [x] 기본·파생 상한, 음수 디버프, bp clamp와 동적 회복 상한을 검증한다.
- [x] 방어 점감, 최소 피해, 치명타 단계별 내림과 정수 roll 경계를 검증한다.
- [x] 자동 전투용 고정 seed 재현성과 확정 roll·피해 event를 검증한다.
- [x] `MAX_HP` 증감 때 HP 비율 유지, `0 HP` 유지와 성장 command의 transaction 원자성을 검증한다.
- [x] 레벨 50 cap, 레벨당 2포인트, 다중 레벨업과 첫 무료·후속 유료 초기화를 검증한다.
- [x] typed `StatAllocation`의 0·음수·overflow, 최신 포인트 부족과 안정 순서 stat cap, 다중 stat 단일 저장을 검증한다.
- [x] 일괄 배분에서 실제 장착 modifier를 포함한 전체 배분 전후 `MAX_HP`로 HP 비율을 한 번만 보존하고 `0 HP`·write 실패 rollback을 검증한다.
- [x] 장비 부위·등급 범위와 `StatTarget`·`ModifierType` 허용 조합 validation을 검증한다.
- [x] occurrence별 완료·보상 unique key와 동시 재시도·취소 후 재완료 멱등성을 검증한다.
- [x] 플레이어 공격·실패 공격 event의 독립 unique key와 재시도 멱등성을 검증한다.
- [x] 반복 원본·일일 효율, 정시 multiplier, 연속 완료일·`MOMENTUM`과 일일 `combatEligible` 상한 snapshot을 검증한다.
- [x] 복귀당 3회 피해 상한, 영구 skip, `0 HP 전투 불능 → 중상 → 50% 응급 회복`과 WorkManager reconciliation을 검증한다.
- [x] 신규 자동 마감 failure와 `MISSED_DEADLINE` `APPLIED`·`SKIPPED` event, player HP와 cursor의 transaction 원자성 및 v7→v8 누락 failure 멱등 backfill을 검증한다.
- [x] `CharacterStatBalanceConfig` version 고정, 확정 reward ledger 비소급성과 파생값 비저장을 검증한다.
- [x] `EquipmentType`·`EquipmentSlot` 일곱 값과 `ARMOR/TOP/BOTTOM/HEAD` 호환 mapping, `PET` 거부를 검증한다.
- [x] `owned_equipment` 중복 금지·quantity 부재와 구매의 골드/판매/레벨/중복/type-slot 재검증 transaction을 검증한다.
- [x] 소유권·slot 일치, `CHEST`·`LEGS` 동시 장착, 대상 slot 단독 교체와 `MAX_HP` 변화의 HP 비율 보존 transaction을 검증한다.
- [x] 장비 해제의 소유권 보존, 대상 장착 row·appearance fallback 원자 갱신, `AlreadyEmpty` 멱등성, 상태이상 포함 modifier 제거와 HP 비율 보존을 검증한다.
- [x] 앱 재시작 뒤 실제 `character_equipment` modifier 효과 복원과 appearance 외형 fallback 보존을 검증한다.

## 구현 테스트 상태

| 검증군 | 실제 테스트 | 상태 |
|---|---|---|
| 순수 Kotlin 계산·정책 | `MonsterStatsCalculatorTest`, `MonsterStagePolicyTest`, `MonsterCombatPolicyTest`, `CombatCalculatorTest`, `CombatUseCaseTest` | phase 34 Step 8 `gradlew test` 재검증 통과 |
| Room v4→v8와 transaction | `TodoQuestDatabaseMigrationTest`, `CombatDaoTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest` | v8 migration과 신규 자동 failure·event transaction 회귀 포함 |
| WorkManager orchestration | `CombatReconciliationWorkerTest` | phase 34 Step 8 `gradlew test` 재검증 통과 |
| 장비 순수 계약 | `EquipmentModifierValidatorTest`, `EquipmentComparisonCalculatorTest`, `PurchaseEquipmentPolicyTest` | 일곱 type/slot, 호환 mapping·`PET` 거부, 구매 우선순위와 같은 slot 비교 검증 |
| Room v7·장비 transaction | `TodoQuestDatabaseMigrationTest`, `EquipmentDaoTest`, `RoomEquipmentRepositoryTest` | v1~v6→v7 보존, 18종 seed, unique 소유, `CHEST`/`LEGS`, 구매·장착·해제·HP rollback 검증 |
| 실제 modifier 연결 | `RoomCharacterRepositoryTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest` | 소유-only 제외, Character 파생값, 새 reward/attack snapshot, 현재 전투·회복과 확정 event 비소급 검증 |
| Room v8·자동 마감 정합성 | `TodoQuestDatabaseMigrationTest`, `RoomCombatRepositoryTest` | v1~v7→v8 보존, 완료·수동 failure 제외, 기존 자동 `APPLIED`·`SKIPPED` event backfill, 신규 failure·event·HP·cursor 원자성·멱등성 검증 |
| typed 일괄 능력치 배분 | `StatAllocationPolicyTest`, `RoomCharacterRepositoryTest` | 0·포인트 부족·안정 순서 cap, 최신 Room 재검증, 다중 stat 단일 write, 장비 modifier 기반 HP 비율 1회·0 HP·rollback 검증 |
| Character draft와 저장 UI | `CharacterViewModelTest`, `CharacterScreenTest`, `AppNavigationTest` | 비영속 `-/+` draft, pending·예상값·남은 포인트, 단일 batch 저장, 오류 유지·초기화 차단, 저장 전 HP·파생값 비preview와 commit 뒤 공유 Flow 갱신 검증 |
| Combat Rewards v1 | `CombatRewardPolicyTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest`, `TodoQuestDatabaseMigrationTest` | 레벨·등급 hit/kill XP·gold, 골드 보너스 최종 절삭, occurrence 멱등 transaction, legacy version 0 비소급과 Room v9 보존 검증 |
| EXP HUD·전투 배지·능력치 설명 | `PlayerProgressHudTest`, `BattleMapTest`, `BattleAnimationControllerTest`, `CharacterScreenTest`, `CharacterViewModelTest` | 글자 폭 EXP track·0/100 outline, 실제 보상 600ms 비재생 배지, 고정 stat control과 기본·파생 설명 dialog 검증 |
| Shop·Inventory presentation | `ShopViewModelTest`, `InventoryViewModelTest`, `ShopScreenTest`, `InventoryScreenTest`, `AppNavigationTest` | filter·같은 slot 비교·확인/성공·장착/해제·보유 카드·96/120dp slot·shared renderer 복원·side effect 비재생과 320dp 큰 글꼴 검증 |

Calendar Combat Feedback v1의 세부 회귀 위치는 다음과 같다.

| 계약 | 실제 테스트 |
|---|---|
| manual/deadline trigger와 같은 occurrence monster damage 멱등성 | `RoomCombatRepositoryTest.manualFailureAppliesOnceAndEmitsOneNonReplayableTransition`, `reconcileEmitsPlayerManualAndDeadlineTransitionsInThatOrderWithoutCollision`, `reconciliationSortsNewDueEventsCapsDamageAtThreeAndIsIdempotent` |
| failure undo 비롤백과 늦은 completion reward·player attack 멱등성 | `RoomTaskRepositoryTest.undoFailureDeletesOnlyFailureSourceAndKeepsExistingMonsterEvent`, `RoomCombatRepositoryTest.failureAttackDoesNotBlockLateCompletionRewardOrPlayerAttack`, `RoomTaskRepositoryTest.completeOccurrenceAwardsRewardOnlyOnce` |
| 처치 회복·다음 monster spawn 단일 적용, 치명 피해의 0 HP 전투 불능·중상 적용/갱신·응급 회복과 presentation 중복 제거 | `RoomCombatRepositoryTest.defeatingMonsterRecoversPlayerOnceAndAdvancesToNextEncounter`, `zeroHpTriggersTheStatusLifecycleAndEmergencyRecovery`, `lethalAttackAppliesSevereInjuryOnceAndDuplicateReturnsStoredResult`, `BattleAnimationControllerTest.lethalMonsterAttackConsumesTheEntireSevereInjuryLifecycleInOrder`, `queuedTransitionsRunOneAtATimeAndDuplicateKeysStayConsumedForControllerLifetime` |
| 중상 유효 스탯·회복 ledger·만료·재시작·Room 12→13 migration | `DerivedStatsCalculatorTest.severeInjuryFloorsMaxHpAndAttackToEightyPercentWithoutMutatingSources`, `StatusEffectDaoTest.recoveryOccurrenceIsCreditedOncePerRevisionTaskAndOccurrenceDate`, `RoomTaskRepositoryTest.threeDistinctRecurringCompletionsRecoverInjuryAndThirdAttackUsesRestoredStats`, `RoomStatusEffectRepositoryTest.observeMapsPersistentActiveEffectAndExactExpiryReconciliationSurvivesRecreation`, `TodoQuestDatabaseMigrationTest.migrationFromVersion12ToVersion13PreservesSourcesAndCreatesEmptyStatusEffectTables` |
| Room v5→v8 기존 상태 보존, 기존 자동 failure backfill과 transition 비소급 | `TodoQuestDatabaseMigrationTest.migrationFromVersion5ToVersion8PreservesAllSourceStateAndAddsFailureContract`, `migrationFromVersion7ToVersion8BackfillsOnlyMissingAutomaticDeadlineFailures` |

WorkManager는 앱 시작 one-time work와 15분 periodic work를 제공하지만 best-effort 실행이다. `MissedOccurrencePolicy`가 계산한 occurrence deadline과 transaction에서 함께 기록하는 `FailureLogEntity.failedAtEpochMillis`·`MonsterAttackEventEntity.processedAtEpochMillis`는 서로 다른 개념이며, 정확한 deadline alarm은 후속 범위다. Calendar의 actor·HP·transient effect, versioned Combat Rewards와 Room v14 snapshot, Equipment Shop and Inventory v1의 구매·장착·해제·modifier·UI를 구현했다. 18종 중 액세서리 2종을 제외한 16종은 실제 bitmap/layer mapping을 사용하고 액세서리와 unknown/decode 실패는 type별 접근 가능한 placeholder와 기존 appearance fallback을 사용한다. 몬스터 상태 효과·Stage HUD, 몬스터 스킬·치명타와 전리품은 계속 미승인 또는 후속 범위다.

2026-07-22 phase 33 전체 검증에서 debug·release JVM test 각 241개, lint, assembleDebug, SM-A325N Android 13의 connected test 45개, harness pytest 69개와 문서 링크가 통과했다. portrait·landscape raw screenshot은 고정 Battle Map의 한 Row HUD·actor 상단 HP와 shared player·goblin sprite, 독립 Calendar scroll 및 bottom navigation 합성을 확인하며, transition 순서는 `BattleAnimationControllerTest`의 virtual time 결과로 판정했다.

2026-07-22 phase 34 최종 검증에서는 debug·release JVM test 각 282개, lint, assembleDebug, SM-A325N Android 13 실기기의 connected test 57개, harness pytest 69개, 18개 Markdown 링크와 `git diff --check`가 실패·오류·skip 없이 통과했다. 최초 connected suite가 장착 직후 Battle Map HP 갱신 누락을 재현해 `RoomCombatRepository`의 교차 table 관찰을 invalidation 이후 단일 Room read transaction으로 교체했고, `activeObservationSurvivesAtomicEquipmentAndHpUpdates`와 구매·장착·재생성 `AppNavigationTest`로 회귀를 고정했다. seeded 장비 전용 bitmap과 대부분의 appearance `layerKey`가 없는 제한은 그대로다.

2026-07-28 phase 37 최종 검증에서는 debug·release JVM test 각 308개, lint, debug 앱·androidTest APK, SM-A325N Android 13 실기기의 connected test 68개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크 89개와 `git diff --check`가 실패·오류·skip 없이 통과했다. Room v8의 자동 failure backfill과 신규 reconciliation transaction, typed 일괄 배분·HP 비율 1회 보존, 비영속 Character draft와 좌측 level·우측 gold/EXP HUD bounds를 순수 Kotlin·Robolectric·연결 test로 함께 검증했다.

2026-07-28 phase 38 최종 검증에서는 debug·release JVM test 각 318개, lint, debug 앱·androidTest APK, Pixel 9 Android 17(API 37) AVD의 connected test 72개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크 90개와 `git diff --check`가 실패·오류·skip 없이 통과했다. Room v9 combat reward snapshot과 legacy 비소급, 레벨·등급별 hit/kill XP·gold transaction, EXP 0/100 track·600ms 보상 배지, 고정 능력치 control과 기본·파생 설명 dialog를 함께 검증했다.

2026-08-10 phase 53 최종 검증에서는 debug·release JVM/Robolectric test 각 580개, lint, assembleDebug, Pixel 9 Android 17(API 37) AVD의 connected test 134개, harness pytest 88개, 19개 Markdown 문서의 로컬 링크 181개와 `git diff --check`가 실패·오류·skip 없이 통과했다. 해제 transaction의 소유 보존·`AlreadyEmpty` 멱등성·상태이상 포함 HP 비율·대상 기본 복장 fallback, Shop·Inventory의 해제와 shared Character·Battle renderer, 보유 카드와 일반 `96dp`·compact `120dp` preview slot 계약을 함께 검증했다.
