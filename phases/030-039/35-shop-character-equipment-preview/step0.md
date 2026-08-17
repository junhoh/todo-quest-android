# Step 0: extend-equipment-store-preview-snapshot

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterSnapshot.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt`
- `/phases/030-039/35-shop-character-equipment-preview/index.json`

## 작업

Repository 테스트를 먼저 추가하고 상점 관찰 snapshot을 캐릭터 프리뷰의 단일 도메인 원천으로 확장한다. `EquipmentStoreSnapshot`에 다음 필드를 추가한다.

```kotlin
val appearance: CharacterAppearance
val renderedEquippedItems: EquippedItems
val derivedStats: DerivedStats
```

`RoomEquipmentRepository.observeStore(characterId)`는 기존 profile·catalog·modifier·owned·equipped source에 appearance와 기존 `character_equipped_items` fallback을 결합한다. 파생 능력치는 새 공식을 만들지 말고 기존 `derivedStatsFor(character, balanceConfig, equippedEquipment.equipmentModifiers())`를 호출한다. 캐릭터 외형은 기존 fallback 위에 `projectEquippedCharacterLayers(fallback, equippedEquipment)`를 적용한다. gameplay 장비에 유효한 `layerKey`가 있을 때만 렌더 loadout에 투영하고, null·알 수 없는 layer는 기존 appearance fallback을 그대로 유지한다.

하나의 store snapshot이 캐릭터 레벨·골드·소유 장비·slot별 실제 장착·최종 스탯·렌더 loadout을 함께 제공하게 한다. 계산된 파생값을 Room에 저장하거나 schema version을 올리지 않는다. 구매와 장착 transaction 구현은 유지하며 Flow가 commit 뒤 새 aggregate를 방출하게 한다.

`RoomEquipmentRepositoryTest`에는 구매 후 골드와 소유 상태가 함께 갱신되는 경우, 장착 후 해당 slot·최종 능력치·렌더 loadout이 갱신되는 경우, 유효한 layer 투영과 layer 미제공 fallback을 추가한다. 기존 CHEST 교체 시 LEGS 유지 검증에 더해 LEGS 교체 시 CHEST 유지도 명시적으로 검증한다. 교체는 대상 `character_equipment` row 하나만 바꾸고 다른 여섯 slot과 소유 row를 보존해야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.EquipmentModifierValidatorTest" --console=plain
git diff --check
```

## 검증 절차

1. 새 snapshot 필드를 기대하는 Repository 테스트를 먼저 작성하고 실패를 확인한다.
2. AC 명령을 실행하고 기존 파생 스탯 계산·외형 fallback·CHEST/LEGS 독립 계약을 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 생성·수정 파일 및 결정 사항을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 파생 능력치 공식을 `RoomEquipmentRepository`에 복사하지 마라. 이유: Character·Task·Combat과 계산 결과가 어긋난다.
- `character_equipped_items`를 gameplay 소유권으로 변환하지 마라. 이유: 기존 외형 fallback과 Room v7 장착 source는 별도 계약이다.
- Room schema나 migration을 변경하지 마라. 이유: 이번 기능은 기존 source의 읽기 projection 확장이다.
- 기존 테스트를 깨뜨리지 마라.
