# Step 0: define-character-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`

## 작업

장비 교체형 기본 캐릭터와 단일 디자인 시트가 이후 장비 제작의 고정 기준이 되도록 `/docs/art/character/character-base-spec.json`을 생성한다. 앱 소스나 Android resource는 이 step에서 수정하지 않는다.

JSON은 UTF-8, `schemaVersion: 1`로 작성하고 다음 계약을 명시한다.

- 최종 시트: RGBA PNG `384x128px`, `64x64px` 논리 타일을 6열 2행으로 배치한다.
- 각 타일의 로컬 중심축은 `x=32`, 캐릭터 공통 bounding box는 양 끝 포함 `[20, 4, 44, 58]`, 최소 여백은 3px, 발바닥 기준선은 `y=58`이다.
- 기준점:
  - `headCenter: [32, 13]`
  - `neck: [32, 24]`
  - `shoulderLeft: [22, 27]`, `shoulderRight: [42, 27]`
  - `waistY: 39`
  - `wristLeft: [20, 40]`, `wristRight: [44, 40]`
  - `ankleLeft: [27, 53]`, `ankleRight: [37, 53]`
  - `soleY: 58`
- 레이어 순서: `rear-hair`, `body-base`, `lower`, `shoes`, `upper`, `front-hair`, `head`, `face`, `accessory`, `weapon`.
- 타일 배치:
  - 첫째 행: `equipped`, `base`, `slot-map`, `anchors`, `hair-split`, `palette`
  - 둘째 행: `head-layer`, `face-layer`, `upper-layer`, `lower-layer`, `shoes-layer`, `accessory-weapon-layer`
- 첫째 행의 `equipped`와 `base` 타일은 동일한 bounding box, 얼굴, 신체 비율, 정면 대기 자세를 사용한다. 무기와 액세서리도 공통 실루엣 밖으로 돌출하지 않는다.
- 팔레트는 아래 16색만 허용한다. 투명 픽셀은 팔레트 수에 포함하지 않는다.
  - `hairBlack: #11151C`
  - `eyeDarkNavy: #1D3557`
  - `outlineDarkNavy: #263B5A`
  - `hairHighlight: #35445C`
  - `underDark: #3A3F45`
  - `blueShadow: #2853A6`
  - `underMid: #737982`
  - `underLight: #B7B0A3`
  - `skinShadow: #D99872`
  - `skinLight: #FFD3AE`
  - `lightCream: #F4EFE3`
  - `bluePrimary: #4F86E8`
  - `blueHighlight: #7FB3FF`
  - `tealAccent: #5CC8A7`
  - `goldAccent: #F2C14E`
  - `guideRed: #E05252`
- 크로마키는 최종 팔레트에 없는 `#FF00FF`를 사용하고 최종 PNG에는 남기지 않는다.
- 기준점 타일에서 `guideRed`로 표시해야 할 로컬 픽셀 좌표를 `anchorGuidePixels` 배열에 모두 기록한다. 점은 위 기준점 가운데 `waistY`와 `soleY`를 각각 `[32, y]`로 변환한 총 10개다.

## Acceptance Criteria

```powershell
.\\.venv\\Scripts\\python.exe -c "import json, pathlib; p=pathlib.Path('docs/art/character/character-base-spec.json'); s=json.loads(p.read_text(encoding='utf-8')); assert s['schemaVersion']==1; assert s['sheet']=={'width':384,'height':128,'mode':'RGBA'}; assert s['logicalTile']=={'width':64,'height':64,'columns':6,'rows':2}; assert s['centerX']==32 and s['soleY']==58; assert len(s['palette'])==16 and len(set(s['palette'].values()))==16; assert len(s['tileMap'])==12; assert len(s['anchorGuidePixels'])==10"
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙과 디자인 방향을 확인한다.
3. `/phases/010-019/16-character-base-design-sheet/index.json`의 step 0을 `completed`로 변경하고 생성 파일과 핵심 좌표 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱의 Kotlin 코드나 Android resource를 수정하지 마라. 이유: 이번 phase는 런타임 통합이 아닌 캐릭터 원안과 아트 계약만 정의한다.
- 개별 장비 완성 PNG를 만들지 마라. 이유: 이번 결과물은 장비 제작의 기준이 되는 단일 디자인 시트다.
- 색상이나 기준 좌표를 임의로 추가·변경하지 마라. 이유: 이후 step의 자동 검증 계약과 어긋난다.
- 기존 테스트를 깨뜨리지 마라.