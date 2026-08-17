# 장비 아트 인덱스

이 디렉터리는 gameplay 장비에 대응하는 캐릭터 합성 layer와 검증 계약을 캐릭터 schema v6의 canonical source와 분리해 관리한다. 장비 소유·장착 상태나 능력치 source는 정의하지 않으며, Android는 아래 canonical source와 byte-identical한 runtime PNG만 thumbnail과 착용 합성에 사용한다.

## 투구 layer 계약

[투구 layer 명세](todo-quest-helmet-layers-spec.json)는 상점의 `가죽 모자(1003)`와 `철 투구(1004)`가 사용할 독립 `headgear_front` source를 정의한다. 두 source는 [캐릭터 schema v6](../character/character-modular-sheet-spec.json)의 `64×64` 원점, 중심축 `x=32`, 발 기준 `y=58`, 16색 production palette와 source-over·최근접 확대 규칙을 그대로 사용한다. 기존 layer id의 의미는 변경하지 않는다.

| 장비 | canonical source | runtime source | 불투명 bounds |
|---|---|---|---|
| 가죽 모자 `1003` | [headgear_leather_hat.png](layers/headgear_leather_hat.png) | [headgear_leather_hat.png](../../../app/src/main/assets/character/layers/headgear_leather_hat.png) | `[19, 4, 45, 22]` |
| 철 투구 `1004` | [headgear_iron_helmet.png](layers/headgear_iron_helmet.png) | [headgear_iron_helmet.png](../../../app/src/main/assets/character/layers/headgear_iron_helmet.png) | `[18, 4, 46, 29]` |

명세는 두 canonical/runtime artifact를 `available`로 고정하고 불투명 픽셀 수, 파일 크기와 file/raw-RGBA/alpha-mask SHA-256을 기록한다. runtime 사본은 대응 canonical PNG와 byte-identical해야 한다. 얼굴 보호 영역 `[23, 20, 41, 28]`은 두 layer 모두 투명하며 철 투구도 눈·코·입을 가리지 않는 open-face 합성을 유지한다.

착용 결과는 runtime source가 아닌 deterministic 검증 artifact다.

- 가죽 모자: [1× preview](previews/leather-hat-equipped.png), [8× nearest-neighbor preview](previews/leather-hat-equipped@8x.png)
- 철 투구: [1× preview](previews/iron-helmet-equipped.png), [8× nearest-neighbor preview](previews/iron-helmet-equipped@8x.png)

## 상·하의 layer 계약

[상·하의 layer 명세](todo-quest-top-bottom-layers-spec.json)는 `천 상의(1005)`·`가죽 갑옷(1006)`·`철 흉갑(1007)`의 `top` source와 `천 바지(1008)`·`가죽 바지(1009)`·`강철 각반(1010)`의 `bottom` source를 정의한다. 여섯 canonical/runtime PNG는 모두 `64×64` 전체 원점, 이진 alpha와 16색 production palette를 사용하며 대응 파일끼리 byte-identical하다.

| 장비 | canonical / runtime source | bounds | 불투명 pixel / byte | file SHA-256 |
|---|---|---|---:|---|
| 천 상의 `1005` | [canonical](layers/top_cloth.png) / [runtime](../../../app/src/main/assets/character/layers/top_cloth.png) | `[20, 29, 44, 45]` | `300 / 479` | `3d4e8422b7fb206f0c1cf49eb1b7f96560c9a1f506d6491425c6ca5e4c180061` |
| 가죽 갑옷 `1006` | [canonical](layers/top_leather_armor.png) / [runtime](../../../app/src/main/assets/character/layers/top_leather_armor.png) | `[20, 29, 44, 45]` | `301 / 431` | `2e4f827f0a41681d1e96274970f1f3e212d5d830744ed8b31e1b420f5317f610` |
| 철 흉갑 `1007` | [canonical](layers/top_iron_breastplate.png) / [runtime](../../../app/src/main/assets/character/layers/top_iron_breastplate.png) | `[20, 29, 44, 45]` | `299 / 605` | `85150725cd0ea182e262918ea402c5eb558f9ab782665eb2fd07d83c9f5f2eea` |
| 천 바지 `1008` | [canonical](layers/bottom_cloth_pants.png) / [runtime](../../../app/src/main/assets/character/layers/bottom_cloth_pants.png) | `[24, 41, 40, 54]` | `217 / 333` | `d5560f6e75d4a5b8a17ab27a5be17825956c5b0b61b90466486b9a55dd8ce8ee` |
| 가죽 바지 `1009` | [canonical](layers/bottom_leather_pants.png) / [runtime](../../../app/src/main/assets/character/layers/bottom_leather_pants.png) | `[24, 41, 40, 54]` | `213 / 288` | `1e18b15b36ad71e87ea4b7287a7bca91cb457b67e7e0bf7ceb76f284b2e7e7d4` |
| 강철 각반 `1010` | [canonical](layers/bottom_steel_greaves.png) / [runtime](../../../app/src/main/assets/character/layers/bottom_steel_greaves.png) | `[24, 41, 40, 54]` | `217 / 512` | `8b0d1e506979e18ba93f0b7d4b11800109df674aa155321eea55d2367d4e0b6d` |

착용 preview는 각 장비별 `1×`와 최근접 `8×`를 제공한다.

- 천 상의: [1×](previews/top-cloth-equipped.png), [8×](previews/top-cloth-equipped@8x.png)
- 가죽 갑옷: [1×](previews/top-leather-armor-equipped.png), [8×](previews/top-leather-armor-equipped@8x.png)
- 철 흉갑: [1×](previews/top-iron-breastplate-equipped.png), [8×](previews/top-iron-breastplate-equipped@8x.png)
- 천 바지: [1×](previews/bottom-cloth-pants-equipped.png), [8×](previews/bottom-cloth-pants-equipped@8x.png)
- 가죽 바지: [1×](previews/bottom-leather-pants-equipped.png), [8×](previews/bottom-leather-pants-equipped@8x.png)
- 강철 각반: [1×](previews/bottom-steel-greaves-equipped.png), [8×](previews/bottom-steel-greaves-equipped@8x.png)

모든 상·하의 조합은 [3×3 원본 matrix](previews/top-bottom-combination-matrix.png)와 [4× nearest-neighbor matrix](previews/top-bottom-combination-matrix@4x.png)에 고정한다. 합성은 허리 `[24, 41, 40, 43]`와 양쪽 발목 `[24, 53, 31, 54]`, `[33, 53, 40, 54]`에서 불투명 coverage를 공유하며 투명 seam과 숨은 접합부의 이중 외곽선을 허용하지 않는다.

## 장갑·신발 layer 계약

[장갑·신발 layer 명세](todo-quest-gloves-shoes-layers-spec.json)는 `가죽 장갑(1011)`·`강철 건틀릿(1015)`의 `hands_front` 대체 source와 `여행자의 장화(1012)`·`바람걸음 장화(1016)`의 `shoes` 대체 source를 정의한다. 네 canonical/runtime PNG는 `64×64` 전체 원점과 이진 alpha를 사용하며 대응 파일끼리 byte-identical하다.

| 장비 | canonical / runtime source | bounds | 불투명 pixel / byte | file SHA-256 |
|---|---|---|---:|---|
| 가죽 장갑 `1011` | [canonical](layers/gloves_leather.png) / [runtime](../../../app/src/main/assets/character/layers/gloves_leather.png) | `[21, 39, 43, 45]` | `38 / 208` | `592df0366d81445015b13d3e6aa504e83499e9515743a79081e921f583b9a03a` |
| 강철 건틀릿 `1015` | [canonical](layers/gloves_steel_gauntlets.png) / [runtime](../../../app/src/main/assets/character/layers/gloves_steel_gauntlets.png) | `[21, 39, 43, 45]` | `38 / 225` | `f1cd19b0209cb6226c5e80d826ba395d57d08c0d7ef7b401da3271c48b47948e` |
| 여행자의 장화 `1012` | [canonical](layers/shoes_travelers_boots.png) / [runtime](../../../app/src/main/assets/character/layers/shoes_travelers_boots.png) | `[23, 53, 41, 58]` | `104 / 263` | `3b378876ea5ef7b3acdb214b9034831fd35b8386e8070b494b326622f9b02c32` |
| 바람걸음 장화 `1016` | [canonical](layers/shoes_windwalker_boots.png) / [runtime](../../../app/src/main/assets/character/layers/shoes_windwalker_boots.png) | `[23, 53, 41, 58]` | `104 / 276` | `3f936920a071ff8999a136e9c42917e3b0d929e98f3347eb5668229f6e4037e7` |

착용 preview는 각 장비별 `1×`와 최근접 `8×`를 제공한다.

- 가죽 장갑: [1×](previews/leather-gloves-equipped.png), [8×](previews/leather-gloves-equipped@8x.png)
- 강철 건틀릿: [1×](previews/steel-gauntlets-equipped.png), [8×](previews/steel-gauntlets-equipped@8x.png)
- 여행자의 장화: [1×](previews/travelers-boots-equipped.png), [8×](previews/travelers-boots-equipped@8x.png)
- 바람걸음 장화: [1×](previews/windwalker-boots-equipped.png), [8×](previews/windwalker-boots-equipped@8x.png)

네 장비의 혼합 착용 결과는 [2×2 원본 matrix](previews/gloves-shoes-combination-matrix.png)와 [4× nearest-neighbor matrix](previews/gloves-shoes-combination-matrix@4x.png)에 고정한다. 두 장갑과 `gloves_adventure`는 기존 `hands_front`와 동일한 38픽셀 alpha mask를 사용한다. schema v6가 보존한 기본 검 `weapon_back → weapon_held → weapon_front`와 gameplay 단일 `weapon_front`는 모두 손을 포함한 비무기 layer 뒤에서 그려져 겹치는 무기 pixel을 최종 결과로 보존한다. 두 신발은 다섯 하의와 `y=53..54` 발목 interface를 공유하고 발바닥 기준선 `y=58`을 유지해 투명 틈과 이중 seam을 만들지 않는다. 원본과 preview는 alpha `0/255`와 최근접 확대만 허용한다.

## 빈 slot과 모험가 상점 세트 계약

[장갑·신발 layer 명세](todo-quest-gloves-shoes-layers-spec.json)와 [캐릭터 schema](../character/character-modular-sheet-spec.json)는 같은 `loadoutArtContract` v1을 선언한다. 빈 `CHEST`·`LEGS`·`SHOES`는 각각 `top_default`·`bottom_default`·`shoes_default`의 회갈색 중립 훈련복을 사용하고, 빈 `HELMET`·`GLOVES`·`ACCESSORY`·`WEAPON`은 투명 overlay다. `body_base`, 기본 머리카락과 맨손 `hands_front`는 gameplay 장비가 아니므로 빈 loadout에서도 제거하지 않는다.

기존 adventure 외형은 key를 바꾸거나 삭제하지 않고 고정 ID `1019..1025`인 `WEAPON`·`HELMET`·`CHEST`·`LEGS`·`GLOVES`·`SHOES`·`ACCESSORY` 상품 layer로 승격한다. `GLOVES`는 생성 완료된 `gloves_adventure`를 사용하고 나머지 부위는 각각 `weapon_default_sword`·`headgear_adventure`·`top_adventure`·`bottom_adventure`·`shoes_adventure`·`accessory_adventure`를 사용한다. 기본 검 상품은 논리적으로 `weapon_default_sword` 하나지만 합성은 기존 back/held/front 세 source를 그대로 사용하고 병합 runtime PNG를 허용하지 않는다.

`top_default`·`bottom_default`·`shoes_default`·`gloves_adventure`는 docs/runtime byte 동일성과 `64×64` 원점·`x=32`·`y=58`·최근접 규칙을 만족한다. 16개 캐릭터 preview tile과 docs/runtime 시트는 같은 manifest로 재생성됐고, 이 승격은 기존 layer key 및 저장된 appearance id의 의미를 보존한다.

## 무기 layer 계약

[무기 layer 명세](todo-quest-weapon-layers-spec.json)는 `낡은 검(1001)`·`철 장검(1002)`·`물푸레나무 창(1017)`·`강철 철퇴(1018)`의 단일 `weapon_front` source를 정의한다. 네 canonical/runtime PNG는 `64×64` 전체 원점, primary grip `(42, 42)`, 이진 alpha와 16색 production palette를 유지하며 대응 파일끼리 byte-identical하다.

| 장비 | canonical / runtime source | bounds | file SHA-256 |
|---|---|---|---|
| 낡은 검 `1001` | [canonical](layers/weapon_worn_sword.png) / [runtime](../../../app/src/main/assets/character/layers/weapon_worn_sword.png) | `[40, 4, 58, 58]` | `7d0c103687af8b97dc2d7368754985189bae639485714b4851cee9ccb9f60eaf` |
| 철 장검 `1002` | [canonical](layers/weapon_iron_longsword.png) / [runtime](../../../app/src/main/assets/character/layers/weapon_iron_longsword.png) | `[40, 4, 58, 58]` | `0801a9ad41cb8ccddb6800898ed32e431d9585fe6f033f845ab25cf8c74c62b1` |
| 물푸레나무 창 `1017` | [canonical](layers/weapon_ash_spear.png) / [runtime](../../../app/src/main/assets/character/layers/weapon_ash_spear.png) | `[40, 4, 58, 58]` | `c37c62b25c5349a790d21f5ba5c11909af338ce214a730a706fb59abae68696d` |
| 강철 철퇴 `1018` | [canonical](layers/weapon_steel_mace.png) / [runtime](../../../app/src/main/assets/character/layers/weapon_steel_mace.png) | `[40, 4, 58, 58]` | `64c25331107a4cffb0eb8143807daf6ef0e95af3a855af3d34647496941dba2a` |

각 무기의 [1×/8× 착용 preview](previews/)와 [2×2 원본 matrix](previews/weapon-combination-matrix.png), [4× nearest-neighbor matrix](previews/weapon-combination-matrix@4x.png)는 schema v6가 보존한 최상단 합성 결과를 고정한다. gameplay 무기는 3분할하지 않으며 item별 이동·crop·scale을 적용하지 않는다. 모험가의 검 `1019`만 기존 기본 검의 3분할 source를 하나의 상품 layer key로 재사용한다.

## 검증 경계

저장소 루트에서 다음 명령을 사용한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check-contract
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check-sources
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
```

- `--check-contract`는 PNG 없이 JSON 구조와 고정 값을 검증한다.
- `--check-sources`는 canonical PNG, 1배·8배 preview와 저장 metadata를 검증하며 runtime 사본은 요구하지 않는다.
- `--check`는 source 검증에 runtime PNG metadata와 canonical/runtime byte equality를 추가한다.

캐릭터 합성에서는 source의 `64×64` 원점을 유지하고 이동·crop·scale하지 않는다. 상점 thumbnail만 같은 PNG의 불투명 bounds를 읽기 전용으로 확대할 수 있다.
