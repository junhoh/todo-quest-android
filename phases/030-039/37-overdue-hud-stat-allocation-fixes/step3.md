# Step 3: persist-batch-stat-allocation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterProfileDao.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterStats.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterCommandUseCases.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomCharacterRepositoryTest.kt`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

`RoomCharacterRepositoryTest`를 먼저 새 일괄 API로 확장해 현재 단건 즉시 저장 구현이 다중 stat을 원자적으로 확정하지 못하는 것을 실패로 확인한다. 이후 `RoomCharacterRepository.allocateStatPoints(allocation)`을 하나의 `database.withTransaction`으로 구현한다.

transaction 안에서 최신 profile과 current state, 실제 장착 equipment modifier를 읽는다. 총 요청 0, 최신 가용 포인트 부족, 각 예상 기본 stat의 투자 상한을 모든 write 전에 검증하고 step 2의 결과 타입으로 반환한다. 성공할 때만 네 기본 stat과 미배분 포인트를 한 번 저장한다.

변경 전·후 `DerivedStats`를 공식 calculator와 실제 장착 modifier로 각각 한 번 계산한다. `MAX_HP`가 변하면 기존 비율 보존 공식을 전체 allocation에 한 번만 적용한다. 현재 HP가 0이면 0을 유지하고, 그 외에는 기존 rounding·clamp 계약을 유지한다. profile 또는 current state write가 실패하면 전체 배분을 rollback한다. `NoChanges`, `InsufficientPoints`, `StatCap`은 Room row와 HP를 변경하지 않는다.

기존 단건 repository 테스트와 fake 구현을 일괄 API로 이전한다. reset transaction과 장비 modifier 계산, Character/Combat 관찰 계약은 변경하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --tests "com.todoquest.data.repository.RoomCombatRepositoryTest" --console=plain
git diff --check
```

## 검증 절차

1. 여러 stat의 단일 성공, 최신 포인트 부족, 하나의 cap 위반으로 전체 rollback, write 강제 실패, HP 비율과 0 HP 테스트를 먼저 작성한다.
2. AC를 실행하고 profile과 current state가 같은 transaction에서만 갱신되는지 확인한다.
3. 파생 스탯을 entity에 저장하지 않고 원천 상태에서 다시 계산하는 ADR을 확인한다.
4. task index의 step 3을 `completed`로 바꾸고 일괄 transaction과 HP 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- stat별로 별도 transaction이나 DAO write를 실행하지 마라. 이유: 중간 실패 시 부분 배분이 확정될 수 있다.
- 파생 능력치를 Room에 저장하지 마라. 이유: 장비와 versioned config 변경 뒤 snapshot 불일치가 생긴다.
- HP를 단순 delta로 더하지 마라. 이유: 전체 allocation 전후 MAX_HP 비율을 한 번 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
