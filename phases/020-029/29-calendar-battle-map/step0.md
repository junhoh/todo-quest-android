# Step 0: Battle Map UI 계약 문서화

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/monster/README.md`
- `/phases/020-029/29-calendar-battle-map/index.json`

## 작업

Post-MVP Monster Combat backend 위에 메인 Calendar용 Battle Map UI를 추가하는 계약을 문서에 먼저 승인한다.

- 화면 순서는 시스템 inset 다음 `Battle Map → 선택 날짜·level·XP·gold 요약 → 월간 캘린더 → 선택 날짜의 할 일 목록`으로 정한다. 기존 Header의 `Todo Quest` 앱명만 제거한다.
- Battle Map은 실제 `CombatRepository.observeCombat()`을 관찰하되 전투 로딩·실패가 일정 조회, 편집, 완료, occurrence 보상 성공을 막지 않는 독립 presentation state로 둔다.
- 현재 backend의 활성 몬스터 한 마리를 운영 화면에 표시하고, UI 컴포넌트·sample·Preview만 0~4마리 확장을 지원한다고 명시한다. Room schema나 combat domain의 단일 활성 몬스터 계약은 바꾸지 않는다.
- 배경, 장식, 지면 그림자, player, monster, overlay를 독립 레이어로 관리하고 캐릭터·몬스터·HP·damage·text를 배경 PNG에 합성하지 않는다.
- 정규화 좌표는 `0f..1f`, 기준점은 bottom-center 발 위치, 큰 y가 앞쪽이며 더 높은 z-order로 렌더링한다고 기록한다.
- 맵 높이는 `availableWidth / 2.4f`를 기준으로 `190dp..320dp`에 제한하고 화면 전체는 하나의 세로 scroll container로 구성한다.
- 기본 초원은 교체 가능한 opaque PNG resource이며 decode 실패 시 단색이 아닌 Canvas 초원 fallback을 사용한다.
- HUD, 체력바, 이름, 상태 효과, 공격 애니메이션과 damage text는 이번 phase에서 렌더링하지 않고 `BattleOverlayLayer` 후속 확장 지점만 둔다.
- PRD의 기존 "전투 UI 미구현" 문구, ARCHITECTURE의 navigation/data flow, ADR-009 후속 범위를 실제 승인 상태와 모순되지 않게 갱신하거나 새 ADR 항목으로 분리한다.

## Acceptance Criteria

```powershell
$docs = @('docs\PRD.md','docs\ARCHITECTURE.md','docs\ADR.md','docs\UI_GUIDE.md')
$markers = @('Battle Map','CombatRepository','bottom-center','BattleOverlayLayer')
foreach ($marker in $markers) {
    if (-not ($docs | Where-Object { (Get-Content -Raw -Encoding UTF8 -LiteralPath $_).Contains($marker) })) {
        throw "Battle Map documentation marker missing: $marker"
    }
}
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 문서가 AGENTS의 UI/Repository 경계, occurrence 멱등성과 권한 독립성 규칙을 보존하는지 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 승인한 UI 범위를 한국어 `summary`로 기록한다.

## 금지사항

- Room schema나 전투 계산 정책을 변경하지 마라. 이유: 이번 phase는 presentation과 Calendar 통합 범위다.
- 다중 활성 몬스터 backend가 구현됐다고 문서화하지 마라. 이유: 현재 `CombatSnapshot`은 단일 활성 몬스터 계약이다.
- 공격 애니메이션·HUD·보상 기능을 완료 범위에 넣지 마라. 이유: overlay 확장 지점만 제공한다.
- 기존 테스트를 깨뜨리지 마라.
