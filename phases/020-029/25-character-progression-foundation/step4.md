# Step 4: 캐릭터와 완료 보상 transaction 통합

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/domain/repository/TaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CompleteOccurrenceUseCase.kt`
- `/app/src/test/java/com/todoquest/data/repository/RoomTaskRepositoryTest.kt`
- step 1~3에서 생성·수정한 도메인 모델, 정책, entity, DAO, migration 파일
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

repository와 transaction 통합 테스트를 먼저 작성해 실패를 확인한 다음 캐릭터 command와 occurrence 보상 흐름을 Room transaction으로 구현한다.

다음 인터페이스 수준의 계약을 추가한다.

```kotlin
interface CharacterRepository {
    fun observeCharacter(referenceDate: LocalDate): Flow<CharacterSnapshot>
    suspend fun allocateStatPoint(statType: StatType): AllocateStatPointResult
    suspend fun resetStats(): StatResetResult
}
```

- `CharacterSnapshot`은 `PlayerCharacter`, 계산 level/XP 진행, `CharacterCurrentState`, `DerivedStats`, current streak와 MOMENTUM bp를 포함한다.
- allocation 결과는 success/no-points/stat-cap, reset 결과는 success/nothing-to-reset/insufficient-gold를 명시적으로 표현한다.
- `RoomCharacterRepository`는 profile/current state/정시 ledger를 조합하되 UI 타입을 알지 않는다.
- `TaskRepository`에서 character 관찰 책임을 제거하고 이후 UI는 `CharacterRepository`를 사용한다.

`RoomTaskRepository.completeOccurrence`는 하나의 transaction 안에서 아래 순서를 지킨다.

1. 실제 occurrence인지 검증하고 completion log를 멱등 insert한다.
2. reward ledger unique key가 있으면 profile·포인트를 바꾸지 않고 `alreadyRewarded`를 반환한다.
3. clock instant와 zone으로 정시 여부와 `rewardLocalDate`를 확정한다.
4. 같은 reward date의 전체 기존 ledger 수와 같은 recurrence series 수를 세어 새 `dailyOrdinal/repeatOrdinal`을 결정한다.
5. 현재 원천 상태에서 `goldGainBonusBp`를 계산하고 RewardPolicy로 실제 지급량을 확정한다. 장비가 없으므로 현재 기본값은 0bp다.
6. snapshot ledger를 insert하고 성공했을 때만 XP·gold와 capped level 차이만큼의 포인트를 갱신한다.
7. 레벨업으로 MAX_HP가 바뀌면 같은 transaction에서 current HP 비율을 보존한다.

첫 20개 보상은 `combatEligible=true`로 snapshot하지만 공격·보스 event는 생성하지 않는다. 완료 취소는 completion log만 제거하고 ledger, 성장, 연속일 source를 보존한다.

일정 생성은 insert 후 자기 id를 series id로 원자 설정한다. 반복 원본 미래 수정으로 task를 분할하면 새 task가 기존 series id를 상속하고 completion/reward 재할당 뒤에도 ledger snapshot의 series id는 유지한다.

포인트 배분과 초기화도 `RoomCharacterRepository` transaction에서 profile과 current HP를 함께 갱신한다. vitality 또는 reset으로 MAX_HP가 바뀌면 0 HP는 0, 그 외 HP 비율을 유지한다. active combat가 없으므로 성공 조건을 만족하면 언제든 초기화할 수 있다.

테스트는 동일 occurrence 중복/동시 재시도, 취소·재완료, 정시·늦은·조기 미래 완료, 두 효율 동시 적용, 반복 분할 계보, 다중 레벨업, allocation cap, 무료/유료 reset, gold 부족, HP 비율과 ledger 비소급성을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.data.repository.RoomTaskRepositoryTest" --tests "com.todoquest.data.repository.RoomCharacterRepositoryTest"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. repository 통합 테스트를 구현보다 먼저 추가하고 실패를 확인한다.
2. AC를 실행하고 profile 갱신이 ledger insert 성공 뒤에만 일어나는지 확인한다.
3. UI가 DAO를 호출하지 않고 transaction이 repository 안에 있는지 확인한다.
4. task index step 4를 `completed`로 변경하고 repository와 transaction 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- completion, ledger, profile 갱신을 서로 다른 transaction으로 나누지 마라. 이유: crash나 재시도에서 보상 중복·누락이 생긴다.
- task title이나 category로 반복 원본 동일성을 판정하지 마라. 이유: 정상적인 별도 TODO를 오탐한다.
- 완료 취소 때 XP·gold나 포인트를 회수하지 마라. 이유: 기존 제품 계약이 보상 비회수로 확정돼 있다.
- 공격·몬스터·WorkManager 코드를 추가하지 마라. 이유: 이번 phase 승인 범위 밖이다.
- 기존 테스트를 깨뜨리지 마라.

