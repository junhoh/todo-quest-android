# Step 2: TODO 보상과 자동 전투 연동 설계

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/app/src/main/java/com/todoquest/domain/model/TodoTask.kt`
- `/app/src/main/java/com/todoquest/domain/model/TaskOccurrence.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/RewardPolicy.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/phases/020-029/23-character-stats-design/index.json`

## 작업

`/docs/game-design/character-stats-design.md`에 TODO 완료·실패 자동 전투, 정시·연속 보상과 악용 방지를 후속 구현 계약으로 추가한다.

### occurrence 결과와 전투

- 실제 완료 occurrence만 플레이어 일반 공격 1회, XP·골드와 보스 진행도 1을 만든다.
- 현재 난이도 보상 쉬움 `10/5`, 보통 `20/10`, 어려움 `35/20` XP/골드를 유지하고 사용자 선택 난이도로 공격 피해는 증폭하지 않는다.
- 완료, 경제 보상, 플레이어 공격, 실패 몬스터 공격은 각각 `(taskId, occurrenceDate)` 기반 ledger/event로 한 번만 처리한다.
- 완료 취소·재완료는 지급 보상을 회수·재지급하지 않고 전투 event도 다시 만들지 않는다.
- 시간 일정은 예정 시각+15분, 무시간 일정은 해당 로컬 날짜 종료 전 완료가 정시다.
- 정시 완료는 XP·골드 +10%이고 늦은 완료는 기본 보상을 삭감하지 않는다. 실패 공격 뒤 늦게 완료해도 플레이어 공격과 기본 보상은 한 번 허용한다.
- 미완료 occurrence는 마감 뒤 몬스터 공격 1회를 기록한다. 앱 시작 또는 WorkManager best-effort reconciliation으로 처리하고 알림·exact alarm 권한에 의존하지 않는다.
- 장기 미사용 복귀 시 실제 피해 공격은 reconciliation당 최대 3회다. 나머지는 처리 완료로 기록하되 XP·골드를 빼거나 일정 기능을 잠그지 않는다.
- 패배의 추가 XP·재화 손실은 범위 밖이며 기본값은 손실 없음이다.

### 보상·연속 기록

`Long` 중간값으로 아래 값을 계산하고 마지막에 내림한다. 양수 기본 보상이 효율 감소로 0이 되면 최소 1이다.

```text
xpAward = floor(baseXp × onTimeMultiplierBp × rewardEfficiencyBp / 10,000²)
goldAward = floor(baseGold × onTimeMultiplierBp × rewardEfficiencyBp
    × (10,000 + GOLD_GAIN_BONUS_BP) / 10,000³)
```

- 정시는 11,000bp, 그 외 10,000bp다.
- 같은 반복 원본을 같은 로컬 보상 일자에 몰아서 완료하면 1~3번째 10,000bp, 4~6번째 5,000bp, 7번째 이후 2,000bp다.
- 전체 일일 완료는 1~20번째 10,000bp, 21~30번째 5,000bp, 이후 2,000bp다.
- 두 효율은 곱하지 않고 더 낮은 하나만 사용하며 골드 보너스는 효율 뒤에 적용한다.
- 보스 진행도와 실제 전투 공격은 하루 최대 20개의 보상 대상 occurrence까지만 만든다. 이후 TODO와 감소된 XP·골드는 정상 처리한다.

연속 완료일은 occurrence 날짜에 정시 완료가 하나 이상 있을 때 하루 한 번만 증가한다. 과거 일정의 늦은 완료는 소급 복구하지 않는다. 3/7/14일 이상에서 공격 +3/+5/+8% `MOMENTUM` 중 가장 높은 한 단계만 활성화하고 기간만 갱신한다. 연속 중단은 기존 보상을 회수하거나 디버프를 주지 않는다.

카테고리는 영구 스탯을 직접 올리지 않는다. 운동·독서 반복이 체력·집중 무한 성장으로 이어지면 생활 패턴 왜곡과 의미 없는 일정 생성이 생기므로 영구 성장은 공통 XP의 레벨 포인트만 사용한다. 제목·카테고리가 같은 별도 TODO는 강제로 중복 판정하지 않아 정상 루틴을 오탐하지 않는다.

reward ledger에는 실제 지급량, 적용 효율, 정시 여부와 balance version을 기록하도록 제안해 앱 재시작·설정 변경 뒤 지급량이 바뀌지 않게 한다.

## Acceptance Criteria

```powershell
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\character-stats-design.md'
@('occurrence','10/5','20/10','35/20','15분','정시','MOMENTUM','1~3','4~6','7번째','1~20','21~30','최대 3회','멱등') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Missing TODO combat contract: $_" } }
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 동일 occurrence의 완료·보상·공격이 재실행되지 않는지 추적한다.
3. 두 효율이 곱해져 20% 아래로 내려가지 않는지 확인한다.
4. 권한 거부, 늦은 완료와 장기 복귀에도 일정 완료가 막히지 않는지 확인한다.
5. task index step 2를 `completed`로 변경하고 TODO·전투·악용 방지 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 카테고리로 영구 스탯을 올리지 마라. 이유: 특정 생활 반복이 최적 성장법이 되면 안 된다.
- 누락 일정 수만큼 제한 없이 복귀 피해를 주지 마라. 이유: 생산성 앱 복귀를 처벌하게 된다.
- 효율 감소율끼리 곱하지 마라. 이유: 20% 하한 아래로 내려간다.
- UI, Repository, Room schema를 수정하지 마라. 이유: 설계 문서 phase다.
- 기존 테스트를 깨뜨리지 마라.
