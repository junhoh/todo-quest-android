# Step 0: define-slime-art-contract

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
- `/docs/art/monster/todo-quest-harpy-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

`/docs/art/monster/todo-quest-slime-front-idle-spec.json`을 생성해 슬라임 한 마리의 정면 기본 대기 스프라이트 계약을 정의한다. 앱 Kotlin 코드와 Android runtime resource는 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성하고 기존 선언형 monster validator가 해석할 수 있는 필드만 사용한다.

- `asset`: 파일 이름 `todo-quest-slime-front-idle.png`, `64×64`, `RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 기준 불투명 bounds `[20,7,44,58]`, 높이 52px. 스타일·논리 크기·외곽선·명암 단계 비교용이며 편집 대상이 아니다.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [17,35,47,58]`, `opaqueHeight: 24`. 플레이어 높이의 약 46.2%이고 물방울형 몸 전체가 중심축에 맞아야 한다.
- `anchors`: `apex: [32,35]`, `eyeLineY: 45`, `coreCenter: [32,52]`, `soleY: 58`.
- `pose`: 완전 정면, 아래쪽이 넓고 위쪽이 살짝 뾰족한 둥근 물방울/젤리 실루엣, 몸을 낮게 앞으로 웅크린 듯한 중립 전투 대기 자세다. 좌우 무게가 안정적이고 넓은 몸체 바닥이 y=58에 닿는다. 회전·공격 동작·점프·분열을 금지한다.
- `groundContacts`: 왼쪽 바닥 x=20..30, 오른쪽 바닥 x=34..44의 y=58에 각각 불투명 픽셀을 최소 4개 요구한다.
- `semanticRegions`:
  - `redEyes`: `[24,43,40,48]` 안의 `dangerRed` 2~4px이며 영역 밖 사용을 금지한다. 짧고 날카롭게 안쪽으로 기울어진 두 눈에만 쓴다.
  - `internalCore`: `[28,49,36,55]` 안의 `coreDeep`, `coreMid`, `coreOrange` 합계 5~16px이며 영역 밖 사용을 금지한다. 몸 안쪽의 작고 어두운 핵으로 읽혀야 한다.
  - `surfaceHighlights`: `[20,38,44,45]` 안의 `surfaceHighlight` 3~8px이며 영역 밖 사용을 금지한다. 서로 연결된 1~2개의 불투명 밝은 픽셀 덩어리로만 표현한다.
- `palette`는 다음 12개 고유 색을 사용하고 `requiredPaletteNames`에 모두 포함한다. 투명 픽셀은 색 수에서 제외한다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `waterDeep: #124A5A`
  - `waterShadow: #176778`
  - `waterBase: #238FA0`
  - `waterMid: #3DAEBC`
  - `waterLight: #72CDD2`
  - `surfaceHighlight: #C3F0E9`
  - `coreDeep: #173041`
  - `coreMid: #2B4B5D`
  - `dangerRed: #E05252`
  - `coreOrange: #E58A3C`
- `outline`: 공통 색 `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계 전체가 외곽선 색이며 외부 경계의 2×2 외곽선 블록을 금지한다.
- `connectivity`: 모든 불투명 픽셀은 하나의 8-connected 구성요소이고 고립 픽셀을 허용하지 않는다. 몸 주변에 분리된 물방울이나 점액 파편이 없어야 한다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, 최종 PNG 안의 크로마키는 금지한다.
- `design`: `water`, `slime`, 게임 시작 지역의 최하위 일반 적, 단순·본능적이지만 적대적, 몸을 튕겨 부딪치는 매우 낮은 위협도의 근접 공격, 귀엽지만 명확히 전투형인 고전 판타지 점액 생물을 기록한다. 팔·다리·장비·무기·이빨·피·잔혹 표현·반투명 몸·특정 게임 대표 슬라임 모방을 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/monster/todo-quest-slime-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-slime-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['centerX']==32 and s['soleY']==58; assert s['expectedOpaqueBoundingBox']==[17,35,47,58]; assert s['opaqueHeight']==24; assert len(s['palette'])==12 and len(set(s['palette'].values()))==12; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 플레이어와 기존 네 몬스터 PNG의 실제 크기, mode, bounds, 공통 외곽선 색을 읽기 전용으로 확인한다.
3. 사용자 규격과 PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. task index의 step 0을 `completed`로 변경하고 계약 경로, 24px 높이, 좌표, 12색과 의미 영역을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱 Kotlin 코드나 Android resource를 수정하지 마라. 이유: 이 step은 canonical 아트 계약만 정의한다.
- 플레이어 또는 기존 몬스터의 형태나 픽셀을 복사하지 마라. 이유: 기존 이미지는 스타일과 크기 참조일 뿐이다.
- 13번째 불투명 색을 추가하지 마라. 이유: 사용자의 최대 12색 요구를 계약으로 고정해야 한다.
- 종족 전용 분기를 validator에 추가하지 마라. 이유: 기존 선언형 schema로 모든 규격을 표현할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
