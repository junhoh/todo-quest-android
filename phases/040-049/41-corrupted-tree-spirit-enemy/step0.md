# Step 0: define-corrupted-tree-spirit-art-contract

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
- `/scripts/validate_monster_sprite.py`
- `/phases/040-049/41-corrupted-tree-spirit-enemy/index.json`

## 작업

`/docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json`을 생성해 타락한 나무 정령 한 명의 정면 기본 대기 스프라이트 계약을 정의한다. 앱 Kotlin 코드와 Android runtime resource는 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성하고 기존 선언형 monster validator가 해석할 수 있는 필드만 사용한다.

- `asset`: 파일 이름 `todo-quest-corrupted-tree-spirit-front-idle.png`, `64×64`, `RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 기준 불투명 bounds `[20,7,44,58]`, 높이 52px. 스타일·논리 크기·외곽선·명암 단계 비교용이며 편집 대상이 아니다.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [14,11,50,58]`, `opaqueHeight: 48`. 플레이어보다 약 7.7% 작고 양쪽 머리 가지와 벌린 팔을 포함한 전체 폭이 중심축에 맞아야 한다.
- `anchors`: `headCenter: [32,21]`, `shoulderY: 30`, `waistY: 42`, `soleY: 58`.
- `pose`: 완전 정면, 큰 머리와 짧고 굵은 몸통의 3등신 치비, 중심축 고정, 양팔을 몸 옆에서 조금 벌린 중립 전투 대기 자세, 양 뿌리발이 y=58에 닿는다. 회전·공격 동작·보행 자세를 금지한다.
- `groundContacts`: 왼쪽 뿌리발 x=23..30, 오른쪽 뿌리발 x=34..41의 y=58에 각각 불투명 픽셀을 최소 2개 요구한다.
- `transparentRegions`: 뿌리발 사이 `[30,48,34,57]`에 완전 투명 픽셀을 최소 4개 요구한다.
- `semanticRegions`:
  - `eyeGlow`: `[26,19,38,25]` 안의 `dangerRed` 2~4px이며 영역 밖 사용을 금지한다.
  - `corruptionCracks`: `[25,29,39,47]` 안의 `corruptionShadow`와 `corruptionGlow` 합계 3~12px이며 영역 밖 사용을 금지한다.
  - `corruptedSap`: `[26,22,38,40]` 안의 `sapBlack` 1~5px이며 영역 밖 사용을 금지한다.
  - `witheredLeaves`: `[14,11,50,23]` 안의 `witheredLeaf` 2~12px이며 가지와 연결된 잎만 허용한다.
- `palette`는 다음 15개 고유 색을 사용한다. `requiredPaletteNames`에는 15색을 모두 포함하고 투명 픽셀은 색 수에서 제외한다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `barkDeep: #2E211C`
  - `barkShadow: #4A3225`
  - `barkBase: #765238`
  - `barkHighlight: #A3744C`
  - `barkLight: #C08A5A`
  - `mossShadow: #42543A`
  - `mossBase: #657A4B`
  - `mossHighlight: #8EA565`
  - `witheredLeaf: #3A3F45`
  - `sapBlack: #1C1318`
  - `corruptionShadow: #45294F`
  - `corruptionGlow: #9A4E8A`
  - `dangerRed: #E05252`
- `outline`: 공통 색 `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계 전체가 외곽선 색이며 외부 경계의 2×2 외곽선 블록을 금지한다.
- `connectivity`: 모든 불투명 픽셀은 하나의 8-connected 구성요소이고 고립 픽셀을 허용하지 않는다. 잎과 잔가지도 몸체에 연결되어야 한다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, 최종 PNG 안의 크로마키는 금지한다.
- `design`: `forest`, `dark`, `corruption`, 낮은 위협도의 일반 적, 느리지만 끈질긴 근접 방해형, 귀엽지만 명확히 적대적인 고전 판타지 나무 정령을 기록한다. 갑옷·의복·인공 무기·기계·금속 장치·총기·전자 장치·현대 요소·잔혹 표현·기존 캐릭터 모방을 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-corrupted-tree-spirit-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['centerX']==32 and s['soleY']==58; assert s['expectedOpaqueBoundingBox']==[14,11,50,58]; assert s['opaqueHeight']==48; assert len(s['palette'])==15 and len(set(s['palette'].values()))==15; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 플레이어·고블린·해골 PNG의 실제 크기, mode, bounds, 공통 외곽선 색을 읽기 전용으로 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙과 사용자 디자인 제약을 확인한다.
4. task index의 step 0을 `completed`로 변경하고 계약 경로, 좌표, 팔레트와 의미 영역을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱 Kotlin 코드나 Android resource를 수정하지 마라. 이유: 이 step은 canonical 아트 계약만 정의한다.
- 플레이어 얼굴, 머리카락, 장비 또는 신체 픽셀을 복사하지 마라. 이유: 플레이어는 스타일과 크기 참조일 뿐이다.
- 16번째 불투명 색을 추가하지 마라. 이유: 최대 16색 요구 안에서 고정 15색 계약을 유지해야 한다.
- 기존 monster validator에 종족 이름 전용 분기를 추가하지 마라. 이유: 기존 선언형 schema로 계약을 표현할 수 있다.
- 기존 테스트를 깨뜨리지 마라.
