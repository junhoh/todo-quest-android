# Step 2: architecture-package-placeholders

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/2-android-skeleton/index.json`
- `/phases/000-009/2-android-skeleton/step0.md`
- `/phases/000-009/2-android-skeleton/step1.md`
- `/app/src/main/java/com/todoquest/MainActivity.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`

## 작업

향후 MVP 구현이 아키텍처 문서의 경계를 따를 수 있도록 패키지 골격만 만든다. 실제 비즈니스 로직은 추가하지 않는다.

다음 패키지 경계를 준비한다.

- `com.todoquest.app`
- `com.todoquest.core`
- `com.todoquest.domain`
- `com.todoquest.data`
- `com.todoquest.feature`
- `com.todoquest.notification`
- `com.todoquest.ui`

구현 규칙은 다음과 같다.

- 빈 디렉터리만으로 추적이 어렵다면 각 패키지에 최소 Kotlin placeholder를 둘 수 있다.
- placeholder는 `internal object <Name>PackageMarker`처럼 빌드 영향이 거의 없는 형태로 만든다.
- marker 이름은 패키지 목적이 드러나야 한다.
- marker에는 TODO성 비즈니스 요구사항을 적지 않는다.
- `data/local`, `data/repository`, `domain/model`, `domain/repository`, `domain/usecase`, `feature/calendar`, `feature/task`, `feature/character`, `feature/shop` 세부 패키지는 실제 기능 구현 phase에서 만들도록 남겨둔다.
- `notification` 패키지는 scheduler 구현 없이 marker만 둔다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. 패키지 marker 외에 도메인, 데이터, 알림, 화면 기능 로직이 추가되지 않았는지 확인한다.
3. `docs/ARCHITECTURE.md`의 디렉터리 구조와 레이어 규칙을 위반하지 않는지 확인한다.
4. `/phases/000-009/2-android-skeleton/index.json`의 step 2 상태와 결과 필드를 업데이트한다.

## 금지사항

- Room entity, DAO, database 클래스를 만들지 마라. 이유: 데이터 저장소는 후속 data phase에서 test-first로 설계해야 한다.
- Repository interface나 UseCase를 만들지 마라. 이유: 도메인 계약 없이 placeholder만 늘리면 후속 구현의 결정권을 흐린다.
- 반복 일정 계산, 보상 지급, 구매 검증 로직을 만들지 마라. 이유: AGENTS.md는 해당 기능을 테스트 먼저 작성하라고 요구한다.
- 알림 scheduler 인터페이스나 receiver를 만들지 마라. 이유: 권한과 재등록 정책은 별도 phase에서 검증해야 한다.
- 기존 테스트를 깨뜨리지 마라.
