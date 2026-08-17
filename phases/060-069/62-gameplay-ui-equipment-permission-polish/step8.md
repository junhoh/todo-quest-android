# Step 8: stabilize-shop-layout-actions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step6.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

Compose UI 테스트를 먼저 추가하고 선택/해제/action label 변화에도 상점 레이아웃의 크기와 위치가 변하지 않게 한다.

공격력·최대 체력·방어력 세 summary cell은 동일 weight뿐 아니라 동일한 고정 높이를 사용한다. 각 cell의 현재 값 영역과 증감 영역에 항상 같은 높이를 예약하고 difference가 0일 때는 같은 높이의 `Spacer` 또는 빈 presentation을 렌더링한다. item 선택 전후, 양수/음수/0 difference에서 `StatSummary`와 세 cell의 width/height가 동일해야 한다. 큰 글꼴에서도 텍스트가 cell 전체 크기를 늘리지 않도록 한 줄/overflow 정책과 semantics를 분리하되 실제 숫자를 숨기지 않는다.

`ShopItemCard`는 action 영역을 content flow에서 분리한 `Box`/constraint 구조로 만들고, 공통 primary action button을 card 우측 하단 12dp inset에 고정한다. button touch target은 모든 상태에서 `104.dp × 48.dp`로 고정한다. card 본문과 구매 불가 reason은 button 영역을 침범하지 않도록 trailing/bottom 공간을 예약한다. 상세 dialog도 같은 typed action과 동일한 `104.dp × 48.dp` button을 하단 우측에 배치한다.

action label은 오직 문자열 리소스의 `구매`, `구매 불가`, `장착`, `해제` 중 하나다. 레벨 부족, 골드 부족, 미판매, slot 오류 등의 자세한 이유는 button 밖의 별도 caption/semantics로 표시한다. 처리 중에도 button 크기와 위치는 유지하고 disabled/progress semantics만 바꾼다. 카드 본문 선택과 상세 열기 동작은 유지한다.

테스트는 네 action label의 정확한 문자열, 모든 button bounds 크기/우측 하단 정렬, 카드와 상세 action 일치, stat summary의 선택 전후 bounds 동일성, 선택된 장착 item 해제 후 상세/선택/preview가 사라지는 UI 결과를 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. stat bounds와 네 action button bounds에 대한 실패 Compose 테스트를 먼저 작성한다.
2. label별 button width/height와 card 우측/하단 inset이 같은지 확인한다.
3. 선택 전, 공격력 증가, 최대 체력 감소, difference 0 상태에서 세 stat cell과 summary bounds가 같은지 확인한다.
4. 큰 font scale과 좁은 화면에서 reason text가 action button을 밀거나 덮지 않는지 확인한다.
5. 연결된 Android device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. 성공 시 task index의 step 8을 `completed`로 바꾸고 고정 layout과 action label 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- button text 길이에 따라 width를 `wrapContent`로 두지 마라. 이유: 상태별 크기와 위치가 다시 달라진다.
- 구매 불가 상세 사유를 button label에 넣지 마라. 이유: action label은 `구매 불가`로 고정해야 한다.
- difference가 0일 때 stat의 세로 공간을 제거하지 마라. 이유: item 선택 시 summary 높이가 바뀐다.
- 고정 크기를 위해 48dp 미만 touch target을 사용하지 마라. 이유: 접근성 기준을 지켜야 한다.
- 표시용 문장을 Kotlin에 하드코딩하지 마라. 이유: 사용자 노출 문구는 문자열 리소스를 사용해야 한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
