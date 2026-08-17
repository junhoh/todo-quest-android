# Step 1: redesign-monster-compendium-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/README.md`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/MonsterCompendiumViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/CompendiumScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/compendium/CompendiumScreenTest.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Theme.kt`
- `/app/src/main/res/values/strings.xml`
- `/phases/060-069/60-monster-compendium-redesign/step0.md`
- `/phases/060-069/60-monster-compendium-redesign/index.json`

## 작업

Compose UI test를 먼저 변경해 실패를 확인한 뒤 `MonsterCompendiumScreen`을 고정 앱바와 단일 scroll의 고밀도 수집 화면으로 재구성한다. outer app `Scaffold`가 고정 bottom navigation과 safe drawing inset을 제공하므로 도감 `Scaffold`는 자신의 TopAppBar inner padding만 적용하고 main content에 중첩 세로 scroll을 만들지 않는다.

본문은 하나의 `LazyVerticalGrid`를 사용한다. 수집 현황, filter, 선택 미리보기와 결과/빈 상태는 full-span item으로 배치하고 카드만 grid cell을 사용한다. 모든 header와 카드는 같은 세로 scroll에 참여하며 TopAppBar와 outer bottom navigation은 움직이지 않는다.

TopAppBar는 기존 48dp 뒤로가기와 `몬스터 도감` 제목을 유지한다. trailing 검색 button을 누르면 title slot을 single-line 검색 입력으로 전환한다. 검색 닫기는 query를 지우고 제목으로 복귀한다. IME action과 clear/close button의 content description은 한국어 resource를 사용하고 검색 결과는 ViewModel event로만 갱신한다.

`CollectionSummaryPanel`은 도감 또는 몬스터를 뜻하는 기존 Material icon, `발견한 몬스터`, `발견 수 / 전체 수`, `LinearProgressIndicator`와 `수집률 N%`를 표시한다. count와 progress는 전달된 state만 사용하고 하드코딩하지 않는다. font scale 2.0에서도 label과 숫자가 겹치지 않도록 wrap 가능한 layout과 합쳐진 TalkBack 설명을 사용한다.

filter는 48dp 이상의 가로 `LazyRow`에 `전체`, `발견`, `미발견` 순서로 둔다. selected chip은 전역 theme를 바꾸지 않고 `MaterialTheme.colorScheme.secondary` 민트 채움/outline과 selected semantics를 함께 사용한다. chip visual과 touch target을 분리해야 하면 최소 touch 영역을 우선한다.

`SelectedMonsterPreview`는 일반 높이 약 184dp, 최대 200dp 안에서 다음을 표시한다.

- 발견 선택: 검증된 sprite, 실제 한국어 이름, 전체 panel click 상세 action
- 발견 0: 공통 자물쇠와 `?`, 아직 발견한 몬스터가 없다는 안내, 상세 action 없음

sprite는 `ImageBitmap.imageResource`, `ContentScale.Fit`, `FilterQuality.None`을 유지하고 canonical 64×64 logical canvas를 크롭하거나 불투명 영역 기준으로 개별 확대하지 않는다. 배경과 합쳐지지 않도록 낮은 강조 surface/outline을 별도 UI layer로 사용한다. 상세 button을 별도로 추가하지 않고 panel 전체를 최소 48dp action으로 사용해 160~200dp 높이를 지킨다.

grid 열 수는 available width와 약 104dp 최소 card 너비를 기준으로 계산한 뒤 `3..5`로 clamp한다. 휴대전화 세로와 320dp 폭은 3열, 넓은 화면은 4~5열을 사용한다. 좌우 16dp, 카드 사이 8dp, 12~16dp corner를 적용한다. 카드 종횡비와 내부 image/text slot을 통일하고 긴 이름은 한 줄 ellipsis로 처리해 font scale 확대에도 카드 높이가 달라지지 않게 한다. 각 item key는 `species.name`을 사용한다.

발견 카드는 중앙 sprite와 하단 이름을 표시한다. 선택 카드는 secondary 민트 2dp outline, 약한 background 강조와 `selected = true` semantics를 모두 사용한다. sprite와 이름은 bounds를 벗어나지 않는다.

미발견 카드는 actual sprite나 runtime tint silhouette도 사용하지 않는다. 공통 `?`, 자물쇠와 `???`만 표시하고 발견 카드보다 낮은 명도를 사용한다. merged semantics는 실제 종족을 포함하지 않은 `미발견 몬스터`와 선택되지 않음 상태를 제공한다. card는 48dp 이상 clickable하며 event를 통해 한국어 `아직 발견하지 못한 몬스터입니다` Snackbar를 표시하지만 selection과 detail은 바꾸지 않는다.

`MonsterCompendiumEffect`는 `LaunchedEffect`에서 lifecycle에 맞춰 수집하고 `SnackbarHostState`에 replay 없이 표시한다. 새 effect collection이 화면 재구성이나 탭 재진입 때 지난 안내를 재생하지 않아야 한다.

발견 preview를 누르면 `ModalBottomSheet`를 연다. sheet는 선택 발견 entry의 sprite, 이름, 기존 한국어 외형 설명과 발견 완료 상태만 표시하며 grade, type, region, ability, reward, drop이나 가짜 값을 추가하지 않는다. sheet close button과 swipe/system dismiss는 같은 Close event를 보낸다. 상세 body를 `DiscoveredMonsterDetailContent` 같은 공용 Composable로 분리해 호환 `MonsterDetailScreen`도 같은 pixel-art/content contract를 사용한다. 호환 locked screen은 generic 미발견 안내만 표시하고 실제 이름을 title로 쓰지 않는다.

`visibleEntries`가 비어 있으면 blank grid 대신 원인을 설명하고 48dp `필터 초기화` button을 제공한다. 전체 catalog 0은 `등록된 몬스터가 없습니다`, Loading과 Error는 각각 progress와 retry를 표시한다. 모든 신규 사용자 문구, semantics와 accessibility label은 `strings.xml`에 한국어 resource로 추가한다.

표준 content와 `320x640`, font scale 2.0 content의 Compose Preview를 추가하거나 갱신한다. UI test는 다음을 포함한다.

- 실제 state와 일치하는 발견/전체/percent와 total 0
- 320dp 3열, wide 4~5열 및 마지막 카드 도달
- 발견 sprite·이름과 미발견 `???`·lock·actual name/image 부재
- 발견 카드 선택 callback, preview 변경, selected semantics와 outline
- filter chip과 검색 결과, 결과 0 안내와 초기화
- 미발견 click Snackbar
- preview detail sheet 열기·닫기와 description
- 48dp target, font scale 2.0에서 summary/name bounds
- `FilterQuality.None`과 `ContentScale.Fit`을 보존하는 sprite node

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.compendium.CompendiumScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. 새 layout·privacy·selection·sheet·큰 글꼴 UI test를 먼저 작성해 실패를 확인한다.
2. 하나의 LazyVerticalGrid와 full-span header 구조로 UI를 구현하고 중첩 scroll이 없는지 확인한다.
3. standard와 320dp/font scale 2.0 Preview에서 summary, preview, 세 열 카드와 empty result를 시각 검토한다.
4. connected test에서 pixel sprite, 48dp target, Snackbar와 ModalBottomSheet를 검증한다.
5. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. 성공 시 task index의 step 1을 `completed`로 바꾸고 단일 scroll·3~5열·privacy·sheet 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- main content에 `Column.verticalScroll`이나 두 번째 `LazyVerticalGrid`를 중첩하지 마라. 이유: 고정 app bar와 단일 scroll 계약이 깨진다.
- 몬스터 PNG를 수정, crop, recolor 또는 재생성하지 마라. 이유: canonical pixel art와 공용 전투 표시를 보존해야 한다.
- 미발견 카드에 실제 sprite tint silhouette를 사용하지 마라. 이유: UI model의 resource privacy를 깨고 종족 형태를 노출한다.
- 모든 상태를 색상만으로 구분하지 마라. 이유: outline, lock, text와 semantics도 필요하다.
- 전역 theme 색상이나 다른 화면 기본 container를 바꾸지 마라. 이유: 사용자 범위를 벗어난다.
- 기존 테스트를 깨뜨리지 마라.
