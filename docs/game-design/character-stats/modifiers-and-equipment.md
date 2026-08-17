# modifier와 장비

[캐릭터 스탯 설계 인덱스로 돌아가기](../character-stats-design.md)

> 문서 지위: PRD의 MVP 제외 범위를 바꾸지 않는 ADR-013 Post-MVP Equipment Shop and Inventory v1의 구현 계약이다. Room v7 영속화·원자 transaction·실제 장착 modifier 연결과 Shop·Inventory UI까지 구현됐다.

## 숫자 표현과 반올림 계약

- 고정값 파생 능력치는 최종 단계에서 `floor`하여 `Int`로 표시한다. 중간 계산의 전체 분자는 `Long`으로 유지하고 마지막에 한 번만 내림한다.
- 확률과 배율은 `10,000bp = 100%`인 `Int` 고정소수점으로 저장·계산한다.
- 확률과 배율의 기본 UI는 half-up으로 소수점 한 자리까지 표시한다. 음수가 아닌 최종 bp의 표시용 0.1% 단위는 `(bp + 5) / 10`의 정수 나눗셈으로 얻고, 상세 진단 화면과 로그는 원시 bp를 사용한다.
- 범위 clamp는 해당 능력치의 모든 허용 기여와 디버프를 합산·적용한 뒤 마지막에 수행한다. 각 공식에서 명시한 정수 나눗셈의 `floor`는 예외다.

## 장비와 효과 적용

장비가 제공하는 기본 스탯은 먼저 장비·효과 포함 기본 스탯 절대 상한 `99`를 지킨 뒤 해당 파생 능력치의 **장비 고정 기여**로 환산한다.

| 장비 기본 스탯 1점 | 파생 능력치의 장비 고정 기여 |
|---|---|
| `STRENGTH` | `ATTACK +2`, `CRITICAL_DAMAGE +50bp` |
| `VITALITY` | `MAX_HP +10`, `DEFENSE +1` |
| `FOCUS` | `ATTACK +1`, `CRITICAL_CHANCE +50bp` |
| `WILLPOWER` | `STATUS_RESISTANCE +75bp`, `HP_RECOVERY +1` |

고정형 파생값은 다음 순서를 사용한다.

```text
finalValue = clamp(floor(
    (levelBase + characterStatContribution + equipmentFlatContribution)
    × (1 + summedEquipmentPercent)
    × (1 + summedPassivePercent)
    × (1 + summedTemporaryPercent)
), minValue, maxValue)
```

구현 시 각 비율을 bp 정수로 바꾸고 전체 곱의 분자를 `Long`으로 계산한다. 세 비율 버킷의 분모 `10,000³`은 모든 곱셈이 끝난 뒤 한 번만 나누어 `floor`한다. 허용된 입력 상한에서 `Long` 범위를 넘지 않는지 구현 테스트로 고정한다.

- 장비 % 버킷은 `0..+50%`(`0..5,000bp`)다.
- 패시브·세트 버킷과 일시 효과 버킷은 각각 `-50%..+30%`(`-5,000..+3,000bp`)다.
- 같은 버킷의 효과는 먼저 합산하고 버킷 범위로 제한한다. 효과별 연쇄 곱셈은 금지한다.
- v1의 패시브·세트·일시 효과는 고정형 파생 능력치에는 비율 modifier만, 확률형 파생 능력치에는 bp 고정 증감만 제공한다.
- `CRITICAL_CHANCE`, `CRITICAL_DAMAGE`, `STATUS_RESISTANCE`, `GOLD_GAIN_BONUS`는 기본값, 장비 환산값, 장비 직접 bp, 패시브·세트 bp, 일시 효과 bp, 디버프 bp를 모두 합산한 뒤 최종 범위로 clamp한다.
- bp 상한 초과분을 다른 능력치나 보상으로 전환하지 않는다. 다만 디버프가 최종 clamp 전에 적용되므로 상한을 넘긴 합계는 디버프에 대한 버퍼가 될 수 있다.

## 장비 부위 역할

gameplay 장비 type과 slot은 각각 아래 일곱 값을 같은 이름으로 사용한다.

```text
WEAPON, HELMET, CHEST, LEGS, GLOVES, SHOES, ACCESSORY
```

type은 catalog 분류이고 slot은 캐릭터당 한 장비만 활성화할 장착 위치다. v1은 type과 slot의 이름이 일치해야 하며 generic `ARMOR`, `TOP`, `BOTTOM`, `HEAD`, `PET`을 canonical 값으로 저장하지 않는다. 현재 순수 검증용 `EquipmentSlot.HEAD/TOP/BOTTOM/SHOES/ACCESSORY/WEAPON/PET`는 이 일곱 값으로 교체한다. `CHEST`와 `LEGS`는 독립 slot이므로 동시에 장착할 수 있고 한 장비 교체는 대상 slot row 하나만 바꾼다.

표의 허용 목록에 없는 직접 affix는 해당 부위에 붙이지 않는다. 기본 스탯 보너스는 정수 고정값이고, 확률·비율 파생값은 곱연산 `%`가 아니라 bp 고정값으로만 제공한다.

| type / slot | 표시명 | 주 기본 스탯 | 주 파생 능력치 | 허용 고정값·bp 보너스 | 허용 장비 고정형 `%` | 금지 능력치 |
|---|---|---|---|---|---|---|
| `WEAPON` | 무기 | `STRENGTH`, `FOCUS` | `ATTACK`, `CRITICAL_DAMAGE` | 주 기본 스탯, 공격 고정값, 치명타 피해 bp | `ATTACK` | HP·방어·골드 보너스 |
| `HELMET` | 투구 | `FOCUS`, `WILLPOWER` | `CRITICAL_CHANCE`, `STATUS_RESISTANCE` | 주 기본 스탯, 치명타 확률·저항 bp | 없음 | `ATTACK %`, 확률·비율 곱연산 `%` |
| `CHEST` | 상의 | `VITALITY` | `MAX_HP`, `DEFENSE` | 주 기본 스탯, HP·방어 고정값 | `MAX_HP`, `DEFENSE` | 치명타 확률·치명타 피해·골드 보너스 |
| `LEGS` | 하의 | `VITALITY`, `WILLPOWER` | `DEFENSE`, `HP_RECOVERY` | 주 기본 스탯, 방어·회복 고정값 | `DEFENSE`, `HP_RECOVERY` | 공격·치명타 능력치 |
| `GLOVES` | 장갑 | `STRENGTH`, `FOCUS` | `CRITICAL_CHANCE`, `CRITICAL_DAMAGE` | 주 기본 스탯, 치명타 확률·피해 bp | 없음 | 직접 `ATTACK` 고정값·`ATTACK %`, HP·방어·골드 보너스 |
| `SHOES` | 신발 | `FOCUS`, `WILLPOWER` | `DEFENSE`, `HP_RECOVERY`, `STATUS_RESISTANCE` | 주 기본 스탯, 방어·회복 고정값, 저항 bp | `DEFENSE`, `HP_RECOVERY`만 | `MAX_HP %`, `ATTACK %`, 치명타 피해 |
| `ACCESSORY` | 장신구 | `FOCUS`, `WILLPOWER` | `CRITICAL_CHANCE`, `CRITICAL_DAMAGE`, `GOLD_GAIN_BONUS` | 주 기본 스탯, 치명타·골드 bp | 없음 | 큰 HP·방어 고정값과 HP·방어 `%` |

직접 `ATTACK` 고정값이나 `%`를 허용하는 부위는 `WEAPON`뿐이다. `GLOVES`의 `STRENGTH`·`FOCUS`는 위 기본 스탯 환산을 거쳐 공격에 기여할 수 있지만 직접 공격 affix를 받지 않는다. 나머지 부위도 생존, 회복, 저항, 치명타 또는 골드 역할을 나눠 가지므로 7개 부위가 모두 공격력 중심으로 수렴하지 않는다.

초기 콘텐츠나 가져오기 입력이 `agility`·민첩 계열을 사용하면 `FOCUS`, `intelligence`·지능 계열을 사용하면 `WILLPOWER` modifier로 변환한다. 기존 `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`와 8개 `DerivedStats`만 사용하며 새로운 agility/intelligence 원천 스탯 또는 Room 컬럼을 추가하지 않는다.

MVP 상점·인벤토리 아이템은 계속 외형 또는 수집 전용이다. 이 표의 gameplay 능력치 장비는 ADR-013에서 별도로 승인한 Post-MVP Equipment Shop and Inventory v1 범위이며, 구매하거나 소유한 사실만으로 modifier를 적용하지 않는다.

## 장비 소유·장착과 저장 호환

- 실제 소유권 source는 `owned_equipment`다. 캐릭터와 equipment 조합을 unique로 두고 같은 equipment의 중복 구매를 금지하며 quantity를 도입하지 않는다.
- 실제 gameplay 장착과 능력치 source는 `character_equipment`다. `DerivedStatsCalculator`에는 이 테이블에 현재 장착된 equipment의 modifier만 전달하고, 소유만 했거나 구매 직후 아직 장착하지 않은 장비는 전달하지 않는다.
- 구매 transaction은 최신 골드, 판매 상태, 요구 레벨, 캐릭터별 중복 소유와 type/slot mapping을 다시 검증한 뒤 골드 차감과 `owned_equipment` 추가를 함께 확정한다.
- 장착 transaction은 소유권과 type/slot 일치를 검증하고 대상 slot의 `character_equipment` 교체만 확정한다. 변경 전·후 `MAX_HP`가 다르면 같은 transaction 안에서 current HP 비율을 보존하고 `0 HP`를 장착만으로 전투 가능 상태로 만들지 않는다.
- 해제 transaction은 대상 slot의 `character_equipment` row만 제거하고 `owned_equipment`는 유지해 재장착 가능성을 보존한다. 활성 상태이상을 포함한 장착 전·후 modifier를 다시 계산하고 `MAX_HP`가 달라지면 같은 transaction에서 current HP 비율을 보존한다. 이미 빈 slot은 `AlreadyEmpty`로 멱등 성공하고 source를 변경하지 않는다.
- Room v6의 `character_appearance`와 `character_equipped_items`는 기존 사용자 render 진행을 보존하는 외형 fallback이다. 특히 `topId`와 `bottomId`는 이미 분리된 appearance layer이며 `owned_equipment`나 gameplay `CHEST`·`LEGS` 장착으로 간주하지 않는다. gameplay asset mapping이 없을 때는 이 외형 fallback을 계속 렌더링한다.
- Room v15의 `MIGRATION_14_15`는 모든 appearance fallback을 빈 gameplay loadout으로 정규화한다. `CHEST`·`LEGS`·`SHOES`는 각각 `top_default`·`bottom_default`·`shoes_default`의 회갈색 중립 훈련복을 사용하고 나머지 gameplay overlay는 null로 비운다. 이 data-only migration은 `owned_equipment`와 `character_equipment`를 수정하지 않으므로 기존 소유권·실제 장착·modifier는 그대로 유지된다.
- 사용자가 명시적으로 gameplay 장비를 해제하면 shared renderer에 같은 의도를 반영하기 위해 해당 slot의 appearance fallback만 같은 transaction에서 기본 복장으로 바꾼다. 이는 ownership source의 병합이나 승격이 아니며 다른 slot fallback은 보존한다.

| 해제 slot | appearance fallback 결과 |
|---|---|
| `WEAPON` | `weaponId = null` |
| `HELMET` | `headId = null` |
| `CHEST` | `topId = top_default` |
| `LEGS` | `bottomId = bottom_default` |
| `GLOVES` | `glovesId = null` |
| `SHOES` | `shoesId = shoes_default` |
| `ACCESSORY` | `accessoryId = null` |

- Room v6에는 `ARMOR` slot 값이 없다. 따라서 아래 mapping은 v6 row를 파괴적으로 고치는 migration이 아니라 외부·과도기 저장 입력을 canonical slot으로 정규화하는 경계다.

| 호환 입력 | canonical slot | 근거 |
|---|---|---|
| `WEAPON` | `WEAPON` | 동일 gameplay 의미 |
| `HEAD` 또는 `HELMET` | `HELMET` | 기존 headgear 의미 보존 |
| `TOP` 또는 `CHEST` | `CHEST` | 기존 상체 appearance layer와 대응 |
| `ARMOR` | `CHEST` | 상·하의 구분 정보가 없고 일반적인 단일 armor가 상체 방어구에 가장 가까움 |
| `BOTTOM` 또는 `LEGS` | `LEGS` | 기존 하체 appearance layer와 대응 |
| `GLOVES` | `GLOVES` | 새 gameplay slot |
| `SHOES` | `SHOES` | 동일 gameplay 의미 |
| `ACCESSORY` | `ACCESSORY` | 동일 gameplay 의미 |
| `PET` | 변환 거부 | Equipment Shop and Inventory v1 승인 slot이 아님 |

## 구현 상태와 검증 위치

- `EquipmentCatalogSeeder`는 명시적 ID의 25종 장비와 정렬된 modifier를 Repository 준비 transaction에서 멱등 seed한다. 기존 18종에 더해 아래 모험가 세트 7종은 모두 `UNCOMMON`, 요구 레벨 5이며 정확히 두 modifier와 검증된 adventure `imageKey`·`layerKey`를 사용한다. 기존 액세서리 2종과 unknown/decode 실패는 한국어 type 설명이 있는 Material icon placeholder와 appearance fallback을 유지한다.

| ID | key / 표시 이름 | slot / weapon | 가격 | modifier |
|---|---|---|---:|---|
| 1019 | `adventure_sword` / 모험가의 검 | `WEAPON` / `LONGSWORD` | 150 | `ATTACK FLAT +5`, `STRENGTH FLAT +1` |
| 1020 | `adventure_hat` / 모험가의 모자 | `HELMET` | 100 | `FOCUS FLAT +1`, `STATUS_RESISTANCE FLAT +150bp` |
| 1021 | `adventure_jacket` / 모험가의 재킷 | `CHEST` | 120 | `VITALITY FLAT +1`, `DEFENSE FLAT +2` |
| 1022 | `adventure_pants` / 모험가의 바지 | `LEGS` | 110 | `WILLPOWER FLAT +1`, `DEFENSE FLAT +2` |
| 1023 | `adventure_gloves` / 모험가의 장갑 | `GLOVES` | 125 | `STRENGTH FLAT +1`, `CRITICAL_CHANCE FLAT +150bp` |
| 1024 | `adventure_shoes` / 모험가의 신발 | `SHOES` | 130 | `FOCUS FLAT +1`, `DEFENSE FLAT +2` |
| 1025 | `adventure_accessory` / 모험가의 장식 | `ACCESSORY` | 160 | `WILLPOWER FLAT +1`, `GOLD_GAIN_BONUS FLAT +150bp` |

모험가의 검은 논리 key `weapon_default_sword` 하나를 사용하지만 합성은 기존 `weapon_back_default_sword`·`weapon_held_default_sword`·`weapon_front_default_sword` 3분할 source를 유지한다. 모험가의 장갑은 `gloves_adventure`가 맨손 `hands_front`의 38픽셀 mask를 대체한다. 일곱 상품은 자동 소유·자동 장착하지 않는다.

- 실제 modifier source는 Room v7 `character_equipment → owned_equipment → equipment_modifiers` 경로다. `RoomCharacterRepository`는 Character snapshot과 성장 command, `RoomTaskRepository`는 새 보상·플레이어 공격 snapshot, `RoomCombatRepository`는 현재 HP·방어·공격·처치 회복에 같은 실제 장착 source를 전달한다. 소유만 한 장비는 어느 calculator 입력에도 포함하지 않는다.
- `RoomEquipmentRepository.equipOwnedEquipment()`는 같은 transaction에서 기존 장착 modifier의 `oldMax`, 대상 slot 교체 뒤 modifier의 `newMax`를 계산하고 `CombatCalculator.preserveHpRatio()`로 current HP를 갱신한다. `0 HP`는 그대로 유지하며 실패하면 slot 교체와 HP 갱신을 함께 rollback한다.
- `RoomEquipmentRepository.unequipEquipment()`는 대상 장착 row 삭제, 대상 fallback 기본화, 상태이상을 포함한 `MAX_HP` 비율 보존을 한 transaction에서 수행하며 소유 row는 삭제하지 않는다. `EquipmentUnequipAppearancePolicy`가 위 slot mapping을 순수하게 결정하고 `AlreadyEmpty`는 write 없는 성공 결과다.
- 일곱 type/slot·호환 mapping·modifier 범위는 `PurchaseEquipmentPolicyTest`, `EquipmentModifierValidatorTest`, `EquipmentComparisonCalculatorTest`에서 검증한다. 네 Room 테이블·25종 seed·unique key·`CHEST`/`LEGS` 독립성과 v14→v15 fallback-only migration은 `TodoQuestDatabaseMigrationTest`와 `EquipmentDaoTest`, 구매·장착·해제·HP 원자성은 `RoomEquipmentRepositoryTest`에서 검증한다.
- Character·Task·Combat 재계산 연결은 각각 `RoomCharacterRepositoryTest.ownedEquipmentWithoutEquippingKeepsEmptyModifierAndAppearanceFallback`, `RoomTaskRepositoryTest.equippedAccessoryAffectsOnlyNewRewardAndPlayerAttackSnapshots`, `RoomCombatRepositoryTest.activeObservationSurvivesAtomicEquipmentAndHpUpdates`, `equipmentChangeDoesNotRewriteAlreadyAppliedAttackSourceOrResult` 및 장비 적용 전투·회복 test로 검증한다. Shop·Inventory 화면과 navigation은 `ShopScreenTest`, `InventoryScreenTest`, `AppNavigationTest`에서 검증한다.

### 장비 등급과 affix 범위

장비 등급은 일반, 고급, 희귀, 영웅, 전설의 5단계다. 수치 affix 수는 순서대로 `1`, `2`, `3`, `4`, `4`이며, 전설은 수치 affix 4개에 고유 패시브 1개를 추가한다. 아래 값은 **affix 하나당 범위**다. 한 아이템이 표의 모든 열을 동시에 받는 것이 아니며, 부위별 허용 목록 안에서 등급별 affix 수만큼 선택한다.

| 등급 | 기본 스탯 | 공격 | HP | 방어 | 회복 | 확률·비율 포인트 | 고정형 % |
|---|---:|---:|---:|---:|---:|---:|---:|
| 일반 | 0~1 | 2~4 | 8~15 | 1~2 | 1 | 0.5~1.5%p (`50~150bp`) | 2~4% |
| 고급 | 1~2 | 4~7 | 15~25 | 2~4 | 1~2 | 1~2.5%p (`100~250bp`) | 4~6% |
| 희귀 | 2~4 | 7~12 | 25~40 | 4~7 | 2~4 | 2~4%p (`200~400bp`) | 6~10% |
| 영웅 | 3~6 | 12~20 | 40~65 | 7~11 | 4~7 | 4~7%p (`400~700bp`) | 10~15% |
| 전설 | 4~8 | 20~30 | 65~100 | 11~16 | 7~10 | 7~10%p (`700~1,000bp`) | 15~20% |

확률·비율 파생값인 `CRITICAL_CHANCE`, `CRITICAL_DAMAGE`, `STATUS_RESISTANCE`, `GOLD_GAIN_BONUS`는 표의 확률·비율 포인트를 bp 고정값으로만 받는다. 이 값에 곱연산 `%` affix를 적용하지 않는다. 고정형 파생값의 모든 장비 `%` affix는 같은 장비 버킷에서 합산하며 기존 `+50%`(`5,000bp`) 상한을 넘지 않는다.

등급 범위와 부위 역할을 함께 검증할 골든 완전 장비 집계는 다음 값으로 고정한다. 기본 스탯 순서는 `STRENGTH / VITALITY / FOCUS / WILLPOWER`다.

- Lv10 일반: 기본 `+1/+1/+1/+1`, HP `+12`, 공격 `+4`, 방어 `+2`, 회복 `+1`, HP·공격·방어 `+3%`, 치명타 `+100bp`, 치명타 피해 `+200bp`, 저항 `+150bp`, 골드 `+200bp`.
- Lv30 희귀: 기본 `+5/+4/+4/+3`, HP `+45`, 공격 `+15`, 방어 `+8`, 회복 `+4`, HP·공격·방어 `+8%`, 치명타 `+300bp`, 치명타 피해 `+700bp`, 저항 `+500bp`, 골드 `+800bp`.
