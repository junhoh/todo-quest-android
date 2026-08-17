# Step 0: 캐릭터 성장 범위 승인

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/README.md`
- `/docs/game-design/character-stats-design.md`
- `/phases/020-029/25-character-progression-foundation/index.json`

## 작업

테스트와 앱 코드를 수정하기 전에 `/docs/PRD.md`와 `/docs/ADR.md`에 `Post-MVP Character Growth v1` 범위를 승인된 후속 기능으로 기록한다.

- 기존 MVP의 일정 생성·수정·삭제·occurrence 완료와 보상 멱등성은 그대로 유지한다.
- 이번 phase의 제품 범위는 레벨 50 cap, 기본 스탯과 파생 스탯, 레벨 포인트, 스탯 초기화, 정시·효율 감소·연속일 비전투 보상, 별도 캐릭터 화면이다.
- 자동 전투, 실패 몬스터 공격, WorkManager reconciliation, 장비·아이템의 Room 영속/획득/장착 UI는 후속 범위다.
- 기존 XP·골드는 보존하고 현재 XP로 계산한 capped level까지 `2 × (level - 1)` 포인트를 소급 지급한다.
- 미래 occurrence 조기 완료는 정시 보상을 받으며 일일 효율은 실제 완료 로컬 날짜, 연속일은 occurrence 날짜에 귀속한다.
- MOMENTUM은 정시 완료 occurrence 날짜와 다음 로컬 날짜 종료까지 유지한다.
- 새 ADR은 원천 상태와 확정 ledger만 저장하고 level·파생값은 versioned config에서 계산한다는 결정을 포함한다.

## Acceptance Criteria

```powershell
$prd = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\PRD.md'
$adr = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\ADR.md'
@('Post-MVP Character Growth v1','레벨','스탯','정시','연속') | ForEach-Object { if (-not $prd.Contains($_)) { throw "Missing PRD scope: $_" } }
@('Character Growth','원천 상태','ledger','파생') | ForEach-Object { if (-not $adr.Contains($_)) { throw "Missing ADR decision: $_" } }
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. PRD의 MVP 제외 사항과 후속 구현 범위가 섞이지 않았는지 확인한다.
3. task index step 0을 `completed`로 변경하고 승인 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 자동 전투나 장비 기능을 이번 범위로 승인하지 마라. 이유: 몬스터 성장과 아이템 획득 정책이 아직 완결되지 않았다.
- 기존 MVP 제외 사항을 삭제하지 마라. 이유: 이번 기능은 MVP가 아니라 승인된 후속 확장이다.
- Android 코드를 수정하지 마라. 이유: 이 step은 제품 계약만 담당한다.
- 기존 테스트를 깨뜨리지 마라.

