# Step 1: compose-app-shell

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/2-android-skeleton/index.json`
- `/phases/000-009/2-android-skeleton/step0.md`
- `/settings.gradle.kts`
- `/build.gradle.kts`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/MainActivity.kt`

## 작업

Step 0에서 생성한 Android 프로젝트에 최소 Compose 앱 shell을 구현한다.

다음 공개 컴포저블을 만든다.

```kotlin
@Composable
fun TodoQuestApp()
```

구현 규칙은 다음과 같다.

- `MainActivity`는 `setContent { TodoQuestApp() }`를 호출한다.
- Material 3 theme을 적용한다.
- 첫 화면은 앱 이름 `Todo Quest`와 오늘 영역 placeholder를 정적으로 표시한다.
- 오늘 영역은 실제 일정 데이터 없이도 “Today” 영역임을 알 수 있는 최소 UI만 포함한다.
- UI 문구는 기능 설명서처럼 길게 쓰지 말고 앱 첫 화면의 placeholder 수준으로 제한한다.
- 접근성을 위해 주요 텍스트는 시스템 글자 크기 설정을 따르는 Compose 텍스트로 표시한다.
- 가독성을 해치지 않는 범위에서 `docs/UI_GUIDE.md`의 어두운 던전 톤과 골드/XP 색상 방향을 theme에 반영한다.
- 아직 ViewModel이나 UI state 계약은 만들지 않는다. 이 step에는 실제 상태가 없으므로 정적 shell로 유지한다.

필요하면 다음 파일을 추가하거나 수정한다.

- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Type.kt`

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. Compose UI가 Room DAO, AlarmManager, WorkManager, Repository, UseCase를 호출하지 않는지 확인한다.
3. `docs/UI_GUIDE.md`의 가독성, 터치 대상, 접근성 원칙을 위반하는 코드가 없는지 확인한다.
4. `/phases/000-009/2-android-skeleton/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- ViewModel을 만들지 마라. 이유: 아직 실제 UI state 계약과 도메인 데이터가 없다.
- Room DAO, AlarmManager, WorkManager를 UI에서 직접 호출하지 마라. 이유: AGENTS.md의 CRITICAL 아키텍처 규칙을 위반한다.
- 실제 일정, 반복, 보상, 알림 로직을 구현하지 마라. 이유: 이 step은 Compose shell만 담당한다.
- 카드 안에 카드를 중첩하지 마라. 이유: UI_GUIDE.md의 컴포넌트 규칙을 위반한다.
- 외부 Google Calendar 연동 문구나 설정을 추가하지 마라. 이유: MVP 범위에서 제외되어 있다.
- 기존 테스트를 깨뜨리지 마라.
