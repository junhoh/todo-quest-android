# Step 0: 캘린더 한국어·월 이동 계약 정의

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarScreen.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`
- `/phases/020-029/26-korean-character-ui/step0.md`
- `/phases/020-029/27-calendar-navigation-korean-ui/index.json`

## 작업

`/docs/UI_GUIDE.md`의 한국어 기본 언어 정책과 월간 캘린더 계약을 현재 승인 범위에 맞게 갱신한다.

- Calendar 화면의 기존 영문 문구 번역이 후속 범위라는 문장을 제거하고 Calendar의 사용자 노출 문구와 TalkBack 설명도 `/app/src/main/res/values/strings.xml`의 한국어 기본 리소스를 사용한다고 명시한다.
- 월 제목 형식은 `2026년 7월`, 요일 머리글은 월요일부터 `월`, `화`, `수`, `목`, `금`, `토`, `일` 순서로 고정한다.
- 월 제목 양옆의 이전 달·다음 달 버튼으로 한 달씩 이동한다고 명시한다. 이동한 달의 선택 일자는 가능한 한 기존 일자를 유지하고 그 일자가 없으면 이동 대상 월의 말일로 맞춘다.
- 직접 년·월 선택기와 오늘로 이동 버튼은 이번 범위에 포함하지 않는다.
- Add 버튼, 일정 목록, 편집·삭제·완료 UI, task editor, 보상 안내, 오류와 snackbar를 포함한 Calendar 화면 전체를 한국어화한다고 명시한다.
- `Todo Quest` 브랜드명과 route, enum, test tag, resource key 같은 내부 식별자는 영문을 유지한다.
- MVP의 로컬 캘린더, occurrence 단위 완료·보상 멱등성, 반복 원본 분리 규칙은 변경하지 않는다.

## Acceptance Criteria

```powershell
$guide = Get-Content -Raw -Encoding utf8 -LiteralPath 'docs\UI_GUIDE.md'
@('2026년 7월', '월`, `화`, `수`, `목`, `금`, `토`, `일', '이전 달', '다음 달', 'Calendar 화면 전체') | ForEach-Object { if (-not $guide.Contains($_)) { throw "Missing Calendar UI contract: $_" } }
if ($guide.Contains('현재 Calendar 화면 내부의 기존 영문 문구 번역은 이번 phase 범위 밖')) { throw 'Obsolete Calendar localization scope remains' }
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. PRD, ARCHITECTURE, ADR과 AGENTS.md의 CRITICAL 규칙을 변경하거나 약화하지 않았는지 확인한다.
3. `/phases/020-029/27-calendar-navigation-korean-ui/index.json`의 step 0을 `completed`로 변경하고 문서 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Android 구현 파일을 이 step에서 수정하지 마라. 이유: 이 step은 Calendar UI 계약 문서만 다룬다.
- 직접 년·월 선택기나 오늘 버튼을 문서 범위에 추가하지 마라. 이유: 승인된 월 이동 방식은 좌우 한 달 단위다.
- Google Calendar 연동을 추가하지 마라. 이유: MVP는 앱 내부 로컬 캘린더만 지원한다.
- 기존 테스트를 깨뜨리지 마라.
