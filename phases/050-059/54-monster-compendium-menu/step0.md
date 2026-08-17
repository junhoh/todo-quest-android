# Step 0: define-monster-discovery-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterStagePolicy.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/test/java/com/todoquest/domain/MonsterSpeciesPolicyTest.kt`
- `/phases/050-059/54-monster-compendium-menu/index.json`

## 작업

몬스터 조우 이력을 종족 발견 상태로 변환하는 순수 Kotlin 테스트를 먼저 작성해 실패를 확인한 뒤 도메인 계약을 구현한다.

`app/src/main/java/com/todoquest/domain/usecase/MonsterDiscoveryPolicy.kt`에 다음 인터페이스를 제공한다.

```kotlin
object MonsterDiscoveryPolicy {
    fun discoveredSpecies(
        instances: Iterable<MonsterInstance>,
        config: MonsterBalanceConfig,
    ): Set<MonsterSpecies>
}
```

각 `MonsterInstance`의 `stageNumber`, `encounterNumber`, `grade`, `balanceVersion`과 기존 `MonsterStagePolicy.encounterCount`, `MonsterSpeciesPolicy.speciesFor`를 사용해 종족을 계산한다. 입력 instance의 `balanceVersion`이 전달된 config version과 다르면 조용히 잘못 분류하지 말고 명확히 실패시킨다. 같은 종족을 여러 번 조우해도 결과에는 한 번만 포함한다. HP, 현재 활성 여부, 처치 여부는 발견 판정에 사용하지 않는다.

`app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`에 다음 관찰 계약을 추가한다.

```kotlin
fun observeDiscoveredMonsterSpecies(): Flow<Set<MonsterSpecies>>
```

기존 테스트 대역과 단계별 컴파일 호환을 위해 interface 기본 구현은 `emptyFlow()`를 반환할 수 있다. production `RoomCombatRepository`가 반드시 override하도록 다음 step에서 구현한다.

테스트는 빈 이력, 같은 종족 중복, 서로 다른 Stage/encounter/grade의 5종 결정적 mapping, HP가 0인 과거 instance 포함, balance version 불일치를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.MonsterDiscoveryPolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. 새 도메인 테스트를 먼저 추가하고 구현 전 실패를 확인한다.
2. 최소 구현 후 AC 명령을 실행한다.
3. `MonsterSpeciesPolicy`의 명시적 종족 스케줄과 AGENTS.md의 데이터 무결성 규칙을 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 생성 파일과 공개 Repository 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 종족 판정을 enum ordinal로 구현하지 마라. 이유: 기존 결정적 종족 스케줄 및 향후 enum 확장과 결합되면 안 된다.
- 발견 여부를 `currentHp == 0` 또는 처치 event에 연결하지 마라. 이유: 승인된 발견 시점은 활성 등장 즉시다.
- Android, Room 또는 Compose 타입을 도메인 정책에 추가하지 마라. 이유: 순수 Kotlin unit test 경계를 유지해야 한다.
- 기존 테스트를 깨뜨리지 마라.
