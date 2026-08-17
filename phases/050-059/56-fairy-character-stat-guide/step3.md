# Step 3: define-character-stat-guide-domain

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/app/src/main/java/com/todoquest/domain/repository/CharacterRepository.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/CharacterCommandUseCases.kt`
- `/app/src/test/java/com/todoquest/domain/CharacterProgressionPolicyTest.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step2.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

능력치 가이드의 Android 비의존 domain 계약을 테스트 먼저 추가한다.

- `/app/src/main/java/com/todoquest/domain/model/CharacterGuide.kt`에 immutable `CharacterStatGuideStatus(automaticDisplayEligible: Boolean, acknowledged: Boolean)`를 정의한다.
- `/app/src/main/java/com/todoquest/domain/repository/CharacterGuideRepository.kt`에 다음 인터페이스를 정의한다.

```kotlin
interface CharacterGuideRepository {
    fun statAllocationGuideStatus(): CharacterStatGuideStatus
    fun acknowledgeStatAllocationGuide(): Boolean
}
```

- `/app/src/main/java/com/todoquest/domain/usecase/CharacterGuideUseCases.kt`에 아래 UseCase를 정의한다.

```kotlin
class PrepareCharacterStatGuideUseCase(repository: CharacterGuideRepository) {
    operator fun invoke(): Boolean
}

class AcknowledgeCharacterStatGuideUseCase(repository: CharacterGuideRepository) {
    operator fun invoke(): Boolean
}
```

`Prepare`는 `automaticDisplayEligible && !acknowledged`만 true다. `Acknowledge`는 repository 결과를 전달하며 중복 호출도 안전한 repository 계약을 전제로 한다. `/app/src/test/java/com/todoquest/domain/CharacterGuideUseCasesTest.kt`에서 네 status 조합, 최초 확인과 중복 확인을 검증한다.

이 domain은 포인트 지급량, 능력치 계산, 투자 상한, 저장·초기화 transaction을 변경하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterGuideUseCasesTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.domain.CharacterProgressionPolicyTest" --console=plain
git diff --check
```

## 검증 절차

1. domain 테스트를 먼저 작성해 타입 부재 실패를 확인한다.
2. 최소 구현 후 AC를 실행한다.
3. Android import와 표시 문자열이 domain에 없는지 확인한다.
4. phase index step 3을 완료 처리하고 타입·UseCase 계약을 한국어로 요약한다.

## 금지사항

- Android Context나 SharedPreferences를 domain에 import하지 마라. 이유: domain 계약은 플랫폼 비의존이어야 한다.
- 기존 CharacterRepository에 가이드 상태를 끼워 넣지 마라. 이유: 캐릭터 원천 상태와 안내 preference를 분리한다.
- 능력치 계산과 transaction을 수정하지 마라. 이유: 가이드는 presentation 보조 기능이다.
- 기존 테스트를 깨뜨리지 마라.

