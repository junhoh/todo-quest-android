# Step 4: calendar-compose-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/phases/000-009/3-schedule-management/step1.md`
- `/phases/000-009/3-schedule-management/step2.md`
- `/phases/000-009/3-schedule-management/step3.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/ui/theme/`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarUiState.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`

## 작업

기존 정적 placeholder를 실제 일정관리 첫 화면으로 교체한다. 이 step은 Compose UI와 앱 composition root만 다룬다.

추가 또는 변경할 대상은 다음과 같다.

- `feature/calendar/CalendarScreen.kt`
- `app/TodoQuestApp.kt`
- 필요 시 앱 composition root에서 Room database, Repository, UseCase, ViewModel factory를 구성한다.

화면 요구사항은 다음과 같다.

- 첫 화면에 월간 캘린더와 선택 날짜의 퀘스트 목록을 보여준다.
- 날짜 셀은 선택 날짜, 오늘, 일정 개수, 완료 개수를 색과 텍스트로 구분한다.
- 선택 날짜의 목록은 제목, 반복 여부, 난이도, 완료 상태, 예상 보상을 표시한다.
- 완료 버튼은 명확한 클릭 대상이며 accessibility content description을 제공한다.
- 일정 추가는 MVP 최소 폼으로 제목 입력과 저장/취소를 제공한다.
- 실제 화면 테스트가 찾을 수 있도록 주요 요소에 stable testTag를 둔다.

레이어 규칙은 다음과 같다.

- Compose는 ViewModel state를 렌더링하고 ViewModel event만 호출한다.
- Compose에서 Room DAO, Room database, AlarmManager, WorkManager를 직접 호출하지 않는다.
- 알림 설정 UI는 이번 step에 만들지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 화면이 `docs/UI_GUIDE.md`의 월간 캘린더, 일간 퀘스트 목록, 접근성 규칙을 따르는지 확인한다.
3. Compose 파일이 DAO나 scheduler를 직접 import하지 않는지 확인한다.
4. 실제 화면 테스트용 testTag와 content description이 안정적인지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`의 step 4 상태와 결과 필드를 업데이트한다.

## 금지사항

- UI에서 Room DAO를 직접 호출하지 마라. 이유: 레이어 규칙과 테스트 가능성을 해친다.
- UI에서 AlarmManager나 WorkManager를 직접 호출하지 마라. 이유: 알림은 후속 notification phase에서 scheduler abstraction으로 다룬다.
- 카드 안에 카드를 중첩하지 마라. 이유: UI_GUIDE.md의 컴포넌트 규칙을 위반한다.
- Google Calendar 연동 버튼이나 권한 요청을 추가하지 마라. 이유: MVP 제외 사항이다.
- 기존 테스트를 깨뜨리지 마라.
