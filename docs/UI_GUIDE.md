# UI 디자인 가이드

## 디자인 원칙

1. 일정 관리는 생산성 도구처럼 빠르고 명확해야 한다.
2. RPG 요소는 완료 보상, 캐릭터 성장, 상점 영역에서 강하게 표현한다.
3. 픽셀 감성은 장식과 피드백에 사용하고, 본문 텍스트와 날짜 정보는 가독성을 우선한다.
4. 모든 핵심 조작은 한 손 사용과 반복 사용을 고려한다.

## 한국어 기본 언어 정책

- 앱의 사용자 노출 언어 기본값은 한국어다. `app/src/main/res/values/strings.xml`을 한국어 기본 문자열 리소스로 사용하며, Compose와 ViewModel에 표시용 영문 문장을 하드코딩하지 않는다.
- 영어 번역을 실제로 지원할 때만 `values-en`을 추가하며, 지원하지 않는 locale에서도 한국어가 적용되도록 `values/strings.xml`의 한국어 기본 리소스를 제거하지 않는다.
- 하단 navigation 표시명은 `캘린더`, `캐릭터`, `상점`, `도감`, `설정`으로 고정한다. `인벤토리`는 상점에서, `몬스터 도감`과 몬스터 상세는 도감에서 여는 nested 화면의 표시명이다.
- Character 화면의 영역명은 `캐릭터`, `성장`, `기본 능력치`, `파생 능력치`로 고정한다.
- Character 화면의 성장 표시 용어는 `체력`, `경험치`, `최대 레벨`, `골드`, `연속 달성`, `기세`, `미배분 능력치 포인트`로 고정한다.
- Character 화면의 기본 능력치는 `힘`, `활력`, `집중`, `의지`로, 파생 능력치는 `최대 체력`, `공격력`, `방어력`, `치명타 확률`, `치명타 피해`, `상태 이상 저항`, `체력 회복`, `골드 획득 보너스`로 고정한다.
- Calendar 화면의 사용자 노출 문구와 TalkBack 설명도 `app/src/main/res/values/strings.xml`의 한국어 기본 리소스를 사용한다.
- `Calendar 화면 전체`는 Add 버튼, 일정 목록, 편집·삭제·완료 UI, task editor, 보상 안내, 오류와 snackbar를 포함해 한국어화한다.
- Equipment Shop과 Inventory의 장비명·부위·구매 가능 여부·오류·구매 성공 action·장착 상태와 TalkBack 설명도 `app/src/main/res/values/strings.xml`의 한국어 기본 문자열 리소스를 사용한다.
- `Todo Quest` 브랜드명과 route, enum, test tag, resource key 같은 내부 식별자는 영문을 유지할 수 있다. 사용자 표시 언어와 코드·테스트 호환성 계약을 분리한다.

## 화면 구조

### 하단 navigation

- 단일 activity의 하단 `NavigationBar`는 `Calendar`, `Character`, `Shop`, `Compendium`, `Settings` 다섯 top-level destination을 이 순서로 제공한다. `Inventory`는 Shop의, Monster 목록과 상세는 Compendium의 nested destination이며 하단 항목을 추가하지 않는다.
- 앱 시작 destination은 `Calendar`이며, 선택된 탭은 시각적 상태와 TalkBack label을 함께 제공한다.
- 탭 전환은 각 destination의 navigation state를 저장·복원하고 같은 탭을 반복 선택해 back stack을 늘리지 않는다. Monster 목록의 기본 상세는 sheet이므로 back은 열린 sheet를 먼저 닫고 다음 back에서 도감 root로 이동한다. 호환 `compendium/monsters/{species}` 상세 route에서는 `도감` 탭을 선택 상태로 유지하며 back은 `호환 상세 → 몬스터 목록 → 도감 root` 순서다.

### 설정

- `설정`은 다섯 번째 top-level 화면이며 top-level back action을 두지 않는다. 현재 `효과음` Switch와 일반 `알림 권한` 관리 row를 제공하고 제목·상태·TalkBack 설명은 한국어 문자열 resource를 사용한다.
- `효과음`은 application-scope SharedPreferences의 key가 없을 때 켜짐이다. Compose는 preference나 `SoundPool`을 직접 호출하지 않고 `SettingsViewModel`의 state와 command만 사용한다.
- Switch를 끄면 이후 `BattleSfx` 재생만 억제한다. 전투 animation, damage text, HP bar, status text와 `전투 불능` 안내는 계속 표시되며 현재 진행 중이거나 과거인 transition을 설정 변경 때문에 다시 재생하지 않는다.
- Switch row 전체는 최소 48dp target과 켜짐·꺼짐 상태 semantics를 제공한다. 음향은 시각·텍스트 피드백을 대체하는 유일한 정보 수단이 아니며 시스템 media volume과 DND 결과를 우회하거나 보장하지 않는다.
- application-scope `SoundPool`은 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 여섯 raw WAV를 preload한다. load 전·background·released 요청은 버리고 앱 복귀 뒤 과거 소리를 지연 재생하지 않는다. 이 설정은 Room v15 schema나 migration source가 아니며 피해·보상·spawn·중상 수식을 바꾸지 않는다.
- `알림 권한` row는 `확인 중`, `허용됨`, `권한 필요`, `알림 채널 꺼짐`, `상태 확인 실패` 중 하나와 각각 `확인 중`, `알림 설정`, `권한 허용`, `알림 채널 설정`, `다시 시도` action을 표시한다. row 전체는 label·상태·action을 합친 한국어 semantics와 최소 48dp target을 제공한다.
- `SettingsViewModel`은 `ReminderScheduler`의 `POST_NOTIFICATIONS` capability만 조회한다. Compose는 ViewModel event가 지정한 Android 13 이상 runtime permission, package 앱 알림 설정 또는 `todo_task_reminders` 채널 설정 launcher를 열고 결과 복귀와 `ON_RESUME`에 최신 상태를 다시 요청한다. 동시에 들어온 조회는 최신 요청이 우선하고 취소를 `상태 확인 실패`로 바꾸지 않는다.
- Settings의 일반 알림 관리에는 exact-alarm permission·special access를 포함하지 않는다. exact alarm은 일정 reminder 설정 흐름에서만 설명하고 요청한다. 일반 알림 권한 거부·dismiss·조회 실패는 효과음, navigation, 일정·완료·보상·전투·구매·장착을 비활성화하지 않는다. Compose는 DAO, `AlarmManager`, `WorkManager` 또는 Android capability 구현을 직접 호출하지 않는다.

### Calendar 전체 순서와 Battle Map

- Android 13 이상에서 `POST_NOTIFICATIONS`가 필요하고 아직 system request를 시도하지 않은 사용자는 첫 Calendar 진입에서 한국어 알림 안내를 먼저 본 뒤 permission dialog를 한 번만 받는다. 거부하거나 보류한 뒤에는 Calendar 재진입·화면 재구성·앱 재시작으로 자동 재요청하지 않는다. 이 안내와 prompt는 Battle Map·Calendar scroll의 고정 배치나 일정 접근을 가리지 않는 일회성 event로 다룬다.
- Calendar Combat Feedback v1은 기존 화면 전체 단일 scroll 계약을 교체한다. 시스템 inset 다음에는 Battle Map을 고정하고 그 아래 Calendar content만 독립적으로 세로 스크롤한다. 앱명·선택 날짜·성장 요약을 표시하던 기존 상단 정보 Header와 대체 spacer는 만들지 않는다.
- 고정 Battle Map은 플레이어 진행 HUD, player와 단일 active monster, actor 상단의 player/monster HP bar 두 개, attack·hit·death·spawn·damage text effect 전체를 포함한다. Calendar scroll은 월 이동, 요일·날짜 grid, 선택 날짜, 완료/실패 요약, 추가 버튼, task 목록과 빈 안내를 포함한다. 고정 map이나 하단 navigation이 Calendar scroll viewport와 핵심 action을 가리면 안 된다.
- `CalendarViewModel`이 실제 `CombatRepository.observeCombat()`을 관찰해 일정 상태와 독립된 presentation state를 제공한다. 전투 state가 로딩 또는 실패여도 일정 조회·편집·완료와 occurrence 보상 성공은 계속 동작해야 한다.
- 운영 화면에는 backend의 단일 활성 몬스터 한 마리만 표시한다. Battle Map UI 컴포넌트와 sample·Preview는 빈 맵과 배치 변형을 검증하기 위해 0~4마리를 지원할 수 있지만, 다중 활성 몬스터 backend를 의미하지 않는다.
- 맵 렌더링은 뒤에서 앞으로 배경, 장식, 지면 그림자, player/monster, actor HP, transient combat effect, status, overlay를 독립 레이어로 관리한다. overlay 상단 중앙에는 map 안쪽 8dp 여백을 둔 플레이어 진행 HUD를 배치한다. 캐릭터·몬스터·HUD·HP·damage·text는 배경 PNG에 합성하지 않는다.
- actor 위치는 `0f..1f` 범위의 정규화 좌표를 사용하고 기준점은 발 위치의 `bottom-center`다. y가 클수록 화면 앞쪽이며 더 높은 z-order로 렌더링한다.
- 일반 화면의 맵 높이는 `availableWidth / 2.4f`를 기준으로 계산하고 기존 `190dp..320dp` 범위를 유지한다. Calendar가 사용할 수 있는 높이가 `520dp` 미만이면 Calendar scroll viewport를 확보하기 위해 `150dp..190dp` compact-height와 `48dp` actor base height를 사용한다.
- 기본 초원 배경은 `BattleMapTheme.backgroundResId`가 선택하는 교체 가능한 opaque PNG resource다. 다른 stage는 `drawable-nodpi/battle_map_*` resource를 추가해 theme으로 선택한다. resource decode가 실패하면 단색 면으로 대체하지 않고 Canvas로 초원 fallback을 그린다.
- 플레이어 진행 HUD의 한 Row는 왼쪽에 level을 두고 남는 공간 뒤 오른쪽 summary에 `골드 아이콘/값 → EXP current/required`를 둔다. 골드 group과 EXP group 사이는 정확히 `12dp`, 골드 아이콘과 값 사이는 정확히 `3dp`다. `PlayerProgressHud`의 EXP `Column`은 `width(IntrinsicSize.Max)`와 `widthIn(min = 104.dp)`를 함께 사용해 `max(104dp, intrinsic content width)`를 구현하고 `0/100`과 progress bar가 잘리지 않게 한다. EXP label/value Row는 progress bar와 같은 폭을 사용하고 `EXP` label의 왼쪽 edge를 bar 왼쪽 끝에, 현재/필요 값의 오른쪽 edge를 bar 오른쪽 끝에 맞추며 남는 공간은 두 요소 사이에 둔다. 기존 label/value의 고정 `3dp` 간격은 사용하지 않는다. bar는 `surfaceVariant` track과 1dp `outline`을 가져 `0/100`에서도 경계가 보인다. 반투명 `surface`와 `outline`, `onSurface`, `primary`, `secondary` Material theme 토큰을 사용하며 stage 전용 색상을 하드코딩하지 않는다. 로딩 중에는 임의 수치를 숨기고, 필요 XP가 0 이하이거나 현재 XP가 0 이하이면 진행률 0, 현재 XP가 필요 XP 이상이면 진행률 1로 제한한다. 이 정렬은 player/monster placement, map 높이, 한국어 TalkBack과 progress semantics를 변경하지 않는다.
- 진행 HUD 전체는 `레벨 N, 경험치 X/Y, 골드 G` 형식의 한국어 TalkBack 설명 하나를 제공하고 골드 아이콘은 decorative로 처리한다. actor HP와 transient combat effect는 진행 HUD를 침범하지 않는 별도 layer와 semantics로 제공한다.
- player/monster HP bar는 공통 actor placement geometry에서 sprite top과 center를 계산해 actor 상단에 배치하고 map 경계와 HUD reserved area 안으로 clamp한다. bar가 actor를 침범하면 actor top도 bar 아래로 보정하며 독립적인 화면 절대 좌표나 sprite bitmap 내부 픽셀로 합성하지 않는다. 현재/최대 HP 숫자는 `11sp`·semi-bold, progress bar는 `5dp`로 표시하고 `surface` 94% 배경과 1dp `outline`을 사용해 초원 배경과 분리한다. player fill은 `secondary`, monster 및 저체력 fill은 `error`를 사용한다. source HP 변경은 `220ms` 동안 목표값으로 보간하고, 현재 HP가 최대 HP의 `25%` 이하이면 한국어 `체력이 낮습니다` 설명과 polite live region도 함께 제공한다.
- attack·hit·death·spawn·damage text는 새 combat event의 최초 적용에서만 표시하는 replay 없는 transition이다. `RoomCombatRepository.events(replay = 0)`와 ViewModel-scope `BattleAnimationController`의 단일 buffered actor가 animation과 효과음의 공통 순서를 소유하며, Composable은 HP Flow나 체력값 변화에서 음향을 추론하지 않는다. player는 `PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시 → 치명 시 MONSTER_DEFEATED → MONSTER_DYING`, monster는 `MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시 → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능` 순서다. defeat 음은 hit 음 뒤 death animation 시작에 재생한다. 공격 phase는 22dp icon과 `16sp` bold `공격!`, 피격 phase는 22dp icon과 `18sp` bold damage 값을 96% `surface`·1dp accent badge에 표시한다. player 공격 accent는 `secondary`, monster 공격과 damage accent는 `error`다. actor는 한 transition을 끝낸 뒤 다음 transition을 시작하고 queue가 빌 때까지 occurrence action을 잠근다. Calendar Combat Feedback v1 당시에는 Compose Material icon과 translation·shake·flash·alpha만 사용하고 새 bitmap·sound asset을 요구하지 않았으며, Battle Sound Effects v1은 pixel bitmap을 바꾸지 않고 여섯 raw WAV만 후속으로 추가한다. animation state와 `BattleSfx` 순서는 screenshot이 아니라 controller virtual-time test로 검증하며, animation 또는 효과음이 비활성인 환경에서도 HP 숫자·bar와 task 결과는 남아야 한다.
- 한 공격의 시작·피격·치명 effect는 같은 안정적 combat event id를 공유할 수 있고 재생 identity는 `eventId + BattleSfx`다. `PLAYER_DEFEATED` 음은 실제 `CombatLifecycleEvent.PlayerDefeated`가 있을 때만 허용한다. 중상 적용·갱신, 응급 회복, 상태 제거와 다음 monster spawn에는 death 또는 revive 음을 연결하지 않는다. persisted `APPLIED` 재처리, 화면 회전·재구성·재진입과 다음 monster 생성은 과거 효과음을 재생하지 않는다.
- 활성 중상은 Battle Map의 player HP bar와 actor 사이에 독립 배지로 표시한다. 중상 status layout은 전달받은 player sprite의 `left`, `top`, `width`, `height`를 그대로 반환하고 HP bar와 badge만 HUD·map 경계 안에서 clamp하거나 축소하므로, 일반·compact map 모두 중상 적용 전후 actor 좌표가 바뀌지 않는다. `중상`, `최대 체력과 공격력 20퍼센트 감소`, `회복까지 할 일 N개`를 하나의 한국어 TalkBack 설명으로 합치고 최소 48dp 버튼으로 상세 dialog를 연다. 상세에는 효과와 `할 일 N개 또는 H시간` 회복 조건을 숫자와 텍스트로 제공한다.
- Character 화면은 캐릭터 정보 영역에 같은 중상 배지와 상세 dialog를 표시하고 ViewModel이 선택 상태를 소유한다. 화면 회전·탭 재진입·process 재시작 뒤에는 Room의 활성 상태만 복원하며 전투 불능·중상 적용·응급 회복 연출은 다시 재생하지 않는다. `AppClock` 만료 또는 세 번째 서로 다른 occurrence 완료로 제거되면 `STATUS_EFFECT_REMOVING` 안내를 한 번만 표시하고 current HP는 자동 증가시키지 않는다.
- 현재 버전 player attack의 보상은 Battle Map 중앙의 고대비 badge로 `600ms` 표시한다. 비치명 공격은 `+N EXP`, 처치는 hit와 kill bonus를 합친 `+N EXP · +G 골드`를 표시하고 polite live region을 제공한다. badge는 animation phase나 input lock 시간을 늘리지 않고 `sequenceId`당 한 번만 나타나며 legacy attack, monster attack, 재구독과 이미 적용된 event 조회에서는 재생하지 않는다.
- 사용자에게 의미가 있는 player와 monster에는 한국어 TalkBack 설명을 제공하고 배경·장식·지면 그림자는 decorative로 처리한다. 공격·피격 damage·사망·등장과 전투 불능·중상 적용/갱신·응급 회복·해제 announcement는 한국어 content description과 polite live region으로 제공하므로 HP·damage·상태 변화를 색만으로 구분하지 않는다.
- Calendar Combat Feedback v1의 HP bar, transient effect, 실패·실패 취소 UI와 고정 map·독립 scroll을 구현했다. 몬스터 이름·상태 효과·Stage HUD는 승인하지 않으며 표시하지 않는다.

### 월간 캘린더

- 월간 캘린더는 고정 Battle Map 아래 독립 Calendar scroll의 첫 콘텐츠다. 그 아래 일정 목록 Header에서 선택 날짜와 퀘스트 완료·실패 개수를 함께 보여준다.
- 월 제목 형식은 `2026년 7월`처럼 `년`과 `월`을 포함하고, 요일 머리글은 일요일부터 `일`, `월`, `화`, `수`, `목`, `금`, `토` 순서로 고정한다. 날짜 cell의 leading offset도 같은 Sunday-first 기준을 사용해 일요일을 첫 번째 열에 맞춘다.
- 월 제목 양옆의 `이전 달`, `다음 달` 버튼으로 한 달씩 이동한다.
- 이동한 달의 선택 일자는 가능한 한 기존 일자를 유지하고, 해당 일자가 없으면 이동 대상 월의 말일로 맞춘다.
- 직접 년·월 선택기와 오늘로 이동 버튼은 이번 범위에 포함하지 않는다.
- 날짜 셀은 일정 개수, 완료 상태, 보상 가능 여부를 작은 마커로 표현한다.
- 날짜 숫자는 픽셀 폰트보다 시스템 폰트를 우선한다.
- 오늘, 선택 날짜, 완료한 날짜는 색과 형태를 함께 사용해 구분한다.
- 월 이동과 한국어화는 앱 내부 로컬 캘린더의 표시 계약만 확장하며, occurrence 단위 완료·보상 멱등성과 반복 일정 원본·날짜별 발생분 분리 규칙은 변경하지 않는다.

### 일간 퀘스트 목록

- 선택 날짜의 할 일을 시간순으로 표시한다.
- 각 occurrence는 `TODO`, `COMPLETED`, `FAILED` 중 하나만 표시한다. 각 항목은 제목, 시간, 반복 여부, 난이도, 현재 상태와 예상 또는 확정 보상을 보여준다.
- reminder가 `설정 없음`이 아닐 때만 mode와 occurrence 기준 실제 local 발화 시각을 `10분 전 · 당일 08:50`, `1시간 전 · 전날 23:30`, `직접 설정 · 당일 07:05` 형식의 독립 한 줄로 표시한다. 완료·실패 occurrence에도 원본 설정은 보이되 `예약됨`이라고 오인시키지 않는다.
- runtime permission·앱 전체 notification switch, `todo_task_reminders` 알림 채널 또는 exact-alarm capability가 막힌 task는 typed 상태에 맞는 한국어 안내와 앱 알림 설정·채널 설정·exact 설정 복구 CTA를 제공한다. CTA는 해당 task를 settings 복귀 뒤 다시 reconcile하고 일정 저장을 반복하지 않는다.
- 완료 버튼은 명확한 체크 액션이어야 하며 작은 장식 아이콘으로 숨기지 않는다.
- `TODO`의 `완료`·`실패`는 Row 오른쪽의 content-sized 시각 버튼으로 배치한다. 텍스트와 아이콘을 함께 유지하고 시각 container를 compact하게 줄여도 실제·semantics 터치 영역은 최소 `48dp`여야 하며 큰 글꼴과 작은 폭에서 잘리거나 겹치면 안 된다.
- `TODO`에는 완료와 수동 실패 action을 제공하고 `FAILED`에는 사용자가 선택할 수 있는 실패 취소 action을 제공한다. 실패 취소는 task를 `TODO`로 되돌릴 뿐 이미 받은 monster damage와 combat event는 되돌리지 않는다는 한국어 안내를 제공한다.
- 실패 취소 뒤 늦은 완료는 기존 보상과 player attack 흐름을 사용한다. 같은 occurrence를 다시 실패로 선택했을 때 기존 monster event가 있으면 두 번째 damage나 animation이 없다는 결과를 UI state와 일치시킨다.
- 수동 실패 상태 저장에 성공했지만 즉시 combat 처리만 실패한 경우 task는 `FAILED`로 유지하고 reconciliation 대상임을 오류로 과장하지 않는다. 알림·exact alarm 권한이 없어도 실패 취소·완료 action은 계속 사용할 수 있어야 한다.
- 완료 전에는 `완료하면 몬스터를 공격합니다`와 `공격 적중 시 경험치 · 처치 시 추가 경험치와 골드`를 표시한다. 쉬움 `100%`, 보통 `150%`, 어려움 `200%` 난이도가 새 공격의 피해와 hit/kill XP를 높인다는 한국어 안내를 제공하되 kill gold 증가를 암시하거나 직접 XP·골드 예상치로 표시하지 않는다.
- 새 `COMBAT_ATTACK` 완료는 Calendar 보상 snackbar를 만들지 않는다. 실제 보상은 player attack transaction이 확정한 뒤 Battle Map badge와 Character Flow 기반 HUD로만 보여준다. 과거 `TODO_COMPLETION` 결과의 양수 보상에 대해서만 기존 snackbar 호환 동작을 유지한다.

### 캐릭터 패널

- 별도 `Character` 화면은 세로 scroll 안에서 `Character`, `Growth`, `Base stats`, `Derived stats` 순서로 읽힌다. gameplay 장착 장비가 적용되면 Derived stats는 `character_equipment`의 실제 장착 modifier까지 포함한 결과를 표시한다.
- 캐릭터, level 또는 `MAX`, current/max HP, 보유 gold, 누적 XP와 다음 level 진행률을 표시한다. level 50 이후에도 누적 XP 숫자는 유지한다.
- Growth에는 정시 occurrence 날짜 기반 streak와 `MOMENTUM` 백분율을 표시한다.
- Base stats는 `Strength`, `Vitality`, `Focus`, `Willpower` 네 항목이다. section title 바로 다음에 저장 전 draft를 뺀 `미배분 능력치 포인트 N`을 표시하고, 각 stat row는 이름 영역과 고정 폭의 `- / 값 / +` control cluster로 배치한다. `(+N 저장 전)`의 유무와 길이는 cluster의 좌우 경계나 두 버튼 위치를 바꾸지 않는다. `+`는 남은 포인트가 없거나 예상값이 60이면 비활성화하고, `-`는 해당 stat에 저장 전 증가량이 있을 때만 활성화하며 두 버튼 모두 48dp 터치 대상을 유지한다.
- `-/+`는 ViewModel의 비영속 draft만 바꾼다. pending stat은 예상값과 `(+N 저장 전)`을 함께 표시하고 TalkBack은 `확정 X, 저장 전 +N, 예상 Y`를 읽는다. `능력치 배분 저장`은 네 stat의 pending 증가량을 한 번에 저장하며, 저장 중에는 `-/+`, 중복 저장과 stat reset을 비활성화한다. 저장 검증이나 쓰기가 실패하면 pending 표시를 유지해 조정·재시도할 수 있게 한다.
- stat reset은 저장 전 배분이 없을 때만 확인 dialog를 연다. 첫 성공은 무료이며 이후에는 level 기반 gold 비용을 표시한다. 배분점이 없거나 gold가 부족하면 버튼과 설명으로 이유를 알린다.
- Derived stats는 `Max HP`, `Attack`, `Defense`, `Critical chance`, `Critical damage`, `Status resistance`, `HP recovery`, `Gold gain bonus`를 숫자로 표시한다. 저장 전 draft는 기본 능력치 예상값까지만 보여주며 current/max HP와 Derived stats를 preview하지 않는다. 저장 transaction이 성공한 뒤 Room Flow에서 받은 확정 HP·파생값으로 갱신한다.
- 기본 능력치 이름과 파생 능력치 행을 누르면 ViewModel이 선택 상태를 소유하는 한국어 설명 dialog를 연다. 기본 능력치는 힘의 공격력, 활력의 최대 체력·방어력, 집중의 치명타, 의지의 저항·회복 기여를 설명하며, 파생 능력치는 각 전투 수치와 처치 골드 보너스의 의미를 설명한다. 기본 능력치의 `-`와 `+`는 설명 열기와 독립된 action이다.
- 신규 설치에서 첫 Character 진입 시 Character source state가 로드되면 `능력치 배분 안내` Material Dialog를 자동 표시한다. 업데이트 전에 `todo-quest.db`가 이미 존재한 사용자는 자동 표시하지 않지만, 모든 사용자는 `기본 능력치` 제목 오른쪽의 최소 48dp `능력치 배분 안내 열기` 도움말로 같은 Dialog를 재열람할 수 있다.
- Dialog는 `96dp` frame에 전체 `64×64` 안내 요정 canvas를 `ContentScale.Fit`·`FilterQuality.None`으로 표시하고 bitmap은 decorative로 처리한다. decode 실패는 같은 frame의 decorative `Info` icon으로 대체하며 안내 문구와 action은 유지한다.
- Dialog 제목은 `능력치 배분 안내`, 화자는 `안내 요정`이다. 본문은 `모험가님, 레벨이 오르면 능력치 포인트를 얻어요.`, `힘은 공격력을, 활력은 최대 체력과 방어력을, 집중은 치명타를, 의지는 상태 이상 저항과 회복을 높여 줘요.`, `원하는 능력치의 + 버튼을 누른 뒤 ‘능력치 배분 저장’을 눌러야 적용돼요. 능력치 이름을 누르면 자세한 효과도 확인할 수 있어요.`를 이 순서로 표시하고, 현재 미배분 포인트가 0인지 양수인지에 맞는 확정 한국어 상태 문장을 덧붙인다.
- primary `능력치 보러 가기`는 자동 안내를 확인 처리한 뒤 기존 Character scroll의 `기본 능력치` 영역을 viewport로 가져온다. secondary `닫기`, back과 outside dismiss도 자동 안내를 확인 처리한다. 수동 도움말의 닫기는 automatic acknowledged 상태를 변경하지 않는다.
- 본문은 제한 높이 안에서 action과 독립적으로 세로 scroll되며 primary·secondary action은 각각 최소 48dp다. `320dp×640dp`, font scale `2.0`에서도 요정·본문을 읽고 두 action에 도달할 수 있어야 한다. TalkBack은 화자, 세 안내 문장과 포인트 상태를 하나의 한국어 설명으로 읽고 도움말 icon은 `능력치 배분 안내 열기`로 제공한다.
- bp 값은 원시 정수가 아니라 half-up으로 반올림한 소수점 한 자리 `%`로 표시한다.
- 픽셀 캐릭터 또는 도트풍 일러스트는 이 영역에 집중한다.
- 성장 정보는 항상 숫자로도 제공한다.
- 구현된 성장·스탯 표시와 전투 수치 계약은 [스탯 설계 인덱스](game-design/character-stats-design.md)를 따른다. 실제 gameplay 장착 modifier가 반영된 Character 파생 능력치와 HP는 Shop·Inventory의 장착 transaction 결과를 Room Flow로 관찰해 갱신한다.

#### 메인 캐릭터 에셋과 합성

- 캐릭터 자산의 역할과 검증 계약은 [캐릭터 아트 인덱스](art/character/README.md)에서 찾는다.
- 메인 캐릭터의 canonical 계약은 [모듈형 시트 명세](art/character/character-modular-sheet-spec.json)의 schema v6다. 기존 `body_base`와 hair·hands·default/adventure 의상·front 장식·3분할 sword 및 gameplay 무기 layer는 모두 같은 `64×64px` 원점을 사용한다.
- [모듈형 캐릭터 시트](art/character/todo-quest-character-modular-sheet.png)는 `512×128px`, 8열×2행의 deterministic debug·golden 결과이며 런타임 source가 아니다. 앱은 `app/src/main/assets/character/layers/`의 canonical source와 byte-identical한 PNG만 읽고 generated sheet의 타일을 자르지 않는다.
- 캐릭터의 논리 중심축은 `x=32`, 발바닥 기준선은 `y=58`이다. 장비를 바꾸더라도 베이스 신체의 위치, 크기와 정면 자세를 변경하지 않는다.
- 현재 빈 gameplay loadout은 기존 body·hair·기본 손을 유지하고 `top_default`·`bottom_default`·`shoes_default`의 회갈색 중립 훈련복을 사용한다. 투구·장갑·장신구·무기 overlay는 비어 있다. 파란 모자·재킷·바지·장갑·신발·장식·검의 adventure 외형은 고정 ID `1019..1025`의 7부위 상점 상품 layer이며 빈 slot fallback이 아니다.
- 런타임 z-order는 뒤에서 앞으로 `accessory_back → hair_back → headgear_back → body_base → shoes → bottom → top → hands_front → face_overlay → hair_front → headgear_front → accessory_front → weapon_back → weapon_held → weapon_front`다. 현재 `accessory_back`, `headgear_back`, `face_overlay` slot은 비어 있다. schema v6는 기본 3분할 검과 gameplay 단일 `weapon_front`를 모두 최상단 weapon group에 두어 손·머리·투구·액세서리가 무기를 가리지 않게 한다. `gloves_adventure`를 포함한 gameplay 장갑은 `hands_front`, gameplay 신발은 `shoes` source를 교체한다.
- 장비는 베이스 신체를 직접 덮는 source-over 레이어다. 상의와 하의는 허리 `y=41~43`, 하의와 신발은 발목 `y=53~54`에서 의도적으로 겹치며, 이 영역을 투명한 경계로 분리하거나 각 레이어에 이중 외곽선을 만들지 않는다.
- `CharacterBitmapComposer`는 asset path별 decode cache와 render-state별 composite LRU cache를 사용한다. 모든 source를 `inScaled=false`, ARGB_8888로 decode해 `(0, 0)`에서 source-over 합성하고 immutable `64×64` bitmap을 반환하며, decode 실패나 잘못된 크기는 일정 기능을 중단시키지 않고 빈 렌더 결과로 격리한다.
- Character 화면과 Calendar Battle Map player는 같은 `CharacterRenderState`와 `LayeredCharacterSprite`를 사용한다. 완성된 composite를 Canvas available size에 한 번만 scale하고 가능한 가장 큰 정수 배율과 `FilterQuality.None`을 우선하며, 작은 target은 uniform fit으로 경계를 유지한다.
- 현재 `character_appearance`와 `character_equipped_items`의 appearance/loadout command와 shared renderer는 유지한다. Room v15가 보존하는 nullable `glovesId`를 포함한 이 상태는 gameplay ownership이 아니라 appearance fallback이며, Inventory의 소유·장착 UI와 능력치 source는 `owned_equipment`·`character_equipment`에서 분리한다. `MIGRATION_14_15`는 이 fallback만 빈 loadout으로 갱신하고 실제 소유·장착은 보존한다. gameplay equipment에 대응하는 layer가 없거나 mapping할 수 없으면 기존 appearance를 계속 렌더링하고 구매·장착·일정 기능을 실패 처리하지 않는다.

### 몬스터 에셋과 표시

- Monster Combat v1 backend의 단일 활성 몬스터는 Calendar Battle Map에 표시한다. Calendar Combat Feedback v1은 player/monster HP bar와 attack·hit·death·spawn·damage text effect를 이 map의 독립 layer로 구현했다. 몬스터 이름은 종족별 한국어 문자열 resource로 표시하지만 상태 효과·Stage HUD와 별도 전투 화면은 계속 후속 범위다. `MonsterDefinition.nameKey`는 사용자 표시 문자열이 아닌 backend key이므로 직접 노출하지 않는다.
- 몬스터 자산의 역할과 검증 계약은 [몬스터 아트 인덱스](art/monster/README.md)에서 찾는다.
- 현재 canonical 에셋은 [고블린 정찰병](art/monster/todo-quest-goblin-scout-front-idle.png), [해골 병사](art/monster/todo-quest-skeleton-soldier-front-idle.png), [타락한 나무 정령](art/monster/todo-quest-corrupted-tree-spirit-front-idle.png), [하피](art/monster/todo-quest-harpy-front-idle.png), [슬라임](art/monster/todo-quest-slime-front-idle.png)의 정면 대기 스프라이트다. 정확한 계약은 각각 [고블린 명세](art/monster/todo-quest-goblin-scout-front-idle-spec.json), [해골 명세](art/monster/todo-quest-skeleton-soldier-front-idle-spec.json), [타락한 나무 정령 명세](art/monster/todo-quest-corrupted-tree-spirit-front-idle-spec.json), [하피 명세](art/monster/todo-quest-harpy-front-idle-spec.json), [슬라임 명세](art/monster/todo-quest-slime-front-idle-spec.json)를 따른다.
- 다섯 스프라이트는 `64×64px` 투명 RGBA PNG다. 고블린·해골은 양 끝 포함 불투명 영역 `[16, 13, 48, 58]`과 불투명 높이 `46px`을 공유하고, 타락한 나무 정령은 `[14, 11, 50, 58]`, 하피는 `[11, 11, 53, 58]`과 불투명 높이 `48px`, 슬라임은 `[17, 35, 47, 58]`과 불투명 높이 `24px`을 사용한다. 슬라임의 몸·눈·내부 핵·표면 하이라이트는 명세의 정확히 12색 팔레트 안에서 표현한다. 다섯 종족 모두 논리 중심축 `x=32`, 발바닥 기준선 `y=58`과 runtime ground anchor `(0.5, 58/64)`를 유지하며 UI에서 다시 크롭하거나 개별 불투명 영역을 기준으로 가운데 정렬하지 않는다. 하피의 양 끝 포함 왼쪽 날개 `[11, 26, 24, 47]`와 오른쪽 날개 `[40, 26, 53, 47]`는 `x=32` 축을 기준으로 RGBA 픽셀이 정확히 대칭이다.
- 공통 외곽선 색상은 `#263B5A`이고 최종 에셋은 완전 투명 또는 완전 불투명 픽셀만 사용한다. 화면 확대·축소는 최근접 이웃 보간과 정수 배율을 우선하며 런타임 색상 보간, 안티앨리어싱과 임의 재색칠을 적용하지 않는다. 선택 표시, 바닥 그림자와 상태 효과가 필요하면 원본 PNG를 수정하지 말고 별도의 UI 레이어로 합성한다.
- `MonsterSpeciesPolicy`는 enum ordinal이 아닌 명시적 다섯 종족 목록과 저장된 `stageNumber + encounterNumber + grade + balanceVersion`으로 결정적 스케줄을 만든다. NORMAL Stage의 8개 encounter에는 모든 종족이 최소 한 번, 최대 두 번 나타나고 ELITE·BOSS Stage는 한 종족을 결정적으로 선택한다. `MonsterType`은 능력치 definition을 고르는 별도 입력이며 종족 스케줄에는 참여하지 않는다. 이 presentation 규칙은 능력치·피해·보상, Room v14 schema 또는 monster balance version 값을 바꾸지 않는다.
- 고블린은 뾰족한 귀·단검·붉은 눈, 해골 병사는 큰 해골·낡은 투구·검·붉은 눈, 타락한 나무 정령은 왕관형 가지·뿌리발·붉은 눈빛·부패 균열·검은 수액·시든 잎, 하피는 좌우 대칭 날개·짧은 다리·발톱·붉은 눈, 슬라임은 낮은 물방울형 몸·붉은 눈·내부 핵·표면 하이라이트를 함께 사용해 적대성과 종족을 형태로도 구분한다. 해골의 `undead`·`dark`, 나무 정령의 `forest`·`dark`·`corruption`, 하피의 `wind`·`flight`·`mountain`·`grassland`, 슬라임의 `water`·`slime`은 시각 콘셉트와 종족 식별 metadata일 뿐 비행 동작, biome, 상성, 상태 효과, 스킬, 전리품 또는 새 맵 배경을 부여하지 않는다.
- 몬스터가 퀘스트 진행이나 전투 상태를 전달하면 `BattleMonsterVisualCatalog`가 고른 슬라임을 포함한 한국어 종족 이름과 종족별 쓰러짐 안내를 TalkBack 설명에 포함하고, 순수 장식이면 decorative로 처리한다.

### 도감

- `도감`은 네 번째 top-level 화면이다. root에는 현재 동작하는 `몬스터` category 카드 하나만 표시하고, 선택하면 몬스터 목록으로 이동한다. 펫·맵은 향후 같은 category·목록·상세 구조로 확장할 수 있지만 현재 category, route, 빈 화면 또는 placeholder를 표시하지 않는다.
- 몬스터 목록은 고정 `TopAppBar`와 그 아래 하나의 `LazyVerticalGrid`로 구성한다. grid는 수집 현황, filter, 선택 preview와 다섯 수집 card를 함께 스크롤하고 바깥 `Scaffold`의 고정·불투명 bottom navigation 및 system inset을 침범하지 않는다. `320dp`와 일반 phone 폭은 3열, 넓은 폭은 카드 최소 밀도에 따라 4~5열을 사용하며 마지막 card 전체에 scroll로 도달할 수 있어야 한다.
- 수집 현황은 `발견 수 / 전체 수`, 정수 수집률과 progress를 같은 source에서 표시하고 하나의 한국어 TalkBack 설명으로 합친다. 앱바 검색 action은 발견한 몬스터의 resource 기반 한국어 이름만 대상으로 하며, trim과 대소문자 무시를 적용한다. filter는 `전체 → 발견 → 미발견` 순서이고 검색과 동시에 적용한다. 결과 없음에는 조건 초기화 action을 제공한다.
- 선택 preview는 첫 발견 종을 기본으로 하고 발견 card 선택에 따라 갱신한다. 발견 종이 하나도 없으면 실제 종족 단서가 없는 `미발견 몬스터`, `?`와 잠금 표시를 사용한다. 발견 preview는 실제 이름·sprite·발견 완료 상태를 표시하며 최소 48dp action으로 공용 상세 `ModalBottomSheet`를 연다. sheet dismiss와 system back은 목록 route·선택·scroll 문맥을 유지한 채 sheet를 먼저 닫는다.
- 발견 card는 공용 `MonsterVisualCatalog`의 검증된 `64×64` PNG와 한국어 이름을 표시하고 선택 상태를 색뿐 아니라 semantics와 2dp outline으로 구분한다. sprite는 전체 canvas의 원래 1:1 aspect ratio를 유지한 `ContentScale.Fit`, `FilterQuality.None`을 사용해 crop·보간 없이 최근접 pixel 경계를 보존한다. 새 bitmap을 만들거나 canonical PNG를 수정하지 않는다.
- 미발견 card는 실제 이름·sprite·silhouette·설명 resource 대신 공통 `???`, `?`와 잠금 icon만 표시하고 TalkBack은 종족 단서 없는 `미발견 몬스터`로 읽는다. card는 최소 48dp click target을 가지지만 선택이나 route 이동 대신 replay 없는 `아직 발견하지 못한 몬스터입니다` 안내만 표시한다. 미발견 이름은 검색 결과나 semantics에도 노출하지 않는다.
- 발견 상세 sheet는 한국어 종족 이름, 최근접 sprite, 발견 완료와 외형 설명을 세로 scroll로 제공하고 48dp 닫기 action을 갖는다. 기존 `compendium/monsters/{species}` route는 호환용으로 같은 발견 본문을 사용한다. 미발견 직접 route는 실제 이름·sprite·설명 resource가 없는 일반 잠금 제목과 안내만 제공하며 알 수 없는 argument는 목록으로 복구한다.
- 검색·filter·선택·sheet 상태는 `MonsterCompendiumViewModel`이 소유하는 비영속 presentation state이고 Compose는 typed event만 전달한다. 목록·상세의 loading, 오류·재시도와 미발견 안내는 한국어 문자열 resource를 사용한다. 발견 projection 실패는 도감 안에 격리하며 Calendar 일정·보상·전투 transaction을 차단하거나 롤백하지 않는다.
- grade·type·region을 도감 전용 종족 metadata로 추가하지 않는다. 종족별 고정 능력치, biome, 스킬, 처치 횟수·전리품과 펫 재화·장착·보너스·농장·맵, 새 몬스터·새 art는 표시하거나 암시하지 않는다.

### 상점과 인벤토리

- MVP 상점 아이템은 기존 계약대로 외형 또는 수집 요소이며 능력치 효과를 암시하지 않는다. 아래 gameplay 장비 UI는 별도로 승인된 Post-MVP Equipment Shop and Inventory v1 범위다.
- Shop은 세 번째 top-level destination으로 장비 목록, 현재 골드와 Inventory 진입 action을 제공한다. Shop app bar에는 back action을 두지 않으며, nested Inventory만 시스템 back으로 Shop에 돌아간다. 다섯 하단 tab은 화면 scroll과 독립된 불투명 navigation surface에 고정하고 system navigation inset까지 앱 배경으로 덮어 이전 destination이 비치지 않게 한다.
- Shop 상단의 골드는 아이콘과 값 사이를 정확히 `4dp`로 인접 배치하고 두 요소를 `보유 골드 N`의 한 TalkBack 설명으로 합친다. Inventory action은 별도 48dp icon button으로 두며 작은 폭에서도 골드 값과 겹치지 않는다.
- Shop의 고정 app bar 다음 콘텐츠는 하나의 `LazyColumn` 안에서 `압축 대장장이 배너 → 장착 미리보기·최종 능력치 통합 카드 → 판매 장비 제목 → category filter → 판매 목록` 순서로 둔다. 마지막 판매 card 전체가 고정 하단 navigation 위에 표시될 수 있도록 content bottom padding과 root inset을 유지한다. 구매·장착·해제 dialog는 이 순서를 바꾸지 않는다.
- 대장장이 배너는 정적이고 비대화형이다. 최소 높이 `88dp` 안에 canonical `64×64` logical canvas를 `60dp` frame으로 문구 왼쪽에 배치하고, `ContentScale.Fit`·`FilterQuality.None`으로 렌더링한다. 호칭은 `대장장이`, 인사는 `필요한 장비를 골라 보게.`, 합쳐진 TalkBack 설명은 `장비 상점 대장장이. 필요한 장비를 골라 보게.`다. decode 실패는 같은 frame 안의 `Build` Material icon으로 격리하며 click·select action이나 대화 route, 장비 command side effect를 만들지 않는다.
- 장착 미리보기와 최종 능력치는 하나의 surface를 공유한다. Character와 Calendar가 쓰는 같은 `LayeredCharacterSprite`를 중앙에 두고, 판매 상품을 선택하지 않은 상태에는 실제 gameplay 장착과 검증된 render loadout을 표시한다. 상품 card 본문을 선택하면 같은 slot의 장비 modifier와 유효한 `layerKey`만 비영속으로 교체한 구매 전 외형·능력치 projection을 표시한다. type/slot 정의가 잘못된 후보는 외형·능력치 모두 현재 상태로 격리하고, layer가 없거나 catalog 검증에 실패한 후보는 능력치 projection과 독립적으로 외형만 현재 loadout을 유지한다. 이 선택은 Room, 골드, 소유권, `character_equipment`, current HP를 변경하지 않는다.
- gameplay 장비 type과 slot은 각각 `WEAPON`(무기), `HELMET`(투구), `CHEST`(상의), `LEGS`(하의), `GLOVES`(장갑), `SHOES`(신발), `ACCESSORY`(장신구) 일곱 값만 표시한다. 무기는 같은 `WEAPON` slot 안에서 `LONGSWORD`(장검), `DAGGER`(단검), `SPEAR`(창), `BLUNT`(둔기) subtype을 `무기 · 장검` 형식으로 표시한다. `CHEST`와 `LEGS`는 동시에 장착할 수 있고 한 장비를 장착할 때 대상 slot만 교체한다.
- 프리뷰는 왼쪽에 `HELMET → CHEST → LEGS`, 중앙에 캐릭터와 그 아래 `SHOES`, 오른쪽에 `WEAPON → GLOVES → ACCESSORY`를 배치한다. 일반 상태는 캐릭터 `144dp`와 slot `68dp`, 사용 가능 폭이 `340dp` 미만이거나 font scale이 `1.5` 이상인 compact 상태는 캐릭터 `120dp`와 slot `64dp`를 사용한다. 일곱 slot은 모두 최소 `48dp` 선택 target을 유지한다.
- 각 slot은 장착 장비명·등급·`장착 중` 또는 `비어 있음`을 텍스트와 하나로 합친 한국어 TalkBack 설명으로 제공하고 선택 상태를 semantics와 테두리로 함께 표시한다. slot을 선택하면 부위 상태, `이 부위 장비 보기`, 장착 중일 때의 `해제`, `닫기`를 제공하는 관리 dialog를 연다. 모든 action은 최소 `48dp`이고 해제 처리 중에는 dialog dismiss와 다른 Shop command를 잠근다. 장비 보기는 같은 type category와 판매 목록 filter를 선택하며 `CHEST`와 `LEGS`는 서로의 선택이나 장착 상태를 바꾸지 않는다.
- 통합 카드 하단의 최종 능력치는 `공격력 → 최대 체력 → 방어력` 세 값을 동일 폭 cell로 표시한다. 각 cell은 label·현재값·delta 영역을 항상 예약하고 일반 글꼴에서 `112dp`, 큰 글꼴에서 `170dp`의 고정 높이를 사용하며, 차이가 0이면 delta 자리만 spacer로 남긴다. 판매 상품 선택 중에는 구매 전 projection과 현재 수치의 signed delta를 함께 표시하지만 이는 gameplay source가 아니다. 실제 공용 render loadout·파생 능력치·current HP는 구매나 미리보기 선택으로 바뀌지 않고 장착 transaction이 commit된 뒤 Repository Flow에서만 갱신한다.
- Shop filter는 가로 스크롤 가능한 `전체 → 무기 → 투구 → 상의 → 하의 → 장갑 → 신발 → 장신구` 순서로 고정하고 각 chip은 최소 48dp 높이를 유지한다. `CHEST`와 `LEGS`는 별도 filter이며 서로의 item을 섞지 않는다.
- 장비 카드에는 한국어 장비명, type/slot, rarity, 가격, 요구 레벨, 대표 양수 modifier, 보유 여부와 현재 장착 여부를 읽기 쉬운 본문 글꼴로 표시한다. 요구 레벨은 구매 가능 여부와 무관하게 항상 보이고, 현재 레벨이 부족하면 잠금 icon·오류 container·`레벨 부족` text와 합쳐 제공한다. 보유 장비는 secondary container 색상, 2dp 테두리, 체크 icon과 `보유 중` text를 함께 사용해 색만으로 구분하지 않는다. 일반 판매 장비에는 `판매 중` badge를 표시하지 않고 판매 중지 예외에만 `판매 중지` badge를 표시한다. rarity는 `일반`·`고급`·`희귀`·`영웅`·`전설` 텍스트를 항상 제공한다.
- 가격 완화 뒤 Shop·Inventory·상세·구매 확인은 `EquipmentStoreSnapshot`의 Repository 가격을 단일 원천으로 일관되게 표시하며 화면별 가격 상수를 두지 않는다. 18종 가격은 id 순서대로 `낡은 검 20`, `철 장검 360`, `가죽 모자 27`, `철 투구 340`, `천 상의 22`, `가죽 갑옷 130`, `철 흉갑 1200`, `천 바지 22`, `가죽 바지 120`, `강철 각반 1150`, `가죽 장갑 140`, `여행자의 장화 380`, `마법사의 반지 1350`, `수호자의 목걸이 3400`, `강철 건틀릿 410`, `바람걸음 장화 430`, `물푸레나무 창 25`, `강철 철퇴 390` 골드다. 이미 구매한 장비에는 환불이나 별도 가격 차액 문구를 만들지 않는다.
- 현재 catalog는 기존 18종과 고정 ID `1019..1025`의 모험가 7부위를 합친 25종이다. 모험가의 검·모자·재킷·바지·장갑·신발·장식은 모두 `고급(UNCOMMON)`, 요구 레벨 5, 가격 `150/100/120/110/125/130/160` 골드이며 각 두 modifier를 가진다. 기존 adventure layer, `gloves_adventure`와 3분할 검을 상품 외형으로 사용하고 migration으로 자동 소유·장착하지 않는다. 기존 18종 중 액세서리 2종의 placeholder, null·unknown·decode 실패 fallback은 그대로 유지한다.
- 상·하의 시각 재질과 catalog 등급은 천 `일반`, 가죽 `고급`, 철 흉갑·강철 각반 `영웅`으로 일치시킨다. 장갑은 가죽 `고급`, 강철 건틀릿 `희귀`이며, 신발은 여행자의 장화와 바람걸음 장화 모두 `희귀`다. 천·가죽·철/강철 matched set과 서로 다른 재질을 섞은 조합 모두 같은 중심축과 전신 silhouette를 유지한다. 장갑 38픽셀 mask는 기본 손을 완전히 대체하고 최상단 weapon group의 pixel은 마지막 합성으로 보존한다. 신발 bounds는 `[23, 53, 41, 58]`이고 다섯 하의와 `y=53..54` 발목 interface, 발바닥 `y=58`에서 투명 틈이나 이중 seam 없이 이어진다. 장갑·신발 네 장비는 이진 alpha와 최근접 확대를 사용하며 혼합 착용에서도 얼굴·머리·상의·하의·투구·액세서리·무기 pixel을 보존한다.
- 실제 imageKey thumbnail은 `64×64` runtime PNG에서 검증된 불투명 bounds만 읽어 surface 안에 bounds-fit하고 최근접 보간한다. 실제 착용은 같은 PNG를 crop·이동·개별 확대하지 않고 canonical `64×64` 전체 원점에서 투구는 `headgear_front`, 상의는 `top`, 하의는 `bottom`, gameplay 무기는 최상단 `weapon_front`에 source-over 합성한다. 무기는 primary grip `(42, 42)`를 공유하고 얼굴 보호 영역 `[20, 7, 44, 28]`을 침범하지 않는다. 따라서 thumbnail의 시각적 중앙 정렬과 캐릭터의 얼굴·발 기준 합성은 서로 다른 presentation 계약이다.
- 장비 card 본문을 선택하면 통합 preview의 비영속 외형·stat delta와 선택 테두리만 갱신한다. card의 별도 48dp 상세 action은 세로 스크롤 가능한 상세 sheet를 열어 설명, 요구 레벨, 전체 modifier, 현재 같은 slot 장비와 후보의 수치 및 signed 차이, 가격·현재 골드와 판매/보유/장착 상태를 표시한다. card와 detail은 같은 typed action 우선순위로 미소유·구매 가능 `구매`, 미소유·구매 불가 `구매 불가`, 소유·미장착 `장착`, 소유·장착 중 `해제`만 사용한다. `104dp × 48dp` action 버튼은 두 surface의 우측 하단에 `12dp` inset으로 고정하고 구매 불가 이유는 버튼 밖의 한국어 설명으로 제공한다. 비교 기준은 반드시 같은 slot 하나이며 `CHEST`와 `LEGS`를 서로 비교하지 않는다.
- 골드 부족, 판매 중지, 요구 레벨 미달, 이미 보유함, 잘못된 type/slot mapping은 구매 불가 이유를 텍스트와 TalkBack 설명으로 제공한다. 버튼 비활성화는 stale state 방지를 위한 transaction 재검증을 대신하지 않는다.
- 구매 전 확인 dialog는 장비명·slot·가격·현재 골드·구매 후 골드를 표시하고 처리 중 중복 입력과 dismiss를 막는다. 구매 성공 dialog는 `바로 장착`, `인벤토리로 이동`, `계속 쇼핑` 세 선택을 세로로 배치한다. `바로 장착`은 별도 장착 transaction 결과를 표시하며 구매 성공을 장착 성공으로 오인하지 않는다.
- Inventory는 소유 장비를 card 목록으로 탐색하게 하고 각 item의 한국어 이름·type·rarity·전체 modifier·현재 장착 배지와 `장착`, `교체` 또는 `해제` action을 표시한다. 장착 중인 item의 action은 `해제`이며 처리 중에는 진행 설명을 제공하고 다른 장착·해제 입력도 막는다. 해제 성공 뒤 item은 소유 목록에 남고 장착 상태만 사라지며, 빈 slot 재해제는 멱등 성공 안내로 처리한다. Shop에서 현재 선택한 장비의 해제가 성공하면 선택·상세·확인과 temporary preview를 함께 닫고 최신 실제 빈 loadout으로 돌아간다. 다른 장비 해제 또는 실패에서는 선택을 유지한다. 같은 equipment는 character별 하나만 존재하므로 quantity control을 표시하지 않는다.
- 장비 구매만으로는 능력치 상승을 표시하지 않는다. `character_equipment`에 실제 장착된 modifier가 반영된 뒤에만 변경 전·후 기본/파생 능력치와 필요 시 현재/최대 HP를 갱신한다. 민첩 계열 문구는 `집중`, 지능 계열 문구는 `의지`로 표시하며 새로운 민첩·지능 원천 스탯을 노출하지 않는다.
- 장착 또는 해제로 `MAX_HP`가 변하면 활성 상태이상을 포함한 변경 전후 값으로 HP 비율을 보존한 transaction 결과를 표시한다. 해제한 slot의 shared renderer는 무기·투구·장갑·장신구 layer를 비우고 상의·하의·신발을 승인된 기본 복장으로 되돌리며 다른 slot 외형은 보존한다. 이 대상 fallback 갱신은 gameplay 소유권과 appearance source를 같은 source로 합치지 않는다. `0 HP`를 장비 command만으로 전투 가능 상태로 바꾸면 안 된다.
- 구매·장착·해제 오류는 Shop 또는 Inventory 안에서 처리하며 Calendar 일정 생성·완료·보상이나 독립 전투 event를 실패 상태로 바꾸지 않는다. 알림 권한이 없어도 로컬 Shop·Inventory 조회와 구매·장착·해제 command는 사용할 수 있어야 한다.
- Shop과 Inventory는 `320dp`, 높이 `640dp`, 글꼴 배율 `2.0`에서도 Shop의 고정 top-level app bar, Inventory의 nested back, 골드·Inventory action, 통합 preview·stat delta, card 이름·상태와 상세·구매/장착 action에 scroll로 도달할 수 있어야 한다. 상세 sheet와 dialog는 긴 한국어 문구가 잘리더라도 핵심 확인·취소·성공 선택을 잃지 않는다.

### 일정 작성 화면

- 필수 입력은 제목과 날짜다.
- 시간, 반복, 알림, 난이도, 카테고리는 같은 editor scroll 안에서 빠르게 선택할 수 있는 컨트롤로 제공한다.
- 반복 일정 설정은 없음, 매일, 매주, 매월 중 하나를 선택하게 한다.
- 알림 selector는 `설정 없음`, `10분 전`, `1시간 전`, `직접 설정` 순서로 제공하고 기본값은 `설정 없음`이다. 네 label과 상태 설명은 한국어 문자열 resource를 사용한다.
- `10분 전`과 `1시간 전`은 일정 시간이 없으면 chip 자체를 비활성화하고 `일정 시간이 없어 선택할 수 없습니다` TalkBack 설명과 한국어 helper를 함께 표시한다. 일정 시간에서 뺀 결과가 자정을 넘으면 이전 날짜 알림으로 계산한다. preset을 고른 뒤 일정 시간을 제거하면 `설정 없음`으로 되돌리고 이유를 알린다.
- `직접 설정`은 occurrence 당일 24시간제 Material time picker를 연다. 일정 시간이 없는 할 일에도 선택할 수 있고 일정 시간보다 늦은 값도 허용한다. 이전 날짜 또는 일수 offset UI는 제공하지 않으며, 시각을 지우거나 선택하지 않으면 `알림 시각 선택 필요`와 저장 전 한국어 validation을 표시한다. selector와 time action은 모두 최소 48dp target과 한국어 content description을 제공한다.
- Android 13 이상의 `POST_NOTIFICATIONS` system dialog는 앞서 정의한 첫 Calendar 진입의 한국어 안내 뒤 한 번만 요청한다. 거부·dismiss 뒤 `설정 없음`이 아닌 알림을 저장해도 system dialog를 자동 재요청하지 않고, package notification settings를 여는 한국어 `알림 설정` CTA를 reminder status 안내와 함께 제공한다.
- runtime permission과 앱 전체 notification switch는 사용할 수 있지만 `todo_task_reminders` 알림 채널이 차단된 경우 `NOTIFICATION_CHANNEL_DISABLED` 한국어 상태와 해당 package·channel로 이동하는 `알림 채널 설정` CTA를 제공한다. 채널을 삭제하거나 새 id로 사용자 선택을 우회하지 않는다.
- notification capability가 확보된 뒤 Android 12 이상 `SCHEDULE_EXACT_ALARM` special access가 필요한 reminder를 저장하면 `선택한 시각에 일정 알림을 보내기 위함`과 일정 기능 독립성을 설명하는 rationale dialog를 먼저 표시하고 시스템 설정을 연다. exact-alarm special access는 첫 실행·첫 Calendar 진입 또는 `설정 없음` 일정에서 요청하지 않는다.
- 권한 거부·dismiss, 미래 occurrence 없음 또는 scheduler 오류가 있어도 editor는 일정 저장 성공으로 닫고 별도 한국어 reminder status 경고를 표시한다. `POST_NOTIFICATIONS_REQUIRED`, `EXACT_ALARM_ACCESS_REQUIRED`, `NO_FUTURE_OCCURRENCE`, `ERROR`를 일정 저장 실패로 표현하지 않으며, 권한 화면 복귀 뒤 persisted task id를 다시 reconcile한다. 권한 상태는 일정·완료·보상·전투·구매 action을 비활성화하지 않는다.
- 일정 생성·수정·삭제와 occurrence 완료·실패·각 취소 뒤 예약 상태가 달라질 수 있으므로 Calendar는 ViewModel state를 다시 렌더링한다. Compose가 AlarmManager, receiver, WorkManager 또는 DAO를 직접 호출하지 않는다.

### 일정 알림 표시

- notification은 일정 제목과 occurrence 날짜·시각을 읽을 수 있는 한국어 본문을 제공하고, tap하면 Calendar에서 해당 occurrence 날짜를 연다. 알림은 private visibility를 사용하고 잠금 화면 public version에는 task 제목·memo를 노출하지 않는다.
- 새 exact alarm plan은 Room에 occurrence key와 trigger를 `PENDING`으로 먼저 stage한 뒤 AlarmManager에 등록한다. 바로 발화한 callback도 persisted key를 검증해 한 번 claim할 수 있어야 하며, scheduler 실패 정리는 이미 claim된 `DELIVERED`를 덮어쓰면 안 된다.
- API 26 이상의 `일정 알림` channel은 높은 중요도와 기본 소리·진동, notification의 high priority를 요청한다. API 23~25는 channel 대신 notification 자체에 high priority와 기본 소리·진동을 지정한다. heads-up, 소리와 진동은 사용자 channel 설정·기기 정책·방해 금지 모드에 따라 달라질 수 있으며 앱은 이를 우회하거나 보장 문구로 표시하지 않는다.
- 완료·실패·삭제된 occurrence는 발화 직전에 다시 검증해 표시하지 않는다. 앱 복귀나 재부팅 뒤 놓친 알림을 한꺼번에 표시하지 않고 다음 미래 occurrence만 복구한다.
- Force Stop과 Android background restricted 상태에서는 OS가 alarm·PendingIntent·background work를 중단할 수 있으므로 제한 해제 전 전달을 보장하지 않는다. 현재 별도 도움말 화면은 구현하지 않았으며 이 제한은 제품·개발 문서의 지원 범위로 명시한다.

## 색상

| 용도 | 값 | 설명 |
|------|----|------|
| 배경 | `#15181B` | 어두운 던전 톤 |
| 표면 | `#20242A` | 카드와 패널 |
| 경계선 | `#3A3F45` | 픽셀 UI 구분선 |
| 주 텍스트 | `#F4EFE3` | 높은 대비의 본문 |
| 보조 텍스트 | `#B7B0A3` | 메타 정보 |
| 골드 | `#F2C14E` | 재화와 보상 |
| XP | `#5CC8A7` | 성장과 성공 |
| 위험 | `#E05252` | 삭제와 오류 |
| 포커스 | `#7FB3FF` | 선택과 접근성 포커스 |

## 타이포그래피

- 제목과 캐릭터 패널의 짧은 라벨에는 픽셀풍 폰트를 사용할 수 있다.
- 일정 제목, 날짜, 시간, 설명, 오류 메시지는 Android 기본 가독성 좋은 폰트를 사용한다.
- 본문 텍스트는 사용자의 시스템 글자 크기 설정을 존중한다.
- 긴 일정 제목은 2줄까지 표시하고, 목록 전체 높이를 과도하게 흔들지 않도록 한다.

## 컴포넌트 규칙

- 터치 대상은 최소 48dp를 유지한다.
- 버튼은 아이콘만 사용할 때도 접근성 label을 제공한다.
- 완료, 삭제, 구매 같은 중요한 액션은 상태 변화를 즉시 피드백한다.
- 카드 안에 카드를 중첩하지 않는다. 반복 항목은 단일 표면과 경계선으로 구분한다.
- 캘린더 날짜 셀은 고정된 비율과 크기를 유지해 월 이동 시 레이아웃이 흔들리지 않게 한다.

## 애니메이션

- 완료 보상 피드백은 600ms 이하로 짧게 사용하고 사용자가 Calendar의 다른 곳을 누르면 즉시 종료한다.
- 전투 transition은 동시에 겹쳐 source state 순서를 숨기지 않도록 직렬로 재생하고, translation·shake·flash·alpha를 짧고 유한하게 끝낸다. 같은 combat event를 재구독이나 재진입으로 replay하지 않는다.
- 전투 효과음도 같은 직렬 actor에서 phase 시작과 함께 한 번만 요청한다. load 전·background·released 요청은 지연 재생하지 않고 폐기하며 앱 복귀 뒤 과거 소리를 보상 재생하지 않는다.
- 레벨업은 명확한 축하 화면 또는 dialog로 표시할 수 있다.
- 무한 반복 glow, 과한 흔들림, 배경 장식 애니메이션은 사용하지 않는다.
- 애니메이션이 꺼진 환경에서도 정보가 사라지면 안 된다.

## 접근성

- 색만으로 완료, 실패, 선택 상태를 구분하지 않는다.
- TalkBack에서 일정 제목, 날짜, 시간, 완료 상태, 보상 정보를 읽을 수 있어야 한다.
- 픽셀 아트는 장식이면 decorative로 처리하고, 의미가 있으면 설명을 제공한다.
- 낮은 대비의 갈색, 회색 조합을 본문에 사용하지 않는다.

## 금지 사항

- 일정 텍스트를 픽셀 폰트로만 표시하지 마라. 이유: 작은 화면에서 가독성이 급격히 떨어진다.
- 게임 장식을 캘린더 날짜 숫자 위에 겹치지 마라. 이유: 일정 확인 속도가 느려진다.
- 앱 launch나 Calendar가 보이기 전에 알림 권한 dialog를 즉시 띄우지 마라. 첫 Calendar 진입의 한국어 안내 뒤 `POST_NOTIFICATIONS`를 한 번만 요청하고, 거부 뒤에는 자동 재요청하지 마라. exact-alarm special access는 notification capability가 확보된 뒤 reminder를 저장하기 전에는 요청하지 마라.
- 보상 애니메이션이 완료 버튼을 다시 누를 수 없게 오래 막지 마라. 이유: 반복 사용 흐름이 끊긴다.
