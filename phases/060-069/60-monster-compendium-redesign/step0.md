# Step 0: present-monster-compendium-state

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumViewModel.kt`
- `/app/src/test/java/com/todoquest/feature/compendium/MonsterCompendiumViewModelTest.kt`
- `/app/src/main/java/com/todoquest/ui/monster/MonsterVisualCatalog.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/phases/050-059/54-monster-compendium-menu/index.json`
- `/phases/060-069/60-monster-compendium-redesign/index.json`

## 작업

ViewModel unit test를 먼저 변경해 실패를 확인한 뒤 기존 `CombatRepository.observeDiscoveredMonsterSpecies()`를 검색·필터·선택·상세 presentation state로 투영한다. Room v14, Repository 발견 Flow와 `MonsterDiscoveryPolicy`는 변경하지 않는다.

`MonsterCompendiumFilter`는 `ALL`, `DISCOVERED`, `UNDISCOVERED` 세 값만 제공한다. 종족별로 고정된 등급·속성·출현 지역은 현재 모델에 없으므로 전투용 `MonsterGrade`, `MonsterType`이나 문서의 시각 콘셉트 태그를 도감 필터에 연결하지 않는다.

`MonsterCompendiumEntryUiModel.Undiscovered`는 `species: MonsterSpecies`만 보유하고 실제 이름, sprite와 설명 resource ID를 제거한다. 호환 route가 사용하는 `MonsterDetailUiState.Locked`도 실제 이름 resource를 보유하지 않는다. 발견 entry만 기존 `MonsterVisualCatalog` 이름·sprite와 한국어 설명 resource를 갖는다.

`MonsterCompendiumUiState.Content`는 다음 상태를 하나의 immutable snapshot으로 제공한다.

- `visibleEntries: List<MonsterCompendiumEntryUiModel>`
- `discoveredCount: Int`, `totalCount: Int`
- `collectionProgress: Float`, `collectionPercent: Int`
- `searchQuery: String`, `isSearchActive: Boolean`
- `selectedFilter: MonsterCompendiumFilter`
- `selectedMonster: MonsterCompendiumEntryUiModel.Discovered?`
- `detailMonster: MonsterCompendiumEntryUiModel.Discovered?`
- `hasActiveCriteria: Boolean`

전체 수가 0이면 progress와 percent를 모두 0으로 하고, 그 외 percent는 `discoveredCount * 100 / totalCount` 정수 내림값으로 계산한다. progress는 실제 비율을 `0f..1f`로 제한한다.

검색은 `fun interface MonsterNameResolver { fun resolve(@StringRes nameResId: Int): String }`처럼 Android `Context`를 ViewModel이 소유하지 않는 주입 경계를 사용한다. `monsterCompendiumViewModelFactory`는 application resource에서 이름을 해석하는 production 구현만 연결하고 Repository 구현이나 Room 객체를 화면에 노출하지 않는다. unit test는 fake resolver를 주입한다. 문자열 비교는 trim한 query와 발견 entry의 실제 한국어 이름을 `ignoreCase = true`로 비교한다. query가 비어 있지 않으면 미발견 entry는 이름과 무관하게 결과에서 제외해 숨겨진 이름을 추론할 수 없게 한다. 상태 필터와 검색은 동시에 적용한다.

첫 선택은 기존 catalog 순서에서 처음 발견된 entry다. 사용자가 고른 종이 계속 발견 상태이면 Repository 갱신, 검색과 필터 변경 뒤에도 유지한다. 필터가 선택 카드를 숨겨도 미리보기 선택은 유지한다. 발견 종이 하나도 없거나 기존 선택이 유효하지 않으면 첫 발견 entry 또는 null로 정규화한다. 이 선택은 back-stack ViewModel 수명에서 재구성·Activity recreation·top-level save/restore를 견디되 Room이나 별도 저장소에 기록하지 않는다.

`MonsterCompendiumEvent`에는 검색 열기/닫기, 검색어 변경, filter 선택, monster 선택, 선택 상세 열기, 상세 닫기, 조건 초기화와 retry를 표현하는 typed event를 둔다. 검색 닫기와 조건 초기화는 query를 지우고 앱바 제목 상태로 복귀하며, 조건 초기화는 filter도 `ALL`로 바꾼다. 미발견 선택은 선택 상태나 상세 상태를 바꾸지 않고 `MonsterCompendiumEffect.ShowUndiscoveredNotice`를 replay 없는 `SharedFlow`로 방출한다. 발견 entry만 detail state가 될 수 있고 발견 집합에서 빠지면 열린 detail을 닫는다.

기존 retry는 새 collection을 시작하고 Loading/Error를 격리한다. 순수 projection 또는 reducer를 분리해 빈 catalog, 수집률, filter와 search 조합을 Android UI 없이 검증할 수 있게 한다. 이름 resolver 실패는 숨겨진 이름을 추측하지 말고 해당 발견 entry가 non-empty query와 일치하지 않는 것으로 안전하게 격리한다.

unit test는 다음을 포함한다.

- 발견 0/5, 1/5, 5/5와 전체 0의 count·progress·percent
- 전체/발견/미발견 filter
- 검색과 filter 동시 적용 및 공백 query
- 미발견 해골에 `해골` query를 입력해도 결과와 UI model field에 실제 이름이 나타나지 않는 계약
- 첫 발견 기본 선택, 명시 선택 유지, 선택 종 제거 fallback
- 미발견 click effect와 발견 상세 열기/닫기
- 검색 닫기·조건 초기화와 기존 collection retry
- 호환 detail ViewModel의 미발견 resource 비노출

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.compendium.MonsterCompendiumViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. 새 unit test를 먼저 작성해 기존 state가 count·검색·filter·선택을 제공하지 않아 실패하는지 확인한다.
2. privacy-preserving entry 구조와 pure projection을 구현하고 모든 조합 test를 통과시킨다.
3. production 이름 resolver wiring이 Android `Context`를 ViewModel에 저장하지 않고 문자열 resource를 사용함을 확인한다.
4. AC 명령을 실행하고 UI가 기존 Repository interface만 관찰하는지 확인한다.
5. 성공 시 task index의 step 0을 `completed`로 바꾸고 새 state·event·privacy 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 미발견 UI model에 실제 이름, sprite 또는 description resource를 넣지 마라. 이유: 검색과 직접 상세 route에서 숨겨진 정보가 유출될 수 있다.
- 종족별 등급·속성·지역 metadata를 만들지 마라. 이유: 현재 데이터에 존재하지 않는 값을 UI를 위해 조작하게 된다.
- Repository, DAO, Room schema 또는 발견 계산을 변경하지 마라. 이유: 기존 전투 이력 projection이 필요한 source를 이미 제공한다.
- 사용자 검색어·filter·선택을 Room에 저장하지 마라. 이유: 화면 수명의 presentation 상태다.
- 표시용 한국어 문장을 ViewModel이나 Composable에 하드코딩하지 마라. 이유: AGENTS.md 문자열 resource 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
