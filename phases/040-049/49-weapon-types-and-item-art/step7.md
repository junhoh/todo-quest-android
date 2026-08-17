# Step 7: verify-weapon-integration-and-docs

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
- `/docs/art/equipment/todo-quest-weapon-layers-spec.json`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/InventoryScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/040-049/49-weapon-types-and-item-art/index.json`

## 작업

Compose instrumentation 시나리오를 먼저 완성하고 필요한 fixture만 보정한 뒤 구현 결과를 canonical 문서와 동기화하고 phase 전체 acceptance를 실행한다.

instrumentation은 다음을 검증한다.

- Shop 무기 filter에 낡은 검·철 장검·물푸레나무 창·강철 철퇴가 정확한 이미지·이름·subtype·가격·등급으로 표시된다.
- 각 상세 sheet, 구매 성공, `바로 장착`, Shop weapon slot과 Inventory가 같은 runtime PNG를 사용한다.
- 네 상품을 구매·장착하면 단일 WEAPON slot에서 교체되고 modifier·비교값과 최종 공격력이 갱신된다.
- 앱 재시작 후 gameplay 장착, Room weaponType과 appearance projection이 동일하게 복원된다.
- 네 무기 전체가 손·상의·투구·머리·액세서리보다 최상단이고 얼굴 보호 영역을 침범하지 않는다.
- Character 화면, Shop 캐릭터 preview와 Calendar Battle Map이 같은 shared renderer 결과를 사용한다.
- `320dp×640dp`, font scale `2.0`에서도 이름·subtype·가격·구매·장착 action이 접근 가능하다.
- unknown/decode 실패는 placeholder로 격리되고 일정·보상·구매·장착 transaction을 실패시키지 않는다.

문서는 실제 결과에 맞춰 다음만 반영한다.

- ADR-016: `WeaponType`은 Equipment 속성으로 저장하고 gameplay weapon은 shared renderer 최상단 overlay로 합성한다.
- PRD/ADR: catalog 18종, visual/layer mapping 16종, accessory placeholder 2종, 신규 두 무기의 승인 수치를 기록한다.
- ARCHITECTURE: Room v12 nullable storage와 weapon row LONGSWORD backfill, schema-v5 topmost group, Repository projection을 기록한다.
- UI_GUIDE: 네 무기 silhouette, 같은 PNG의 thumbnail/full-origin 재사용, subtype 한국어 표시와 접근성을 기록한다.
- character/equipment art README: schema v5, 새 spec, canonical/runtime 경로, preview matrix, validator와 실제 metadata를 기록한다.
- DEVELOPMENT: 실제 실행한 Python/JVM/lint/APK/connected 결과만 기록한다.

Android 도구를 임의 설치하지 않는다. 연결 가능한 기기나 기존 emulator가 없으면 connected test를 성공으로 기록하지 말고 step을 `blocked`로 갱신한다. 모든 검증 성공 시 step 7과 phase를 `completed`로 갱신하고 한국어 summary를 작성한 뒤 manager `Sync`로 상위 index와 README를 동기화한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-weapon-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-helmet-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-top-bottom-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\validate_character_equipment_layers.py --spec docs\art\equipment\todo-quest-gloves-shoes-layers-spec.json --check
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_character_equipment_layers.py scripts\test_validate_character_sheet.py scripts\test_build_character_assets.py scripts\test_execute.py scripts\test_phase_manager.py --basetemp build\pytest-49-weapon-final
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

1. 실제 artwork·subtype·구매·장착·교체·복원·fallback instrumentation을 먼저 완성하고 예상 실패를 확인한다.
2. 모든 AC를 실행하고 canonical/runtime hash, 장비별 preview와 matrix를 직접 확인한다.
3. Room v12, 18종 catalog, 31개 runtime path, 7200개 loadout과 최상단 z-order를 확인한다.
4. occurrence 보상 멱등성, 권한 독립성, 외부 Calendar 제외와 UI→Repository 경계가 변경되지 않았는지 확인한다.
5. 성공 시 step/phase 상태와 summary를 갱신하고 Sync한다. 필수 기기 부재면 `blocked`, 세 번 수정 후에도 실패하면 `error`와 구체적 사유를 기록한다.

## 금지사항

- 연결 테스트를 실행하지 못한 상태를 통과로 기록하지 마라. 이유: 실제 상점 표시와 착용 결과가 사용자 성공 기준이다.
- 액세서리 이미지나 새 단검 상품을 추가하지 마라. 이유: 승인 범위는 무기 네 상품과 확장 가능한 DAGGER enum이다.
- 실제 결과와 다른 hash·테스트 수·완료 상태를 문서에 추정해 쓰지 마라. 이유: canonical 문서는 실행 증거와 일치해야 한다.
- phase 완료 뒤 부모 Stop을 두 번째 전체 acceptance gate로 사용하지 마라. 이유: child step AC와 status가 harness 완료 판정이다.
- 기존 테스트를 깨뜨리지 마라.
