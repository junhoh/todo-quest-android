# Step 3: render-refined-character-sheet

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/scripts/validate_character_sheet.py`
- `/scripts/test_validate_character_sheet.py`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`
- `/phases/010-019/19-character-hair-outfit-refinement/index.json`

## 작업

기존 모듈 PNG를 이미지 보기 도구로 먼저 확인한 뒤 built-in `image_gen` edit 모드로 `/docs/art/character/todo-quest-character-modular-sheet.png`를 개선한다. 현재 모듈 시트는 edit target, 기존 기준 시트와 새 base body는 얼굴·체형·포즈와 레이어 좌표 reference로 사용한다. CLI/API fallback, `gpt-image-1.5`와 다른 이미지 모델은 사용하지 않는다.

작업 시작 전에 현재 모듈 시트의 `adventure-shoes-layer`, `head-gear-layer`, `accessory-layer`, `palette` 타일을 `.tmp/imagegen/`에 보존한다. 이 타일들은 최종 조립에서 원본 픽셀을 그대로 복원한다.

built-in edit은 서로 다른 변경을 섞지 않도록 다음 두 번의 집중 편집으로 수행한다.

1. 기본 머리와 기본 상·하의·신발 및 해당 분리 레이어를 편집한다.
2. 모험가 상의와 하의의 허리 경계를 편집한다.

검증에서 하나의 구체적인 시각 문제가 남은 경우에만 그 문제를 대상으로 한 후속 edit을 최대 한 번 수행한다. 총 세 번 후에도 계약이나 시각 검사를 통과하지 못하면 새 캐릭터, 임의 코드 드로잉이나 다른 모델로 우회하지 않고 step을 `blocked`로 기록한다.

각 built-in edit 프롬프트는 다음 구조와 불변 조건을 그대로 포함한다.

```text
Use case: identity-preserve
Asset type: modular paper-doll pixel-art character sheet for Todo Quest
Input images: Image 1 is the edit target; Image 2 is the immutable canonical character/style reference; Image 3 is the aligned base_body layer reference.
Primary request: Change only the default hair, default short-sleeve top, default shorts, default shoes, adventure top, adventure bottom, and their required previews/composites. Do not create a new character.
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background; no floor, shadow, grid, labels, text, or decoration
Style/medium: native hard-edged pixel art with uniform square logical pixels, one-pixel stepped diagonals, no antialiasing, blur, gradients, dithering, or semitransparency
Composition/framing: preserve the exact 8x2 grid of 64x64 cells, x=32 center, head y=7, shoulder y=25, waist y=39, ankle y=53, sole y=58
Color palette: use only the 16 colors listed in character-modular-sheet-spec.json; do not add colors
Constraints: preserve the original face, eyes, one-pixel nose, one-to-two-pixel relaxed smiling mouth, ears, body proportions, front pose, coordinates, and all unrequested equipment. Keep every isolated layer on a full 64x64 cell at its actual equipped coordinates.
Avoid: new characters, new equipment, asymmetrical eye-covering bangs, hair between the eyes, hair over nose or mouth, long hair, robe-like jacket tails, merged top/bottom layers, changed anatomy, changed face, alternate pose, cropped layers, stray pixels, text, logos, watermark, background or shadow
```

기본 머리는 다음을 모두 만족한다.

- 기존 짙은 남색 계열을 유지한 짧고 단정한 완전 정면 머리다.
- 윗부분은 둥글고 좌우 균형을 이루며, 옆머리는 얼굴 바깥을 따라 짧게 내려오고 뒷머리는 목선에서 끝난다.
- 중앙 앞머리의 최저점은 눈썹 위이며 양쪽 눈, 눈 사이, 코와 입 주변을 완전히 비운다.
- 눈 외곽선과 머리 외곽선 사이에는 최소 1px 간격이 있다.
- `default-hair-back-layer`는 뒤통수와 목 뒤 머리만 포함하고, `default-hair-front-layer`는 윗머리·짧은 앞머리·옆머리만 포함한다. 두 레이어에는 피부나 얼굴 픽셀이 없다.

기본 복장은 다음을 만족한다.

- `default-top-layer`: 무늬·로고·벨트 없는 밝은 아이보리 반팔 티셔츠, 둥근 목, 동일 길이 소매, `y=39` 밑단
- `default-bottom-layer`: 무장식 짙은 회색/남색 반바지, `y=39..46`, 동일한 좌우 바짓단
- `default-shoes-layer`: 동일 크기의 흰색/회색 운동화, `y=53..58`, 그림자 없음
- 세 레이어는 피부와 서로 명확한 `outlineDarkNavy` 경계를 갖고 base body나 다른 아이템 픽셀을 포함하지 않는다.

모험가 장비는 다음을 만족한다.

- `adventure-top-layer`: 허리 길이 파란 재킷과 밝은 아이보리 셔츠, 대칭에 가까운 소매·어깨 장식, `y=39` 밑단, 긴 앞자락 없음
- `adventure-bottom-layer`: 상의보다 어두운 짙은 남색 긴 바지, `y=39..53`, 분리된 양쪽 다리, 상의·신발 영역 침범 없음
- 기존 `adventure-shoes-layer`의 부츠, `head-gear-layer`의 모자와 `accessory-layer`는 픽셀 단위로 보존한다.

생성 결과를 `$CODEX_HOME/generated_images`에서 `.tmp/imagegen/`으로 복사하고 설치된 `remove_chroma_key.py`로 평면 `#FF00FF`를 제거한다. 픽셀아트이므로 최종 알파는 `0/255`로 양자화하고 nearest-neighbor만 사용한다. 16색 최근접 매핑에는 dithering을 사용하지 않는다.

최종 시트는 모델 출력 전체를 그대로 채택하지 않고 다음 결정적 조립을 수행한다.

- 모델 결과에서 변경 대상 7개 레이어만 추출하고 schema v3 좌표에 정렬한다.
- 기존 모듈 시트에서 보존한 모험가 신발, 모자, 액세서리와 팔레트 타일을 원본 픽셀 그대로 복사한다.
- 보호된 얼굴·신체 픽셀은 `/docs/art/character/todo-quest-character-base-body.png`에서 가져온다.
- `default-outfit`, `default-hair-preview`, `equipped`, `composite`는 spec의 source-over 순서로 새로 합성하며 모델이 만든 미리보기 픽셀은 사용하지 않는다.
- `anchors`는 새 `default-hair-preview` 위에 spec의 기존 점선 픽셀만 다시 그린다.
- `equipped`와 `composite`는 같은 합성 결과를 두 위치에 복사해 픽셀 단위로 동일하게 만든다.
- 외부 투명 영역에 맞닿은 불투명 경계만 필요한 경우 `outlineDarkNavy`로 정규화할 수 있다. 알파 마스크, 내부 디자인과 비경계 픽셀은 임의 수정하지 않는다.
- 모델이 만든 고립 픽셀, 중복 외곽선, 셀 밖 픽셀은 제거하되 캐릭터를 코드로 새로 그리지 않는다.

완성 후 1배율과 nearest-neighbor 확대 상태로 직접 검사한다. 얼굴 요소 노출, 머리 균형, 소매와 반바지 대칭, 허리·발목 경계, 바지 다리 분리, 외곽선 연결, 고립 픽셀, 잘림과 잔여 크로마키를 확인한다. 중간 파일은 `.tmp/imagegen/`에만 두고 커밋하지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 최종 시트와 base body를 원본 및 nearest-neighbor 확대 상태로 시각 검사한다.
3. validator가 얼굴 보호, 눈 여백, 레이어 경계, 대칭, 합성 동일성, 외곽선, 팔레트와 알파 계약을 모두 통과했는지 확인한다.
4. 기존 기준 PNG와 기준 spec, Android 소스와 resource가 변경되지 않았는지 확인한다.
5. imagegen 최종 프롬프트, built-in mode 사용, 최종 파일 경로와 검증 결과를 작업 기록에 남긴다.
6. `/phases/010-019/19-character-hair-outfit-refinement/index.json`의 step 3을 `completed`로 변경하고 최종 PNG, 합성 계약과 검증 결과를 한국어 `summary` 한 줄로 기록한다.
7. 모든 step이 완료되면 `/phases/index.json`의 `19-character-hair-outfit-refinement` 상태를 `completed`로 변경한다.

## 금지사항

- built-in imagegen 대신 CLI/API fallback이나 다른 이미지 모델을 사용하지 마라. 이유: 승인되지 않은 실행 경로 전환이다.
- 얼굴, 체형, 포즈, 기준 좌표와 변경 대상 밖 장비를 재디자인하지 마라. 이유: 기존 캐릭터의 정체성을 보존해야 한다.
- Python, SVG, HTML 또는 canvas로 머리와 의상을 새로 그리지 마라. 이유: 디자인 개선은 imagegen 편집 결과를 사용해야 한다. 크로마키 제거, 팔레트/알파 정규화, 보호 픽셀 복원과 계약 기반 합성만 결정적 후처리로 허용한다.
- 상의와 하의를 한 레이어로 합치거나 기본 의상을 base body에 병합하지 마라. 이유: 모듈형 장착 계약을 위반한다.
- 새 캐릭터, 장비, 색상, 텍스트, 로고, 배경이나 바닥 그림자를 추가하지 마라. 이유: 사용자 제한 범위를 벗어난다.
- 개별 의상 PNG나 Android resource를 추가하지 마라. 이유: 승인된 별도 산출물은 base body 한 파일뿐이다.
- `.tmp/imagegen/` 중간 파일을 커밋하지 마라. 이유: 최종 프로젝트 산출물만 추적해야 한다.
- 기존 테스트를 깨뜨리지 마라.
