# Step 0: 몬스터 전투 범위 승인

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

제품·아키텍처 문서에 `Post-MVP Monster Combat v1`을 승인된 후속 범위로 추가한다. 기존 MVP의 일정 관리, occurrence 완료, XP·골드 멱등 지급과 권한 독립성은 바꾸지 않는다.

승인 범위는 다음으로 제한한다.

- 몬스터의 `MAX_HP`, `DAMAGE`, `DEFENSE`, 레벨·유형·등급 성장과 Stage 진행.
- combat-eligible occurrence 최초 완료의 플레이어 공격 1회와 마감 실패 occurrence의 몬스터 공격 1회를 서로 독립된 멱등 event로 기록한다.
- Room에 몬스터 인스턴스, Stage 진행, 양방향 공격 event와 reconciliation cursor를 저장한다.
- 실패 공격은 앱 시작과 WorkManager best-effort reconciliation으로 처리하며 알림·exact alarm 권한에 의존하지 않는다.
- 치명 피해는 event로 남기고 최대 HP의 25%로 즉시 부활한다. 향후 사망 디버프가 이 event를 사용한다.
- backend와 기존 캐릭터 HP 상태까지만 구현하며 몬스터/전투 UI, 사망 디버프, 몬스터 스킬·치명타, 장비 전투 연결, 처치 추가 XP·골드·전리품은 제외한다.

`ADR-009`를 추가해 다음 결정을 고정한다.

- 계산 가능한 몬스터 최종 능력치는 저장하지 않고 versioned definition/config와 인스턴스 원천 상태에서 계산한다.
- 경제 보상과 전투 event는 독립 기록이며 전투 처리 실패가 일정 완료·보상 성공을 되돌리지 않도록 pending attack outbox를 사용한다.
- 완료 취소·재완료는 기존 공격을 삭제하거나 다시 만들지 않는다.
- 첫 combat 초기화 이전의 과거 누락 일정에는 피해를 소급 적용하지 않는다.

ARCHITECTURE에는 `CombatRepository`, Room v4 전투 테이블, WorkManager worker와 UI 비노출 경계를 추가한다. 기존 문서에서 자동 전투가 전부 미구현이라고 표현한 문구는 이번 승인 범위와 남은 범위를 구분하도록 수정하되 실제 구현 완료로 미리 표시하지 않는다.

## Acceptance Criteria

```powershell
$prd = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\PRD.md'
$adr = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\ADR.md'
$arch = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\ARCHITECTURE.md'
@('Post-MVP Monster Combat v1','몬스터','occurrence','25%') | ForEach-Object { if (-not $prd.Contains($_)) { throw "PRD scope missing: $_" } }
@('ADR-009','pending','멱등','소급') | ForEach-Object { if (-not $adr.Contains($_)) { throw "ADR decision missing: $_" } }
@('CombatRepository','Room v4','WorkManager') | ForEach-Object { if (-not $arch.Contains($_)) { throw "Architecture contract missing: $_" } }
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 승인 범위가 MVP 일정·보상 규칙과 충돌하지 않는지 확인한다.
3. phase index의 step 0을 완료 처리하고 핵심 승인 결정을 한국어 `summary`로 기록한다.

## 금지사항

- Kotlin이나 Room schema를 수정하지 마라. 이유: 이 step은 제품·아키텍처 승인만 담당한다.
- 전투 UI나 사망 디버프를 승인 범위에 넣지 마라. 이유: 사용자가 backend만 선택했고 디버프는 후속 범위다.
- 전투 실패 때문에 일정 완료나 경제 보상을 롤백하도록 설계하지 마라. 이유: 생산성 기능이 우선이다.
- 기존 테스트를 깨뜨리지 마라.
