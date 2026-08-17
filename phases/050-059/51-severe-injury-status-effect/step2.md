# Step 2: integrate-status-effect-repositories

## 읽어야 할 파일

- /AGENTS.md
- /docs/ARCHITECTURE.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step0.md
- /phases/050-059/51-severe-injury-status-effect/step1.md
- /app/src/main/java/com/todoquest/domain/repository/CharacterRepository.kt
- /app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt
- /app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt
- /app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt
- /app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt
- /app/src/main/java/com/todoquest/app/TodoQuestApplication.kt
- /app/src/test/java/com/todoquest/data/repository/RoomCharacterRepositoryTest.kt
- /app/src/test/java/com/todoquest/data/repository/RoomEquipmentRepositoryTest.kt
- /app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt

## 작업

테스트를 먼저 작성하고 모든 유효 스탯 조회와 할 일 완료 트랜잭션에 영구 상태이상을 연결한다.

1. 기존 Repository/UseCase 구조를 확장해 상태이상 관찰, AppClock.now() 기준 만료 reconciliation, 상태 제거를 제공한다. 별도의 UI 전용 저장소나 중복 상태 모델을 만들지 않는다.
2. CharacterSnapshot, 전투 snapshot 및 장비 변경 뒤 스탯 재계산 경로가 동일한 활성 status modifier 목록을 DerivedStatsCalculator에 전달하도록 한다. 중상 해제 시 유효 최대 체력과 공격력만 복원하고 현재 HP는 임의로 완전 회복하지 않는다. 다만 기존 invariant에 맞게 상한 clamp가 꼭 필요하면 그 이유와 테스트를 남긴다.
3. RoomTaskRepository의 실제 미완료 → 완료 저장 트랜잭션에서만 recovery credit을 처리한다. 먼저 현재 시각 만료를 reconcile하고, 실제 CompletionLog insert/상태 전환이 성공한 뒤 활성 중상의 (characterId, type, revision, taskId, occurrenceDate) credit insert가 성공할 때만 남은 횟수를 1 감소시킨다.
4. 남은 횟수가 0이 되는 세 번째 정상 완료에서는 중상을 먼저 비활성화하고 StatusEffectRemoved를 기록한 다음 복원된 공격력으로 해당 occurrence의 player attack outbox를 생성한다.
5. 이미 완료된 occurrence, 중복 탭, 같은 occurrence의 완료 취소 후 재완료, 저장 실패, transaction rollback, Preview/fake 데이터는 credit을 추가하지 않는다. 명시적으로 커밋된 완료를 나중에 취소해도 이미 얻은 credit은 되돌리지 않으며 같은 revision에서는 재지급하지 않는다.
6. 앱 재실행, Character/Calendar 진입 또는 resume, 전투 reconciliation에서 호출할 수 있는 만료 경계를 마련한다. 정확한 조건은 now >= expiresAt이고 UI 타이머를 권위 데이터로 사용하지 않는다.
7. 중상 상태에서도 XP, 골드, completion reward, 일정 등록/완료/실패/캘린더 동작이 기존과 동일한지 회귀 테스트한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.data.repository.RoomStatusEffectRepositoryTest" --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest" --tests "com.todoquest.data.repository.RoomEquipmentRepositoryTest" --console=plain
git diff --check
~~~

## 검증 절차

1. 실패하는 Repository 테스트로 3회 정상 완료, duplicate occurrence, rollback, 24시간 경계, 앱 재생성 후 복원 시나리오를 먼저 고정한다.
2. 같은 반복 원본의 서로 다른 날짜 3개는 각각 세고 같은 날짜 재처리는 세지 않는지 확인한다.
3. 세 번째 완료의 attack outbox snapshot이 중상 해제 후 공격력을 쓰는지 확인한다.
4. 중상 적용 전후 base stats/장비 row와 XP/골드 보상이 동일한지 확인한다.
5. AC를 실행하고 phase index의 step 2를 completed와 한국어 summary로 갱신한다.

## 금지사항

- 완료 요청을 받았다는 이유만으로 횟수를 차감하지 마라. 이유: DB에 실제 신규 완료 전환이 커밋된 경우만 인정해야 한다.
- 반복 일정 원본 ID만 recovery key로 사용하지 마라. 이유: 날짜별 occurrence를 분리해야 한다.
- 완료 transaction 밖에서 credit과 outbox를 따로 커밋하지 마라. 이유: 부분 성공과 롤백 불일치가 발생한다.
- 중상 상태에서 XP, 골드, 할 일 보상을 20% 낮추지 마라.
- UI lifecycle만으로 만료를 처리하지 마라.
- 기존 일정 완료/실패 테스트를 삭제하거나 우회하지 마라.
