# 몬스터 아트 인덱스

## 현재 canonical 자산

| 종족 | canonical PNG | 좌표·팔레트·외곽선 계약 | runtime 사본 |
|---|---|---|---|
| 고블린 정찰병 | [정면 대기 스프라이트](todo-quest-goblin-scout-front-idle.png) | [고블린 정찰병 명세](todo-quest-goblin-scout-front-idle-spec.json) | `app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png` |
| 해골 병사 | [정면 대기 스프라이트](todo-quest-skeleton-soldier-front-idle.png) | [해골 병사 명세](todo-quest-skeleton-soldier-front-idle-spec.json) | `app/src/main/res/drawable-nodpi/todo_quest_skeleton_soldier_front_idle.png` |
| 타락한 나무 정령 | [정면 대기 스프라이트](todo-quest-corrupted-tree-spirit-front-idle.png) | [타락한 나무 정령 명세](todo-quest-corrupted-tree-spirit-front-idle-spec.json) | `app/src/main/res/drawable-nodpi/todo_quest_corrupted_tree_spirit_front_idle.png` |
| 하피 | [정면 대기 스프라이트](todo-quest-harpy-front-idle.png) | [하피 명세](todo-quest-harpy-front-idle-spec.json) | `app/src/main/res/drawable-nodpi/todo_quest_harpy_front_idle.png` |
| 슬라임 | [정면 대기 스프라이트](todo-quest-slime-front-idle.png) | [슬라임 명세](todo-quest-slime-front-idle-spec.json) | `app/src/main/res/drawable-nodpi/todo_quest_slime_front_idle.png` |

`docs/art/monster/`의 PNG와 JSON이 편집·검증의 canonical source다. `drawable-nodpi` PNG는 Android runtime용 byte-identical 사본이며 별도 원본으로 수정하지 않는다. 다섯 자산은 모두 `64×64` RGBA, binary alpha, 공통 외곽선 `#263B5A`와 발 기준선 `y=58`을 사용한다. 고블린·해골의 불투명 영역은 양 끝 포함 `[16, 13, 48, 58]`, 높이 `46px`이고, 타락한 나무 정령은 `[14, 11, 50, 58]`, 하피는 `[11, 11, 53, 58]`과 높이 `48px`을 사용한다. 슬라임은 `[17, 35, 47, 58]`과 높이 `24px`, 명세에 고정된 12색 팔레트를 사용한다. 하피의 양 끝 포함 왼쪽 날개 `[11, 26, 24, 47]`와 오른쪽 날개 `[40, 26, 53, 47]`는 `x=32` 축을 기준으로 RGBA 픽셀이 정확히 대칭이다. 슬라임 명세의 `water`·`slime`은 시각 콘셉트와 종족 식별 metadata이며 상성·상태 효과·스킬·전리품·biome 또는 새 맵 배경을 추가하지 않는다.

JSON 명세 내부의 [캐릭터 base-body](../character/todo-quest-character-base-body.png) 참조는 캐릭터와 몬스터의 상대 크기와 기준선을 비교하기 위한 것이다. 이 참조나 몬스터 아트 명세는 전투 스탯의 저장 장소가 아니다. 몬스터 능력치가 추가되면 [게임 설계 문서](../../game-design/README.md)에서 관리한다.

## 검증

저장소 루트에서 [몬스터 스프라이트 검증기](../../../scripts/validate_monster_sprite.py)를 실행한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png --spec docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-harpy-front-idle.png --spec docs\art\monster\todo-quest-harpy-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-slime-front-idle.png --spec docs\art\monster\todo-quest-slime-front-idle-spec.json
```

runtime 사본 경계는 canonical PNG와 SHA-256이 같은지 별도로 확인한다.

```powershell
$goblinCanonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-goblin-scout-front-idle.png).Hash
$goblinRuntime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_goblin_scout_front_idle.png).Hash
$skeletonCanonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-skeleton-soldier-front-idle.png).Hash
$skeletonRuntime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_skeleton_soldier_front_idle.png).Hash
$treeSpiritCanonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png).Hash
$treeSpiritRuntime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_corrupted_tree_spirit_front_idle.png).Hash
$harpyCanonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-harpy-front-idle.png).Hash
$harpyRuntime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_harpy_front_idle.png).Hash
$slimeCanonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-slime-front-idle.png).Hash
$slimeRuntime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_slime_front_idle.png).Hash
if ($goblinCanonical -ne $goblinRuntime -or $skeletonCanonical -ne $skeletonRuntime -or $treeSpiritCanonical -ne $treeSpiritRuntime -or $harpyCanonical -ne $harpyRuntime -or $slimeCanonical -ne $slimeRuntime) { throw 'Runtime monster sprite differs from canonical art' }
```
