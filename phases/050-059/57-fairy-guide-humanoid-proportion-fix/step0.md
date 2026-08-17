# Step 0: redraw-fairy-guide-canonical-art

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/56-fairy-character-stat-guide/step0.md`
- `/phases/050-059/56-fairy-character-stat-guide/step1.md`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`

## 작업

기존 canonical 페어리 PNG를 세로로 늘리거나 비균등 리사이즈하지 않고 논리적 `64×64` 픽셀에서 실루엣과 신체 구조를 다시 그린다. 사용자의 직접 64px 편집 요구가 imagegen의 일반적인 고해상도 생성·축소 흐름보다 우선한다. built-in `image_gen`, CLI/API fallback, 고해상도 source와 리샘플링을 최종 자산 제작에 사용하지 않는다.

테스트를 먼저 작성한다.

- `/scripts/test_validate_npc_sprite.py`에 `test_fairy_guide_humanoid_proportion_contract`를 추가한다.
- 테스트는 PNG와 JSON을 함께 읽어 `64×64 RGBA`, 실측 불투명 bounds와 `expectedOpaqueBoundingBox` 일치, `opaqueHeight`와 실측 높이 일치, `heightPercentOfReferenceApprox == round(opaqueHeight / 52 * 100, 1)`을 확인한다.
- 목표값은 bounds `[8,14,56,54]`, 높이 `41`, 비율 `78.8`, `centerX=32`, `soleY=54`, `headCenter=[32,22]`, `shoulderY=29`, `waistY=39`, `anchors.soleY=54`, `pose.bothToeTipsReachY=54`다.
- 양쪽 `groundContacts.y`가 54이고 각 발 접점이 독립된 x 범위를 사용하는지 확인한다.
- `wingColors.region`은 목표 `[8,24,56,43]` 안에 있고 모든 `wingShadow`·`wingLight` 픽셀이 해당 region 안에 있는지 확인한다.
- `goldAccent`는 별 영역, `inkDeep`는 얼굴 영역에만 있고 각 semantic region의 좌표가 실제 해당 색상 픽셀을 포함하는지 확인한다.
- wing 색상과 공용 외곽선을 제외한 머리·피부·의상·가죽·얼굴 색상은 신체 목표 영역 `x=22..42`, `y=15..54` 밖으로 나가지 않는지 확인한다. `goldAccent`의 머리 장식 최고점 `y=14`는 예외다.
- 새 테스트를 기존 PNG에 실행해 비율 계약이 실패하는 것을 확인한 뒤 작화를 시작한다.

기존 파일을 덮어쓰기 전에 다음 비교 산출물을 만든다. 모두 `build/fairy-guide-humanoid-proportion-fix/` 아래에 두고 커밋하지 않는다.

- `original.png`: 현재 canonical PNG의 byte-for-byte 사본.
- `candidate.png`: 새 64×64 RGBA 후보.
- `candidate-spec.json`: 후보를 검증할 spec 사본.
- `original@8x.png`, `candidate@8x.png`: `Image.Resampling.NEAREST`만 사용한 검토용 확대본. 확대본은 검토용일 뿐 canonical 원천이 아니다.

후보는 다음 픽셀 구조를 만족해야 한다.

- 전체 중심축은 `x=32`, 목표 전체 불투명 bounds는 양 끝 포함 `[8,14,56,54]`다. 시각 품질을 위해 목표와 1~2px 차이가 불가피한 경우에만 실제 좌표를 사용하고 테스트·spec·summary에 함께 기록한다.
- 금색 별 장식 최고점은 `y=14`, 머리는 `y=15..28`, 머리 중심은 `[32,22]`다. 정면 얼굴, 턱선 길이 금발, 가리지 않는 앞머리, 큰 눈과 부드러운 미소를 유지한다.
- 목 또는 머리와 어깨 사이에 최소 1px 구분을 두고 어깨 기준선은 `y=29`다.
- 몸통은 `y=29..40`, 허리 기준선은 `y=39`, 튜닉 밑단은 `y=41..43`으로 세로 길이가 읽히게 한다.
- 양쪽 다리는 `y=42..54`, 무릎은 약 `y=47`이고 다리 사이에는 연속된 투명 공간이 있어야 한다. 양쪽 부츠는 서로 분리되며 양 발끝 모두 `y=54`에 도달한다.
- 날개와 공용 외곽선을 제외한 인간형 신체 색상은 `x=22..42` 안에 둬 신체 폭을 21px 이하로 유지한다.
- 보는 사람 기준 오른쪽/canvas 오른쪽 팔만 가슴 높이에서 팔꿈치를 살짝 굽혀 안내한다. 손과 팔을 긴 수평선으로 만들지 않는다. 반대 팔은 허리 주머니 가까이에 붙인다.
- 날개 한 쌍은 목표 영역 `[8,24,56,43]`에서 어깨 뒤에 연결하고 끝이 바깥 위쪽을 향하는 약 20~30도 사선이 되게 한다. 좌우 크기와 각도는 거의 대칭이고, 각 날개는 세로 두께가 있는 둥근 잎사귀형이다.
- 날개는 얼굴·머리·손·튜닉을 가리지 않고 몸과 시각적 투명 간격을 두되 뿌리에서 8-connected 단일 불투명 컴포넌트로 연결한다.
- 친절한 안내 대기 자세만 유지하고 공격·시전·걷기·돌진·회전·배경·그림자·텍스트·말풍선·느낌표·입자를 추가하지 않는다.

팔레트는 기존 JSON의 정확한 16색만 사용한다. alpha는 `0/255`만 허용하고 투명 픽셀 RGB는 0으로 정리한다. 외부 4-neighbor 경계는 끊기지 않는 `#263B5A` 1px이며 2×2 외곽선 블록, 고립 픽셀과 불규칙한 노이즈를 만들지 않는다.

후보를 먼저 `candidate-spec.json`으로 validator에 통과시키고 `view_image`로 원본·후보 1배율과 8배율을 비교한다. 머리·목·몸통·허리·양다리·양부츠가 구분되고 날개와 팔이 한 수평선으로 보이지 않는 경우에만 후보를 canonical 경로로 복사한다.

최종 `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`은 schema version과 16색 팔레트를 유지하면서 다음을 실측값으로 동기화한다.

- `soleY`, `expectedOpaqueBoundingBox`, `opaqueHeight`, `scaleComparison.heightPercentOfReferenceApprox`.
- `anchors.headCenter`, `anchors.shoulderY`, `anchors.waistY`, `anchors.soleY`.
- `pose.stance`를 곧은 정면 안내 대기 자세로 수정하고 `pose.bothToeTipsReachY`를 갱신한다.
- `wings.placement`와 날개 영역 설명.
- `semanticRegions.starAccent`, `eyesAndSmile`, `wingColors`의 실제 inclusive 좌표와 색상 pixel count 범위.
- `groundContacts.leftFoot/rightFoot`의 실제 xRange와 y.
- `transparentRegions.legGap`, `leftWingBodyGap`, `rightWingBodyGap`의 실제 위치와 유효 최소 투명 pixel count.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py -k "not runtime_sprite" --basetemp build\pytest-57-fairy-canonical
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-57-fairy-art-regression
git diff --check
```

## 검증 절차

1. 비율 테스트를 먼저 추가하고 기존 자산에서 실패를 확인한다.
2. 원본과 후보를 별도 파일로 만든 뒤 후보만 편집한다.
3. validator와 AC를 실행한다.
4. 1배율과 8배율에서 정면 인간형 비율, 신체 구분, 날개 각도, 팔 자세, 외곽선과 고립 픽셀을 직접 확인한다.
5. 최종 PNG의 불투명 픽셀을 다시 측정해 spec과 테스트가 실측값을 사용하는지 확인한다.
6. AGENTS.md의 CRITICAL 규칙과 외부 Calendar·Room·보상 범위 불변을 확인한다.
7. phase index step 0을 `completed`로 바꾸고 최종 bounds·높이·비율·후보 비교 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 PNG를 세로로 늘리거나 비균등 리사이즈하지 마라. 이유: 납작한 실루엣과 신체 구조를 픽셀 단위로 다시 그려야 한다.
- 고해상도 이미지를 생성해 64px로 축소하지 마라. 이유: 사용자가 logical 64×64 직접 교정을 명시했다.
- built-in imagegen이나 CLI/API fallback을 최종 자산 원천으로 사용하지 마라. 이유: 정확한 64px 좌표·16색·binary alpha 계약과 충돌한다.
- 후보 검증 전에 canonical PNG를 덮어쓰지 마라. 이유: 원본과 수정본을 비교 가능하게 유지해야 한다.
- 17번째 색, 반투명 alpha, 안티앨리어싱, 블러, 그라데이션과 dithering을 만들지 마라. 이유: 픽셀아트 계약을 위반한다.
- Android runtime resource를 수정하지 마라. 이유: 이 step은 canonical 아트와 spec만 담당한다.
- 기존 테스트를 깨뜨리지 마라.

