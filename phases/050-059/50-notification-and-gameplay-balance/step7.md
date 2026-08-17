# Step 7: rebalance-equipment-catalog

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /app/src/main/java/com/todoquest/data/local/EquipmentDao.kt
- /app/src/main/java/com/todoquest/data/local/EquipmentEntities.kt
- /app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt
- /app/src/test/java/com/todoquest/data/local/EquipmentDaoTest.kt
- /app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt
- /app/src/test/java/com/todoquest/domain/PurchaseEquipmentPolicyTest.kt
- /phases/050-059/50-notification-and-gameplay-balance/step0.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

DAO/Repository test를 production보다 먼저 작성한다. EquipmentCatalogSeeder의 18종 fresh definition 가격을 step 0의 정확한 표로 변경한다. EquipmentPriceUpdate metadata와 조건부 DAO update를 추가한다. update WHERE 절은 id, nameKey, type, slot, expectedOldPrice를 모두 확인하고 새 가격만 갱신한다. 이미 새 가격, 임의 custom 가격, identity 불일치 row는 보존한다.

기존 seedCatalogIgnoreAndUpdateVisualMetadata transaction을 가격 update까지 포함하는 명확한 이름/계약으로 확장한다. fresh insert, modifier insert, visual metadata와 price update는 같은 transaction에서 멱등해야 한다. Repository의 observeStore/observeInventory/purchase 준비 경계가 seed를 호출하므로 기존 설치도 상점 조회나 구매 전에 canonical old price에서 새 가격으로 갱신돼야 한다.

구매한 장비에 대한 골드 환불이나 owned/character_equipment 변경은 하지 않는다. 구매 transaction은 최신 새 가격을 다시 읽어 정확히 차감한다. Room schema version과 schema JSON은 변경하지 않는다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.local.EquipmentDaoTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --tests "com.todoquest.domain.PurchaseEquipmentPolicyTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 18종 fresh 가격, old-to-new, custom 보존과 ownership 무변경 test를 먼저 추가한다.
2. conditional price update와 catalog 값을 구현한다.
3. 구매 최신 상태·동시 구매·rollback test를 포함해 AC를 실행한다.
4. step 7을 completed와 한국어 summary로 갱신한다.

## 금지사항

- OnConflictStrategy.REPLACE로 equipment definition을 덮어쓰지 마라. 이유: FK와 custom/legacy metadata를 손상시킬 수 있다.
- 예상 old price가 아닌 row를 변경하지 마라. 이유: 이미 변경됐거나 알 수 없는 catalog 상태를 보존해야 한다.
- 기존 구매자에게 차액을 환불하지 마라. 이유: 실제 구매 가격 history가 저장돼 있지 않아 정확한 환불이 불가능하다.
- Room migration이나 schema JSON을 변경하지 마라. 이유: data seed 정책 변경이며 schema 변경이 아니다.
- 기존 테스트를 깨뜨리지 마라.
