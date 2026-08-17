# Step 5: render-helmet-equipment-images

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/ui/character/LayeredCharacterSprite.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/shop/InventoryViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

presentation catalog/loader unit test와 Compose test fixture를 먼저 작성해 non-null `imageKey`가 여전히 placeholder로 표시되는 예상 실패를 확인한 뒤 공용 equipment artwork renderer를 구현한다. UI는 Repository/UiState가 제공한 key만 소비하고 DAO를 호출하지 않는다.

다음 내부 인터페이스 또는 동등한 역할 분리를 사용한다.

```kotlin
@Immutable
data class EquipmentArtworkDefinition(val imageKey: String, val assetPath: String)

object EquipmentArtworkCatalog {
    fun resolve(imageKey: String?): EquipmentArtworkDefinition?
}

class EquipmentArtworkLoader(assetManager: AssetManager) {
    fun load(definition: EquipmentArtworkDefinition): LoadedEquipmentArtwork?
}

@Composable
internal fun EquipmentArtwork(
    imageKey: String?,
    type: EquipmentType,
    contentDescription: String?,
    modifier: Modifier = Modifier,
)
```

- catalog는 `headgear_leather_hat`와 `headgear_iron_helmet`만 각각 같은 runtime layer asset path로 해석한다.
- loader는 `inScaled=false`, ARGB_8888로 정확히 64×64 asset을 decode하고 positive/negative 결과를 path별 cache한다. 잘못된 size, decode 실패와 빈 alpha는 `null`로 격리한다.
- loader는 alpha에서 inclusive opaque bounds를 계산한다. Shop renderer는 같은 PNG의 해당 source rect만 64dp surface 안의 padding 영역에 가운데 배치하고 가능한 가장 큰 정수 배율과 `FilterQuality.None`으로 그린다. 원본 bitmap이나 캐릭터 layer 좌표는 변경하지 않는다.
- `imageKey == null`, unknown key 또는 load 실패면 기존 `EquipmentPlaceholder(type)`를 표시한다.
- 실제 이미지에는 `equipment_artwork_<imageKey>` test tag를, fallback에는 기존 placeholder tag를 유지한다.

다음 위치를 모두 `EquipmentArtwork`로 연결한다.

1. Shop 판매 목록 card.
2. Shop 선택 상세 sheet.
3. Shop 캐릭터 preview의 장착 slot item. 이 이미지는 parent의 병합된 장비명·등급·장착 semantics가 있으므로 decorative다.
4. Inventory 소유 장비 card.

실제 image content description은 새 한국어 문자열 `<장비명> 이미지`를 사용한다. 이름을 얻을 수 없는 경우 type placeholder 설명을 유지한다. 카드·상세·인벤토리에서 장비 이름과 이미지 설명이 TalkBack에 중복 병합되지 않도록 기존 semantics 구조를 확인한다.

테스트는 catalog 두 mapping/unknown, loader cache와 bounds, 실제 image tag, 네 표시 위치, unknown/decode fallback, 기존 한국어 placeholder와 장착 slot 병합 semantics를 포함한다. Preview fixture에도 한 실제 helmet과 한 fallback item을 둔다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.shop.EquipmentArtworkCatalogTest" --tests "com.todoquest.feature.shop.EquipmentArtworkLoaderTest" --tests "com.todoquest.feature.shop.ShopViewModelTest" --tests "com.todoquest.feature.shop.InventoryViewModelTest"
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. catalog/loader와 Compose fixture 테스트를 먼저 추가하고 placeholder만 나오는 예상 실패를 확인한다.
2. renderer와 네 UI 연결을 구현하고 AC를 실행한다.
3. 실제 PNG가 상점에서는 bounds-fit 되지만 캐릭터 합성에서는 full 64×64 원점을 유지하는지 코드로 확인한다.
4. null/unknown/decode 실패가 구매·장착·일정·보상 흐름을 실패시키지 않는지 확인한다.
5. task index의 step 5를 `completed`로 바꾸고 네 표시 위치, cache와 fallback을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Compose나 ViewModel에 asset 파일명을 조건문으로 흩어 놓지 마라. 이유: artwork catalog가 단일 mapping source여야 한다.
- 이미지 실패를 Shop 전체 오류나 구매·장착 실패로 승격하지 마라. 이유: presentation failure는 핵심 로컬 기능과 격리해야 한다.
- bitmap에 bilinear filtering, antialiasing 또는 tint를 적용하지 마라. 이유: hard pixel art를 보존해야 한다.
- 사용자 노출 영문 문구를 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 테스트보다 UI 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
