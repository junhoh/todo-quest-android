# Step 3: redesign-shop-compose-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/java/com/todoquest/feature/shop/ShopUiState.kt`
- `/app/src/main/java/com/todoquest/feature/shop/EquipmentArtwork.kt`
- `/app/src/main/java/com/todoquest/ui/theme/Color.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/58-shop-screen-redesign/step2.md`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

Compose UI test를 먼저 갱신하고 상점 화면을 `Scaffold`의 고정 `TopAppBar`와 단일 `LazyColumn`으로 재구성한다. outer app Scaffold가 bottom navigation과 safe drawing inset을 제공하므로 상점 Scaffold는 중복 system inset을 만들지 않고 자신의 top bar inner padding만 LazyColumn에 적용한다. 메인 콘텐츠에 중첩 세로 scroll을 만들지 않는다.

Composable을 `MerchantBanner`, `EquipmentPreviewCard`, `EquipmentSlot`, `StatSummary`, `CategoryFilterRow`, `ShopItemCard` 단위로 분리한다. domain enum과 이름이 충돌하면 import alias를 사용한다.

상단 앱바는 뒤로가기 없이 `상점` 제목, 4dp 간격의 gold icon/value를 합친 작은 chip, 48dp 인벤토리 icon button을 제공한다. gold chip은 `보유 골드 N`, 인벤토리는 기존 한국어 content description을 사용한다.

`MerchantBanner`의 일반 화면 높이는 약 88dp, NPC frame은 60dp로 두고 왼쪽 이미지와 오른쪽 `대장장이`/`필요한 장비를 골라 보게.`를 중앙 정렬한다. font scale 1.5 이상에서는 텍스트가 잘리지 않도록 intrinsic 높이를 허용한다. 상점 전용 warm low-emphasis surface token을 한 곳에 정의하되 전역 Material container 기본값을 바꿔 관련 없는 화면의 색을 변경하지 않는다.

`EquipmentPreviewCard` 안에 `장착 미리보기`, 중앙 캐릭터, 일곱 slot과 통합 stat bar를 둔다. 일반 폭 캐릭터는 정사각형 136~144dp, 작은 폭/큰 글꼴에서는 최소 120dp로 uniform fit하고 기존 `LayeredCharacterSprite`의 nearest-neighbor 렌더링을 유지한다. slot은 64~72dp 정사각형으로 왼쪽 `투구/상의/하의`, 오른쪽 `무기/장갑/액세서리`, 캐릭터 아래 `신발` 순서다. 장착 이미지를 우선하고 빈 slot은 흐린 `+`와 작은 부위명만 표시한다. rarity는 작은 점/outline, 선택 slot은 gold outline로 표시하며 전체가 48dp 이상 clickable이다. 레벨 badge는 작은 `Lv.N` 보조 요소로 둔다.

`StatSummary`는 공격력·최대 체력·방어력을 같은 너비 한 줄로 배치하고 민트 icon, 이름보다 큰 현재 값과 non-zero difference만 표시한다. 증가 delta는 secondary/mint, 감소는 error/warning을 사용하고 TalkBack은 현재값과 선택 시 변화를 함께 읽는다.

프리뷰 아래에 `판매 장비`와 현재 filter item count를 배치한다. `CategoryFilterRow`는 `LazyRow`로 `전체/무기/투구/상의/하의/장갑/신발/액세서리` 순서를 유지하고 시각 pill 약 40dp, 전체 touch target 48dp를 제공한다. 선택 chip은 채운 gold 계열, 미선택은 낮은 강조 outline을 사용한다.

`ShopItemCard`는 단일 열 가로 카드로 72~80dp artwork, 이름(최대 2줄), type/subtype·rarity·대표 modifier, gold icon/value와 action을 제공한다. 카드 탭은 `SelectEquipment`, 48dp 상세 icon은 `OpenEquipmentDetail`, 구매 버튼은 `RequestPurchaseConfirmation(id)`만 호출한다. 선택 카드는 얇은 gold outline을 사용한다. 구매 가능은 강조 `구매`, 부족 골드는 disabled `N 골드 부족`, 보유는 `보유 중`, 현재 장착은 `장착 중`, level/not-for-sale은 구체적 disabled 이유를 표시한다. 320dp와 font scale 2.0에서 가격/action은 content column 안에서 wrap되어 겹치지 않아야 한다.

기존 상세 bottom sheet, 구매 확인·성공, 바로 장착, 슬롯 관리·해제, 오류·재시도 dialog는 유지하고 새 event signature에 연결한다. 새 사용자 문구와 semantics는 모두 `strings.xml`에 둔다. 일반/선택/보유/강조 surface를 구분하되 gold는 잔액·선택·구매, mint는 능력치·긍정 변화에만 사용한다.

표준 상태와 `320x640`, font scale 2.0 상태의 Compose Preview를 갱신한다. UI test는 배너 80~96dp, 캐릭터와 slot 상대 크기, 일곱 slot 위치, 단일 LazyColumn 순서, stat delta 색/semantics, filter 48dp target, 카드 event 분리와 상태별 action을 검증한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.shop.ShopScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. 새 구조·geometry·이벤트·큰 글꼴 Compose test를 먼저 작성해 실패를 확인한 뒤 UI를 구현한다.
2. 표준 Preview와 small/large-font Preview에서 배너, 캐릭터 중심성, 첫 상품 도달성과 카드 action wrap을 시각 검토한다.
3. connected test에서 artwork 종횡비, 48dp target, 한국어 semantics와 기존 구매·장착 dialog를 확인한다.
4. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
5. 성공 시 task index의 step 3을 `completed`로 바꾸고 UI 구조·Preview·connected 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 메인 상점 콘텐츠에 `Column.verticalScroll`이나 두 번째 `LazyColumn`을 중첩하지 마라. 이유: inset과 scroll 위치가 불안정해진다.
- 모든 카드에 동일한 purple container를 사용하지 마라. 이유: 정보 우선순위가 사라진다.
- slot에 `비어 있음`을 큰 텍스트로 반복하지 마라. 이유: 캐릭터보다 빈 상태가 강조된다.
- 전역 theme container 색을 바꿔 관련 없는 화면을 재디자인하지 마라. 이유: 사용자 범위를 벗어난다.
- 표시용 문구를 Composable이나 ViewModel에 하드코딩하지 마라. 이유: AGENTS.md 한국어 리소스 규칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
