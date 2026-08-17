# Step 0: define-fairy-guide-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json`
- `/docs/art/monster/todo-quest-harpy-front-idle-spec.json`
- `/scripts/validate_monster_sprite.py`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

`/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`을 UTF-8 JSON, `schemaVersion: 1`로 생성한다. 이 step에서는 PNG, Android resource와 Kotlin 코드를 만들지 않는다.

계약에는 다음 내용을 고정한다.

- `asset`: `todo-quest-fairy-guide-front-idle.png`, `64×64`, `RGBA`.
- `reference`: `../character/todo-quest-character-base-body.png`, style·logicalScale·outline·shadingSteps 비교 전용, 편집 불가, 기준 bounds `[20,7,44,58]`, 높이 52px.
- `centerX: 32`, `soleY: 52`, `minimumMargin: 4`, 양 끝 포함 `expectedOpaqueBoundingBox: [6,22,58,52]`, `opaqueHeight: 31`. 플레이어 높이의 약 59.6%다.
- anchors는 `headCenter: [32,30]`, `shoulderY: 36`, `waistY: 43`, `soleY: 52`다.
- 정면을 향해 살짝 떠 있는 2.5~3등신 인간형 요정 한 명이다. 화면 오른쪽 손은 가슴 높이에서 검지로 옆을 안내하고 화면 왼쪽 손은 허리 주머니를 잡는다. 공격·마법 시전·걷기·회전 자세를 금지한다.
- 턱선 길이의 차분한 금발, 눈·코·입을 가리지 않는 앞머리, 큰 밝은 눈, 부드러운 미소, 작은 금색 별 머리 장식 하나를 사용한다.
- 연녹색·크림색의 단순한 짧은 튜닉, 작은 갈색 주머니, 부드러운 가죽 부츠를 사용한다. 왕관·보석·프릴·갑옷·무기·총기·기계·현대 요소를 금지한다.
- 길쭉하고 둥근 곤충형 날개 한 쌍은 몸 뒤에서 좌우로 살짝 펼치고 머리·얼굴·양팔과 시각적으로 분리한다. 특정 곤충을 그대로 모방하지 않으며 날개당 내부 무늬는 1~2개의 픽셀 덩어리만 허용한다. 날개도 alpha 255의 불투명 연청록색 두 단계로 표현한다.
- `semanticRegions`는 최소한 다음을 포함한다.
  - `starAccent`: `[29,22,35,27]`, `goldAccent`, 영역 밖 사용 금지, 2~7px.
  - `eyesAndSmile`: `[27,28,37,34]`, `inkDeep`, 영역 밖 사용 금지, 3~8px.
  - `wingColors`: `[6,27,58,44]`, `wingShadow`와 `wingLight`, 영역 밖 사용 금지, 전체 30~180px.
- `groundContacts` validator 계약을 두 발끝 확인에 재사용한다. 왼발 `x=27..30`, 오른발 `x=34..37`, `y=52`, 각각 최소 2px다.
- `transparentRegions`는 다리 사이 `[31,47,33,51]`에 최소 6px, 날개와 얼굴·팔 사이 좌우 간격에 각각 최소 12px를 요구한다.
- 팔레트는 아래 정확히 16색이고 모두 required다: `outlineDarkNavy #263B5A`, `inkDeep #11151C`, `skinShadow #D99872`, `skinLight #FFD3AE`, `hairShadow #A9824B`, `hairBase #D7B86D`, `creamShadow #B7B0A3`, `creamLight #F4EFE3`, `tunicShadow #4F6B55`, `tunicBase #789B6E`, `tunicLight #AFC79A`, `leatherShadow #4A3225`, `leatherBase #765238`, `wingShadow #A9D9D4`, `wingLight #D9F1E8`, `goldAccent #F2C14E`.
- 외부 실루엣은 `#263B5A` 1px, 4-neighbor 외부 경계 전체 적용, 2×2 외곽선 블록 금지다. 불투명 픽셀은 정확히 한 개의 8-connected component이고 고립 픽셀은 없다.
- `allowedAlphaValues: [0,255]`, `chromaKey: #FF00FF`, final PNG의 chroma key를 금지한다.
- 배경·바닥 그림자·숲·꽃·풀·말풍선·텍스트·느낌표·물음표·오라·마법진·반짝이·별가루·입자를 금지한다. 특정 게임 캐릭터 모방도 금지한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; p=pathlib.Path('docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['asset']=={'fileName':'todo-quest-fairy-guide-front-idle.png','width':64,'height':64,'mode':'RGBA'}; assert s['reference']['opaqueBoundingBox']==[20,7,44,58]; assert s['centerX']==32 and s['soleY']==52; assert s['expectedOpaqueBoundingBox']==[6,22,58,52] and s['opaqueHeight']==31; assert s['minimumMargin']==4; assert len(s['palette'])==16 and len(set(s['palette'].values()))==16; assert s['palette']['outlineDarkNavy']=='#263B5A'; assert s['allowedAlphaValues']==[0,255]; assert s['chromaKey']=='#FF00FF'"
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. reference PNG의 실제 크기·bounds·공통 외곽선을 읽기 전용으로 확인한다.
3. 기존 validator가 사용하는 필드만 기계 검증 계약에 사용했는지 확인한다.
4. AGENTS.md와 관련 문서의 CRITICAL 규칙을 확인한다.
5. phase index의 step 0을 `completed`로 바꾸고 명세 경로·bounds·16색을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG나 Android resource를 만들지 마라. 이유: 이 step은 아트 계약만 확정한다.
- 플레이어 픽셀을 요정 원본으로 정의하지 마라. 이유: reference는 스타일과 크기 비교 전용이다.
- 17번째 색이나 반투명 alpha를 허용하지 마라. 이유: 최대 16색·binary alpha 계약을 위반한다.
- 기존 validator를 수정하지 마라. 이유: 기존 spec-driven 계약으로 검증 가능해야 한다.
- 기존 테스트를 깨뜨리지 마라.

