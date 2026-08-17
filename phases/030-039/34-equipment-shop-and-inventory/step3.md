# Step 3: implement-equipment-transactions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/main/java/com/todoquest/data/mapper/CharacterMapper.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/DerivedStatsCalculator.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CombatCalculator.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt`
- `/app/src/main/java/com/todoquest/data/local/EquipmentDao.kt`
- `/app/src/main/java/com/todoquest/domain/repository/EquipmentRepository.kt`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

`RoomEquipmentRepositoryTest`를 먼저 실패시키고 `RoomEquipmentRepository`를 구현한다. store Flow는 catalog, 현재 profile gold·level, 소유 장비, slot별 장착을 결합하고 seeder를 먼저 멱등 실행한다. inventory Flow는 owned definition과 현재 장착 slot을 관찰한다. mapper는 raw storage enum을 안전하게 해석하며 type/slot 불일치 장비를 구매 불가 상태로 노출하고 알 수 없는 storage 값은 앱을 종료시키지 않는 repository 오류로 격리한다.

구매 command는 `database.withTransaction` 안에서 catalog·profile·현재 level·소유 상태를 다시 읽고 step 1 구매 정책을 호출한다. 검증 성공 뒤 `currentGold = currentGold - price`를 음수 없이 저장하고 owned row를 삽입한다. 성공 결과에는 owned id, equipment id/key/type/slot과 최신 gold를 반환한다. unique 경합으로 두 요청이 동시에 들어와도 한 번만 차감·소유되어야 하며 처리되지 않은 예외는 transaction 전체를 rollback한다. 판매 중지, 중복, 레벨·골드 부족과 slot mismatch에서는 profile과 inventory를 변경하지 않는다.

장착 command는 같은 transaction에서 owned row가 대상 character 소유인지, definition type과 target slot이 일치하는지 검증한다. 현재 전체 장착 modifier로 old stats를 계산하고 `(characterId, targetSlot)` 행 하나만 upsert한 뒤 새 전체 modifier로 new stats를 계산한다. MAX_HP가 바뀌면 기존 `CombatCalculator.preserveHpRatio`로 current HP 비율을 유지하며 `0 HP`는 0을 보존한다. 기존 같은 slot 장비는 owned inventory에 남고 다른 slot 행은 변경하지 않는다. current state와 slot 교체 중 하나라도 실패하면 둘 다 rollback해야 한다.

필요하면 여러 Repository에 중복된 singleton character 초기화 코드를 data/repository 내부 helper로 추출하되 public domain API를 노출하지 않는다. 장비 계산 결과 `DerivedStats` 자체는 Room에 저장하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. 구매 성공/실패·동시 중복·정확한 차감, CHEST/LEGS 장착·교체·rollback 테스트를 먼저 작성한다.
2. transaction 안에서 UI 사전 계산이 아닌 최신 Room source로 재검증하는지 확인한다.
3. task index의 step 3을 `completed`로 바꾸고 transaction과 결과 타입을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 골드 차감과 owned insert를 별도 transaction으로 나누지 마라. 이유: 부분 구매 상태가 생긴다.
- `isEquipped` boolean을 owned source로 추가하지 마라. 이유: slot row와 장착 상태가 이중화된다.
- 장착 교체 시 다른 slot을 삭제하지 마라. 이유: CHEST와 LEGS를 포함한 모든 slot은 독립적이다.
- 기존 테스트를 깨뜨리지 마라.
