# Step 0: define-modular-sheet-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/phases/010-019/16-character-base-design-sheet/index.json`

## 작업

기존 기준 시트와 계약은 수정하지 않고, 첫 번째 파란색 장비 캐릭터를 유일한 기준으로 사용하는 `/docs/art/character/character-modular-sheet-spec.json`을 UTF-8 JSON으로 추가한다. 이 step에서는 PNG, 앱 소스, Android resource를 수정하지 않는다.

새 계약은 `schemaVersion: 2`와 아래 내용을 명시한다.

- 최종 시트는 `512x128px` RGBA PNG이며 `64x64px` 논리 타일 8열 2행이다.
- 로컬 중심축은 `x=32`, 발바닥은 `y=58`, 최소 바깥 여백은 3px다. 기존 캐릭터의 공통 허용 영역 `[20,4,44,58]`을 유지한다.
- 신체 기준은 머리 위 `y=7`, 어깨 `y=25`, 허리 `y=39`, 발목 `y=53`, 발바닥 `y=58`이다. 머리카락과 머리 장비만 허용 영역 안의 `y=4..6`을 사용할 수 있다.
- 외부 실루엣의 1px 외곽선은 기존 `outlineDarkNavy: #263B5A`로 고정한다.
- 첫째 행 타일:
  - column 0 `equipped`
  - column 1 `body-base`
  - column 2 `default-hair-underwear`
  - column 3 `anchors`
  - column 4 `palette`
  - columns 5, 6, 7은 `reserved-transparent`로 명시한다.
- 둘째 행 타일:
  - column 0 `rear-hair-layer`
  - column 1 `shoes-layer`
  - column 2 `lower-layer`
  - column 3 `upper-layer`
  - column 4 `front-hair-layer`
  - column 5 `head-gear-layer`
  - column 6 `accessory-layer`
  - column 7 `composite`
- 합성 순서는 `rear-hair-layer`, `body-base`, `shoes-layer`, `lower-layer`, `upper-layer`, `front-hair-layer`, `head-gear-layer`, `accessory-layer`다.
- `default-hair-underwear`는 `rear-hair-layer + body-base + front-hair-layer`의 알파 합성과 픽셀 단위로 같아야 한다.
- `equipped`와 `composite`는 전체 합성 순서 결과와 각각 픽셀 단위로 같아야 한다.
- `body-base`는 민머리 얼굴, 귀, 목, 팔, 손, 다리와 기본 내의만 포함한다. 기본 상의는 `lightCream: #F4EFE3` 중심의 흰 티셔츠, 기본 하의는 `underDark: #3A3F45` 중심의 검은 바지다. 머리카락은 포함하지 않는다.
- 레이어별 허용 y 범위를 기록한다.
  - `rear-hair-layer`: `4..30`
  - `shoes-layer`: `53..58`
  - `lower-layer`: `39..52`
  - `upper-layer`: `24..39`
  - `front-hair-layer`: `4..30`
  - `head-gear-layer`: `4..25`
  - `accessory-layer`: `4..58`
- 안내용 `anchors` 타일은 `default-hair-underwear` 위에 `tealAccent: #5CC8A7`의 1px 점선을 오버레이한다. 세로선은 `x=32`, 가로선은 `y=7,25,39,53,58`이며 `1px on / 1px off` 패턴과 계약에 기록된 정확한 픽셀 좌표 목록을 함께 둔다. 실제 캐릭터와 장비 타일에는 안내선을 넣지 않는다.
- 팔레트 타일은 4열 4행이며 각 칸은 `10x10px`, 간격은 `2px`, 로컬 시작점은 `[9,9]`다. 기존 16색과 순서를 값 변경 없이 유지한다.
- 투명 픽셀은 팔레트 수에 포함하지 않으며 최종 PNG의 알파는 `0` 또는 `255`만 허용한다. 크로마키 `#FF00FF`는 최종 PNG에 허용하지 않는다.
- `equipped`, `body-base`, `default-hair-underwear`, `composite`는 모두 허용 영역 안에서 같은 중심축과 발바닥 좌표를 사용한다. `anchors`와 `palette`는 이 bounding box 비교 대상에서 제외한다.

계약의 필드명은 validator가 임의의 픽셀을 추측하지 않고 다음을 읽을 수 있게 명확히 작성한다: `sheet`, `logicalTile`, `tileMap`, `reservedTiles`, `centerX`, `soleY`, `minimumMargin`, `commonAllowedBox`, `anchors`, `guide`, `layerOrder`, `layerBounds`, `compositionContracts`, `paletteGrid`, `palette`, `outlineColor`, `chromaKey`.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json, pathlib; p=pathlib.Path('docs/art/character/character-modular-sheet-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==2; assert s['sheet']=={'width':512,'height':128,'mode':'RGBA'}; assert s['logicalTile']=={'width':64,'height':64,'columns':8,'rows':2}; assert s['centerX']==32 and s['soleY']==58; assert len(s['tileMap'])==13; assert len(s['reservedTiles'])==3; assert s['layerOrder']==['rear-hair-layer','body-base','shoes-layer','lower-layer','upper-layer','front-hair-layer','head-gear-layer','accessory-layer']; assert len(s['palette'])==16 and len(set(s['palette'].values()))==16; assert s['outlineColor']=='#263B5A'"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 기존 spec과 PNG가 변경되지 않았는지 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙과 사용자 확정 좌표를 확인한다.
4. `/phases/010-019/17-modular-character-design-sheet/index.json`의 step 0을 `completed`로 변경하고 생성 파일과 핵심 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 `/docs/art/character/character-base-spec.json`과 기준 PNG를 수정하지 마라. 이유: 첫 번째 파란 캐릭터의 원본 기준을 보존해야 한다.
- PNG나 개별 장비 리소스를 만들지 마라. 이유: 이 step은 새 시트의 데이터 계약만 정의한다.
- 팔레트 색을 추가하거나 기존 값을 바꾸지 마라. 이유: 사용자 요구인 기존 16색 제한을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
