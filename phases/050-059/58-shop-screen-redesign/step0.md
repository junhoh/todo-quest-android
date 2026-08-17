# Step 0: define-shop-preview-projection

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterStats.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/DerivedStatsCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/EquipmentPolicies.kt`
- `/app/src/test/java/com/todoquest/domain/EquipmentComparisonCalculatorTest.kt`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

순수 Kotlin unit test를 먼저 작성하고, 판매 장비를 현재 장착 슬롯에 임시 대입했을 때의 외형과 최종 능력치를 계산하는 도메인 projection 계약을 추가한다.

`EquipmentPreviewProjection`은 `renderedEquippedItems: EquippedItems`와 `derivedStats: DerivedStats`를 제공한다. `EquipmentStoreSnapshot`에는 `previewByEquipmentId: Map<Long, EquipmentPreviewProjection> = emptyMap()`을 추가해 기존 fixture와 호환한다. 이름이 동일한 대체 계약을 이미 찾으면 중복 타입을 만들지 말고 기존 계약을 확장한다.

`EquipmentPreviewProjectionCalculator.calculate(...)`는 candidate `Equipment`, 현재 `equippedBySlot`, 실제 `renderedEquippedItems`, 현재 `StatCalculationInput`, `CharacterStatBalanceConfig`를 받아 같은 slot의 modifier만 candidate modifier로 교체한다. base stat, derived stat, flat, basis-point, passive/set modifier와 활성 temporary effect는 `DerivedStatsCalculator`의 기존 공식과 순서를 그대로 사용한다. 계산은 실제 소유·장착·골드·HP를 변경하지 않는다.

candidate의 유효한 `layerKey`만 해당 `EquippedItems` field에 임시 투영하고 `CharacterLoadoutCatalog.contains`를 통과하지 못한 null/unknown layer는 현재 외형을 유지한다. type/slot 불일치처럼 구매할 수 없는 정의도 상점 로드를 실패시키지 말고 현재 외형·현재 능력치 fallback projection을 반환한다.

테스트는 같은 slot 교체와 다른 slot 보존, base modifier가 공격력·최대 체력·방어력에 미치는 파생 변화, direct/percentage modifier, temporary effect 유지, 유효/unknown layer와 입력 불변성을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.EquipmentPreviewProjectionCalculatorTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 새 unit test를 먼저 작성해 실패를 확인한 뒤 projection 계약과 계산기를 구현한다.
2. 기존 `DerivedStatsCalculator`와 장비 slot 호환 정책을 재사용하고 계산 결과가 입력 collection을 변경하지 않는지 확인한다.
3. AC 명령을 실행하고 AGENTS.md의 UI/Repository 경계와 구매 무결성 규칙을 확인한다.
4. 성공 시 task index의 step 0을 `completed`로 바꾸고 생성 타입·핵심 계산 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Room schema나 DAO를 변경하지 마라. 이유: 구매 전 미리보기는 저장되지 않는 순수 projection이다.
- 파생 능력치 차이를 장비 modifier 단순 합으로 근사하지 마라. 이유: base stat, 비율 modifier와 상태 효과가 적용된 공식 결과여야 한다.
- unknown layer 때문에 능력치 또는 상점 로드를 실패시키지 마라. 이유: 외형 실패는 기존 기능과 transaction에서 격리되어야 한다.
- 기존 테스트를 깨뜨리지 마라.
