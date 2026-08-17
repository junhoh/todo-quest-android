# Step 1: model-equipment-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterStats.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/DerivedStatsCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentModifierValidator.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentModifierValidatorTest.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`
- step 0에서 수정한 canonical 문서

## 작업

새 도메인 테스트를 먼저 실패시키고 장비·구매·비교·장착 계약을 순수 Kotlin으로 구현한다. `domain/model/Equipment.kt`에 `EquipmentType`, 새 `EquipmentSlot`, 기존 `EquipmentRarity`, 현지화 key와 가격 `Long`, 요구 레벨, `EquipmentStatModifier` 목록, optional image/layer key, 판매 상태를 가진 `Equipment`, `OwnedEquipment`, `EquippedEquipment` 및 store/inventory snapshot을 정의한다. type과 slot은 모두 일곱 값이며 total mapping 함수를 제공한다. 저장 compatibility mapper는 `ARMOR|TOP → CHEST`, `BOTTOM → LEGS`, `HEAD → HELMET`을 지원하고 알 수 없는 값은 지원하지 않는 slot 결과로 처리한다.

기존 `EquipmentRarity`와 `EquipmentStatModifier`를 중복 정의하지 말고 확장한다. `EquipmentModifierValidator`와 `CharacterStatBalanceConfig`의 slot rule을 새 enum에 맞춰 갱신한다. `GLOVES`는 `STRENGTH`·`FOCUS`와 공격/치명 계열, `HELMET`은 방어·최대 HP와 집중/의지 계열, `CHEST`와 `LEGS`는 생존 능력치를 허용하되 서로 독립된 rule이어야 한다. `PET` rule과 pet 전용 rarity 범위는 제거한다. 초기 catalog의 모든 modifier가 rarity affix 수·범위·slot 허용 목록을 통과할 수 있어야 한다.

`domain/repository/EquipmentRepository.kt`에 store/inventory 관찰과 `purchaseEquipment(characterId, equipmentId)`, `equipOwnedEquipment(characterId, ownedEquipmentId, targetSlot)` command interface를 정의한다. `domain/usecase/EquipmentPolicies.kt`에 UI와 Repository transaction 양쪽에서 재사용할 구매 가능 정책, type/slot 일치 정책, 동일 slot 장비 비교 계산기를 만든다. 구매 불가 결과는 지원하지 않는 slot, 판매 중지, 이미 보유, 레벨 부족, 골드 부족을 데이터가 포함된 sealed type으로 표현하며 우선순위를 이 순서로 고정한다. 비교는 같은 `StatTarget + ModifierType`끼리 candidate-current를 계산하고 candidate/current 합집합을 안정적인 능력치 순서로 반환하며 양수·음수·0을 보존한다.

구매/장착 command의 public UseCase를 추가한다. UI 문자열, Android resource, Room entity를 도메인 타입에 포함하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --tests "com.todoquest.domain.EquipmentComparisonCalculatorTest" --tests "com.todoquest.domain.EquipmentModifierValidatorTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.DerivedStatsCalculatorTest" --console=plain
git diff --check
```

## 검증 절차

1. 정책·mapping·CHEST/LEGS 독립성 테스트를 구현보다 먼저 작성하고 실패를 확인한 뒤 production 코드를 작성한다.
2. 기존 calculator와 rarity validation 회귀를 함께 실행한다.
3. task index의 step 1을 `completed`로 바꾸고 생성한 public 타입과 slot 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- UI 표시 문자열로 type이나 slot을 판정하지 마라. 이유: 저장·구매·장착 무결성은 enum 계약이어야 한다.
- 장비별 고정 bonus 필드와 `EquipmentStatModifier`를 동시에 source로 두지 마라. 이유: 동일 능력치의 중복 원천을 만들게 된다.
- 구매 가능 여부를 Android UI 타입에 결합하지 마라. 이유: Repository transaction에서 동일 정책을 재검증해야 한다.
- 기존 테스트를 깨뜨리지 마라.
