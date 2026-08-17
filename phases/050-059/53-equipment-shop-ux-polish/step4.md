# Step 4: polish-shop-card-preview

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/53-equipment-shop-ux-polish/step3.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

Compose test를 먼저 작성하고 상점 카드와 preview geometry를 다듬는다.

모든 `ShopEquipmentCard`에는 상세를 열기 전 `요구 레벨 N`을 항상 표시한다. `isRequiredLevelMet == false`이면 lock icon, `errorContainer`/`onErrorContainer` 계열과 `레벨 부족` 한국어 semantics를 사용하고, 충족이면 중립적인 `onSurfaceVariant` 표현을 사용한다. 이 표시는 현재 구매 요구 레벨을 설명할 뿐 Repository의 장착 policy를 새로 추가하지 않는다.

`isOwned` card는 전체 container를 `secondaryContainer`, border를 `2.dp` `secondary`로 바꾸고 체크 icon과 굵은 `보유 중` badge를 함께 표시한다. 미보유 card는 기존 `surface`와 `1.dp outline`을 유지한다. `장착 중` badge는 별도로 유지해 소유와 장착을 구분하고, TalkBack에서도 두 상태를 텍스트로 제공한다. 테스트는 badge/test tag뿐 아니라 theme 아래 owned/unowned card capture의 container 또는 border color가 실제로 다른지 검증한다.

`판매 중` badge는 카드와 상세에서 완전히 제거한다. `isForSale == false`인 예외만 `판매 중지` badge를 표시하며 구매 불가 사유는 유지한다.

preview의 모든 `EquipmentSlotItem`은 같은 layout mode 안에서 empty/equipped 여부와 무관하게 고정 높이를 사용한다. 일반 좌우 4/3 배치는 `96.dp`, 360dp 미만 또는 font scale 1.5 이상의 compact 배치는 `120.dp`다. slot명, 장비명(최대 두 줄), 등급과 장착/빈 상태를 고정 공간 안에 배치하고 전체 장비명과 상태는 semantics에 보존한다. 기존 avatar 크기, 일반 좌우 4/3 순서, compact canonical 순서와 최소 48dp target은 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. 요구 레벨·owned visual·판매 badge·slot height Compose test를 먼저 추가해 실패를 확인한 뒤 구현한다.
2. 일반 `96.dp`, compact/큰 글꼴 `120.dp`에서 empty/equipped slot 높이가 같고 내용이 잘리지 않는지 확인한다.
3. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
4. task index의 step 4를 `completed`로 바꾸고 카드 상태와 geometry 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `판매 중`을 모든 item에 계속 표시하지 마라. 이유: 동일 정보가 목록의 시각적 잡음이 된다.
- 보유 여부를 색으로만 표현하지 마라. 이유: 색각과 TalkBack 사용자가 상태를 확인할 수 없다.
- required level을 상세에만 남기지 마라. 이유: 사용자가 목록에서 제한을 비교할 수 있어야 한다.
- empty/equipped content에 따라 slot 높이를 intrinsic하게 바꾸지 마라. 이유: 장착 때 preview 영역이 흔들리는 문제를 재발시킨다.
- 기존 테스트를 깨뜨리지 마라.
