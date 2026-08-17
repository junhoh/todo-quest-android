# 캐릭터 레이어 마이그레이션 보고서

## Step 0 사전 감사 범위와 계산 방식

이 문서는 character layer runtime composition 구현 전인 phase 32 step 0의 기준 상태를 기록한다. PNG와 JSON, Android 코드는 수정하지 않았다. 모든 수치는 저장소 루트에서 Pillow 12.3.0으로 PNG를 `load()`한 뒤 계산했다.

- 파일 SHA-256: PNG 파일의 원본 byte 전체
- raw RGBA SHA-256: Pillow가 제공한 RGBA 픽셀을 왼쪽 위부터 row-major 순서로 직렬화한 `image.tobytes()`
- alpha-mask SHA-256: 같은 순서의 alpha byte 1개씩을 직렬화한 `image.getchannel("A").tobytes()`
- inclusive opaque bounds: alpha가 0이 아닌 픽셀의 Pillow exclusive `getbbox()`를 `[left, top, right - 1, bottom - 1]`로 변환한 값
- 타일 hash: 각 spec의 `logicalTile`과 `tileMap` 좌표로 자른 64×64 RGBA의 row-major raw byte SHA-256

색상만으로 guide pixel을 분류하지 않았다. 특히 `#E05252`와 teal 계열은 실제 캐릭터 픽셀일 수 있으므로 geometry는 alpha와 좌표 영역으로 측정했고, 손 피부 38개만 계약에 명시된 `skinLight`/`skinShadow` RGBA를 기준으로 추출했다.

## PNG 원본 식별

아래 경로는 모두 저장소 루트 기준 실제 경로다.

| 역할 | 실제 경로 | 크기 | mode | 파일 byte | 파일 SHA-256 | raw RGBA SHA-256 | alpha-mask SHA-256 | inclusive opaque bounds |
|---|---|---:|---|---:|---|---|---|---|
| 외부 base body | `docs/art/character/todo-quest-character-base-body.png` | 64×64 | RGBA | 948 | `465ba078046e3bc48b8c6477b4913d5bea5359795313a0a2bc4fe7205b973478` | `7a63a4100e9954edc2705462f3b441ee8fb639058fc83a9ecc596f7bd98293d9` | `50a09892663855568f303c8f1b840ed289900d44b523aa423b8d76dc3f8922e8` | `[20,7,44,58]` |
| 현재 modular sheet | `docs/art/character/todo-quest-character-modular-sheet.png` | 512×128 | RGBA | 6,339 | `23637f01f08dea8c505d388353bca20a46c33e36fdd4e7a3e56a541b87aa7bab` | `ae5245796f3954f99a629ca8765d57291647663eaabc8507323192b0beea3e04` | `dea3eaf219173a4a7b3baf8277fd1c91413c214e6447fe69b4d6ed959b2aebfe` | `[20,4,492,122]` |
| legacy base sheet | `docs/art/character/todo-quest-character-base-sheet.png` | 384×128 | RGBA | 9,238 | `0dd6dc4ce609871c72065e641d3a92c38b0911e42c34fd5dfb3954b69187035c` | `13345782e827a27df3b5b6f57d7a345438045e0d803d6e11ab3e0782113a09d7` | `c0418ea4aeb364c39e8a9905477820f7eca2509602a724f91129dad062c81fcb` | `[20,4,379,122]` |

저장소 전체의 파일명을 재귀 검색해 위 세 basename 뒤에 숫자, 괄호 숫자 또는 `copy`가 붙은 사본을 확인했으나 번호가 붙은 사본은 없었다. Android의 `app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png`는 번호 사본이 아니라 runtime resource이며 아래에서 별도로 대조한다.

## 타일 raw RGBA SHA-256

### 외부 base body

64×64 이미지 전체를 단일 타일로 보았다.

| 타일 | row/column | raw RGBA SHA-256 |
|---|---|---|
| `base-body` | 0/0 | `7a63a4100e9954edc2705462f3b441ee8fb639058fc83a9ecc596f7bd98293d9` |

### 현재 modular sheet

| 타일 | row/column | raw RGBA SHA-256 |
|---|---|---|
| `equipped` | 0/0 | `fef63f6ccc8aff859236ac516ca286c69ebcfafdb2f4ffc2e0671a1f18bb79d5` |
| `default-outfit` | 0/1 | `ae0245a77970080d211e91bf9d50a727345e02bbdd67aed4f74b9f8596f9d67c` |
| `default-hair-preview` | 0/2 | `74b42c9245cc1d2d6625963f5b161df6431a5b0c168014c7c895f1584a12d082` |
| `anchors` | 0/3 | `d6e7c60fb94dd413a936a599fd0c6fe041a21b1660688df6e51eeb2e857c38ee` |
| `palette` | 0/4 | `dd8118b21a94bdb0f7d0dd904496844fc17ab4e3472a8fa07d3c1badaaf11531` |
| `default-top-layer` | 0/5 | `f69170bcf6ca5ef1691512d497a71ada64adb353a5dddae94c1e4597393ddb98` |
| `default-bottom-layer` | 0/6 | `8f7501c83021f202e83e6d8541abc6082ac3da2b3cb73e916011a4bc6723c22f` |
| `default-shoes-layer` | 0/7 | `c8421158b67d96e8ac14148744edbe4195d51a505cc263f0d14ac8dc19242dfd` |
| `default-hair-back-layer` | 1/0 | `868cb31b177d67b204ac929d9c5dc327e9beac269eaebe84eb0de5d4ec8b06c5` |
| `adventure-shoes-layer` | 1/1 | `d6f76051ebd9c92e9519bb6084658ae651dd1e865b9d662e7b236bd5c1fd78bd` |
| `adventure-bottom-layer` | 1/2 | `6a31a1b6b1309c5052730a9f034606f01c5919a7743e44875f95757cebf3550d` |
| `adventure-top-layer` | 1/3 | `fba5e054b7df8b8372630dec6dd3aaa1d63e6dcc29674aa6b38f349868266dfe` |
| `default-hair-front-layer` | 1/4 | `fbee9f420877dbd00cd2d3c7d5ee626dbc4050189c15ea7e86c9a3e2cddc0f70` |
| `head-gear-layer` | 1/5 | `4cd87e2791d5d59877ccd674b63f385382d21d5502bd487dd281f85a5dbf99ae` |
| `accessory-layer` | 1/6 | `bf3361d94522aac7bb4d105df0c2de912a11b4016c564aa5f6aacc243277d806` |
| `composite` | 1/7 | `fef63f6ccc8aff859236ac516ca286c69ebcfafdb2f4ffc2e0671a1f18bb79d5` |

`equipped`와 `composite`는 4,096개 RGBA 픽셀 모두 동일하다.

### legacy base sheet

| 타일 | row/column | raw RGBA SHA-256 |
|---|---|---|
| `equipped` | 0/0 | `257ef35e6fcbb1d49ff6bbf61bcebb347d930a2316cc120506477ce01d1080b9` |
| `base` | 0/1 | `3cbf468bff4f83d1fcbc070b1f00a05891b9899d4795b257ec983cf9db64c6a4` |
| `slot-map` | 0/2 | `74278b9db798cd39ea27ca9db0212d4ecb88a485881eb15f428ac7585a0e48ce` |
| `anchors` | 0/3 | `86755c21757f7a13750b6d6e63e5e6513894f208fd1740ab68f7c130af40048c` |
| `hair-split` | 0/4 | `b213bd56ca424012f8face1c3348454ddb60d13375acd586a95a98223b9ed82a` |
| `palette` | 0/5 | `9fda8e2225b9bd350aaf8c2c6dda5ada12bbe626dba1b76013cfc4f96421c1d8` |
| `head-layer` | 1/0 | `323212d4e307879c578553c8146cf8b47fd36a4ec2a5a89c036d4bc9e1fe831e` |
| `face-layer` | 1/1 | `abee0b83d14a7ca4097b9f360aa7b10002aa91556de019ee4c2d3a6ca206c6d0` |
| `upper-layer` | 1/2 | `d0f069219b6026b4a1b917427e983f9e103d2f5405fd25651a597ea6339cfd61` |
| `lower-layer` | 1/3 | `c05a95ee53758ccb608114d87f95a6305a908fed5f32435f65ef64cb7ea52557` |
| `shoes-layer` | 1/4 | `ccb0c5ef81a7b03f2195eb986889370799620cf44df5fd0faedc91c8b6db2420` |
| `accessory-weapon-layer` | 1/5 | `fd82cde724ba710589d67b4d5989a87a77e06d7783096a2b597cc688116e1b67` |

## base body geometry 기준

`bodyOpaqueBounds`는 alpha가 0이 아닌 1,025개 픽셀로 계산한 `[20,7,44,58]`이다. 따라서 폭 중심은 `(20 + 44) / 2 = 32`, 즉 `centerX=32`이고 마지막 opaque row는 `soleY=58`이다. 다음 영역은 모두 inclusive local coordinate다.

| 영역 | bounds/anchor | 측정 근거 |
|---|---|---|
| head | `[20,7,44,28]` | y=7..28 alpha를 제한해 다시 구한 bounds이며 opaque 448개다. |
| neck transition | y=29..30 | y=29의 opaque span은 x=26..37(12개), y=30은 x=25..39(15개)다. |
| shoulder band | y=30..35 | 이 구간의 opaque 104개를 포함하며 span이 x=25..39에서 x=22..42로 넓어진다. |
| shoulder anchors | `[22,35]`, `[42,35]` | 두 좌표 모두 opaque `#263B5A`이고 y=35 outer endpoints다. |
| torso | `[24,30,40,43]` | 238칸 중 234칸이 opaque다. |
| waist overlap | `[24,41,40,43]` | 17×3=51칸 모두 opaque다. |
| ankle overlap left | `[24,53,31,54]` | 8×2=16칸 모두 opaque다. |
| ankle overlap right | `[33,53,40,54]` | 8×2=16칸 모두 opaque다. |

hand protected regions는 left `[20,39,24,45]`, right `[40,39,44,45]`다. 이 두 영역에서 base body의 `skinLight` 또는 `skinShadow`인 피부 픽셀은 정확히 38개다. extraction order는 left 후 right, 각 영역 안에서 y/x row-major다.

```text
left (19):
(22,39) (23,39) (24,39) (22,40) (23,40) (24,40)
(22,41) (23,41) (21,42) (22,42) (23,42) (21,43)
(22,43) (23,43) (21,44) (22,44) (23,44) (22,45) (23,45)

right (19):
(40,39) (41,39) (42,39) (40,40) (41,40) (42,40)
(41,41) (42,41) (41,42) (42,42) (43,42) (41,43)
(42,43) (43,43) (41,44) (42,44) (43,44) (41,45) (42,45)
```

## 현재 레이어 침범과 composite 차이

face region은 현재 spec의 `faceProtectionContract.protectedRegion=[20,20,44,28]`로 측정했다. 레이어의 opaque 픽셀 수와 그중 base body의 opaque 픽셀을 실제 source-over로 덮는 수를 함께 기록했다.

| 레이어 | face region opaque | base body 실제 덮음 | row별 opaque/덮음(y=24..28) |
|---|---:|---:|---|
| `default-top-layer` | 78 | 62 | `5/5, 14/14, 19/17, 19/15, 21/11` |
| `adventure-top-layer` | 67 | 67 | `10/10, 14/14, 17/17, 15/15, 11/11` |

y=20..23에는 두 top 모두 opaque 픽셀이 없고 침범은 y=24..28에 집중된다. 즉 현재 default/adventure top은 face region을 각각 78픽셀과 67픽셀 점유하며 실제 base body RGBA를 각각 62픽셀과 67픽셀 가린다.

`default-bottom-layer`는 hand protected 피부 좌표 38개 중 17개(left 9, right 8)를 opaque 픽셀로 덮는다.

```text
left:  (23,39) (24,39) (23,40) (24,40) (23,41) (23,42) (23,43) (23,44) (23,45)
right: (40,39) (41,39) (40,40) (41,40) (41,41) (41,42) (41,43) (41,44)
```

legacy는 `todo-quest-character-base-sheet.png`의 `equipped` `(row=0,column=0)`, current composite는 modular sheet의 `composite` `(row=1,column=7)`로 비교했다. 4,096픽셀 중 RGBA가 다른 픽셀은 1,095개이고 inclusive difference bounds는 `[20,4,44,58]`다. alpha가 다른 픽셀은 227개이며 alpha difference bounds도 `[20,4,44,58]`다. legacy raw hash는 `257ef35e6fcbb1d49ff6bbf61bcebb347d930a2316cc120506477ce01d1080b9`, current composite raw hash는 `fef63f6ccc8aff859236ac516ca286c69ebcfafdb2f4ffc2e0671a1f18bb79d5`다.

## Step 0 당시 Android 렌더링 경로

- Step 0 당시 `app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`: `EquippedCharacterSprite`가 `R.drawable.todo_quest_character_modular_sheet`를 읽고 `srcOffset=IntOffset.Zero`, `srcSize=64×64`로 첫 타일을 직접 잘랐다.
- `app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`: player sprite resource를 같은 modular sheet로 지정하고 `BattleMapDefaults.PLAYER_FRAME`을 전달한다. 이 frame은 `sourceX=0`, `sourceY=0`, `sourceWidth=64`, `sourceHeight=64`다.
- `app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`: `BattleUnitSprite`의 `drawImage`가 전달받은 frame의 `srcOffset`/`srcSize`로 source rect를 직접 자른다.

따라서 Step 0 당시 Character 화면과 Battle Map 모두 runtime layer composition 없이 modular sheet 첫 `equipped` 타일을 렌더링했다.

Step 0 당시 Android resource `app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png`와 docs PNG는 둘 다 6,339 byte이고 파일 SHA-256이 `23637f01f08dea8c505d388353bca20a46c33e36fdd4e7a3e56a541b87aa7bab`로 같으므로 byte-identical이었다.

## 충돌 목록

1. 실제 base body raw RGBA SHA-256은 `7a63a4100e9954edc2705462f3b441ee8fb639058fc83a9ecc596f7bd98293d9`지만 `originalBodyBaseReference.sha256.value`는 `ad4e6a19b07832fa7256a7f47282894fced2c2e58b4ead4dd0c7ebcc5ac659e4`다. RGBA hash는 불일치한다.
2. 실제 base body alpha-mask SHA-256과 spec의 `originalBodyBaseReference.alphaMaskSha256.value`는 모두 `50a09892663855568f303c8f1b840ed289900d44b523aa423b8d76dc3f8922e8`로 일치한다. 즉 silhouette는 같지만 RGBA 계약은 오래됐다.
3. `originalBodyBaseReference.tile`은 존재하지 않는 `body-base`를 `(row=0,column=1)`로 참조한다. 실제 `tileMap`의 `(row=0,column=1)`은 `default-outfit`이며 raw hash는 `ae0245a77970080d211e91bf9d50a727345e02bbdd67aed4f74b9f8596f9d67c`다.
4. 현재 modular sheet는 512×128, 즉 **65,536**픽셀이다. 그러나 `targetedEditContract.hashSerialization`은 baseline sheet가 32,768픽셀 전체를 hash한다고 기록해 실제 픽셀 수와 2배 차이가 난다.
5. `targetedEditContract.baselineSheetRgbaSha256`는 `42b40ddb93efc1294a15d9913347809cbfeb5486d890ec0184e4b7d52db056ac`지만 실제 전체 sheet raw RGBA hash는 `ae5245796f3954f99a629ca8765d57291647663eaabc8507323192b0beea3e04`다. targeted baseline이 stale하다. 다만 명시된 preserved tile 11개의 개별 raw hash는 현재 PNG와 모두 일치한다.
6. `capturedFromSheetSha256`는 `404ec074dfc40fa62c1c25c71b1a3bd6217a91553d83029996b8d567619933d0`지만 현재 modular sheet 파일 SHA-256은 `23637f01f08dea8c505d388353bca20a46c33e36fdd4e7a3e56a541b87aa7bab`다. 현재 `validationMode=embedded-contract`와 `capturedFromSheetRequiredAtValidation=false` 때문에 validator는 이 stale file hash와 잘못된 tile reference를 읽지 않는다.
7. 현재 validator는 embedded alpha bitset, face feature 좌표와 preserved tile hash를 검증하지만 `originalBodyBaseReference.sha256.value` 및 전체 `baselineSheetRgbaSha256` 자체는 비교하지 않는다. 그래서 위 충돌이 있어도 현재 base sheet와 modular sheet validator 실행은 모두 통과한다.
8. top 두 레이어의 face region 침범, default bottom의 17개 hand 피부 침범과 legacy/current 1,095픽셀 차이는 향후 레이어 재매핑의 입력 사실이며 이 step에서는 수정하지 않았다.

## 불변 규칙 확인

AGENTS.md의 파괴적 명령 금지를 지켰고 PNG, JSON, Android 코드를 변경하지 않았다. 외부 base body를 geometry canonical reference로 유지했으며 spec hash에 맞추기 위한 이미지 수정이나 색상 기반 guide 제거를 수행하지 않았다.

---

## Step 12 최종 검증 결론

2026-07-22 기준 마이그레이션은 완료됐다. production runtime은 generated sheet의 첫 타일을 읽지 않고 Room v5의 appearance·equipped item 상태를 15개 독립 asset에 매핑한 뒤 `CharacterBitmapComposer`로 합성한다. Character 화면과 Calendar Battle Map player는 같은 `CharacterRenderState`와 `LayeredCharacterSprite`를 사용한다. canonical/runtime/generated asset 검증, 64개 loadout, Room migration·Repository, Android unit/lint/build와 API 37 16 KB emulator connected test가 모두 통과했다.

## 수정·생성 파일과 역할

| 역할 | 파일 | 결과 |
|---|---|---|
| geometry canonical | `docs/art/character/todo-quest-character-base-body.png` | `body_base`의 유일한 geometry·RGBA 기준이며 변경하지 않았다. |
| canonical contract | `docs/art/character/character-modular-sheet-spec.json` | schema v4, 15개 source, anchor, z-order, production/debug palette 분리, 저장 hash를 정의한다. |
| canonical appearance source | `docs/art/character/layers/*.png` | body를 제외한 14개 독립 `64×64` source다. 아래 layer 표가 파일별 전체 목록이다. |
| runtime source | `app/src/main/assets/character/layers/*.png` | `body_base`를 포함한 15개 파일이며 각 canonical source와 byte-identical하다. |
| generated sheet | `docs/art/character/todo-quest-character-modular-sheet.png`, `app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png` | SHA-256 `cbe55a6cd81bb6dbd580a17d4e0525259825e0db311068448ea39dff9fb12be7`인 deterministic debug·golden 복사본이다. production code는 이를 읽지 않는다. |
| generated previews | `docs/art/character/previews/*.png` | 16종의 1×/8×, 총 32개 deterministic preview다. required 조합 10개와 body/debug/palette/golden 보조 결과를 포함한다. |
| asset builder·validator | `scripts/build_character_assets.py`, `scripts/validate_character_sheet.py`, `scripts/test_build_character_assets.py`, `scripts/test_validate_character_sheet.py`, `scripts/fixtures/character-modular-sheet-spec-v3.json` | schema v4 contract/layer/build/check, 64개 조합과 v1~v3 validator 회귀를 검증한다. |
| Room v5 | `app/schemas/com.todoquest.data.local.TodoQuestDatabase/5.json`, `CharacterAppearanceEntity.kt`, `CharacterEquippedItemsEntity.kt`, `CharacterProfileDao.kt`, `TodoQuestDatabase.kt` | appearance·equipped rows, `MIGRATION_4_5`, fresh 4-row 원자 초기화를 제공한다. |
| domain·Repository | `CharacterLoadout.kt`, `CharacterSnapshot.kt`, `CharacterRepository.kt`, `CharacterMapper.kt`, `RoomCharacterRepository.kt`, `RoomTaskRepository.kt`, `RoomCombatRepository.kt` | catalog 검증, loadout 관찰·갱신과 기존 생성 경로의 기본 loadout 보장을 담당한다. |
| runtime composer | `ui/character/CharacterLayerCatalog.kt`, `CharacterBitmapComposer.kt`, `LayeredCharacterSprite.kt` | schema v4 순서 매핑, asset/composite 2단계 cache, shared 최근접 보간 렌더를 담당한다. |
| presentation | `CharacterUiState.kt`, `CharacterViewModel.kt`, `CharacterScreen.kt`, `CalendarViewModel.kt`, `BattleMapUiModel.kt`, `BattleMap.kt`, 관련 Preview·문자열 | Character와 Battle player에 동일한 loadout 상태를 전달하고 monster drawable 경로는 유지한다. |
| Android tests | migration·DAO·Repository·ViewModel·catalog·composer unit test와 `BattleMapTest`, `CalendarDayIndicatorTest`, `CharacterScreenTest`, `TodoQuestDatabaseIsolationTest` | v5와 shared runtime 경계를 회귀 검증한다. |
| 문서·phase | `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`, `docs/UI_GUIDE.md`, `docs/art/character/README.md`, 이 보고서, `phases/030-039/32-character-layer-runtime-composition/index.json`, `phases/index.json` | 구현 범위, no-selection-UI 제약과 최종 검증 상태를 기록한다. |

## 최종 semantic anchor

모든 좌표와 bounds는 `64×64` local canvas의 inclusive 값이다.

| 의미 | 최종 값 | 계약 |
|---|---|---|
| canvas | `[0,0,63,63]` | 모든 source가 같은 원점을 사용하며 item별 offset·crop·scale은 금지한다. |
| appearance envelope | `[20,4,44,58]` | 모든 appearance source와 composite의 허용 범위다. |
| body opaque bounds | `[20,7,44,58]` | centerX `32`, soleY `58`을 유지한다. |
| head / protected face | `[20,7,44,28]` | appearance layer는 body face feature를 대체하지 않는다. |
| neck | top `y=29`, base `y=30` | body와 top 전환 기준이다. |
| shoulder band / anchors | `y=30..35`, left `[22,35]`, right `[42,35]` | 상의 mapping 기준이다. |
| torso | `[24,30,40,43]` | neutral underwear coverage 기준이다. |
| waist overlap | `[24,41,40,43]` | top과 bottom이 51픽셀 모두 공유하며 hidden double outline을 금지한다. |
| ankle overlap | left `[24,53,31,54]`, right `[33,53,40,54]` | bottom과 shoes가 총 32픽셀을 공유한다. |
| protected hands | left `[20,39,24,45]`, right `[40,39,44,45]` | `hands_front`가 body의 피부 RGBA 38개를 같은 좌표에 보존한다. |
| primary sword grip | `[42,42]` | `weapon_held → hands_front → weapon_front` 가림 순서를 사용한다. |

## 최종 z-order

| z | slot | 현재 source |
|---:|---|---|
| 0 | `accessory_back` | 미사용 |
| 1 | `hair_back` | `hair_back_default` |
| 2 | `headgear_back` | 미사용 |
| 3 | `weapon_back` | `weapon_back_default_sword` 또는 생략 |
| 4 | `body_base` | `body_base` |
| 5 | `shoes` | `shoes_default` 또는 `shoes_adventure` |
| 6 | `bottom` | `bottom_default` 또는 `bottom_adventure` |
| 7 | `top` | `top_default` 또는 `top_adventure` |
| 8 | `weapon_held` | `weapon_held_default_sword` 또는 생략 |
| 9 | `hands_front` | `hands_front` |
| 10 | `weapon_front` | `weapon_front_default_sword` 또는 생략 |
| 11 | `face_overlay` | 미사용; face는 `body_base`에 보존 |
| 12 | `hair_front` | `hair_front_default` |
| 13 | `headgear_front` | `headgear_adventure` 또는 생략 |
| 14 | `accessory_front` | `accessory_adventure` 또는 생략 |

## 최종 layer bounds와 hash

`file`은 저장 PNG byte, `RGBA`는 4,096개 raw pixel, `alpha`는 4,096개 alpha byte의 SHA-256이다. 아래 canonical source와 대응 runtime asset은 모두 byte-identical하므로 같은 세 hash를 사용한다.

| layer | inclusive opaque bounds | opaque px | file SHA-256 | raw RGBA SHA-256 | alpha SHA-256 |
|---|---|---:|---|---|---|
| `body_base` | `[20,7,44,58]` | 1025 | `465ba078046e3bc48b8c6477b4913d5bea5359795313a0a2bc4fe7205b973478` | `7a63a4100e9954edc2705462f3b441ee8fb639058fc83a9ecc596f7bd98293d9` | `50a09892663855568f303c8f1b840ed289900d44b523aa423b8d76dc3f8922e8` |
| `hair_back_default` | `[20,4,44,30]` | 520 | `af797866b575f1ceecf31d324bc7bab19313bb8f295609dc8b44f6a55201a7c8` | `868cb31b177d67b204ac929d9c5dc327e9beac269eaebe84eb0de5d4ec8b06c5` | `58567952bc56b5bcd324bcf5794664f904f53adf45f4aa9acde161342a2b0fb8` |
| `hair_front_default` | `[20,4,44,30]` | 383 | `8df4a8e6c31453e8091e3cbd19a4309706f1e905cb5d6babb850c186204ba675` | `fbee9f420877dbd00cd2d3c7d5ee626dbc4050189c15ea7e86c9a3e2cddc0f70` | `2f1e22336d850903d835e793a29a0ef6f0b78430ee241c086295a8986a608640` |
| `hands_front` | `[21,39,43,45]` | 38 | `c6c8068b2dfa6aea53ecacd208e3a77b2dfa27346d8b75f7ca56c298d6c4b039` | `7a115515268da155790e74cef2c99609a750512f7558582cfab7055d9d7209b0` | `115452260a8f6d94e7dd000bda02875d52ec02a0516be5b547b82da8fc3169e3` |
| `top_default` | `[20,29,44,45]` | 271 | `7fb4383467e7e88a656a7a8eaf244258e9a53c1e0c714966b4aed92c66b097fa` | `bc8e56bb43eb044e5c6bc906a585988e8e15a60310eda91c408ca336a1e5faee` | `d3c062d7bf83071f43757952fe086e767cfd1fc261a6ca976d5617d0d320ce8d` |
| `bottom_default` | `[24,41,40,54]` | 232 | `2b894e2c709f233f33abef31c63e62ee458974fa1512b3a15040e4c9528a2ad5` | `d50281da6662d09b7a6756bb9f4ff5a38907012a29f36cfa2573f946092cda92` | `63cde22b2797b42c67a6eba9da5459ef1e4332364b4769110f98842ccf75ad47` |
| `shoes_default` | `[23,53,41,58]` | 104 | `007772620fb92ae0df685b5a319d6f747d0d6fe2612c1c5c8777f3b4a54af175` | `ca5b565f4db7e83c1d55bcaff3c3ce789215cacb30ac4d3fa8731e7173a8bc9c` | `9d42064b05d8ede2c5ca9ce1cb0dc7c0551ed11f2e9f855e6e25fe0e69f0f104` |
| `top_adventure` | `[20,29,44,43]` | 297 | `4278a1ddd6be0a7cac5ae5c69fefcc94cc2d67b4a7f4162cd8b483827edc792e` | `615f0ae0a4b3379000ff273291929869ca3620f2714f0f9a13a96db7a98d0953` | `b0c588b72bf93791a322f01ac73c5349cf122167565c4b4dd658946fdb739eb1` |
| `bottom_adventure` | `[24,41,40,54]` | 232 | `f689ae531232efb6c227db282ede4292c18b918f8bd7cfc4fdeb1e716974bf96` | `0dc6a3e2ee20b7ab5ddecb0fd04ff6dd663b96b249c36d3a7fac222ae31b801d` | `63cde22b2797b42c67a6eba9da5459ef1e4332364b4769110f98842ccf75ad47` |
| `shoes_adventure` | `[23,53,41,58]` | 104 | `e41bb37f56deff8c156e369427291a8b3eb1ed3b0fb423ff45be1cc6401335d6` | `d6f76051ebd9c92e9519bb6084658ae651dd1e865b9d662e7b236bd5c1fd78bd` | `9d42064b05d8ede2c5ca9ce1cb0dc7c0551ed11f2e9f855e6e25fe0e69f0f104` |
| `headgear_adventure` | `[20,7,44,25]` | 363 | `7dd56f9690a475ec3441c71e4a5101369120ea60de8ac694e0bab9e19c25767f` | `4cd87e2791d5d59877ccd674b63f385382d21d5502bd487dd281f85a5dbf99ae` | `ff3d5a233dd97ad7f0fd3af80fa59bde083a20a639b0734d8d1d77477f956e50` |
| `accessory_adventure` | `[20,24,44,36]` | 119 | `69a688be1a4a04e7b221bf4e193195071d9c5774edef99fc804599a908474b0e` | `bf3361d94522aac7bb4d105df0c2de912a11b4016c564aa5f6aacc243277d806` | `2cd0161e69e7ae932fbf50dab8a0743dd60ca7e06c15807a0ffce83dac3b6b42` |
| `weapon_back_default_sword` | `[42,13,43,38]` | 51 | `b5dc22837299d4f7dda13046c9f119c97de0c7d55f5787e5994c92624a8a75cc` | `2963628116af2c1e907ed848d407ea6c446be9b5b5f3e7e04e271e4ff28cca32` | `e8eea9f1a45d1787db73dd82cbd2e2a3d71757bb8c6995c6f5e3e81f9171d3af` |
| `weapon_held_default_sword` | `[41,40,44,54]` | 40 | `d1d68d6b508ff708b752baec0fe371556fa2feed73555830e17c91b6a8fb3244` | `8845bbba0ba4cddef519128dc60c222989a7049c8ee1b024f09908fdd6ccdb65` | `c3baa19897ad92da406c8f70dbc8c3fc081a7b96ec23fac366a51a9c96ca655f` |
| `weapon_front_default_sword` | `[41,39,44,39]` | 4 | `fff1d5f5dd29ef0ecfaf497eb634dca07b865e8612e152bdc2e3d4c1ea5d4fb7` | `a3e08c1a54d8c5c8e550c735bdeea59359301ecf9712719203f830fd88ec648a` | `072af167565156061db10603bea978e5567a6dd8486a52589d9093f03d73df11` |

## palette set과 debug guide 분리

legacy `character-base-spec.json`과 schema v4 production palette는 16개 RGB 값의 set이 정확히 같다. 기존 semantic key `guideRed=#E05252`는 실제 캐릭터에 쓰이는 production 색상이므로 `redAccent=#E05252`로 이름을 바로잡았다. debug overlay는 production set 밖의 hard key `debugGuideColor=#FF00FF`로 분리했다.

- production `redAccent`: `#E05252`, canonical/runtime layer에서 허용
- debug `debugGuideColor`: `#FF00FF`, `anchors-preview`와 `layer-bounds-preview`의 generated overlay에서만 허용
- `--check` 결과 canonical/runtime 15개 layer에 `#FF00FF` contamination이 없고, debug 색상은 production palette member가 아니다.

## required preview 10개와 64개 조합

| preview | 조합 의미 | file SHA-256 | raw RGBA SHA-256 | 결과 |
|---|---|---|---|---|
| `default-equipped` | default top/bottom/shoes, optional off | `42ecd1cba94cb868070a5592bea2685b324f76fbcee965d538a8693d1e332911` | `cacd562a5afd7828510be4b8bd23c1724c5593e9b715b99d28e9f538e5cf0b45` | 통과 |
| `adventure-equipped` | adventure 전체, optional on | `11d7bbf1c32841330d7b8690b2edfec8bf0d1c550e51437113f579e7bf4961f7` | `35a72279ff39892e5eb160bb314d11a2b76e88ba8bc37e78dec7b0b6abadf7b4` | 통과 |
| `mixed-default-top` | default top + adventure bottom/shoes | `eab8a646db3512c720634080d6252d9620d252f978a567b794c7ccb04b758852` | `7bc2a1ca82f9a3182e9e7d1144a53238f41a5c82c6d107630fa3be4ce5242380` | 통과 |
| `mixed-adventure-top` | adventure top + default bottom/shoes | `16974a76ecb05d3f5a114ada67435698882196c829c2337972c615ee60709e37` | `b387c92b633284824fd824d1ace6b004be7a7a482b1cf14d746698d1e35d34af` | 통과 |
| `headgear-off` | head 미장착 | `3645ea27ac23ecc5932a85792aebf296582a9c8cb66dc3faa60b5ca2b35f19e9` | `1545ef1977c5bd6722f18d7dce79984c785f39b745ef68fd905bf2b2251643e6` | 통과 |
| `headgear-on` | adventure head 장착 | `11d7bbf1c32841330d7b8690b2edfec8bf0d1c550e51437113f579e7bf4961f7` | `35a72279ff39892e5eb160bb314d11a2b76e88ba8bc37e78dec7b0b6abadf7b4` | 통과 |
| `accessory-off` | accessory 미장착 | `7ea40c18577362e6a957014313cd747b3e761d1f2b37076b62eccf9b121daa97` | `a2cc4515aca00300fded9cf0a9007781d0bd0b42700f8b6308d74c3c3c305b1a` | 통과 |
| `accessory-on` | adventure accessory 장착 | `11d7bbf1c32841330d7b8690b2edfec8bf0d1c550e51437113f579e7bf4961f7` | `35a72279ff39892e5eb160bb314d11a2b76e88ba8bc37e78dec7b0b6abadf7b4` | 통과 |
| `weapon-off` | weapon 미장착 | `50d83e36fe0dfd19ee6860ad39e9468e2266318b85df93811424ef2131d5071d` | `c72a4c8dcec7ccf9ea9350501be91baf6ca8f675905f7f4d695f0ba5e6b6e331` | 통과 |
| `weapon-on` | 3분할 default sword 장착 | `11d7bbf1c32841330d7b8690b2edfec8bf0d1c550e51437113f579e7bf4961f7` | `35a72279ff39892e5eb160bb314d11a2b76e88ba8bc37e78dec7b0b6abadf7b4` | 통과 |

builder와 Android composer는 `2 top × 2 bottom × 2 shoes × 2 head × 2 accessory × 2 weapon = 64`개 loadout을 모두 합성했다. 각 조합은 face feature, 38개 hand RGBA, waist·ankle seam, neutral underwear 비노출, `centerX=32`, `soleY=58`, 입력 layer 좌표·RGBA 불변 검사를 통과했다. 생성 sheet와 `layer-bounds-preview@8x.png`를 이미지 뷰어로 확인해 10개 required 조합과 debug tile의 pixel edge·anchor도 확인했다.

## Room v5 migration과 Repository 결과

- `MIGRATION_4_5`는 기존 `character_profile` row를 변경하지 않고 `character_appearance(characterId, hairId)`와 `character_equipped_items(characterId, headId, topId, bottomId, shoesId, accessoryId, weaponId)`를 만든다.
- 기존 character id마다 `hair_default`, adventure head/top/bottom/shoes/accessory와 `weapon_default_sword`를 `INSERT OR IGNORE`로 추가해 재실행 중복을 방지한다.
- fresh character는 profile·current state·appearance·equipped items 네 row를 `insertCharacterIfAbsent()` transaction에서 함께 생성한다. 부분 row 상태는 허용하지 않는다.
- `CharacterRepository.observeCharacter()`는 두 loadout row를 `CharacterSnapshot`에 포함한다. `updateAppearance()`와 `updateEquippedItems()`는 `CharacterLoadoutCatalog`를 먼저 검증하고 유효한 update만 Room transaction으로 저장한다.
- migration v1/v2/v3/v4→v5, 기본 loadout, mixed loadout 관찰, nullable optional slot, invalid id의 무변경을 unit/Robolectric test가 통과했다.

## Character/Battle runtime golden과 Android 화면

`CharacterBitmapComposerTest.appAssetsComposeEveryLoadoutAndDefaultMatchesRuntimeGoldenRawPixels`는 runtime asset으로 합성한 64개 bitmap이 모두 `64×64` immutable ARGB_8888임을 확인했다. 기본 loadout은 generated sheet `(row=1,column=7)`의 `runtime-equipped-reference`와 raw RGBA SHA-256 `35a72279ff39892e5eb160bb314d11a2b76e88ba8bc37e78dec7b0b6abadf7b4`로 pixel-equal하다. 최초 전체 조합에서 layer decode는 15회, composite는 64회였고, 같은 state 재요청은 두 cache를 재사용하며 LRU eviction 뒤에도 layer를 다시 decode하지 않는다.

`CalendarViewModelTest.mixedLoadoutAndWeaponChangesUpdateTheBattlePlayerWithoutChangingMonsterRenderer`는 Repository loadout 변경이 player `CharacterRenderState`만 갱신하고 goblin drawable renderer를 바꾸지 않음을 확인했다. Character 화면과 Battle Map은 모두 `LayeredCharacterSprite`를 호출하며 production `app/src/main`에서 generated sheet drawable을 참조하는 코드는 0건이다.

API 37 16 KB `Pixel_9` emulator에서 같은 debug APK를 실행해 다음 artifact를 저장하고 원본 해상도로 확인했다.

| 화면 | artifact | SHA-256 | 육안 결과 |
|---|---|---|---|
| Calendar | `app/build/verification/character-layer-migration/emulator-5554/calendar-layered.png` | `e682a3d0656fb71c52ae333dacc2db62b854c1d6379fa33cbef0549f0c16b46d` | Battle Map player가 모자·adventure 의상·accessory·sword를 표시하고 발 anchor·지면 그림자·goblin과 분리됐다. |
| Character | `app/build/verification/character-layer-migration/emulator-5554/character-layered.png` | `cf0456828ee7d4994ff4d6b3b35adf41cabe96c7b74e405a0371e5d57b845436` | 같은 loadout이 더 큰 정수 배율에서도 흐림·crop 없이 표시되고 한국어 요약과 겹치지 않았다. |

## Acceptance Criteria 실행 결과

| 명령 | 결과 |
|---|---|
| `.\.venv\Scripts\python.exe scripts\build_character_assets.py --check` | 통과. canonical/runtime/generated file·RGBA·alpha metadata와 64개 조합 일치. |
| base sheet `validate_character_sheet.py` | 통과. |
| modular sheet schema v4 `validate_character_sheet.py` | 통과. |
| 지정 Python pytest 3개 파일 | `252 passed`, 실패 0. `.pytest_cache` 쓰기 권한 warning 1건은 test 결과와 `--basetemp` artifact에 영향 없음. |
| `.\gradlew.bat test` | 통과. debug 205개 + release 205개, 총 410개, 실패·error·skip 0. |
| `.\gradlew.bat lint` | 통과. |
| `.\gradlew.bat assembleDebug` | 통과. |
| `$env:ANDROID_SERIAL='emulator-5554'; .\gradlew.bat connectedDebugAndroidTest` | API 37 16 KB `Pixel_9`에서 36/36, 실패·skip 0으로 통과. |
| `git diff --check` | 최종 문서·status 갱신 뒤 통과. |

첫 번째 장치 미지정 `connectedDebugAndroidTest`는 동시에 연결된 SM-A325N의 기존 TimePicker test가 영어 semantics `10 hours`를 찾지 못해 실패했고 전체 프로세스가 6분 제한에 걸렸다. 이 실행은 성공으로 기록하지 않는다. 같은 실행에서 emulator 결과는 36/36 통과했으며, AC의 emulator 검증은 위처럼 serial을 고정해 다시 실행하고 성공을 확인했다. 이 물리 기기 locale 의존 TimePicker 문제는 character layer migration의 코드·화면 결과와 무관하며 이번 step에서 제품 또는 테스트 범위를 확장해 수정하지 않았다.

## 최종 불변 조건과 남은 범위

- base-body file/raw RGBA/alpha SHA-256은 각각 `465ba078...3478`, `7a63a410...93d9`, `50a09892...22e8`로 Step 0부터 불변이다.
- `rg`로 `app/src/main`의 `R.drawable.todo_quest_character_modular_sheet`와 generated sheet asset 참조가 0건임을 확인했다. drawable 복사본은 debug·golden artifact일 뿐 production render source가 아니다.
- UI가 Room DAO를 직접 호출하지 않고 `CharacterRepository` 경계를 사용한다. occurrence 완료·RewardLedger·전투 event 멱등성, 반복 occurrence 분리와 알림 권한 실패 독립성은 변경하지 않았다.
- 현재 남은 후속 범위는 사용자 외형 선택 UI, inventory, ownership·획득, 사용자가 수행하는 장착 흐름, 장비 stat·전투 modifier다. appearance/loadout persistence와 update command가 존재해도 이 기능들이 구현됐다는 뜻은 아니다.
