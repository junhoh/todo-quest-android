# Battle Map 아트 인덱스

Calendar Battle Map에서 전투 개체와 분리해 렌더링하는 환경 배경 자산을 관리한다.

## 현재 기준 자산

- 기본 초원 배경: [Todo Quest 초원 Battle Map](todo-quest-battle-map-grassland.png)
- 크기·불투명도·해시·runtime 사본 검증 계약: [초원 Battle Map 명세](battle-map-grassland-spec.json)

배경 PNG에는 캐릭터, 몬스터, 생명체, HUD, 문자와 전투 효과를 합성하지 않는다. 플레이어와 몬스터는 별도 runtime sprite layer로 배치한다. 최종 배경은 built-in image generation 결과를 중앙 기준으로 2.4:1 crop한 뒤 `Image.Resampling.LANCZOS`로 `1200×500px`에 정규화한 opaque PNG다. 색상이나 물체를 로컬 후처리로 추가하거나 삭제하지 않는다.

## Canonical 자산과 runtime resource

- 문서 canonical 배경은 `todo-quest-battle-map-grassland.png`다.
- runtime 배경은 `app/src/main/res/drawable-nodpi/battle_map_grassland.png`이며 canonical 배경과 byte-for-byte 동일해야 한다.
- canonical 고블린 스프라이트의 runtime 사본은 `app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png`이며 원본과 byte-for-byte 동일해야 한다.
- 플레이어의 기존 모듈형 시트 runtime resource는 이 자산 준비 범위에서 변경하지 않는다.

## Stage 배경 교체

기본 `BattleMapTheme()`은 `R.drawable.battle_map_grassland`를 사용한다. 다른 stage는 동일한 역할의 opaque 배경을 `app/src/main/res/drawable-nodpi/battle_map_<stage>.png`로 추가하고 presentation 호출부에서 theme만 바꾼다. 예를 들어 `battle_map_forest.png`를 추가했다면 다음처럼 선택한다.

```kotlin
BattleMap(
    state = state,
    theme = BattleMapTheme(
        backgroundResId = R.drawable.battle_map_forest,
    ),
)
```

새 배경도 2.4:1 화면비와 actor 안전 영역을 유지하고, unit·HUD·문자·전투 효과를 포함하지 않는다. `backgroundResId` 교체는 presentation 설정이며 `CombatRepository`, Room schema, monster slot을 변경하지 않는다. resource decode가 실패하면 `BattleMap`이 sky·mountain·forest·field·road·ground detail을 나눈 Canvas fallback을 사용한다.

## 수동 시각 검수

명세의 `manualVisualReview`를 기준으로 다음을 직접 확인한다.

- 사람, 캐릭터, 몬스터, 동물과 생명체 실루엣이 없다.
- 체력바, 피해 숫자, HUD, 아이콘, 문자, 로고와 워터마크가 없다.
- 하늘, 원경 산과 숲, 중경 마을 또는 울타리, 초원과 흙길, 평평한 전경 전투 지면이 읽힌다.
- player `x=0.20`과 monster `x=0.55..0.95`, `y=0.72..0.88` 안전 영역에 큰 전경 물체가 없다.
- 낮은 채도와 절제된 대비를 유지해 별도 픽셀 sprite가 선명하게 보인다.

2026-07-21 수동 visual QA에서는 canonical 초원 배경과 runtime 사본이 같은 독립 배경 resource이고, 배경 PNG 안에 player·monster unit, 체력바, 피해 숫자, HUD 또는 text가 없음을 확인했다. player modular sheet와 goblin sprite도 각각 별도 투명 runtime resource로 유지되므로 Compose의 player·monster layer에서 독립 합성된다.

## 검증

저장소 루트에서 [Battle Map 검증기](../../../scripts/validate_battle_map.py)를 실행한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_battle_map.py --image docs\art\battle\todo-quest-battle-map-grassland.png --spec docs\art\battle\battle-map-grassland-spec.json --runtime app\src\main\res\drawable-nodpi\battle_map_grassland.png
```
