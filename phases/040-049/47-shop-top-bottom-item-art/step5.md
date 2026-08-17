# Step 5: register-outfit-shop-artwork

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
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
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

artwork catalog unit test와 Compose fixture를 먼저 확장해 여섯 non-null `imageKey`가 placeholder로 표시되는 예상 실패를 확인한 뒤 기존 공용 renderer에 mapping을 추가한다. UI는 Repository/UiState가 제공한 key만 소비하고 DAO를 직접 호출하지 않는다.

`EquipmentArtworkCatalog`는 여섯 key를 각각 `character/layers/<key>.png`로 해석한다. 기존 `EquipmentArtworkLoader`의 정확한 64×64 decode, positive/negative path cache, alpha bounds 계산, integer bounds-fit, `FilterQuality.None`, null/unknown/decode fallback을 변경하지 않는다. 같은 PNG는 캐릭터 합성에서는 full origin, 상점에서는 opaque bounds source rect로만 표시한다.

기존 연결 위치 네 곳을 유지하고 각 새 장비가 다음 위치에 실제 `equipment_artwork_<key>` tag와 한국어 `<장비명> 이미지` 설명으로 나타나는지 검증한다.

1. Shop 판매 목록 card.
2. Shop 선택 상세 sheet.
3. Shop 캐릭터 preview의 장착 slot item. 부모 semantics가 있으므로 이미지는 decorative다.
4. Inventory 소유 장비 card.

테스트는 여덟 known mapping(기존 투구 2+신규 6), unknown/null, loader cache·bounds, 상의/하의별 실제 tag, 네 표시 위치, fallback, 기존 한국어 semantics와 Preview fixture를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.EquipmentArtworkCatalogTest" --tests "com.todoquest.feature.shop.EquipmentArtworkLoaderTest" --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. catalog와 Compose fixture 테스트를 먼저 변경하고 예상 placeholder 실패를 확인한다.
2. 여섯 mapping을 추가한 뒤 AC를 실행한다.
3. 상점 bounds-fit과 캐릭터 full-origin 합성 차이가 유지되는지 확인한다.
4. 이미지 실패가 구매·장착·일정·보상 흐름을 실패시키지 않는지 확인한다.
5. task index의 step 5를 `completed`로 바꾸고 여섯 mapping과 네 표시 위치를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Compose나 ViewModel에 asset 파일명을 조건문으로 흩어 놓지 마라. 이유: artwork catalog가 단일 mapping source여야 한다.
- 이미지 실패를 Shop 전체 오류나 구매·장착 실패로 승격하지 마라. 이유: presentation failure는 핵심 로컬 기능과 격리해야 한다.
- bitmap에 bilinear filtering, antialiasing 또는 tint를 적용하지 마라. 이유: hard pixel art를 보존해야 한다.
- 사용자 노출 영문 문구를 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
