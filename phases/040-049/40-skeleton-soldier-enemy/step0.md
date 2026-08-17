# Step 0: define-skeleton-soldier-art-contract

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
- `/scripts/validate_monster_sprite.py`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

`/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`을 생성해 한 장짜리 해골 병사 정면 대기 스프라이트의 canonical 픽셀 계약을 정의한다. 앱 Kotlin 코드와 Android runtime resource는 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성하고 다음 계약을 포함한다.

- `asset`: 최종 파일 이름 `todo-quest-skeleton-soldier-front-idle.png`, `width: 64`, `height: 64`, `mode: RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 기준 플레이어 불투명 bounding box `[20,7,44,58]`, 높이 52px. 참조는 스타일·논리적 크기·명암 단계 비교용이며 편집 대상이 아니다.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [16,13,48,58]`, `opaqueHeight: 46`. 플레이어보다 약 11.5% 작은 높이이며 왼쪽의 손상된 천과 오른쪽 검을 포함한 전체 폭을 중심축에 맞춘다.
- `anchors`: `headCenter: [32,21]`, `shoulderY: 31`, `waistY: 43`, `soleY: 58`.
- `pose`: 완전히 정면, 큰 두개골의 3등신 치비, 중심축 고정, 중립적인 전투 대기 자세, 양발이 y=58에 닿고 검을 즉시 사용할 수 있는 긴장된 자세. 회전, 공격 동작과 비대칭 보행 자세는 금지한다.
- `groundContacts`: `leftFoot`는 x=24..31, `rightFoot`는 x=33..40에서 y=58에 각각 불투명 픽셀을 최소 2개 가져야 한다.
- `equipment`: 녹슬고 찌그러진 철제 투구, 낡은 가죽 또는 천 경갑, 일부 손상된 짧은 망토나 허리 천, 화면 오른쪽 몸 옆의 짧고 단순한 녹슨 한손검. 검은 중앙 몸통을 가리지 않고 손·자루·칼날이 전체 실루엣과 하나의 8-connected 구성요소로 이어진다.
- `semanticRegions`는 이름에 고정되지 않는 선언형 계약으로 작성한다.
  - `eyeGlow`: 양 끝 포함 영역 `[26,19,38,26]`, `dangerRed`만 2~4px, 해당 색은 이 영역 밖에서 금지한다.
  - `swordMetal`: 양 끝 포함 영역 `[41,34,48,53]`, `bladeDark`와 `bladeLight`만 사용하며 두 색은 이 영역 밖에서 금지한다. 두 색의 합친 bounding box 높이는 14~20px이고 x=24..40을 침범하지 않는다.
- `transparentRegions`: `ribCageGaps [28,35,36,43]` 최소 4px, `legGap [30,49,34,57]` 최소 4px, `swordBodyGap [39,38,44,50]` 최소 2px의 완전 투명 픽셀을 요구한다.
- `palette`는 다음 15개 고유 색만 허용하며 투명 픽셀은 색 수에 포함하지 않는다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `boneShadow: #737982`
  - `boneBase: #B7B0A3`
  - `boneHighlight: #F4EFE3`
  - `ironShadow: #35445C`
  - `ironBase: #56677A`
  - `rustAccent: #A3744C`
  - `leatherShadow: #4A3225`
  - `leatherBase: #765238`
  - `clothDark: #3A3F45`
  - `clothMid: #5B6068`
  - `bladeDark: #4B5059`
  - `bladeLight: #9A9A94`
  - `dangerRed: #E05252`
- `requiredPaletteNames`에는 15색을 모두 포함해 재료별 최소 명암과 제한된 적대적 눈빛을 보장한다. 16번째 불투명 색은 허용하지 않는다.
- `outline`: `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계는 전부 외곽선 색이며 외부 경계에 닿는 2×2 외곽선 블록은 금지한다.
- `connectivity`: 모든 불투명 픽셀은 8-connected 단일 구성요소이고 고립 픽셀은 허용하지 않는다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, 최종 PNG의 크로마키는 금지한다.
- `design`: `undead`, `dark`, 감정 없이 명령을 따르는 끈질긴 초반 일반 적, 귀엽지만 명확히 적대적인 실루엣, 고전 중세 판타지 자연 재료 장비를 기록한다. 기계·총기·전자 장치·현대 의상·잔혹 표현·특정 기존 캐릭터 모방을 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-skeleton-soldier-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['centerX']==32 and s['soleY']==58; assert s['expectedOpaqueBoundingBox']==[16,13,48,58]; assert s['opaqueHeight']==46; assert len(s['palette'])==15 and len(set(s['palette'].values()))==15; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 기준 플레이어와 고블린 PNG의 실제 크기, mode, 불투명 경계, 알파와 공통 외곽선 색을 읽기 전용으로 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. `/phases/040-049/40-skeleton-soldier-enemy/index.json`의 step 0을 `completed`로 변경하고 계약 경로, 좌표와 팔레트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱 Kotlin 코드나 Android resource를 수정하지 마라. 이유: 이 step은 canonical 아트 계약만 정의한다.
- 플레이어 얼굴, 머리카락, 장비 또는 신체 픽셀을 복사하지 마라. 이유: 플레이어는 스타일과 크기 참조일 뿐이다.
- 16번째 불투명 색을 추가하지 마라. 이유: 최대 16색 요구 안에서 고정 15색 계약을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
