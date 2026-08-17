# Step 6: render-gloves-shoes-shop-artwork

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/shop/EquipmentArtworkCatalogTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/EquipmentArtworkLoaderTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/InventoryViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

artwork catalog unit test와 Compose fixture를 먼저 확장해 네 non-null `imageKey`가 placeholder이거나 신규 한국어 이름을 해석하지 못하는 예상 실패를 확인한 뒤 기존 공용 renderer에 mapping과 문자열을 추가한다. UI는 Repository/UiState가 제공한 key만 소비하고 DAO를 직접 호출하지 않는다.

다음 한국어 resource를 정확히 추가한다.

```text
equipment_name_steel_gauntlets = 강철 건틀릿
equipment_description_steel_gauntlets = 정교한 강철 관절이 힘과 치명타 위력을 높입니다.
equipment_name_windwalker_boots = 바람걸음 장화
equipment_description_windwalker_boots = 가벼운 밑창이 집중과 방어, 회복 효율을 높입니다.
```

`EquipmentArtworkCatalog`는 네 key를 각각 `character/layers/<key>.png`로 해석한다. 기존 loader의 정확한 64×64 decode, positive/negative cache, alpha bounds 계산, integer bounds-fit, `FilterQuality.None`, null/unknown/decode fallback을 변경하지 않는다. 같은 PNG는 캐릭터 합성에서는 full origin, 상점에서는 opaque bounds source rect로만 표시한다.

기존 연결 위치 네 곳을 유지하고 장갑 filter에 가죽 장갑·강철 건틀릿, 신발 filter에 여행자의 장화·바람걸음 장화가 다음 위치에서 서로 다른 실제 artwork와 한국어 `<장비명> 이미지` 설명으로 나타나게 한다.

1. Shop 판매 목록 card.
2. Shop 선택 상세 sheet.
3. Shop 캐릭터 preview의 장착 slot item. 부모 semantics가 있으므로 이미지는 decorative다.
4. Inventory 소유 장비 card.

테스트는 전체 12 known artwork mapping, unknown/null, loader cache·bounds, 네 장비별 실제 tag, 네 표시 위치, 구매 성공 action, 같은 slot 교체, fallback, compact layout과 기존 한국어 semantics를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.EquipmentArtworkCatalogTest" --tests "com.todoquest.feature.shop.EquipmentArtworkLoaderTest" --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. catalog와 Compose fixture 테스트를 먼저 변경하고 예상 placeholder·문자열 실패를 확인한다.
2. 네 mapping과 신규 문자열을 추가한 뒤 AC를 실행한다.
3. 상점 bounds-fit과 캐릭터 full-origin 합성 차이가 유지되는지 확인한다.
4. 이미지 실패가 구매·장착·일정·보상 흐름을 실패시키지 않는지 확인한다.
5. task index의 step 6을 `completed`로 바꾸고 네 mapping·신규 문자열·표시 위치를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Compose나 ViewModel에 asset 파일명을 조건문으로 흩어 놓지 마라. 이유: artwork catalog가 단일 mapping source여야 한다.
- 이미지 실패를 Shop 전체 오류나 구매·장착 실패로 승격하지 마라. 이유: presentation failure는 핵심 기능과 격리해야 한다.
- bitmap에 bilinear filtering, antialiasing 또는 tint를 적용하지 마라. 이유: hard pixel art를 보존해야 한다.
- 사용자 노출 영문 문구를 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
