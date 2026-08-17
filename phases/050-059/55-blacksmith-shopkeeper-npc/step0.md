# Step 0: define-blacksmith-shopkeeper-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/index.json`

## 작업

`/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json`을 UTF-8 JSON, `schemaVersion: 1`로 생성해 대장장이 상점 NPC 한 명의 정면 기본 대기 스프라이트 계약을 정의한다. 이 step에서는 PNG, Android resource와 Kotlin 코드를 만들지 않는다.

계약에는 다음 필드와 값을 포함한다.

- `asset`: 파일명 `todo-quest-blacksmith-shopkeeper-front-idle.png`, `width: 64`, `height: 64`, `mode: RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, 스타일·논리 크기 비교 전용, 편집 불가, 기준 불투명 bounds `[20,7,44,58]`, 높이 `52px`.
- `centerX: 32`, `soleY: 58`, `minimumMargin: 3`, 양 끝 포함 `expectedOpaqueBoundingBox: [14,13,50,58]`, `opaqueHeight: 46`. 기준 플레이어보다 약 11.5% 작고 망치와 반대쪽 팔꿈치를 포함한 전체 폭은 더 넓다.
- `anchors`: `headCenter: [32,21]`, `shoulderY: 31`, `waistY: 43`, `soleY: 58`.
- `pose`: 완전 정면, 3등신 치비, 짧은 몸통·넓은 어깨·굵은 팔, 양발 `y=58`, 차분한 상점 대기 자세. 화면 오른쪽 손은 짧은 작업용 망치를 몸 옆 아래로 들고, 화면 왼쪽 손은 허리에 둔다. 공격·회전·걷기 자세를 금지한다.
- `appearance`: 중년 드워프, 정돈된 짙은 적갈색 머리와 가슴 위쪽까지 내려오는 수염, 금속 고리 하나, 굵은 눈썹, 둥근 코, 부드러운 눈매와 옅은 미소, 얼굴·팔의 제한된 검댕. 어두운 소매를 걷은 작업복, 두껍고 낡은 갈색 가죽 앞치마, 최소 공구가 달린 벨트, 가죽 장갑과 금속 코 작업화를 명시한다.
- `equipment`: 망치는 화면 오른쪽 몸 옆의 짧고 묵직한 실제 제작 공구다. 얼굴·수염·몸통을 가리지 않고 손·자루·망치 머리가 전체 불투명 실루엣과 8-connected로 이어진다. 별도 칼이나 전투 무기는 없다.
- `semanticRegions`:
  - `beardRing`: 양 끝 포함 `[29,34,35,40]`, `brassAccent`만 1~5px, 영역 밖 사용 금지.
  - `hammerHighlight`: 양 끝 포함 `[43,44,50,54]`, `metalLight`만 2~8px, 불투명 높이 2~7px, 영역 밖과 중심 신체 `x=20..42` 사용 금지.
  - `eyesAndSoot`: 양 끝 포함 `[18,18,44,43]`, `inkDeep` 4~12px, 영역 밖 사용 금지. 눈·눈썹과 소량의 검댕 이외에 사용하지 않는다.
- `groundContacts`: 왼발 `x=22..30`, 오른발 `x=34..42`, 각각 `y=58`에 불투명 픽셀 최소 2개.
- `transparentRegions`: 양 끝 포함 `[31,54,33,57]`에 투명 픽셀 최소 6개를 두어 양발을 분리한다.
- `palette`: 아래 정확히 16색만 허용하고 모두 `requiredPaletteNames`에 포함한다. 투명 픽셀은 색 수에 포함하지 않는다.
  - `outlineDarkNavy: #263B5A`
  - `inkDeep: #11151C`
  - `clothDark: #3A3F45`
  - `clothMid: #737982`
  - `metalDark: #35445C`
  - `metalMid: #B7B0A3`
  - `metalLight: #F4EFE3`
  - `skinShadow: #D99872`
  - `skinLight: #FFD3AE`
  - `hairShadow: #4A2A26`
  - `hairBase: #7C3F32`
  - `hairHighlight: #B25F43`
  - `leatherShadow: #4A3225`
  - `leatherBase: #765238`
  - `leatherHighlight: #A3744C`
  - `brassAccent: #F2C14E`
- `outline`: `#263B5A`, 외부 실루엣 1px, 4-neighbor 외부 경계 전체가 외곽선 색이고 외부 외곽선 2×2 블록을 금지한다.
- `connectivity`: 불투명 픽셀은 정확히 하나의 8-connected 구성요소이고 고립 픽셀을 허용하지 않는다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, final PNG의 chroma key 금지.
- `design`: 고전 중세 판타지 마을의 책임감 있고 친절한 숙련 장인·상점 주인. 왕실 장식, 전투 자세, 총기, 전자 장치, 현대 작업복, 기계 장비, 불꽃·연기·용광로 광원·파티클, 배경·바닥 그림자와 기존 게임 캐릭터 모방을 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-blacksmith-shopkeeper-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['reference']['opaqueBoundingBox']==[20,7,44,58]; assert s['centerX']==32 and s['soleY']==58; assert s['expectedOpaqueBoundingBox']==[14,13,50,58] and s['opaqueHeight']==46; assert len(s['palette'])==16 and len(set(s['palette'].values()))==16; assert s['palette']['outlineDarkNavy']=='#263B5A'; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 기준 플레이어 PNG의 실제 `64×64 RGBA`, bounds `[20,7,44,58]`, height `52`, 공통 외곽선 `#263B5A`를 읽기 전용으로 확인한다.
3. 기존 spec-driven 단일 스프라이트 validator가 새 계약 필드를 지원하는지 확인하되 이 step에서는 validator를 수정하지 않는다.
4. AGENTS.md와 관련 문서의 CRITICAL 규칙을 확인한다.
5. phase index의 step 0을 `completed`로 변경하고 계약 경로·bounds·16색 팔레트를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android resource를 만들지 마라. 이유: 이 step은 기계 검증 가능한 아트 계약만 확정한다.
- 플레이어 얼굴·머리·장비 픽셀을 NPC 계약의 원본으로 정의하지 마라. 이유: 플레이어는 스타일과 크기 비교용이다.
- 17번째 색이나 반투명 alpha를 허용하지 마라. 이유: 사용자가 지정한 최대 16색·binary alpha 계약을 위반한다.
- 앱 Kotlin 코드, Room, Repository와 ViewModel을 수정하지 마라. 이유: 아트 계약은 UI와 데이터 계층에서 독립적이어야 한다.
- 기존 테스트를 깨뜨리지 마라.
