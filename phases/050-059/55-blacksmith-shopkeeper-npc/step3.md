# Step 3: render-blacksmith-shopkeeper-greeting

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/ui/character/LayeredCharacterSprite.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/res/drawable-nodpi/todo_quest_blacksmith_shopkeeper_front_idle.png`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/step2.md`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/index.json`

## 작업

Compose test를 먼저 작성해 실패를 확인한 뒤 Shop의 첫 스크롤 항목에 비대화형 대장장이 인사 카드를 추가한다. Room, Repository, UseCase, ViewModel, `ShopUiState`, 구매·장착·해제 event는 변경하지 않는다.

먼저 `ShopScreenTest`에 다음 동작을 검증하는 테스트를 추가한다.

- `shop-shopkeeper-greeting`이 `shop-character-preview`보다 위에 있고 `shop-equipment-list`의 첫 콘텐츠 item이다.
- `대장장이`와 `어서 오게. 필요한 장비를 골라 보게.`가 정확히 표시된다.
- merged TalkBack 설명이 `장비 상점 대장장이. 어서 오게. 필요한 장비를 골라 보게.`이고 카드에 click action이 없다.
- 정상 runtime bitmap이 표시되며 104dp 논리 캔버스와 `FilterQuality.None` 경로를 사용한다.
- 일반 폭에서는 sprite가 문구 왼쪽에, 폭 360dp 미만 또는 font scale 1.5 이상에서는 sprite가 문구 위에 있으며 bounds가 겹치지 않는다.
- `spriteResId = 0`을 주입한 decode 실패 fixture도 예외 없이 같은 호칭·인사·TalkBack을 유지하고 `Build` Material icon fallback을 표시한다.
- 320×640dp, font scale 2.0에서 인사 카드 추가 뒤에도 back, 골드, category와 판매 card에 scroll로 도달할 수 있다.

`/app/src/main/res/values/strings.xml`에 아래 한국어 기본 리소스를 추가한다.

```xml
<string name="shop_blacksmith_name">대장장이</string>
<string name="shop_blacksmith_greeting">어서 오게. 필요한 장비를 골라 보게.</string>
<string name="shop_blacksmith_description">장비 상점 대장장이. 어서 오게. 필요한 장비를 골라 보게.</string>
```

`ShopScreen.kt`에 다음 내부 Compose 경계를 추가한다. 이름은 테스트와 구현에서 동일하게 유지한다.

```kotlin
@Composable
internal fun ShopkeeperGreeting(
    @DrawableRes spriteResId: Int = R.drawable.todo_quest_blacksmith_shopkeeper_front_idle,
    modifier: Modifier = Modifier,
)
```

구현 계약은 다음과 같다.

- `ShopContent`의 `LazyColumn`에서 `item(key = "shopkeeper-greeting")`을 `character-equipment-preview`보다 먼저 배치하고 horizontal padding `16.dp`를 사용한다.
- `ShopkeeperGreeting`은 `Surface`와 Material theme의 `surfaceVariant`/`outline` 계열만 사용한다. 고정 RGB나 NPC 팔레트를 UI 배경에 재사용하지 않는다.
- root는 `shop-shopkeeper-greeting` test tag와 `semantics(mergeDescendants = true)`를 사용해 정확한 `shop_blacksmith_description`을 한 번만 제공한다. click/select role이나 action을 부여하지 않는다.
- `BoxWithConstraints`에서 기존 Shop 기준과 동일하게 `maxWidth < 360.dp || fontScale >= 1.5f`를 compact 조건으로 사용한다.
- 일반 layout은 104dp sprite 왼쪽과 가중치가 있는 텍스트 Column 오른쪽의 Row다. compact layout은 가운데 정렬된 104dp sprite 위, 호칭과 인사 아래의 Column이다. 호칭은 `titleMedium` bold, 인사는 `bodyMedium`을 사용하며 글꼴 확대 시 maxLines로 자르지 않는다.
- bitmap은 `LocalContext.current.resources`와 `remember(resources, spriteResId)`에서 `runCatching { ImageBitmap.imageResource(...) }.getOrNull()`로 decode한다. 성공 시 `Image(bitmap=..., contentScale=ContentScale.Fit, filterQuality=FilterQuality.None)`를 사용하고 이미지 자체는 parent 설명과 중복되지 않게 decorative로 처리한다.
- decode 실패 시 `Icons.Default.Build`를 같은 104dp frame 안의 적절한 크기로 표시하고 `shop-shopkeeper-fallback` tag를 제공한다. 실패는 Shop의 장비 목록·구매·장착 흐름을 차단하지 않는다.
- 기존 `CharacterEquipmentPreview`, slot geometry, stat summary, category와 판매 목록 순서는 인사 카드 아래에서 그대로 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. Compose 테스트를 먼저 추가해 resource/UI/문구 부재로 실패하는 것을 확인한다.
2. 구현 뒤 AC를 실행하고 일반 폭과 320×640dp·font scale 2.0을 확인한다.
3. 카드가 비대화형이며 기존 Shop command와 ViewModel state가 변하지 않는지 확인한다.
4. 한국어 문구가 Compose나 ViewModel에 하드코딩되지 않았는지 확인한다.
5. Android 도구 또는 안정적인 connected target이 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. phase index의 step 3을 `completed`로 변경하고 인사 카드·한국어 접근성·반응형 layout·connected 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- NPC를 클릭 가능한 구매·대화 action으로 만들지 마라. 이유: 사용자가 승인한 범위는 정적 맞이 카드다.
- ViewModel이나 `ShopUiState`에 정적 drawable·문구 상태를 추가하지 마라. 이유: 화면 고정 presentation이 도메인 상태를 오염시키면 안 된다.
- UI에서 Room DAO, Repository, AlarmManager나 WorkManager를 호출하지 마라. 이유: AGENTS.md의 CRITICAL 계층 규칙을 위반한다.
- bitmap에 bilinear filtering, crop, 개별 불투명 bounds 재정렬이나 runtime recolor를 적용하지 마라. 이유: canonical 64×64 좌표와 픽셀 경계를 보존해야 한다.
- 영어 표시 문구를 하드코딩하지 마라. 이유: 새 사용자 노출 문구는 한국어 문자열 리소스가 기본이다.
- 기존 테스트를 깨뜨리지 마라.
