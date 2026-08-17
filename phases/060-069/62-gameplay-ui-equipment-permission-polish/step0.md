# Step 0: preserve-injured-player-position

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapLayout.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleMapLayoutTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

순수 Kotlin 회귀 테스트를 먼저 추가하고, 중상 배지가 표시되어도 Battle Map의 플레이어 스프라이트 좌표가 바뀌지 않게 한다.

`BattleMapLayout.calculatePlayerStatusLayout(...)`은 입력으로 받은 `spritePlacement`를 그대로 `BattlePlayerStatusLayout.sprite`에 반환해야 한다. 현재처럼 HUD·HP bar·중상 배지를 수용하기 위해 `spritePlacement.top`을 `minimumActorTop`까지 내리지 않는다. HP bar와 중상 배지만 map 경계, HUD 하단, actor 중심을 기준으로 각각 clamp한다. 공간이 부족하면 status presentation의 간격을 축소하거나 map 안에서 위쪽 요소를 clamp하되 actor의 `left`, `top`, `width`, `height`는 변경하지 않는다.

테스트는 동일한 map/sprite 입력에서 중상 status layout을 계산하기 전후의 player sprite 좌표가 byte-for-byte 같은 값인지, compact map에서도 sprite가 이동하지 않고 HP와 badge bounds가 유효한지 검증한다. Compose 테스트에는 중상 적용 전후 player node의 bounds top/left가 같다는 회귀 시나리오를 추가한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleMapLayoutTest" --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
git diff --check
```

## 검증 절차

1. 실패하는 player-position 회귀 테스트를 먼저 작성한 뒤 layout 계산을 수정한다.
2. 일반 상태와 중상 상태에서 player sprite의 좌표와 크기가 동일한지 확인한다.
3. HP bar와 중상 badge가 map 바깥 또는 진행 HUD 위로 벗어나지 않는지 확인한다.
4. 연결된 Android device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
5. 성공 시 task index의 step 0을 `completed`로 바꾸고 수정한 layout 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 중상 배지를 위한 공간을 만들기 위해 player sprite를 아래로 이동하지 마라. 이유: 중상은 presentation overlay이며 actor의 전투 위치를 바꾸면 안 된다.
- persisted combat position이나 중상 도메인 상태를 변경하지 마라. 이유: 결함은 Battle Map layout 계산에 있다.
- monster 배치 또는 전투 이벤트 순서를 수정하지 마라. 이유: 이 step의 범위는 player status presentation이다.
- 기존 테스트를 삭제하거나 완화하지 마라.
