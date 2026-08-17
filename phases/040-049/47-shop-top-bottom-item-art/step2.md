# Step 2: extend-outfit-loadout-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 새 top/bottom ID가 거부되는 예상 실패를 확인한 뒤 domain loadout catalog만 확장한다. Android asset, UI, DAO와 Repository 구현은 수정하지 않는다.

`CharacterLoadoutCatalog`에 다음 공개 상수를 추가한다.

```kotlin
const val TOP_CLOTH = "top_cloth"
const val TOP_LEATHER_ARMOR = "top_leather_armor"
const val TOP_IRON_BREASTPLATE = "top_iron_breastplate"
const val BOTTOM_CLOTH_PANTS = "bottom_cloth_pants"
const val BOTTOM_LEATHER_PANTS = "bottom_leather_pants"
const val BOTTOM_STEEL_GREAVES = "bottom_steel_greaves"
```

`topIds`에는 기존 default/adventure와 새 상의 3개를, `bottomIds`에는 기존 default/adventure와 새 하의 3개를 허용한다. `defaultEquippedItems`와 head/shoes/accessory/weapon catalog는 변경하지 않는다.

테스트는 새 top/bottom ID 각각의 유효성, `top_unknown`, `bottom_unknown`, 공백과 반대 slot ID 거부, 기존 상수·기본 loadout 보존, top 또는 bottom 교체 시 나머지 field 불변을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. domain 테스트를 먼저 작성하고 새 상수가 없는 예상 실패를 확인한다.
2. 최소 catalog 변경 후 AC를 실행한다.
3. gameplay 소유권, modifier, Room entity와 기본 appearance가 변경되지 않았는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 여섯 ID와 기본값 보존을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- imageKey, asset 경로나 Android 타입을 domain model에 넣지 마라. 이유: domain loadout은 UI asset 위치를 알지 않아야 한다.
- 기본 top/bottom을 새 상점 장비로 바꾸지 마라. 이유: gameplay 장비가 없을 때의 fallback을 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
