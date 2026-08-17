# Step 6: verify-outfit-ui-and-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/character/README.md`
- `/docs/art/equipment/README.md`
- `/docs/art/equipment/todo-quest-helmet-layers-spec.json`
- `/docs/art/equipment/todo-quest-top-bottom-layers-spec.json`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/040-049/47-shop-top-bottom-item-art/index.json`

## 작업

Compose instrumentation 시나리오를 먼저 완성하고 필요한 fixture만 보정한 뒤 구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

instrumentation은 다음을 검증한다.

- Shop의 상의 filter에서 천 상의·가죽 갑옷·철 흉갑, 하의 filter에서 천 바지·가죽 바지·강철 각반이 서로 다른 실제 artwork와 한국어 이미지 설명을 표시한다.
- 각 상세 sheet와 장착 slot은 같은 runtime PNG의 artwork를 사용하고 unknown key fixture는 기존 type placeholder로 fallback한다.
- 상의 구매 후 `바로 장착`, 하의 구매 후 `바로 장착`, 같은 slot 교체가 Shop character sprite와 해당 slot만 갱신한다.
- 천·가죽·철/강철 matched set과 최소 두 개의 cross-set top/bottom 조합에서 얼굴·손·허리·발목·신발·투구·무기 픽셀이 깨지지 않는다.
- Inventory의 owned item은 실제 artwork와 장착/교체 상태를 표시한다.
- Character 화면과 Calendar Battle Map의 shared renderer가 확정된 top/bottom projection을 사용한다.
- `320dp×640dp`, font scale `2.0`에서도 이미지 때문에 이름·가격·장착 action이 접근 불가능해지지 않는다.
- decode 실패가 일정·보상과 구매·장착 transaction을 실패시키지 않는다.

문서는 다음 사실만 실제 결과에 맞춰 반영한다.

- PRD/ADR: seeded 14종 중 기존 투구 2종과 상·하의 6종이 visual/layer mapping을 가지며 나머지 6종은 placeholder/fallback을 유지한다.
- ARCHITECTURE: 기존 imageKey thumbnail 경로와 layerKey full-origin 합성 경로가 여덟 장비에 적용된다.
- UI_GUIDE: 재질·등급 및 세트 일관성, 허리·발목 seam, 상점 네 위치의 실제 이미지와 한국어 접근성을 기록한다.
- equipment art README: 새 spec, canonical/runtime 경로, preview matrix, validator 명령과 실제 metadata를 기록한다.
- DEVELOPMENT: 실제 실행한 Python/JVM/lint/APK/connected 결과만 기록한다.

Android 도구를 임의 설치하지 않는다. 연결 가능한 기기나 기존 emulator가 없으면 connected test를 성공으로 기록하지 말고 step을 `blocked`로 갱신한다. 모든 검증 성공 시 step 6과 phase를 `completed`로 갱신하고 한국어 summary를 작성한 뒤 manager `Sync`로 상위 index와 README를 동기화한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py scripts\test_execute.py scripts\test_phase_manager.py --basetemp build\pytest-47-outfit-final
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest,com.todoquest.feature.shop.InventoryScreenTest,com.todoquest.feature.character.CharacterScreenTest
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. 실제 artwork·구매·장착·교체·혼합·fallback instrumentation을 먼저 완성하고 구현 전 예상 실패를 확인한다.
2. 모든 AC를 실행하고 canonical/runtime hash, 장비별 1×/8× preview, 3×3 matrix와 앱 화면을 직접 확인한다.
3. top bounds `[20,29,44,45]`, bottom bounds `[24,41,40,54]`, 허리·발목 interface, 이진 alpha와 nearest-neighbor를 확인한다.
4. Room version, 가격·modifier·소유권, occurrence 보상과 권한 독립 규칙이 변경되지 않았는지 확인한다.
5. 성공 시 step/phase 상태와 summary를 갱신하고 Sync한다. 필수 기기 부재면 `blocked`, 세 번 수정 후에도 실패하면 `error`와 구체적 사유를 기록한다.

## 금지사항

- 연결 테스트를 실행하지 못한 상태를 통과로 기록하지 마라. 이유: 실제 상점 표시와 착용 결과가 사용자 성공 기준이다.
- 나머지 6개 seeded 장비에 임의 이미지나 layer mapping을 추가하지 마라. 이유: 승인 범위는 현재 상·하의 6종이다.
- 실제 결과와 다른 hash·테스트 수·완료 상태를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: child step AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
