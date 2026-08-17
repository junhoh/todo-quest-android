# Step 1: define-loadout-art-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/scripts/build_character_assets.py`
- `/scripts/validate_character_equipment_layers.py`
- `/scripts/test_build_character_assets.py`
- `/scripts/test_validate_character_equipment_layers.py`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step0.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

아트 자산을 만들기 전에 빈 slot 외형과 기존 모험가 기본 복장의 상품화 계약을 테스트와 spec으로 고정한다.

빈 gameplay slot의 canonical fallback은 회갈색 계열의 중립 훈련복으로 정의한다. CHEST는 `top_default`, LEGS는 `bottom_default`, SHOES는 `shoes_default`를 사용하고 HELMET·GLOVES·ACCESSORY·WEAPON은 nullable/투명 overlay로 표현한다. 몸, 머리카락, 맨손 `hands_front`는 장비가 아니며 항상 유지한다. 새 fallback layer는 기존과 동일한 64×64 canvas, ground anchor, nearest-neighbor pixel 규칙과 canonical runtime/docs byte 동일성 계약을 지킨다.

현재 기본 외형인 `headgear_adventure`, `top_adventure`, `bottom_adventure`, `shoes_adventure`, `accessory_adventure`, `weapon_*_default_sword`는 제거하지 않고 7부위 모험가 상점 세트의 layer로 승격한다. 누락된 `gloves_adventure`를 같은 팔레트와 원점으로 추가하도록 character/equipment spec과 validator manifest를 확장한다. default sword는 back/held/front 3분할 조합을 하나의 WEAPON 상품 layer key로 다루며 단일 PNG로 합치지 않는다.

validator/build script 테스트를 먼저 수정해 새 `gloves_adventure`, 갱신된 default fallback, runtime/docs mirror, preview/sheet 재생성 목록이 누락되면 실패하도록 한다. spec schema version을 올릴 때 기존 layer key의 의미와 호환성도 문서화한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_validate_character_equipment_layers.py scripts/test_build_character_assets.py --basetemp build/pytest-phase62-art-contract
.\.venv\Scripts\python.exe scripts/validate_character_equipment_layers.py --help
git diff --check
```

## 검증 절차

1. 새 fallback 및 `gloves_adventure`가 없을 때 실패하는 script test를 먼저 작성한다.
2. character/equipment spec과 build manifest가 같은 layer key, canvas, 원점, 합성 순서를 선언하는지 확인한다.
3. runtime asset과 docs canonical mirror의 byte 동일성 검증이 유지되는지 확인한다.
4. 성공 시 task index의 step 1을 `completed`로 바꾸고 확정한 빈 slot 및 모험가 세트 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 기존 adventure layer key를 이름 변경하거나 삭제하지 마라. 이유: 저장된 appearance와 renderer 호환성을 유지해야 한다.
- 검과 캐릭터를 하나의 합성 PNG로 런타임에 저장하지 마라. 이유: 3분할 weapon layer와 slot별 교체 계약이 깨진다.
- 장비가 빈 상태에서 body, hair, hands까지 숨기지 마라. 이유: 이들은 장비 slot이 아닌 기본 캐릭터 구성이다.
- validator를 새 자산에 맞추기 위해 pixel/bounds 검사를 완화하지 마라. 이유: 새 자산도 기존 canonical 품질 계약을 만족해야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
