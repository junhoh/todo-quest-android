# Step 0: define-goblin-scout-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/phases/020-029/22-goblin-scout-sprite/index.json`

## 작업

`/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`을 생성해 한 장짜리 고블린 정찰병 정면 대기 스프라이트의 픽셀 계약을 정의한다. 앱 Kotlin 코드와 Android resource는 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성하고 다음 필드와 계약을 포함한다.

- `asset`: 최종 파일 이름 `todo-quest-goblin-scout-front-idle.png`, `width: 64`, `height: 64`, `mode: RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 기준 플레이어 불투명 bounding box `[20,7,44,58]`, 높이 52px. 참조 이미지는 스타일·논리 크기 비교용이며 편집 대상이 아니다.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [16,13,48,58]`. 최종 높이 46px는 기준 플레이어보다 약 11.5% 작다. 가로 폭은 뾰족한 양쪽 귀와 몸 옆 단검을 포함하며 신체 코어는 플레이어보다 가늘게 디자인한다.
- `anchors`: `headCenter: [32,21]`, `shoulderY: 31`, `waistY: 43`, `soleY: 58`.
- `pose`: 완전히 정면, 3등신 치비, 중심축 고정, 양발이 y=58에 닿는 중립적인 전투 대기 자세. 공격적인 눈썹과 경계하는 팔 자세를 사용하되 공격 동작이나 회전은 사용하지 않는다.
- `equipment`: 화면 오른쪽 몸 옆의 짧고 단순한 독 단검, 화면 왼쪽 허리의 작은 천 가방, 낡은 가죽 갑옷. 단검은 중앙 신체를 가리지 않고 손·자루·칼날이 하나의 8-connected 불투명 구성요소로 이어진다.
- `semanticRegions`:
  - 붉은 눈 영역은 양 끝 포함 `[26,19,38,25]`이고 `dangerRed`는 이 영역에서만 1~4px 허용한다.
  - 단검 금속 영역은 양 끝 포함 `[41,35,48,50]`이고 `metalDark`, `metalMid`, `metalLight`, `poisonAccent`는 이 영역에서만 사용한다. 금속 불투명 bounding box 높이는 8~12px이며 중심부 `x=24..40`을 가리지 않는다.
  - `poisonAccent`는 단검에서만 1~6px 허용한다.
- `palette`는 다음 15색만 허용하고 투명 픽셀은 색 수에 포함하지 않는다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `clothDark: #3A3F45`
  - `clothMid: #737982`
  - `metalDark: #35445C`
  - `metalMid: #B7B0A3`
  - `metalLight: #F4EFE3`
  - `skinShadow: #42543A`
  - `skinBase: #657A4B`
  - `skinHighlight: #8EA565`
  - `leatherShadow: #4A3225`
  - `leatherBase: #765238`
  - `leatherHighlight: #A3744C`
  - `poisonAccent: #5CC8A7`
  - `dangerRed: #E05252`
- `requiredPaletteNames`에는 외곽선, 피부 3색, 가죽 3색, 금속 3색, 독색, 위험색을 포함한다. `inkDeep`, `clothDark`, `clothMid`는 선택적으로 허용한다.
- `outline`: 색 `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계가 모두 외곽선 색이어야 하고 2×2 외부 외곽선 블록은 금지한다.
- `connectivity`: 모든 불투명 픽셀은 8-connected 단일 구성요소이고 고립 픽셀을 허용하지 않는다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, `chromaKeyAllowedInFinalPng: false`.
- `design`: 숲/독 속성, 교활하고 민첩한 초반 일반 등급 적, 귀엽지만 적대적인 표정과 각진 귀 실루엣, 고전 중세 판타지 자연 재료 장비. 기계·총기·전자 장치·현대 의상·잔혹 표현은 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-goblin-scout-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['centerX']==32 and s['soleY']==58; assert s['expectedOpaqueBoundingBox']==[16,13,48,58]; assert len(s['palette'])==15 and len(set(s['palette'].values()))==15; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 기준 플레이어 PNG의 실제 크기, mode, bounding box, 알파와 외곽선 색을 읽기 전용으로 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙과 디자인 방향을 확인한다.
4. `/phases/020-029/22-goblin-scout-sprite/index.json`의 step 0을 `completed`로 변경하고 계약 파일, 좌표, 팔레트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱 Kotlin 코드나 Android resource를 수정하지 마라. 이유: 이번 phase는 런타임 통합이 아닌 단일 아트 에셋 제작이다.
- 플레이어 얼굴, 머리카락, 장비 또는 신체 픽셀을 고블린에 복사하지 마라. 이유: 참조 이미지는 스타일과 크기 기준일 뿐이다.
- 16번째 불투명 색을 추가하지 마라. 이유: 계약은 여유 1색을 둔 최대 16색 규격으로 고정한다.
- 기존 테스트를 깨뜨리지 마라.
