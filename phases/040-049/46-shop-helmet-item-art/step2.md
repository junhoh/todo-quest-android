# Step 2: extend-headgear-loadout-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/test/java/com/todoquest/ui/character/CharacterLayerCatalogTest.kt`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

순수 Kotlin 테스트를 먼저 추가해 새 head ID가 거부되는 예상 실패를 확인한 뒤 domain loadout catalog만 확장한다. Android asset, UI, DAO와 Repository 구현은 수정하지 않는다.

`CharacterLoadoutCatalog`에 다음 공개 상수를 추가한다.

```kotlin
const val HEADGEAR_LEATHER_HAT = "headgear_leather_hat"
const val HEADGEAR_IRON_HELMET = "headgear_iron_helmet"
```

`headIds`는 기존 `null`, `HEADGEAR_ADVENTURE`와 새 두 값을 허용한다. `defaultEquippedItems.headId`는 기존 `HEADGEAR_ADVENTURE`를 유지한다. top, bottom, shoes, accessory와 weapon catalog는 바꾸지 않는다.

`/app/src/test/java/com/todoquest/domain/CharacterLoadoutCatalogTest.kt`를 생성하거나 동등한 domain test에 다음을 먼저 고정한다.

- 두 새 head ID를 각각 사용한 `EquippedItems`는 유효하다.
- `headgear_unknown`, 공백, 다른 slot ID는 계속 거부된다.
- 기존 default loadout과 모든 기존 ID 값은 동일하다.
- head 교체는 다른 여섯 appearance field를 바꾸지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterLoadoutCatalogTest"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. domain 테스트를 먼저 작성하고 새 상수가 없는 예상 실패를 확인한다.
2. 최소 catalog 변경 후 AC를 실행한다.
3. gameplay 소유권, 장비 modifier, Room entity와 기본 appearance가 변경되지 않았는지 확인한다.
4. task index의 step 2를 `completed`로 바꾸고 새 head ID와 default 보존을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- imageKey, 파일 경로나 Android 타입을 domain model에 넣지 마라. 이유: domain loadout은 UI asset 위치를 알지 않아야 한다.
- 기본 파란 모자를 새 상점 투구로 바꾸지 마라. 이유: gameplay 투구가 없을 때의 appearance fallback을 보존해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
