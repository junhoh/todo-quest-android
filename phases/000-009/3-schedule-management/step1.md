# Step 1: schedule-room-storage

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/phases/000-009/3-schedule-management/index.json`
- `/phases/000-009/3-schedule-management/step0.md`
- `/app/build.gradle.kts`
- `/build.gradle.kts`
- `/app/src/main/java/com/todoquest/domain/model/`
- `/app/src/main/java/com/todoquest/data/DataPackageMarker.kt`

## 작업

일정관리 영속화에 필요한 Gradle 의존성, Room local storage, mapper를 추가한다. Repository와 UseCase 비즈니스 transaction은 아직 구현하지 않는다.

Gradle 변경은 다음 기준을 따른다.

- Room 안정 버전 `2.8.4`를 사용한다.
- Kotlin annotation processing은 현재 Gradle/Kotlin 구성에서 빌드 가능한 방식으로 구성한다.
- `room.schemaLocation`을 `app/schemas`로 지정하고 Room schema export를 켠다.
- `java.time`을 Android minSdk 23에서 사용하기 위해 core library desugaring을 켠다.
- Coroutines와 Lifecycle 의존성은 후속 Repository/ViewModel step에서 사용할 안정 버전으로 고정한다.

Room local storage는 다음을 추가한다.

- `data/local/TodoTaskEntity.kt`
- `data/local/CompletionLogEntity.kt`
- `data/local/RewardLedgerEntity.kt`
- `data/local/CharacterProfileEntity.kt`
- `data/local/TodoTaskDao.kt`
- `data/local/CompletionLogDao.kt`
- `data/local/RewardLedgerDao.kt`
- `data/local/CharacterProfileDao.kt`
- `data/local/TodoQuestDatabase.kt`
- `data/mapper/TodoTaskMapper.kt`

데이터 무결성 규칙은 다음과 같다.

- `CompletionLogEntity`는 `taskId + occurrenceDateEpochDay` unique index를 가진다.
- `RewardLedgerEntity`는 `taskId + occurrenceDateEpochDay` unique index를 가진다.
- `TodoTaskEntity`는 반복 원본을 저장하며 occurrence row를 미리 생성하지 않는다.
- 삭제는 MVP에서 soft delete 필드로 처리해 과거 completion/reward ledger를 임의 삭제하지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
git diff --check
```

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. Room schema export가 활성화되어 있는지 확인한다.
3. unique index가 completion과 reward ledger 모두에 적용되었는지 확인한다.
4. Repository, UseCase, ViewModel, Compose 화면 로직이 추가되지 않았는지 확인한다.
5. `/phases/000-009/3-schedule-management/index.json`의 step 1 상태와 결과 필드를 업데이트한다.

## 금지사항

- UI에서 사용할 Repository 구현을 만들지 마라. 이유: transaction과 멱등성은 별도 step에서 테스트 먼저 검증해야 한다.
- occurrence를 DB에 미리 materialize하지 마라. 이유: ADR-004는 원본과 날짜별 계산을 분리하도록 결정했다.
- completion 또는 reward ledger unique constraint를 생략하지 마라. 이유: 같은 occurrence 보상 중복 지급을 막는 저장소 방어선이다.
- 알림 scheduler나 권한 로직을 추가하지 마라. 이유: 알림은 후속 notification phase 범위다.
- 기존 테스트를 깨뜨리지 마라.
