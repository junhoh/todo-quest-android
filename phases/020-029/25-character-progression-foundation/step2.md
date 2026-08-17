# Step 2: 성장 및 비전투 보상 정책 구현

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/core/AppClock.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/RewardPolicy.kt`
- `/app/src/main/java/com/todoquest/domain/model/CompletionResult.kt`
- step 1에서 생성한 캐릭터 도메인 모델, balance config와 계산기
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

순수 Kotlin unit test를 먼저 작성해 실패를 확인한 다음 성장, 정시, 보상 효율과 연속일 정책을 구현한다.

- `CharacterProgressionPolicy`는 `level = min(50, 1 + floor(totalXp / 100))`, 레벨당 2포인트, 한 보상에서의 다중 레벨업, 개별 투자 상한 60을 처리한다.
- 포인트 배분은 `sum(baseStats - 5) + unspentStatPoints == 2 × (level - 1)` 불변식을 유지한다.
- `StatResetPolicy`는 투자분만 반환한다. 첫 성공은 무료이고 이후 비용은 `min(2_000, 100 + 20 × level)` gold다. 투자분이 없으면 무료 기회나 gold를 소비하지 않는다.
- `RewardPolicy` 입력은 난이도, 정시 여부, 반복 원본 순번, 일일 순번과 `goldGainBonusBp`다. 출력 XP/gold는 `Long`이며 문서 식을 마지막에 한 번 내리고 양수 기본 보상은 최소 1이다.
- 반복 순번 `1~3/4~6/7+`와 일일 순번 `1~20/21~30/31+` 효율 중 낮은 하나만 적용한다.
- `OnTimePolicy`는 `ZoneId`를 명시적으로 받는다. 시간이 있으면 occurrence 예정 instant +15분 이하, 시간이 없으면 occurrence 다음 날 시작 instant 미만을 정시로 본다. 하한을 두지 않아 미래 occurrence 조기 완료도 정시다.
- `rewardLocalDate`는 완료 instant의 로컬 날짜다. 조기 완료도 해당 실제 완료 날짜의 일일·반복 순번을 소비한다.
- `StreakPolicy`는 정시 reward ledger의 distinct occurrence 날짜로 계산한다. 미래 날짜 기록은 그 날짜가 될 때까지 현재 streak에 포함하지 않는다.
- 기준 날짜 이하의 최신 정시 날짜가 오늘 또는 어제일 때 연속 날짜를 역순으로 계산한다. MOMENTUM은 3/7/14일에 `300/500/800bp`이며 최신 정시 날짜의 다음 로컬 날짜 종료까지 활성이다.
- `AppClock`은 `ZoneId`를 노출하도록 확장하고 모든 fake clock을 컴파일 가능하게 갱신한다.
- `CompletionResult`는 실제 지급 `Long` XP/gold, `alreadyRewarded`, 정시 여부와 적용 효율을 표현한다.

테스트 클래스는 `CharacterProgressionPolicyTest`, `RewardPolicyTest`, `OnTimePolicyTest`, `StreakPolicyTest`로 분리한다.

## Acceptance Criteria

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.todoquest.domain.CharacterProgressionPolicyTest" --tests "com.todoquest.domain.RewardPolicyTest" --tests "com.todoquest.domain.OnTimePolicyTest" --tests "com.todoquest.domain.StreakPolicyTest"
.\gradlew.bat testDebugUnitTest
git diff --check
```

## 검증 절차

1. 테스트를 구현보다 먼저 추가하고 대상 테스트가 실패하는지 확인한다.
2. 조기 미래 완료의 보상일과 연속일 귀속이 서로 다른지 확인한다.
3. 두 효율을 곱하지 않고 최소 2,000bp를 유지하는지 확인한다.
4. 레벨 50 이후 XP 보존과 포인트 미증가를 확인한다.
5. task index step 2를 `completed`로 변경하고 성장·보상 정책을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 카테고리에 영구 스탯 성장을 연결하지 마라. 이유: 특정 생활 패턴을 강제하고 악용을 만든다.
- 효율 감소율끼리 곱하지 마라. 이유: 설계된 20% 하한 아래로 내려간다.
- 미래 완료를 정시에서 제외하지 마라. 이유: 사용자 승인으로 조기 완료도 정시로 확정했다.
- 공격 event나 실패 피해를 생성하지 마라. 이유: 이번 phase는 비전투 보상까지만 구현한다.
- 기존 테스트를 깨뜨리지 마라.
