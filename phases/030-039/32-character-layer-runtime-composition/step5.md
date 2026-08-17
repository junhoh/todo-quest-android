# Step 5: remap-adventure-outfit-layers

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/layers/top_default.png`
- `/docs/art/character/layers/bottom_default.png`
- `/docs/art/character/layers/shoes_default.png`
- `/scripts/build_character_assets.py`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`

## 작업

built-in imagegen `precise-object-edit`를 layer별로 사용해 current blue adventure design을 보존한 `top_adventure.png`, `bottom_adventure.png`, `shoes_adventure.png`를 만든다. Image roles와 chroma/normalization은 step 4와 동일하며 CLI fallback을 사용하지 않는다.

default outfit과 정확히 같은 required waist/ankle interface와 transparent hand/face contract를 사용한다. top은 기존 재킷·아이보리 셔츠·청록 장식, bottom은 짙은 남색 바지, shoes는 파랑·아이보리·골드 디자인을 유지하되 geometry와 mask는 base-body에서 다시 측정한 값만 따른다. top/bottom 및 bottom/shoes가 겹쳐 가려지는 내부 boundary에는 outline을 중복하지 않는다.

default top+adventure bottom+default shoes와 adventure top+default bottom+adventure shoes 두 혼합 composite를 생성해 seam, neutral underwear, hands, centerX와 soleY를 확인한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer top_adventure
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer bottom_adventure
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check-layer shoes_adventure
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py -k "adventure_outfit or mixed_outfit or common_interface" --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. adventure set과 두 mixed set을 1×/8×로 비교한다.
2. top 2 × bottom 2 × shoes 2의 8개 조합이 모두 gap/double-outline 검사를 통과하는지 확인한다.
3. task index의 step 5를 `completed`로 바꾸고 prompts, layer 경로와 mixed 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 세트 전용 seam을 만들지 마라. 이유: 모든 상·하의·신발이 교차 장착 가능해야 한다.
- face나 hand pixel을 outfit layer에 복사하지 마라. 이유: body/hands 책임을 분리해야 한다.
- 기존 sheet target tile을 최종 source로 남기지 마라. 이유: 독립 layer PNG가 canonical source다.
- 기존 테스트를 깨뜨리지 마라.
