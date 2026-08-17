# Step 2: build-base-body-layer

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `/phases/010-019/19-character-hair-outfit-refinement/index.json`

## 작업

변경 전 모듈 시트의 `body-base` 타일에서 `/docs/art/character/todo-quest-character-base-body.png` 64x64 RGBA 독립 레이어를 만든다. 이 step은 하나의 base body 레이어만 생성하며 모듈 시트, spec, validator, 테스트와 Android 소스는 수정하지 않는다.

Pillow를 사용한 결정적 팔레트 변환만 허용한다. 캐릭터를 새로 그리거나 imagegen을 호출하지 않는다.

- `body-base` 타일의 64x64 로컬 좌표, 알파 마스크, bounding box, 중심축, 외부 실루엣과 발바닥을 픽셀 단위로 유지한다.
- `y<=29`의 모든 불투명 픽셀은 RGBA까지 그대로 보존해 얼굴, 귀, 머리 윤곽과 표정을 변경하지 않는다.
- 전신의 `skinLight`, `skinShadow` 및 schema v3 `identityProtection`에 기록된 눈·코·입·얼굴 윤곽 픽셀은 위치와 색을 그대로 유지한다.
- `outlineDarkNavy` 외곽선은 그대로 유지한다.
- `y>=30`의 불투명 비피부·비외곽선 픽셀만 중립 내의로 정규화한다. 기존 밝은 계열은 `underLight`, 중간 계열은 `underMid`, 어두운 계열은 `underDark`로 결정적으로 매핑한다. 기존 `underDark`, `underMid`, `underLight`는 유지한다.
- 머리카락, 파란 모험가 색, 아이보리 기본 상의, 실제 반바지나 실제 운동화로 식별되는 색은 최종 base body 내부에 남기지 않는다.
- 투명 배경, 0/255 알파, 기존 16색, 1px 외곽선과 고립 픽셀 금지를 유지한다.

외부 base body는 옷 아래에 숨는 중립 회색 내의 상태이며 새로운 캐릭터나 장비가 아니다. 기본 머리 전용 미리보기에서만 이 중립 상태가 보이고, 기본 및 모험가 완성 합성에서는 독립 의상 레이어가 이를 덮는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import json,pathlib; from PIL import Image; spec=json.loads(pathlib.Path('docs/art/character/character-modular-sheet-spec.json').read_text(encoding='utf-8')); p=pathlib.Path('docs/art/character')/spec['externalLayers']['base-body']['path']; im=Image.open(p); assert im.size==(64,64) and im.mode=='RGBA'; allowed={tuple(bytes.fromhex(v[1:])) for v in spec['palette'].values()}; assert all(a in (0,255) and (a==0 or tuple(rgb) in allowed) for *rgb,a in im.getdata()); assert im.getbbox() is not None"
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py -k "base_body or schema_v3" --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 원본 `body-base`와 새 base body를 1배율 및 nearest-neighbor 확대 상태로 비교한다.
3. 알파 마스크, 얼굴 보호 픽셀, 중심축, 발바닥과 외부 실루엣이 동일하고 의상 내부색만 중립 회색으로 바뀌었는지 계산한다.
4. 기준 PNG, 모듈 PNG, 두 spec, validator와 테스트가 변경되지 않았는지 확인한다.
5. ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
6. `/phases/010-019/19-character-hair-outfit-refinement/index.json`의 step 2를 `completed`로 변경하고 base body 경로와 불변 조건을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- imagegen 또는 다른 생성 모델을 사용하지 마라. 이유: base body는 기존 캐릭터의 결정적 분리 레이어여야 한다.
- 알파 마스크, 얼굴, 체형, 포즈와 기준 좌표를 바꾸지 마라. 이유: 모든 의상 합성의 불변 베이스다.
- 실제 기본/모험가 의상을 base body에 그리지 마라. 이유: 의상은 별도 레이어로 유지해야 한다.
- 모듈 시트나 기준 시트를 수정하지 마라. 이유: 이 step은 승인된 외부 base body 한 파일만 생성한다.
- 새 색상이나 반투명 픽셀을 추가하지 마라. 이유: 고정 팔레트와 알파 계약을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
