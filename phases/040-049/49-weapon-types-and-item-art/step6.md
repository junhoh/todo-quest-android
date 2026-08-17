# Step 6: render-weapon-shop-and-inventory-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/shop/EquipmentArtworkCatalogTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/EquipmentArtworkLoaderTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/InventoryViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

artwork catalog·ViewModel·Compose fixture 테스트를 먼저 확장해 네 무기가 placeholder이고 subtype/신규 이름을 표시하지 못하는 예상 실패를 확인한 뒤 Shop/Inventory presentation을 확장한다. UI와 ViewModel은 DAO를 직접 호출하지 않는다.

`ShopEquipmentUiModel`과 `InventoryEquipmentUiModel`에 `weaponType: WeaponType?`를 추가하고 domain `Equipment`에서 그대로 mapping한다. category와 slot 표시는 계속 `무기`다. 장비 card, 상세 sheet, Inventory item의 type line은 무기일 때 `무기 · <subtype>`, 비무기는 기존 type 이름만 표시한다.

다음 한국어 resource를 추가한다.

```text
equipment_weapon_type_longsword = 장검
equipment_weapon_type_dagger = 단검
equipment_weapon_type_spear = 창
equipment_weapon_type_blunt = 둔기
equipment_type_with_weapon_type = %1$s · %2$s
equipment_name_ash_spear = 물푸레나무 창
equipment_description_ash_spear = 탄력 있는 자루와 날카로운 창끝으로 빈틈을 찌릅니다.
equipment_name_steel_mace = 강철 철퇴
equipment_description_steel_mace = 묵직한 강철 머리로 공격과 치명타 위력을 높입니다.
```

기존 낡은 검·철 장검 이름과 설명은 유지한다. 이름/설명 resource key resolver에 신규 두 key를 등록한다.

`EquipmentArtworkCatalog`는 네 key를 각각 `character/layers/<key>.png`로 해석한다. 기존 loader의 64×64 decode, cache, alpha bounds, integer bounds-fit, `FilterQuality.None`, null/unknown/decode fallback은 변경하지 않는다.

네 무기는 다음 위치에서 서로 다른 실제 artwork, 한국어 이름과 subtype을 제공한다.

1. Shop 무기 filter의 판매 card.
2. Shop 선택 상세 sheet.
3. Shop 캐릭터 preview의 weapon slot item. 부모 semantics가 있으므로 이미지는 decorative다.
4. Inventory 소유 장비 card.

테스트는 전체 16 known artwork mapping, unknown/null fallback, loader cache·bounds, 네 무기의 실제 tag, subtype text/semantics, 가격·등급·modifier, 구매 성공 action, 같은 WEAPON slot 교체와 compact layout을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.EquipmentArtworkCatalogTest" --tests "com.todoquest.feature.shop.EquipmentArtworkLoaderTest" --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. catalog, UiState와 Compose fixture 테스트를 먼저 변경하고 예상 실패를 확인한다.
2. subtype mapping, 네 artwork와 신규 문자열을 추가한 뒤 AC를 실행한다.
3. 상점 bounds-fit과 캐릭터 full-origin 합성의 용도 차이가 유지되는지 확인한다.
4. 이미지 실패가 구매·장착·일정·보상 기능을 실패시키지 않는지 확인한다.
5. task index의 step 6을 `completed`로 바꾸고 네 mapping·subtype·표시 위치를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 무기 subtype을 새 Shop category나 EquipmentSlot으로 만들지 마라. 이유: subtype은 WEAPON 내부 표시·확장 정보다.
- Compose나 ViewModel에 asset 파일명을 흩어 놓지 마라. 이유: artwork catalog가 단일 mapping source여야 한다.
- 이미지 실패를 Shop 전체 오류나 구매·장착 실패로 승격하지 마라. 이유: presentation failure는 핵심 기능과 격리해야 한다.
- 사용자 노출 영문 문구를 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
