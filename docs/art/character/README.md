# 캐릭터 아트 인덱스

독립 캐릭터 레이어, 런타임 복사본과 생성된 검증 자산의 역할을 구분하고 canonical 계약으로 안내한다.

## 자산 역할

- canonical 계약: [모듈형 시트 명세](character-modular-sheet-spec.json)의 schema v6가 15개 독립 canonical source와 `gloves_adventure`를 포함한 runtime variant, semantic anchor, z-order, 팔레트와 hash를 정의한다.
- geometry canonical source: [캐릭터 base-body](todo-quest-character-base-body.png)는 `body_base`의 유일한 좌표·실루엣 기준이다.
- canonical appearance source: [`layers/`](layers/)의 hair·hands·의상·headgear·accessory·3분할 weapon PNG는 모두 같은 `64×64` 원점을 유지한다.
- runtime source: `app/src/main/assets/character/layers/`의 15개 canonical PNG와 17개 gameplay 장비 variant PNG는 대응 source와 각각 byte-identical하며, 앱은 이 32개 파일만 layer source로 읽는다.
- generated 검증 자산: [모듈형 캐릭터 시트](todo-quest-character-modular-sheet.png)와 [`previews/`](previews/)는 deterministic debug·golden·육안 검증 결과다. 앱의 캐릭터 렌더러는 생성 시트를 source로 읽지 않는다.
- legacy art direction: [캐릭터 기본 시트](todo-quest-character-base-sheet.png)와 [기본 시트 명세](character-base-spec.json)는 팔레트·외곽선·역사적 디자인 방향만 제공한다.

정확한 좌표, 팔레트, 레이어 순서와 합성 규칙은 schema v6 JSON이 canonical 계약이다. schema v6는 schema v5의 기존 layer id와 최상단 weapon group 의미를 보존하면서 중립 훈련복 source와 `gloves_adventure`의 생성 결과를 `available`로 확정한다. 과거 z-order 마이그레이션 전후 수치와 Android 검증 결과는 [캐릭터 레이어 마이그레이션 보고서](character-layer-migration-report.md)에 기록한다.

## 런타임 합성

`CharacterRepository.observeCharacter()`가 제공하는 appearance와 equipped item id를 `CharacterRenderState`로 만들고, Character 화면과 Calendar Battle Map이 같은 `LayeredCharacterSprite`를 사용한다. `CharacterBitmapComposer`는 asset path별 decode cache와 render-state별 composite LRU cache를 사용해 원점에서 source-over 합성하고, 완성된 `64×64` bitmap을 화면에서 한 번만 확대한다.

Room v15는 Room v5에서 시작한 appearance fallback을 보존하되 빈 gameplay loadout을 중립 훈련복으로 정규화한다. 실제 inventory·ownership·장착과 장비 능력치 source는 별도 `owned_equipment`·`character_equipment`에 있고, gameplay layer projection만 이 fallback 위에 합성한다.

## 빈 slot과 모험가 상점 세트 계약

schema v6 명세의 `loadoutArtContract` v1은 생성 완료 상태인 `available`이다. 빈 gameplay slot은 `CHEST → top_default`, `LEGS → bottom_default`, `SHOES → shoes_default`의 회갈색 중립 훈련복을 사용하고, `HELMET`·`GLOVES`·`ACCESSORY`·`WEAPON`은 투명 overlay로 표현한다. 빈 slot이어도 `body_base`, 기본 머리카락 앞·뒤와 맨손 `hands_front`는 장비가 아니므로 항상 유지한다.

기존 `headgear_adventure`, `top_adventure`, `bottom_adventure`, `shoes_adventure`, `accessory_adventure`와 3분할 기본 검은 이름과 의미를 유지한 채 7부위 `adventure_set`의 상품 layer로 사용한다. 생성된 `gloves_adventure`는 `hands_front`와 같은 원점·38픽셀 alpha mask를 사용하며 파란색·청록색 모험가 색상만 적용한다. 기본 검 상품의 논리 layer key는 `weapon_default_sword`이지만 runtime source는 계속 `weapon_back_default_sword`, `weapon_held_default_sword`, `weapon_front_default_sword` 세 파일이며 단일 PNG로 합치지 않는다.

`top_default`·`bottom_default`·`shoes_default`·`gloves_adventure`의 docs canonical/runtime mirror는 byte-identical하며 16개 preview tile과 docs/runtime 생성 시트도 schema v6 manifest에 따라 결정론적으로 재생성됐다. 생성 시트는 이후에도 runtime 합성 source가 아니다.

## 검증

저장소 루트에서 [캐릭터 자산 builder](../../../scripts/build_character_assets.py)와 [캐릭터 시트 검증기](../../../scripts/validate_character_sheet.py)로 canonical/runtime/generated 일치와 두 시트를 검증한다.

```powershell
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
```
