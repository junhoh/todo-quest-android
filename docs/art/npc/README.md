# NPC 아트 인덱스

상점과 마을에서 사용하는 NPC의 canonical 픽셀 아트와 기계 검증 계약을 안내한다.

## 대장장이 상점 주인

- canonical PNG: [대장장이 상점 주인 정면 대기 스프라이트](todo-quest-blacksmith-shopkeeper-front-idle.png)는 투명 배경의 `64×64` RGBA 원본이며 아트 수정의 유일한 bitmap source다.
- canonical 계약: [대장장이 상점 주인 명세](todo-quest-blacksmith-shopkeeper-front-idle-spec.json)는 양 끝 포함 불투명 bounds `[14,13,50,58]`, 정확히 16색의 필수 팔레트, 공통 외곽선 `#263B5A`, alpha `0/255`, 중심축 `x=32`, 양발 기준선 `y=58`과 의미 영역을 정의한다. 최종 PNG는 반투명 pixel, chroma key, 배경과 지면 그림자를 포함하지 않는다.
- 비편집 reference: [캐릭터 base-body](../character/todo-quest-character-base-body.png), [기본 장착 8배율](../character/previews/default-equipped@8x.png), [팔레트 8배율](../character/previews/palette@8x.png)은 스타일·logical pixel 크기·공통 외곽선·명암 단계 비교에만 사용한다. 플레이어 reference는 NPC 생성물의 원천이나 편집 대상이 아니며 NPC PNG에 플레이어 얼굴·머리·장비 pixel을 합성하지 않는다.
- runtime PNG: [Android drawable-nodpi resource](../../../app/src/main/res/drawable-nodpi/todo_quest_blacksmith_shopkeeper_front_idle.png)는 canonical PNG를 재인코딩·리사이즈하지 않은 byte-identical 배포본이다. `drawable-nodpi`에만 두어 Android 밀도별 재샘플링과 분리하고, Shop의 비대화형 인사 카드가 전체 `64×64` logical canvas를 `104dp` frame에 `FilterQuality.None`으로 표시한다. decode 실패는 Material `Build` icon으로 격리한다.

## 능력치 안내 요정

- canonical PNG: [능력치 안내 요정 정면 대기 스프라이트](todo-quest-fairy-guide-front-idle.png)는 투명 배경의 `64×64` RGBA 원본이며 아트 수정의 유일한 bitmap source다.
- canonical 계약: [능력치 안내 요정 명세](todo-quest-fairy-guide-front-idle-spec.json)는 양 끝 포함 불투명 bounds `[6,22,58,52]`, 정확히 16색의 필수 팔레트, 공통 외곽선 `#263B5A`, alpha `0/255`, 중심축 `x=32`, 양발 끝 `y=52`와 날개·별·표정 의미 영역을 정의한다. 최종 PNG는 반투명 pixel, chroma key, 배경·지면 그림자·text와 particle을 포함하지 않는다.
- 비편집 reference: [캐릭터 base-body](../character/todo-quest-character-base-body.png)는 style·logical scale·공통 외곽선·명암 단계 비교에만 사용한다. 플레이어 reference는 요정 생성물의 원천이나 편집 대상이 아니며 요정 PNG에 플레이어 얼굴·머리·장비 pixel을 합성하지 않는다.
- runtime PNG: [Android drawable-nodpi resource](../../../app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png)는 SHA-256 `8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0`인 canonical PNG의 byte-identical 배포본이다. Character 능력치 안내 Dialog가 전체 `64×64` logical canvas를 `96dp` frame에 `ContentScale.Fit`·`FilterQuality.None`으로 표시하고 decode 실패는 Material `Info` icon으로 격리한다.

## 검증

저장소 루트에서 기존 spec-driven 몬스터 스프라이트 검증기로 canonical PNG의 크기·bounds·16색 팔레트·외곽선·binary alpha 계약을 검사하고, NPC 회귀 테스트와 SHA-256 비교로 runtime resource의 byte identity를 확인한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png --spec docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py --basetemp build\pytest-55-blacksmith-final
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_blacksmith_shopkeeper_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime blacksmith sprite differs from canonical art' }
```

능력치 안내 요정은 다음 명령으로 같은 계약과 runtime byte identity를 검증한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py --basetemp build\pytest-56-fairy-art
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
```
