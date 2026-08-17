# Step 7: render-character-stat-guide-dialog

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterUiState.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/050-059/56-fairy-character-stat-guide/step6.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

Compose 테스트를 먼저 추가하고 신규 설치 첫 Character 진입의 단일 능력치 안내 Dialog와 재열람 도움말을 구현한다.

`strings.xml`에 다음 한국어 resource를 추가한다. 표시 문장을 Compose나 ViewModel에 하드코딩하지 않는다.

- 제목 `능력치 배분 안내`
- 화자 `안내 요정`
- `모험가님, 레벨이 오르면 능력치 포인트를 얻어요.`
- `힘은 공격력을, 활력은 최대 체력과 방어력을, 집중은 치명타를, 의지는 상태 이상 저항과 회복을 높여 줘요.`
- `원하는 능력치의 + 버튼을 누른 뒤 ‘능력치 배분 저장’을 눌러야 적용돼요. 능력치 이름을 누르면 자세한 효과도 확인할 수 있어요.`
- 포인트 0 `지금은 미배분 포인트가 없어요. 퀘스트를 완료해 레벨을 올리면 새 포인트를 얻을 수 있어요.`
- 포인트 보유 `지금 배분할 수 있는 포인트가 %1$d개 있어요.`
- primary `능력치 보러 가기`, secondary `닫기`, 도움말 TalkBack `능력치 배분 안내 열기`.

UI 계약은 다음과 같다.

- `CharacterScreen`은 ViewModel의 visible state와 `showStatAllocationGuide`·`dismissStatAllocationGuide`를 `CharacterContent`에 전달한다.
- 단일 Material Dialog는 96dp sprite frame, 화자, 고정 세 문단, 포인트 상태 문장과 두 action을 세로로 제공한다. 본문은 제한 높이 안에서 독립 스크롤 가능하고 action은 잃지 않는다.
- sprite는 `ImageBitmap.imageResource` decode를 격리하고 전체 64×64 canvas를 `ContentScale.Fit`, `FilterQuality.None`으로 표시한다. 대사의 TalkBack에 화자를 포함하므로 bitmap은 decorative다. decode 실패는 같은 frame의 decorative `Info` 계열 Material icon으로 대체한다.
- `CharacterSection` header가 optional action을 받을 수 있게 일반화하거나 Base stats 전용 header를 사용한다. `기본 능력치` 제목 오른쪽에 최소 48dp 도움말 버튼을 두고 manual dialog를 연다.
- `CharacterContent`의 scroll state를 명시적으로 소유하고 Base stats section에 `BringIntoViewRequester`를 연결한다. primary action은 먼저 ViewModel dismiss를 호출한 뒤 Compose coroutine에서 Base stats를 viewport로 가져온다. scroll position은 ViewModel·Repository에 저장하지 않는다.
- secondary, back, outside dismiss도 ViewModel dismiss를 호출한다.
- `320dp×640dp`, font scale 2.0에서도 화자·본문을 읽고 두 action에 도달할 수 있어야 한다. 도움말과 Dialog action은 최소 48dp이고 색만으로 상태를 전달하지 않는다.

`CharacterScreenTest`에서 자동 Dialog, 0/양수 포인트 문구, primary scroll, secondary dismiss, 도움말 reopen, 96dp frame, decode fallback, 한국어·TalkBack, 큰 글꼴 접근성을 검증한다. `AppNavigationTest`에서는 injected eligible repository로 Character 첫 진입 자동 표시와 확인 후 탭 재진입 미표시를 검증하되 기존 navigation 상태를 깨뜨리지 않는다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
git diff --check
```

## 검증 절차

1. Compose와 navigation 테스트를 먼저 작성해 UI 부재 실패를 확인한다.
2. AC를 실행한다.
3. 실제 runtime fairy resource가 최근접으로 표시되고 fallback이 기능을 막지 않는지 확인한다.
4. 320dp·font scale 2.0과 TalkBack 계약을 확인한다.
5. phase index step 7을 완료 처리하고 Dialog·scroll·재열람·접근성을 한국어로 요약한다.

## 금지사항

- 영문 표시 문장을 Compose나 ViewModel에 하드코딩하지 마라. 이유: 한국어 기본 문자열 정책을 위반한다.
- sprite를 antialiasing 또는 기본 bilinear로 확대하지 마라. 이유: 픽셀 경계가 흐려진다.
- Dialog에서 포인트를 자동 배분하거나 저장하지 마라. 이유: 사용자의 명시적 stat command를 대신하면 안 된다.
- 도움말 재열람 때 automatic acknowledged 상태를 되돌리지 마라. 이유: 자동 표시와 수동 도움말은 독립적이다.
- UI가 SharedPreferences나 Room DAO를 직접 호출하지 마라. 이유: CRITICAL 아키텍처 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.

