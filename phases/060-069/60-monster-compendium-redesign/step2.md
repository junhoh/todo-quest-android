# Step 2: preserve-compendium-navigation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/CompendiumScreen.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/compendium/CompendiumScreenTest.kt`
- `/phases/060-069/60-monster-compendium-redesign/step1.md`
- `/phases/060-069/60-monster-compendium-redesign/index.json`

## 작업

navigation integration test를 먼저 변경해 실패를 확인한 뒤 새 도감 state, events와 effects를 application composition root에 연결한다.

`Calendar`, `Character`, `Shop`, `Compendium` 네 bottom destination의 route, 순서, label, icon, selected 상태, `saveState/restoreState`와 start destination을 변경하지 않는다. `Compendium` root의 단일 `몬스터` category와 `compendium → compendium/monsters` 진입도 유지한다. outer `Scaffold`의 고정 NavigationBar, 불투명 surface와 safe drawing inset을 유지한다.

`compendium/monsters` destination은 back-stack entry 범위 `MonsterCompendiumViewModel`에서 state와 effect를 수집하고 `MonsterCompendiumScreen`에 typed event callback을 연결한다. 발견 card는 route를 push하지 않고 `SelectMonster(species)`, 미발견 card는 같은 select event를 통해 안내 effect, preview는 `OpenSelectedMonsterDetail`, sheet dismiss는 `CloseMonsterDetail`로 연결한다.

TopAppBar back과 sheet가 닫힌 상태의 system back은 `Compendium` root로 돌아간다. sheet가 열린 상태의 system back 또는 dismiss는 먼저 sheet만 닫고 list destination을 유지한다. root에서 system back의 기존 Calendar fallback과 bottom tab 동작을 바꾸지 않는다.

기존 `AppDestination.MonsterDetail` route, `routeFor`, `parseSpecies`, `MonsterDetailViewModel` factory와 screen은 호환용으로 유지한다. 발견 route는 step 1의 공용 detail body를 사용하고 Compendium bottom tab을 selected로 유지한다. 미발견 직접 route는 generic `몬스터 도감` title과 미발견 안내만 표시하며 name, sprite와 description을 노출하지 않는다. unknown argument의 Monster list 복구를 유지한다. 새 list UI에서 이 route로 navigation하는 action은 만들지 않는다.

같은 back-stack ViewModel instance가 tab 이동과 restore 뒤 selected monster, query, filter와 search mode를 유지하는지 검증한다. Activity recreation 뒤에도 ViewModel state 또는 Navigation save/restore 범위에서 가능한 상태가 유지되어야 하며 Room persistence나 새 `SavedStateHandle` 저장 계약을 강제하지 않는다. Repository가 새 발견 set을 방출하면 기존 선택 정규화 규칙을 따른다.

integration test는 다음을 변경·추가한다.

- 네 bottom tab의 기존 순서·선택과 Compendium root 진입
- 초기 database의 실제 발견 종 count, preview와 locked card privacy
- 발견 card 선택과 preview, preview sheet open/close
- 미발견 card click이 route를 push하지 않고 Snackbar만 표시
- list TopAppBar back, sheet system back, list system back 순서
- Character tab 왕복과 Activity recreation 뒤 selection/filter/query 유지
- compatibility detail discovered content, locked generic state와 unknown route 복구
- TopAppBar와 bottom navigation 사이만 scroll되고 마지막 card bounds가 NavigationBar 위에 위치

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.AppNavigationTest,com.todoquest.feature.compendium.CompendiumScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. 기존 card-to-detail navigation 가정을 선택+sheet 계약으로 바꾼 integration test를 먼저 작성해 실패를 확인한다.
2. app composition root의 ViewModel factory, state/effect collection과 callbacks를 새 signature에 연결한다.
3. 호환 detail route가 발견/미발견 privacy와 unknown argument recovery를 지키는지 확인한다.
4. connected test에서 bottom tab, nested back, sheet back와 state restore를 검증한다.
5. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. 성공 시 task index의 step 2를 `completed`로 바꾸고 navigation·back·호환 route 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 top-level destination이나 bottom navigation 구성을 변경하지 마라. 이유: 요청 범위는 도감 nested content 재구성이다.
- 발견 card click에서 선택 event와 route navigation을 함께 실행하지 마라. 이유: selection과 상세 back stack이 중복된다.
- 호환 locked route title이나 content에 실제 종족 이름을 표시하지 마라. 이유: 직접 route 구성으로 숨겨진 이름이 유출된다.
- bottom navigation을 도감 LazyVerticalGrid 안으로 옮기지 마라. 이유: 앱 전역 고정 navigation 구조가 깨진다.
- Compose에서 DAO, AlarmManager 또는 WorkManager를 호출하지 마라. 이유: AGENTS.md의 CRITICAL architecture 경계를 위반한다.
- 기존 테스트를 깨뜨리지 마라.
