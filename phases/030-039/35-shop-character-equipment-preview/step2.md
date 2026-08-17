# Step 2: render-shop-character-equipment-preview

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/java/com/todoquest/ui/character/LayeredCharacterSprite.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/030-039/35-shop-character-equipment-preview/step1.md`
- `/phases/030-039/35-shop-character-equipment-preview/index.json`

## 작업

Compose UI 테스트를 먼저 작성하고 기존 `ShopScreen`을 확장한다. 상단 app bar의 back, `상점`, gold 아이콘과 3dp 간격 값, Inventory action은 유지한다. app-level 하단 navigation도 이 화면 안으로 옮기지 않는다. app bar 아래의 콘텐츠는 `캐릭터/장비 프리뷰 → 최종 능력치 → category → 판매 목록` 순서의 하나의 세로 스크롤 영역으로 구성해 작은 높이에서도 목록에 도달하게 한다. 기존 장비 카드, 구매 확인·성공 dialog와 상세 sheet 로직을 불필요하게 다시 작성하지 않는다.

다음 preview 전용 Composable을 분리한다.

```kotlin
CharacterEquipmentPreview(...)
CharacterAvatar(...)
EquipmentSlotItem(...)
CharacterStatSummary(...)
```

`CharacterAvatar`는 `CharacterRenderState(characterAppearance, characterEquippedItems)`와 기존 `LayeredCharacterSprite`를 사용하고 중앙에 캐릭터와 `Lv.N`을 표시한다. 일반 배치는 왼쪽에 `HELMET, CHEST, LEGS, SHOES`, 중앙에 level/avatar, 오른쪽에 `WEAPON, GLOVES, ACCESSORY`를 둔다. 가용 폭이 360dp 미만이거나 `fontScale >= 1.5f`이면 level/avatar 아래에 일곱 slot을 `WEAPON, HELMET, CHEST, LEGS, GLOVES, SHOES, ACCESSORY` 순서의 `FlowRow`로 전환한다.

각 `EquipmentSlotItem`은 최소 48dp 터치 영역을 사용한다. 장착된 slot은 기존 type별 아이콘/image fallback, rarity 텍스트와 `장착 중` 표시를 함께 제공한다. 비어 있는 slot은 종류별 placeholder와 한국어 빈 슬롯 설명을 제공한다. 선택 slot은 primary 계열 container·두꺼운 border와 selected semantics로 강조하고 category `FilterChip`도 기존 selected semantics를 유지한다. slot 클릭은 `ShopEvent.SelectSlot`만 전달한다.

`CharacterStatSummary`는 `공격력 → 최대 체력 → 방어력` 순서로 렌더링한다. 각 항목은 아이콘과 숫자를 하나의 `Row` 및 한국어 TalkBack 설명으로 묶고, 바깥은 `FlowRow`를 사용해 320dp·큰 글꼴에서 항목 내부가 갈라지지 않은 채 줄바꿈되게 한다. 표시 문구는 모두 `strings.xml`에 추가하고 기존 능력치/type/rarity resource를 우선 재사용한다.

`ShopScreenTest`는 캐릭터 level·shared sprite semantics, 장착 slot의 rarity/장착 표시, 빈 slot placeholder, slot 클릭 event와 category 강조, 능력치 순서·수치, 일반/compact 반응형 배치, 모든 slot의 최소 48dp 터치 영역을 검증한다. 기존 320dp·font scale 2.0 테스트는 app bar뿐 아니라 프리뷰 이후 category와 판매 카드까지 scroll로 도달하는지 확인한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. preview, slot interaction, empty placeholder, stat summary와 compact layout UI 테스트를 먼저 작성하고 실패를 확인한다.
2. AC 명령을 실행하고 Korean resource, selected semantics, 48dp 터치 영역과 세로 scroll 접근성을 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 컴포넌트·반응형 배치·접근성 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 새 캐릭터나 장비 bitmap을 생성하지 마라. 이유: 기존 shared layered renderer와 type placeholder가 승인된 fallback이다.
- 고정 높이 preview 때문에 category나 판매 목록을 화면 밖에서 접근 불가능하게 만들지 마라. 이유: 320dp·큰 글꼴 접근성 계약을 위반한다.
- rarity나 선택 상태를 색상만으로 전달하지 마라. 이유: 텍스트·border·semantics가 함께 필요하다.
- 기존 테스트를 깨뜨리지 마라.
