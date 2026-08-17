# Step 1: validate-mirrored-monster-regions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`
- `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json`
- `/docs/art/monster/todo-quest-harpy-front-idle-spec.json`
- `/phases/040-049/42-harpy-enemy/index.json`

## 작업

테스트를 먼저 작성하고 실패를 확인한 뒤 `/scripts/validate_monster_sprite.py`의 schema v1에 선택적 `mirroredRegions` 검증을 추가한다. 기존 CLI와 기존 세 monster spec의 동작은 유지한다.

지원 인터페이스는 다음 의미로 고정한다.

```json
{
  "mirroredRegions": {
    "wings": {
      "axisX": 32,
      "leftRegion": [11, 26, 24, 47],
      "rightRegion": [40, 26, 53, 47],
      "regionInclusive": true,
      "comparison": "rgba"
    }
  }
}
```

- 필드가 없으면 검증을 생략한다.
- 각 이름은 object이고 axis와 좌우 네 좌표는 정수여야 한다.
- 좌우 region은 같은 높이·폭이고 `xRight = 2 * axisX - xLeft` 관계로 서로 정확히 반사돼야 한다.
- `comparison`은 이번 schema에서 `rgba`만 허용하며 왼쪽의 모든 픽셀을 중심축으로 반사한 오른쪽 픽셀과 정확히 비교한다. 투명 픽셀의 RGB까지 포함해 RGBA 전체를 비교한다.
- 영역이 image bounds를 벗어나거나 서로 겹치거나 중심축을 침범하면 명확한 schema 오류를 낸다.
- 불일치 메시지는 이름, 첫 좌우 좌표와 두 RGBA 값을 포함한다.

테스트는 field 부재 통과, 정확한 미러 통과, 불투명 mask 불일치, 색상 불일치, alpha 불일치, 잘못된 region 크기·axis·comparison·bounds를 포함한다. 기존 fixture와 세 canonical spec의 회귀를 유지한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py --basetemp build\pytest-42-harpy-mirror
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle.png --spec docs\art\monster\todo-quest-corrupted-tree-spirit-front-idle-spec.json
git diff --check
```

## 검증 절차

1. 새 실패 fixture를 먼저 작성해 구현 전 실패를 확인한다.
2. optional parser와 validation을 구현하고 AC 명령을 실행한다.
3. 기존 spec에 `mirroredRegions`를 소급 추가하지 않아도 모두 통과하는지 확인한다.
4. task index의 step 1을 `completed`로 변경하고 optional schema와 회귀 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `mirroredRegions` 부재를 오류로 처리하지 마라. 이유: 기존 세 자산의 schema v1 호환성을 유지해야 한다.
- 이미지 픽셀을 validator가 수정하게 만들지 마라. 이유: validator는 read-only 계약 검사기다.
- 좌우 불투명 bounds만 비교하고 색·alpha를 생략하지 마라. 이유: 하피 날개의 정확한 픽셀 대칭 계약을 검증해야 한다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
