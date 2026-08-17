# Step 6: verify-helmet-ui-and-docs

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
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/InventoryScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/040-049/46-shop-helmet-item-art/index.json`

## 작업

Compose instrumentation 시나리오를 먼저 완성하고 필요한 test fixture만 보정한 뒤 구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

instrumentation은 다음을 검증한다.

- Shop의 투구 filter에서 가죽 모자와 철 투구 card가 서로 다른 실제 artwork tag와 한국어 이미지 설명을 표시한다.
- 각 상세 sheet가 같은 실제 artwork를 표시하고 unknown key fixture는 기존 투구 placeholder로 fallback한다.
- 가죽 모자 구매 후 `바로 장착`하면 Shop character sprite와 HELMET slot이 같은 item으로 갱신되고 다른 slot은 유지된다.
- 철 투구로 교체하면 가죽 모자 픽셀이 남지 않고 눈·코·입이 보이는 open-face 합성 결과를 사용한다.
- Inventory의 두 owned item이 실제 artwork를 표시하고 장착/교체 상태가 기존 transaction 결과와 일치한다.
- `320dp×640dp`, font scale `2.0`에서도 card 이미지 때문에 장비명·가격·장착 action이 잘리거나 접근 불가능해지지 않는다.
- 이미지 decode 실패가 일정·보상과 구매/장착 state를 실패시키지 않는다.

문서는 다음 사실만 반영한다.

- PRD/ADR: seeded 14종 중 두 HELMET에 canonical/runtime bitmap과 valid layer mapping이 생겼고 나머지는 placeholder/fallback을 유지한다.
- ARCHITECTURE: `equipment.imageKey → EquipmentArtworkCatalog → same runtime PNG thumbnail`, `equipment.layerKey → Repository projection → CharacterLayerCatalog → full-canvas composition` 흐름과 실패 격리.
- UI_GUIDE: 상점 목록·상세·slot·Inventory의 실제 이미지, 한국어 접근성, bounds-fit thumbnail과 full-origin 착용 차이.
- equipment art README: 두 canonical PNG/spec, runtime byte equality, preview와 validator 명령.
- DEVELOPMENT: 실제로 실행한 Python/JVM/lint/APK/connected 결과와 hash만 기록한다.

Android 도구를 임의 설치하지 않는다. 연결 가능한 기기나 기존 emulator가 없으면 instrumentation 명령을 우회하거나 성공으로 기록하지 말고 step을 `blocked`로 갱신한다.

모든 검증 성공 시 step 6과 phase를 `completed`로 갱신하고 한국어 phase summary를 작성한 뒤 manager `Sync`로 상위 index와 README를 동기화한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py scripts\test_execute.py scripts\test_phase_manager.py --basetemp build\pytest-46-helmet-final
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

1. 실제 artwork·구매·장착·교체·fallback instrumentation을 먼저 완성하고 구현 전/fixture 전 예상 실패를 확인한다.
2. 모든 AC를 실행하고 두 canonical/runtime hash, 1×/8× preview와 앱 화면을 직접 확인한다.
3. 가죽 모자 `[19,4,45,22]`, 철 투구 `[18,4,46,29]`, 얼굴 보호 `[23,20,41,28]`, 이진 alpha와 nearest-neighbor를 확인한다.
4. Room version, 가격·modifier·소유권, occurrence 보상과 알림 권한 독립 규칙이 변경되지 않았는지 확인한다.
5. 성공 시 step/phase 상태와 summary를 갱신하고 Sync한다. 필수 기기 부재면 `blocked`, 세 번 수정 후에도 실패하면 `error`와 구체적 사유를 기록한다.

## 금지사항

- 연결 테스트를 실행하지 못한 상태를 통과로 기록하지 마라. 이유: 실제 상점 표시와 착용 결과가 사용자 성공 기준이다.
- 나머지 12개 seeded 장비에 임의 이미지나 layer mapping을 추가하지 마라. 이유: 승인 범위는 현재 투구 두 종뿐이다.
- 실제 결과와 다른 hash·테스트 수·완료 상태를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: child step AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
