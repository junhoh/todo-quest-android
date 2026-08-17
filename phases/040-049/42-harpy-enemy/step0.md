# Step 0: define-harpy-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/README.md`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`
- `/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/phases/040-049/42-harpy-enemy/index.json`

## 작업

`/docs/art/monster/todo-quest-harpy-front-idle-spec.json`을 생성해 하피 한 명의 정면 기본 대기 스프라이트 계약을 정의한다. 앱 Kotlin 코드와 Android runtime resource는 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성한다.

- `asset`: 파일 이름 `todo-quest-harpy-front-idle.png`, `64×64`, `RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 기준 불투명 bounds `[20,7,44,58]`, 높이 52px. 스타일·논리 크기·외곽선·명암 단계 비교용이며 편집 대상이 아니다.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [11,11,53,58]`, `opaqueHeight: 48`. 플레이어보다 약 7.7% 작고 양 날개를 포함한 전체 폭이 중심축에 맞아야 한다.
- `anchors`: `headCenter: [32,20]`, `shoulderY: 29`, `waistY: 42`, `soleY: 58`.
- `pose`: 완전 정면, 큰 인간형 머리와 짧은 몸통의 3등신 치비, 중심축 고정, 날개형 양팔을 몸 옆에서 조금 벌린 중립 전투 대기 자세, 두 발이 y=58에 닿는다. 비행·회전·공격 동작·보행 자세를 금지한다.
- `groundContacts`: 왼발 x=24..30, 오른발 x=34..40의 y=58에 각각 불투명 픽셀을 최소 2개 요구한다.
- `transparentRegions`: 다리 사이 `[30,49,34,57]`, 왼쪽 날개와 몸 사이 `[23,34,25,45]`, 오른쪽 날개와 몸 사이 `[39,34,41,45]`에 각각 완전 투명 픽셀을 최소 4개, 6개, 6개 요구한다.
- `mirroredRegions.wings`: 중심축 x=32, 왼쪽 `[11,26,24,47]`, 오른쪽 `[40,26,53,47]`, inclusive rectangle, `comparison: rgba`로 고정한다. 이 필드는 step 1에서 optional schema로 validator에 추가한다.
- `semanticRegions`:
  - `redEyes`: `[27,18,37,24]` 안의 `dangerRed` 2~4px이며 영역 밖 사용을 금지한다.
  - `talons`: `[22,50,42,58]` 안의 `clawShadow`와 `clawLight` 합계 8~32px이며 영역 밖 사용을 금지한다.
  - `wornCloth`: `[24,31,40,49]` 안의 세 cloth 색 합계 8~48px이며 영역 밖 사용을 금지한다.
- `palette`와 `requiredPaletteNames`는 다음 15개 고유 색을 모두 사용하며 투명 픽셀은 색 수에서 제외한다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `featherDeep: #203B46`
  - `featherShadow: #2E5960`
  - `featherBase: #3F7680`
  - `featherHighlight: #6A9AA0`
  - `skinShadow: #A66B52`
  - `skinBase: #D69870`
  - `skinHighlight: #F0C09C`
  - `clothShadow: #4A3225`
  - `clothBase: #765238`
  - `clothHighlight: #A3744C`
  - `clawShadow: #737982`
  - `clawLight: #B7B0A3`
  - `dangerRed: #E05252`
- `outline`: 공통 `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계 전체가 outline 색, 외부 outline 2×2 block 금지.
- `connectivity`: 불투명 픽셀은 8-neighbor 단일 component이며 고립 픽셀을 금지한다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, 최종 PNG에 chroma key를 금지한다.
- `design`: `wind`, `flight`, `mountain`, `grassland` 시각 속성, 낮은 위협의 빠른 일반 근접 적, 인간형 머리·몸통과 새 날개·짧은 다리·발톱, 헝클어진 짙은 청록 깃털, 낡은 짧은 상의·허리 천, 귀엽지만 명확히 적대적인 실루엣을 기록한다. 무기·기계·총기·현대 요소·잔혹 표현·기존 캐릭터 모방을 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -m json.tool docs\art\monster\todo-quest-harpy-front-idle-spec.json > $null
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py --basetemp build\pytest-42-harpy-contract
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 좌표·팔레트·대칭·투명 영역이 사용자 규격과 기존 canonical 자산 계약에 맞는지 확인한다.
3. 앱 코드, Room schema와 기존 PNG가 변경되지 않았는지 확인한다.
4. task index의 step 0을 `completed`로 변경하고 spec 경로와 핵심 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- spec에 몬스터 능력치나 biome 저장 구조를 넣지 마라. 이유: 아트 계약은 전투 상태의 source of truth가 아니다.
- 16색을 초과하는 팔레트를 정의하지 마라. 이유: 모바일 픽셀아트 규격을 위반한다.
- 플레이어 또는 기존 몬스터 이미지를 수정하지 마라. 이유: 참조 자산은 스타일 비교용이다.
- 기존 테스트를 깨뜨리지 마라.
