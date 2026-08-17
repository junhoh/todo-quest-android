# Step 0: 캐릭터 스탯 설계 문서 분리

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/phases/020-029/23-character-stats-design/index.json`
- `/phases/020-029/24-doc-indexing-and-cross-references/index.json`

## 작업

`/docs/game-design/character-stats-design.md`의 기존 경로는 후속 구현과 과거 phase가 계속 사용할 수 있는 호환 인덱스로 유지하고, 긴 본문을 `/docs/game-design/character-stats/` 아래의 주제별 문서로 분리한다. 수치, 공식, enum 코드, 정책, 예시와 Kotlin 계약의 의미는 바꾸지 않는다.

다음 문서와 책임 경계를 사용한다.

1. `todo-combat-rewards.md`
   - 기존 `TODO 보상과 자동 전투 연동 계약` 전체
   - occurrence 멱등 event, 정시/실패 처리, 효율 감소, MOMENTUM, ledger 테스트 경계
2. `stats-and-progression.md`
   - 기존 `기본 스탯`, `레벨 성장과 스탯 포인트`, `파생 능력치` 전체
3. `modifiers-and-equipment.md`
   - 기존 `숫자 표현과 반올림 계약`, `장비와 효과 적용`, `장비 부위 역할` 전체
4. `combat-calculation.md`
   - 기존 `확률 판정, 상태 적용 및 전리품`, `방어와 피해 계산`, `최대 HP 변경 시 현재 HP` 전체
5. `implementation-and-validation.md`
   - 기존 `구현용 데이터 계약`부터 `순수 Kotlin unit test 우선 checklist`까지 전체

각 하위 문서는 고유한 H1 제목, 상단의 `[캐릭터 스탯 설계 인덱스로 돌아가기](../character-stats-design.md)` 링크, “PRD의 MVP 제외 범위를 바꾸지 않는 후속 확장 설계”라는 지위 안내를 포함한다. 같은 규칙이나 표를 여러 문서에 복제하지 말고 필요한 경우 상대 링크로 연결한다.

`character-stats-design.md`에는 다음만 남긴다.

- 기존 문서 지위와 적용 범위
- 기존 명세 우선순위 및 충돌 표
- 권장 읽기 순서와 다섯 하위 문서의 책임
- 기존 주요 H2 이름을 사용하는 링크 섹션. 각 섹션은 새 문서의 대응 heading으로 연결해 기존 파일과 상위 heading 탐색을 최대한 보존한다.
- 게임 설계 디렉터리 인덱스는 다음 step에서 추가될 예정임을 고려한 상대 링크 구조

인덱스는 120줄 이하, 각 하위 문서는 220줄 이하로 유지한다. 원문에서 이동한 heading 이름은 변경하지 않아 검색 가능성을 보존한다.

## Acceptance Criteria

```powershell
$files = @(
  'docs\game-design\character-stats-design.md',
  'docs\game-design\character-stats\todo-combat-rewards.md',
  'docs\game-design\character-stats\stats-and-progression.md',
  'docs\game-design\character-stats\modifiers-and-equipment.md',
  'docs\game-design\character-stats\combat-calculation.md',
  'docs\game-design\character-stats\implementation-and-validation.md'
)
$files | ForEach-Object { if (-not (Test-Path -LiteralPath $_)) { throw "missing: $_" } }
if ((Get-Content -Encoding UTF8 -LiteralPath $files[0]).Count -gt 120) { throw 'character stats index is too long' }
$files[1..5] | ForEach-Object { if ((Get-Content -Encoding UTF8 -LiteralPath $_).Count -gt 220) { throw "split document is too long: $_" } }
$body = ($files[1..5] | ForEach-Object { Get-Content -Raw -Encoding UTF8 -LiteralPath $_ }) -join "`n"
@('## TODO 보상과 자동 전투 연동 계약','## 기본 스탯','## 레벨 성장과 스탯 포인트','## 파생 능력치','## 숫자 표현과 반올림 계약','## 장비와 효과 적용','## 장비 부위 역할','## 확률 판정, 상태 적용 및 전리품','## 방어와 피해 계산','## 최대 HP 변경 시 현재 HP','## 구현용 데이터 계약','## 파생 능력치 재계산 이벤트','## 밸런스 설정과 확정 결과','## 골든 수치 검증','## 순수 Kotlin unit test 우선 checklist') | ForEach-Object { if (([regex]::Matches($body, "(?m)^$([regex]::Escape($_))$")).Count -ne 1) { throw "heading missing or duplicated: $_" } }
@('STRENGTH','VITALITY','FOCUS','WILLPOWER','MAX_HP','ATTACK','DEFENSE','CRITICAL_CHANCE','CRITICAL_DAMAGE','STATUS_RESISTANCE','HP_RECOVERY','GOLD_GAIN_BONUS','defenseConstant','Lv10 계산 전개') | ForEach-Object { if (-not $body.Contains($_)) { throw "contract marker missing: $_" } }
```

```powershell
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 원본 문서의 수치 표, 공식 코드 블록, 데이터 계약과 Lv1·10·30 예시가 새 문서 중 정확한 책임 파일에 존재하는지 diff로 확인한다.
3. PRD의 MVP 제외 사항과 AGENTS.md의 occurrence 멱등 규칙을 변경하거나 약화하지 않았는지 확인한다.
4. task index에서 step 0을 `completed`로 바꾸고, 생성한 인덱스와 다섯 하위 문서를 설명하는 한국어 `summary` 및 `completed_at`을 기록한다.

## 금지사항

- 기존 공식, 상한, 성장 수치나 골든 예시를 재설계하지 마라. 이유: 이 phase는 설계 변경이 아니라 문서 구조 개선이다.
- `character-stats-design.md`를 삭제하거나 다른 경로로 이동하지 마라. 이유: 기존 phase와 외부 참조의 진입 경로를 보존해야 한다.
- 같은 규칙을 인덱스와 하위 문서에 중복 작성하지 마라. 이유: 후속 수정에서 문서가 불일치할 수 있다.
- 앱 Kotlin 코드, Room 스키마, JSON 명세와 PNG를 수정하지 마라. 이유: 이 step은 game-design 문서 모듈만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
