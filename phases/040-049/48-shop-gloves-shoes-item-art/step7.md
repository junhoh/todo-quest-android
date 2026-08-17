# Step 7: verify-gloves-shoes-ui-and-docs

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
- `/docs/art/equipment/todo-quest-gloves-shoes-layers-spec.json`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/040-049/48-shop-gloves-shoes-item-art/index.json`

## 작업

Compose instrumentation 시나리오를 먼저 완성하고 필요한 fixture만 보정한 뒤 구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

instrumentation은 다음을 검증한다.

- Shop의 장갑 filter에 가죽 장갑·강철 건틀릿, 신발 filter에 여행자의 장화·바람걸음 장화가 정확한 이미지·이름·가격·등급으로 표시된다.
- 각 상세 sheet, 구매 성공, `바로 장착`, 장착 slot과 Inventory가 같은 runtime PNG를 사용한다.
- 각 slot의 두 상품을 차례로 구매·장착하면 해당 장갑 또는 신발만 교체되고 modifier와 비교값이 갱신된다.
- 앱 재시작 후 gameplay 장착과 nullable gloves appearance fallback이 동일하게 복원된다.
- 두 장갑 × weapon on/off에서 손·검 손잡이·검 front pixel이 깨지지 않는다.
- 두 신발 × 5개 하의에서 발목 겹침, 발바닥 `y=58`, 투명 틈과 이중 seam이 없다.
- 네 장비의 2×2 혼합 착용에서 얼굴·머리·상의·하의·투구·액세서리·무기 픽셀이 보존된다.
- Character 화면과 Calendar Battle Map의 shared renderer가 확정된 projection을 사용한다.
- `320dp×640dp`, font scale `2.0`에서도 이름·가격·구매·장착 action이 접근 가능하다.
- unknown/decode 실패는 placeholder로 격리되고 일정·보상과 구매·장착 transaction을 실패시키지 않는다.

문서는 다음 사실만 실제 결과에 맞춰 반영한다.

- PRD/ADR: seeded catalog는 16종이며 기존 8종과 장갑·신발 4종을 합친 12종이 visual/layer mapping을 가지고 무기·액세서리 4종은 placeholder다.
- ARCHITECTURE: Room v11 nullable `glovesId`, `hands_front` 대체, gameplay projection과 16종 seeder를 기록한다.
- UI_GUIDE: 네 소재·희귀 선택 장비, grip·발목 seam, 상점 네 표시 위치와 한국어 접근성을 기록한다.
- equipment art README: 새 spec, canonical/runtime 경로, preview matrix, validator 명령과 실제 metadata를 기록한다.
- DEVELOPMENT: 실제 실행한 Python/JVM/lint/APK/connected 결과만 기록한다.

Android 도구를 임의 설치하지 않는다. 연결 가능한 기기나 기존 emulator가 없으면 connected test를 성공으로 기록하지 말고 step을 `blocked`로 갱신한다. 모든 검증 성공 시 step 7과 phase를 `completed`로 갱신하고 한국어 summary를 작성한 뒤 manager `Sync`로 상위 index와 README를 동기화한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py scripts\test_execute.py scripts\test_phase_manager.py --basetemp build\pytest-48-gloves-shoes-final
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest,com.todoquest.feature.shop.InventoryScreenTest,com.todoquest.feature.character.CharacterScreenTest,com.todoquest.feature.battle.BattleMapTest
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. 실제 artwork·구매·장착·교체·복원·혼합·fallback instrumentation을 먼저 완성하고 예상 실패를 확인한다.
2. 모든 AC를 실행하고 canonical/runtime hash, 장비별 preview와 2×2 matrix를 직접 확인한다.
3. 장갑 38픽셀 mask와 grip 순서, 신발 bounds `[23,53,41,58]`, 발목 interface, 이진 alpha와 nearest-neighbor를 확인한다.
4. 가격·modifier·소유권, occurrence 보상과 권한 독립 규칙이 변경되지 않았는지 확인한다.
5. 성공 시 step/phase 상태와 summary를 갱신하고 Sync한다. 필수 기기 부재면 `blocked`, 세 번 수정 후에도 실패하면 `error`와 구체적 사유를 기록한다.

## 금지사항

- 연결 테스트를 실행하지 못한 상태를 통과로 기록하지 마라. 이유: 실제 상점 표시와 착용 결과가 사용자 성공 기준이다.
- 무기·액세서리에 임의 이미지나 layer mapping을 추가하지 마라. 이유: 승인 범위는 장갑·신발 네 상품이다.
- 실제 결과와 다른 hash·테스트 수·완료 상태를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: child step AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
