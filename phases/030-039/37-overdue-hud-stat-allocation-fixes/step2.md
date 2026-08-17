# Step 2: define-batch-stat-allocation-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterStats.kt`
- `/app/src/main/java/com/todoquest/domain/model/CharacterSnapshot.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterCommandUseCases.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterProgressionPolicy.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterProgressionPolicyTest.kt`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

순수 Kotlin 테스트를 먼저 작성한 뒤 단건 즉시 배분 계약을 typed 일괄 배분 계약으로 교체한다.

다음 시그니처 수준의 타입과 API를 추가한다.

```kotlin
data class StatAllocation(
    val strength: Int = 0,
    val vitality: Int = 0,
    val focus: Int = 0,
    val willpower: Int = 0,
)

sealed interface AllocateStatPointsResult {
    data class Success(val allocation: StatAllocation) : AllocateStatPointsResult
    data object NoChanges : AllocateStatPointsResult
    data class InsufficientPoints(val requested: Int, val available: Int) : AllocateStatPointsResult
    data class StatCap(val type: StatType) : AllocateStatPointsResult
}

interface CharacterRepository {
    suspend fun allocateStatPoints(allocation: StatAllocation): AllocateStatPointsResult
}

class AllocateStatPointsUseCase {
    suspend operator fun invoke(allocation: StatAllocation): AllocateStatPointsResult
}
```

`StatAllocation`은 모든 값이 0 이상이어야 하고 overflow 없이 총 요청 포인트와 `StatType`별 값을 제공한다. 배분 정책은 `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`의 안정적인 순서로 모든 요청을 먼저 검증한다. 총 요청이 0이면 `NoChanges`, 가용 포인트보다 크면 `InsufficientPoints`, 예상 stat이 투자 상한 60을 넘으면 첫 위반 `StatCap`을 반환하며 어떤 경우에도 부분 변경 객체를 만들지 않는다. 성공 시 `sum(baseStats - 5) + unspentStatPoints == 2 × (level - 1)` 불변식을 유지한다.

기존 `AllocateStatPointResult`, `CharacterRepository.allocateStatPoint(StatType)`, `AllocateStatPointUseCase`를 제거하거나 새 일괄 계약으로 이름과 호출부가 완전히 교체될 수 있게 정리한다. Android·Room·UI 타입을 도메인 API에 넣지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.StatAllocationPolicyTest" --tests "com.todoquest.domain.CharacterProgressionPolicyTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.DerivedStatsCalculatorTest" --console=plain
git diff --check
```

## 검증 절차

1. 0 요청, 음수 거부, 다중 stat 성공, 포인트 부족, stat cap, overflow와 포인트 보존 테스트를 먼저 작성하고 실패를 확인한다.
2. AC를 실행하고 domain 패키지에 Android나 Room import가 없는지 확인한다.
3. 기존 레벨업·초기화·파생 스탯 공식이 바뀌지 않았는지 검토한다.
4. task index의 step 2를 `completed`로 바꾸고 새 API와 검증 우선순위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `Map<StatType, Int>`를 public command로 사용하지 마라. 이유: 네 stat의 명시적 typed 계약과 안정적인 검증 순서를 유지해야 한다.
- 배분 정책에서 Room 상태를 읽지 마라. 이유: 도메인 검증은 순수 Kotlin이고 최신 상태 검증은 Repository transaction의 책임이다.
- 개별 stat을 순차 저장하는 API를 남기지 마라. 이유: 저장 전 draft와 원자 일괄 확정 요구를 깨뜨린다.
- 기존 테스트를 깨뜨리지 마라.
