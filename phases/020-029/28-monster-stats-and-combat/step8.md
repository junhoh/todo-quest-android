# Step 8: WorkManager 전투 reconciliation 연결

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/build.gradle.kts`
- `/app/src/main/AndroidManifest.xml`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/ReconcileCombatUseCase.kt`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

app/background composition 레이어에 WorkManager reconciliation을 연결한다. Android 도구나 dependency가 없으면 임의 설치하지 말고 step을 blocked 처리한다.

- stable `androidx.work:work-runtime:2.11.2`를 추가한다. worker test가 필요하면 같은 버전 `androidx.work:work-testing`을 test dependency로 사용한다.
- `TodoQuestApplication` 또는 동등한 application-scoped container를 만들어 단일 Room database, `AppClock`, Task/Character/Combat Repository와 UseCase를 Compose와 Worker가 공유하게 한다. 기존 `TodoQuestApp` 안에서 DB를 별도 생성하지 않는다.
- manifest에 application class를 등록한다.
- `CombatReconciliationWorker : CoroutineWorker`는 container의 `ReconcileCombatUseCase`만 호출하며 DAO를 직접 조합하지 않는다.
- 앱 시작 시 unique one-time reconciliation을 enqueue하고 unique periodic work를 15분 주기 `KEEP` 또는 동등한 중복 방지 정책으로 등록한다. network, charging, notification/exact-alarm permission constraint를 두지 않는다.
- 성공은 `Result.success`, 재시도 가능한 DB 일시 오류는 제한된 `Result.retry`, 결정적 schema/config 오류는 무한 재시도하지 않도록 diagnostic 후 `Result.failure`로 분류한다.
- worker가 동시에 실행돼도 Room event unique key와 repository transaction으로 결과가 한 번만 적용되어야 한다.
- backend-only 선택을 유지해 navigation, Compose 화면, 새 사용자 문구를 추가하지 않는다.

Robolectric/WorkManager test로 unique 등록, worker→UseCase 호출, retry/failure 분류, 두 worker 동시 실행 멱등을 검증한다. 기존 app navigation instrumentation wiring이 shared container로 바뀐 뒤에도 test database isolation을 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test lint assembleDebug
git diff --check
```

## 검증 절차

1. unit test, lint와 debug assemble을 실행한다.
2. Application, Compose와 Worker가 database singleton을 공유하는지 확인한다.
3. phase index의 step 8을 완료 처리하고 worker·container 결정을 한국어 `summary`로 기록한다.

## 금지사항

- WorkManager 15분보다 짧은 periodic interval을 사용하지 마라. 이유: 플랫폼 최소 주기다.
- worker에서 DAO를 직접 호출하지 마라. 이유: Repository/UseCase 경계를 지켜야 한다.
- Android SDK나 JDK를 임의 설치하거나 사용자 환경 변수를 영구 변경하지 마라. 이유: AGENTS 개발 프로세스 규칙이다.
- 기존 테스트를 깨뜨리지 마라.
