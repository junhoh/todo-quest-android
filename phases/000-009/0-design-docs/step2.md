# Step 2: architecture-records

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/phases/000-009/0-design-docs/index.json`

## 작업

`docs/ARCHITECTURE.md`와 `docs/ADR.md`를 Todo Quest Android 구현 기준으로 작성한다.

`docs/ARCHITECTURE.md`에는 다음 내용을 포함한다.

- 단일 Android 앱 구조
- Compose UI, ViewModel, UseCase, Repository, Room DAO 레이어 분리
- 권장 패키지 구조
- `TodoTask`, `RecurrenceRule`, `TaskOccurrence`, `Reminder`, `CompletionLog`, `RewardLedger`, `CharacterProfile`, `ShopItem`, `InventoryItem` 도메인 모델 설명
- 사용자 입력부터 Room transaction 또는 scheduler까지의 데이터 흐름
- 반복 occurrence 계산 규칙
- 보상 지급 멱등성 규칙
- 구매 transaction 규칙
- 알림 scheduler 인터페이스와 권한 실패 처리
- WorkManager와 AlarmManager 역할 구분
- 순수 Kotlin unit test 우선 전략

`docs/ADR.md`에는 다음 결정을 포함한다.

- Android 네이티브와 Jetpack Compose 선택
- 로컬 우선 Room 저장소 선택
- MVVM, UseCase, Repository 구조 선택
- 반복 일정은 occurrence 단위로 완료
- 보상 지급은 ledger 기준으로 멱등 처리
- 알림 권한 실패와 일정 저장 분리
- 픽셀 RPG 감성은 보상 영역에 집중

## Acceptance Criteria

```powershell
git diff --check
$matches = rg -n "\{[^}]+\}|Next\.js|Tailwind|npm run|app/api" AGENTS.md docs
if ($LASTEXITCODE -eq 0) { $matches; exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
exit 0
```

검색 결과가 있으면 실패로 처리한다.

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 아키텍처 문서와 ADR이 PRD의 일정, 반복, 알림, 보상, 상점 범위를 모두 지원하는지 확인한다.
3. `/phases/000-009/0-design-docs/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- UI에서 Room DAO나 Android scheduler를 직접 호출하는 구조를 제안하지 마라. 이유: 테스트와 권한 실패 처리가 어려워진다.
- 완료 보상을 단순 Boolean 상태만으로 처리하지 마라. 이유: 중복 지급과 감사 추적 문제가 생긴다.
- 기존 harness 테스트를 깨뜨리지 마라.
