# Step 4: integrate-equipped-stat-sources

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterSnapshot.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterLoadout.kt`
- `/app/src/main/java/com/todoquest/ui/character/CharacterLayerCatalog.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

기존 Repository 회귀 테스트를 먼저 확장하고 모든 production 파생 능력치 계산 경로에 실제 장착 modifier를 연결한다. `derivedStatsFor`는 equipment modifier를 명시적으로 받도록 바꿔 새 호출부가 장비 source를 빼먹지 않게 한다. 장비를 소유만 한 경우 empty 장착 source와 동일하고, CHEST와 LEGS가 장착되면 두 modifier 집합이 모두 계산에 들어가며 같은 slot 교체 후 이전 modifier가 남지 않아야 한다.

`RoomCharacterRepository.observeCharacter()`는 장착 정의/modifier Flow를 결합해 Character 화면의 final stats와 current/max HP를 즉시 갱신한다. stat point 배분·초기화의 old/new 계산에는 동일한 장착 modifier를 포함한다. 기존 외형 `EquippedItems`는 fallback으로 읽고 장착 definition에 검증된 `characterLayerItemId`가 있을 때만 해당 head/top/bottom/shoes/accessory/weapon visual field에 투영한다. gloves나 layer가 없는 장비는 fallback 외형을 유지한다.

`RoomTaskRepository`는 완료 transaction과 missing player attack repair에서 현재 장착 modifier를 읽는다. 장착 accessory의 gold bonus는 새 reward에만 적용하고 기존 RewardLedger 확정값을 다시 계산하지 않는다. 레벨업 전후 MAX_HP 비율 보존과 새 pending player attack의 source attack/critical snapshot도 장착값을 포함한다.

`RoomCombatRepository.observeCombat()`와 모든 command transaction은 현재 장착 modifier를 읽는다. Battle Map의 player max HP, monster attack을 받을 때 defense, 새 player attack source, victory HP recovery가 같은 장착 계산을 사용한다. 이미 확정된 attack event의 source 값은 장비 교체 뒤에도 덮어쓰지 않는다. 장착으로 MAX_HP가 바뀔 때 current state는 step 3 transaction에서 이미 비율 보존된 값이어야 한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.DerivedStatsCalculatorTest" --tests "com.todoquest.domain.CombatUseCaseTest" --console=plain
git diff --check
```

## 검증 절차

1. 보유-only, CHEST, LEGS, 동시 장착, 교체, reward와 combat source 회귀를 테스트로 먼저 고정한다.
2. 모든 `derivedStatsFor` production 호출을 검색해 장착 source 누락이 없는지 확인한다.
3. task index의 step 4를 `completed`로 바꾸고 연결된 계산 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- final `DerivedStats`를 Room에 저장하지 마라. 이유: 원천 상태와 versioned calculator에서 매번 계산하는 기존 계약이다.
- 장비 교체 뒤 이미 확정된 RewardLedger나 attack event를 재작성하지 마라. 이유: 확정 event 비소급성 계약을 깨뜨린다.
- layer가 없는 장비 때문에 character bitmap을 숨기지 마라. 이유: 기존 외형 fallback을 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
