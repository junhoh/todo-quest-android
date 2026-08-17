# Step 2: Battle Map UI 모델과 배치 계산 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

Android ViewModel이나 database 없이 검증 가능한 presentation 모델과 순수 pixel 배치 계산을 테스트 우선으로 구현한다.

- `app/src/main/java/com/todoquest/feature/battle/`에 다음 수준의 계약을 둔다.
  - `data class BattlePosition(val x: Float, val y: Float)` — finite `0f..1f`만 허용한다.
  - `enum class BattleUnitType { PLAYER, MONSTER }`.
  - `data class BattleSpriteFrame(...)` — source rect와 frame 내부의 normalized ground anchor를 담는다.
  - `data class BattleSpriteUiModel(@DrawableRes val spriteResId: Int, val frame: BattleSpriteFrame)`.
  - `data class BattleUnitUiModel(...)` — nonblank id, type, sprite, position, positive scale, signed `groundOffset`, `currentHp`, positive `maxHp`, 한국어 이름 resource를 담고 HP 범위를 검증한다.
  - `sealed interface BattleMapUiState` 또는 동등한 immutable state로 Loading, Content(player 1명과 0~4 monsters, stageNumber), Unavailable을 표현한다.
  - `BattleMapTheme`은 기본 `battle_map_grassland` drawable id와 향후 다른 stage drawable을 전달할 수 있는 resource 계약을 갖는다.
- `BattleMonsterSlots.forCount(count)`는 다음 preset을 반환하며 0..4 외 count는 거부한다.
  - 0: `[]`
  - 1: `(0.76, 0.82)`
  - 2: `(0.66, 0.83)`, `(0.84, 0.78)`
  - 3: `(0.60, 0.82)`, `(0.76, 0.78)`, `(0.90, 0.84)`
  - 4: `(0.57, 0.77)`, `(0.69, 0.86)`, `(0.82, 0.76)`, `(0.92, 0.85)`
- player 기본 위치는 `(0.20, 0.82)`로 별도 상수화한다. Composable 내부에 resource·position·slot을 하드코딩하지 않는다.
- source frame은 player sheet `(0,0,64,64)`, goblin `(0,0,64,64)`이며 둘 다 ground anchor `(0.5,58/64)`를 사용한다. 원본의 투명 아래 여백을 crop하지 않는다.
- 순수 `BattleMapLayout` 계산은 다음 식을 사용한다.
  - `left = x * mapWidth - spriteWidth * groundAnchorX`
  - `top = y * mapHeight - spriteHeight * groundAnchorY + groundOffsetPx`
  - 결과 top-left는 map bounds 안으로 clamp한다.
- unit render ordering은 작은 y부터 큰 y 순이며 동률은 입력 순서 또는 stable id로 결정적으로 유지한다. 큰 y가 높은 `zIndex`를 얻도록 한다.
- JVM unit test를 먼저 작성해 모델 validation, 정확한 기본 슬롯, bottom-center 변환, ground offset 부호, 네 방향 clamp와 depth order를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.feature.battle.*"
.\gradlew.bat :app:compileDebugKotlin
git diff --check
```

## 검증 절차

1. 실패하는 unit test를 먼저 확인한 뒤 구현하고 AC를 통과시킨다.
2. 모델이 `domain/`이나 `data/`에 Android resource id를 유입하지 않는지 확인한다.
3. task index의 step 2를 `completed`로 바꾸고 모델·slot·좌표식 결정을 한국어 `summary`로 기록한다.

## 금지사항

- UI resource id를 domain model에 추가하지 마라. 이유: resource와 위치는 presentation concern이다.
- 화면 pixel이나 dp 좌표를 slot 상수로 저장하지 마라. 이유: 기기 독립적인 normalized 배치를 유지해야 한다.
- source PNG의 불투명 bounding box로 재크롭하지 마라. 이유: player와 monster의 공통 64×64 기준선 계약이 깨진다.
- 기존 테스트를 깨뜨리지 마라.
