# Step 1: product-spec

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/0-design-docs/index.json`

## 작업

`docs/PRD.md`를 Todo Quest 제품 요구사항으로 작성한다.

반드시 포함할 제품 범위는 다음과 같다.

- Android 앱 이름은 `Todo Quest`다.
- 사용자는 내부 캘린더 기반으로 TODO와 일정을 관리한다.
- 월간 캘린더와 일간 퀘스트 목록을 제공한다.
- 일정은 제목, 메모, 날짜, 선택적 시간, 난이도, 카테고리, 반복, 알림을 가진다.
- 반복 유형은 없음, 매일, 매주, 매월이다.
- 반복 일정 완료는 원본 일정 전체가 아니라 날짜별 occurrence에만 적용한다.
- 일정 완료 시 XP와 골드를 지급한다.
- 같은 occurrence에 XP와 골드가 두 번 지급되면 안 된다.
- 캐릭터 레벨, XP, 골드, 상점, 인벤토리를 MVP에 포함한다.
- 상점 아이템은 MVP에서 외형 또는 수집 요소이며 능력치 효과는 없다.
- 알림 권한이 없어도 일정 생성, 완료, 보상 기능은 동작한다.
- 외부 Google Calendar 연동, 로그인, 서버 동기화, 결제, 소셜, 전투, 스토리 챕터는 MVP 제외다.

성공 기준에는 일정 등록 속도, 오늘 할 일 확인, 반복 occurrence 완료, 보상 중복 방지, 알림 권한 거부 시 핵심 기능 유지가 포함되어야 한다.

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
2. `docs/PRD.md`에 일정, 반복, 알림, RPG 보상, MVP 제외 범위가 모두 있는지 확인한다.
3. `/phases/000-009/0-design-docs/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- 외부 캘린더 연동을 MVP 기능으로 넣지 마라. 이유: 사용자 권한, 동기화 충돌, 외부 API 의존성이 초기 범위를 키운다.
- 능력치 보너스나 전투를 MVP에 넣지 마라. 이유: 일정관리 앱의 핵심 검증보다 게임 시스템이 앞서게 된다.
- 기존 harness 테스트를 깨뜨리지 마라.
