# PRD: Todo Quest

## 목표

Todo Quest는 개인 일정과 할 일을 캘린더로 관리하면서, 완료 행동을 RPG 퀘스트 보상으로 연결해 지속적인 실행 동기를 제공하는 Android 앱이다.

## 사용자

- 매일 해야 할 일을 캘린더로 정리하고 싶은 개인 사용자
- 단순 체크리스트보다 성취감과 시각적 피드백이 필요한 사용자
- 생산성 앱의 실용성은 유지하면서 가벼운 RPG 성장 루프를 원하는 사용자

## 핵심 가치

1. 일정 관리는 빠르고 명확해야 한다.
2. 게임 요소는 완료 행동을 강화해야 하며 일정 관리를 방해하면 안 된다.
3. 서버 없이 로컬에서 안전하게 동작해야 한다.

## MVP 기능

### 일정과 TODO

- 사용자는 제목, 메모, 날짜, 선택적 시간, 난이도, 카테고리를 입력해 일정을 등록한다.
- 사용자는 월간 캘린더에서 날짜별 일정 밀도를 확인하고 날짜를 선택한다.
- 사용자는 일간 화면에서 해당 날짜의 퀘스트 목록을 보고 완료, 수정, 삭제한다.
- 완료 상태는 날짜별 occurrence 단위로 기록한다.
- 완료 취소가 가능하되, 이미 지급된 보상을 중복 지급하지 않는다.

### 반복 일정

- MVP 반복 유형은 없음, 매일, 매주, 매월이다.
- 반복 일정은 원본 일정과 날짜별 발생분을 분리한다.
- 사용자가 특정 날짜의 반복 일정을 완료하면 해당 날짜 occurrence만 완료 처리한다.
- 반복 원본을 수정할 때는 앞으로 생성될 occurrence에 적용한다.
- 과거 완료 기록은 사용자가 명시적으로 삭제하지 않는 한 유지한다.

### 알림

- 사용자는 일정별 알림 시간을 설정할 수 있다.
- 알림 권한을 허용하지 않아도 일정 등록, 완료, 보상 기능은 계속 사용할 수 있다.
- Android 13 이상에서는 알림 권한을 사용자 행동 이후 요청한다.
- 정확한 시간 알림이 필요한 경우 Android 12 이상 exact alarm 정책과 특수 권한 흐름을 고려한다.
- 기기 재부팅, 앱 업데이트, 권한 변경 이후 알림 재등록 정책을 구현 문서에 반영한다.

구체적인 선택지·occurrence 예약·권한 실패 계약은 아래 `Task Reminder v1` 승인 범위를 따른다. 현재 앱은 Room v15이며, v10에서 추가한 로컬 설정·예약 상태와 occurrence별 exact alarm, Calendar editor·권한 흐름·notification tap, v14의 staged plan과 알림 채널 차단 복구를 보존한다. v15는 장비 appearance fallback만 갱신하며 reminder source나 전달 정책을 변경하지 않는다.

### RPG 성장

- 일정은 완료 시 XP와 골드를 지급한다.
- 보상량은 난이도와 일정 속성에 따라 계산하되 MVP에서는 단순하고 예측 가능한 규칙을 사용한다.
- 캐릭터는 XP 누적으로 레벨업한다.
- 골드는 상점 아이템 구매에 사용한다.
- 상점 아이템은 MVP에서 외형 또는 수집 요소로만 동작하고 능력치 보너스는 제공하지 않는다.
- 인벤토리는 구매한 아이템과 보유 상태를 보여준다.

능력치·장비·전투의 후속 방향은 [캐릭터 스탯 및 전투 계산 설계](game-design/character-stats-design.md)를 참고한다. 해당 설계 중 아래의 승인된 후속 범위만 제품 계약으로 채택한다. 장비 능력치는 MVP에는 포함하지 않지만 아래 Post-MVP Equipment Shop and Inventory v1에서 별도 승인하며, 아래에서 승인하지 않은 나머지 전투 시스템은 계속 `MVP 제외 사항`이다.

## 데이터 무결성 규칙

- 같은 task occurrence에 대한 완료 기록은 하나만 존재해야 한다.
- XP와 골드 지급은 CompletionLog 또는 RewardLedger 기준으로 한 번만 발생해야 한다.
- 구매는 골드 차감과 인벤토리 추가가 하나의 트랜잭션으로 처리되어야 한다.
- 알림 예약 실패는 일정 저장 실패로 이어지지 않는다. 실패 상태는 사용자에게 별도로 표시한다.

## MVP 제외 사항

아래 항목은 MVP 범위에서 제외한다. 이 목록에 있는 기능이 이후 Post-MVP 범위로 별도 승인되더라도 MVP 계약 자체가 소급 변경되는 것은 아니다.

- 외부 Google Calendar 읽기/쓰기 연동
- 계정, 로그인, 서버 동기화
- 친구, 길드, 랭킹 같은 소셜 기능
- 결제, 광고, 유료 재화
- 아이템 장착 효과와 능력치 보너스
- 스토리 챕터와 전투 시스템
- iOS 또는 웹 버전

## 승인된 범위: Task Reminder v1

Task Reminder v1은 MVP 알림 요구를 로컬 exact alarm으로 구체화하는 승인 범위다. 외부 Google Calendar 읽기/쓰기, 계정 또는 서버 동기화를 추가하지 않는다.

- 일정 editor의 알림 선택지는 기본값 `설정 없음`과 `10분 전`, `1시간 전`, `직접 설정` 네 가지다. `10분 전`과 `1시간 전`은 일정 시간이 있을 때만 사용할 수 있고 계산 결과가 자정을 넘으면 이전 날짜에 발화한다.
- `직접 설정`은 occurrence 당일의 독립된 local time이다. 시간이 없는 할 일에도 사용할 수 있고 일정 시간보다 뒤여도 허용하지만, 이전 날짜 또는 일수 offset은 입력받지 않는다.
- task 원본의 알림 설정과 `(taskId, occurrenceDate)` occurrence 예약 key를 분리한다. 반복 일정은 현재 활성 task segment에서 완료·실패하지 않은 다음 미래 occurrence 하나만 예약하고, 발화 처리 뒤 다음 유효 occurrence를 예약한다.
- 생성·수정·반복 분할·삭제·완료·실패·완료 취소·실패 취소는 관련 task의 예약을 재검토한다. 발화 직전에도 task 활성 상태와 occurrence의 `TODO` 상태를 재검증해 완료·실패·삭제된 occurrence의 알림을 억제한다.
- 지나간 일회성 알림은 즉시 발화하지 않고 `NO_FUTURE_OCCURRENCE`로 남긴다. 반복 일정은 지나간 occurrence를 건너뛰고 다음 미래 occurrence를 예약하며, 재부팅이나 프로세스 부재 중 놓친 알림을 복귀 시 몰아서 표시하지 않는다.
- 정확한 시각은 `AlarmManager.setExactAndAllowWhileIdle()`과 manifest `BroadcastReceiver`로 처리한다. Android 12 이상에서는 `SCHEDULE_EXACT_ALARM`과 `canScheduleExactAlarms()`, Android 13 이상에서는 `POST_NOTIFICATIONS` runtime permission을 사용한다. notification runtime permission은 첫 Calendar 진입 안내 뒤 한 번만 요청하고, exact-alarm special access는 notification capability가 확보된 뒤 사용자가 알림을 저장한 흐름에서만 설명과 함께 처리한다. `USE_EXACT_ALARM` 또는 부정확한 alarm fallback은 사용하지 않는다.
- 권한이 없거나 scheduler가 실패해도 일정 저장·수정·삭제, occurrence 완료·실패·각 취소, 보상과 전투 transaction은 성공 상태를 유지한다. 알림 결과는 표준화된 reminder status와 한국어 안내로 분리한다.
- 알림 채널은 높은 중요도와 기본 소리·진동을 사용해 가능한 기기에서 heads-up 표시를 요청한다. 사용자 채널 설정과 방해 금지 모드를 우회하지 않으며, notification tap은 Calendar에서 해당 occurrence 날짜를 연다.
- 앱 시작, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, 시스템 시각·시간대 변경과 exact-alarm 권한 부여 뒤 예약을 재검토한다. WorkManager는 재등록과 상태 복구만 담당하고 정확한 발화 시각을 보장하지 않는다.
- Force Stop 또는 Android background restricted 상태처럼 OS가 alarm·PendingIntent·background work를 의도적으로 중단하는 경우는 전달 보장 범위에서 제외한다.

**구현 상태**: Room v10의 `task_reminders`에 네 mode, typed status와 현재 예약 occurrence key·trigger를 저장한다. 일정 생성·수정은 task와 reminder setting을 한 transaction에서 확정하고, 일정 및 occurrence command가 성공한 뒤 `ReconcileTaskReminderUseCase`가 기존 alarm 취소, notification→exact capability 확인과 다음 유효 occurrence 한 건 예약을 best-effort로 수행한다. explicit manifest receiver는 발화 직전에 저장된 key·활성 task·`TODO` 상태를 조건부로 다시 확인해 한 번만 게시한다. 높은 중요도 channel, 첫 Calendar 진입의 one-shot `POST_NOTIFICATIONS` 안내·요청, reminder 저장 시 notification settings CTA와 exact-alarm 설명 dialog, Calendar 날짜를 여는 notification tap, 앱 시작·시스템 broadcast의 독립 restore work까지 구현했다.

## 승인된 범위: Notification Permission Entry v2

Notification Permission Entry v2는 Task Reminder v1의 exact alarm·occurrence 예약·발화 계약을 유지하면서 notification runtime permission의 사용자 진입점만 조정한다.

- Android 13 이상에서 권한이 필요하고 이전 system request 시도가 없다면 첫 Calendar 진입에서 한국어 사전 안내 뒤 `POST_NOTIFICATIONS`를 한 번만 요청한다.
- 거부하거나 요청을 보류·dismiss한 뒤에는 Calendar 재진입, Activity 재생성, process 재시작 또는 background reconciliation으로 자동 재요청하지 않는다.
- notification capability가 없는 상태에서 `NONE`이 아닌 reminder를 저장해도 일정과 reminder setting은 성공으로 보존한다. Calendar는 package notification settings를 여는 한국어 `알림 설정` CTA를 제공한다.
- exact-alarm special access는 첫 실행 또는 첫 Calendar 진입에서 요청하지 않는다. notification capability가 확보된 뒤 `NONE`이 아닌 reminder를 저장할 때만 기존 한국어 설명 dialog와 package exact-alarm settings를 순차적으로 제공한다.
- notification 또는 exact-alarm 권한 거부는 일정·완료·보상·전투·구매·장착을 차단하거나 이미 성공한 transaction을 롤백하지 않는다.

**구현 상태**: application-scope `SharedPreferencesFirstLaunchNotificationPromptStore`가 최초 확인을 동기 commit으로 한 번만 소비하고, `PrepareFirstLaunchNotificationPromptUseCase`가 그 확인 뒤 `POST_NOTIFICATIONS` capability만 조회한다. `CalendarViewModel`은 `FIRST_LAUNCH`와 `REMINDER` 안내 origin을 분리해 replay 없는 event를 내보낸다. 첫 진입 확인은 API 33 이상 미허용 runtime permission, 시스템 notification이 꺼진 경우 package settings, 이미 사용 가능한 경우 no-op으로 분기한다. 이후 reminder 저장의 `POST_NOTIFICATIONS_REQUIRED`는 system dialog를 재요청하지 않고 `알림 설정` CTA로 이동하며, settings 복귀 뒤 notification이 확보된 경우에만 exact-alarm rationale와 settings를 순서대로 제공한다. 저장소·capability·UI 실패와 사용자의 거부·dismiss는 Calendar 로딩과 이미 성공한 일정·보상·전투·구매 transaction을 변경하지 않는다.

이 범위는 외부 Google Calendar, 계정·서버 동기화와 새 top-level 화면을 추가하지 않는다.

## 승인된 후속 범위: Post-MVP Character Growth v1

Post-MVP Character Growth v1은 MVP 완료 이후 구현하는 승인된 후속 기능이다. 기존 MVP의 일정 생성·수정·삭제, occurrence 단위 완료, XP·골드 보상 멱등성 계약은 변경하지 않는다.

이번 후속 범위는 다음 항목으로 제한한다.

- 현재 XP에서 계산한 레벨을 최대 50으로 제한하고, 기존 XP와 골드는 그대로 보존한다.
- 기본 스탯과 파생 스탯, 레벨당 포인트, 스탯 초기화를 제공한다.
- 기존 사용자에게 capped level을 기준으로 `2 × (level - 1)` 스탯 포인트를 소급 지급한다.
- 정시 완료, 일일 효율 감소, 연속일에 따른 비전투 보상을 occurrence 보상 흐름에 추가한다.
- 미래 occurrence를 조기 완료해도 정시 보상을 지급한다. 일일 효율은 실제 완료 로컬 날짜에, 연속일은 occurrence 날짜에 귀속한다.
- `MOMENTUM`은 정시 완료 occurrence 날짜부터 그 다음 로컬 날짜가 끝날 때까지 유지한다.
- 캘린더와 분리된 별도 캐릭터 화면에서 성장 상태를 제공한다.

자동 전투, 실패 시 몬스터 공격, WorkManager reconciliation은 이 Character Growth v1 승인 범위에 포함하지 않으며, 아래 Post-MVP Monster Combat v1의 별도 승인 범위로 다룬다. 캐릭터 appearance와 equipped item id의 최소 영속·렌더 경계는 아래 Character Layer Runtime Composition v1에서 별도로 승인했고, inventory·ownership·획득·사용자 장착 UI와 장비 능력치 효과는 Post-MVP Equipment Shop and Inventory v1에서 다시 분리해 승인한다. 따라서 위 `MVP 제외 사항`은 그대로 유효하고, 이 승인은 전투 시스템이나 아이템 능력치가 MVP에 포함되었다는 의미가 아니다.

## 승인된 후속 범위: Character Stat Allocation Guide v1

Character Stat Allocation Guide v1은 능력치 배분 기능을 처음 접하는 신규 설치 사용자에게만 Character 화면의 기존 배분 방법을 설명하는 로컬 안내 기능이다. Room 데이터와 능력치 계산·저장 transaction은 변경하지 않는다.

- 앱 composition root가 Room을 열기 전에 `todo-quest.db` 파일 존재 여부를 확인한다. DB 파일이 없던 신규 설치만 자동 안내 대상이며, 업데이트 전에 DB가 이미 존재한 기존 설치는 자동 안내 대상이 아니다.
- 자동 안내 대상 여부는 최초 초기화 시 한 번 저장해 이후 DB 생성이나 process 재시작으로 다시 판정하지 않는다. 자동 안내를 확인하지 않은 채 process가 종료되면 다음 Character 진입에서 다시 표시한다.
- 자동 안내를 닫거나 `능력치 보러 가기`를 선택하면 확인 상태를 기록한다. 확인 뒤에는 Character 재진입·Activity 재생성·process 재시작으로 자동 표시하지 않는다.
- 모든 사용자는 `기본 능력치` 제목의 `능력치 배분 안내 열기` 도움말로 같은 안내를 다시 볼 수 있다. 수동 도움말 열기·닫기는 자동 안내 확인 상태를 되돌리거나 새 자동 안내를 만들지 않는다.
- 안내 상태 저장·조회 또는 sprite decode가 실패해도 Character 조회·능력치 배분·초기화와 일정·보상·전투 command를 차단하거나 롤백하지 않는다.

**구현 상태**: `SharedPreferencesCharacterGuideRepository`가 자동 대상과 확인 상태를 별도 key로 보존하고, `PrepareCharacterStatGuideUseCase`·`AcknowledgeCharacterStatGuideUseCase`를 application scope에서 `CharacterViewModel`에 주입한다. Character의 source state가 로드된 뒤 자동 안내를 한 번 준비하며, Compose Dialog는 한국어 안내 요정 대사, 현재 미배분 포인트 상태, `능력치 보러 가기`와 `닫기`를 제공한다. 자동·수동 origin은 ViewModel에서 분리하고 UI는 SharedPreferences나 Room DAO를 직접 호출하지 않는다.

## 승인된 후속 범위: Post-MVP Monster Combat v1

Post-MVP Monster Combat v1은 Character Growth v1의 원천 상태와 계산 계약 위에 로컬 backend 전투를 추가하는 승인된 후속 기능이다. 이 승인은 위 `MVP 제외 사항`을 바꾸거나 전투를 MVP에 포함하지 않는다. 기존 MVP의 일정 생성·수정·삭제, occurrence 단위 완료, XP·골드 보상 멱등성 및 알림·exact alarm 권한과 무관한 핵심 기능 동작은 그대로 유지한다.

이번 후속 범위는 다음 항목으로 제한한다.

- 몬스터의 `MAX_HP`, `DAMAGE`, `DEFENSE`, 레벨·유형·등급 성장과 Stage 진행을 제공한다.
- `combatEligible`로 확정된 occurrence의 최초 완료는 플레이어 공격 1회를 만든다. 마감까지 완료되지 않은 occurrence는 reconciliation에서 `FAILED` 원천 기록과 `MISSED_DEADLINE` 몬스터 공격 event를 같은 transaction으로 확정한다. 두 방향의 공격은 서로 독립된 멱등 event로 기록한다.
- Room에 몬스터 인스턴스, Stage 진행, 양방향 공격 event와 reconciliation cursor를 저장한다.
- 실패 occurrence의 몬스터 공격은 앱 시작과 WorkManager best-effort reconciliation으로 처리하며 알림 또는 exact alarm 권한과 예약 성공 여부에 의존하지 않는다.
- 치명 피해는 별도 event에 `0 HP` 전투 불능 결과로 남긴다. 이후 처리는 아래 Severe Injury v1의 중상 적용·50% 응급 회복 lifecycle을 따르며, 같은 attack key 재처리로 피해나 상태를 중복 적용하지 않는다.
- 구현은 backend와 기존 캐릭터 HP 상태 연동까지만 포함한다.

위 backend 범위는 Room v4와 앱 시작·15분 주기 WorkManager reconciliation까지 구현 완료됐다. 신규 자동 마감 실패는 완료·기존 실패·기존 monster event가 없는 occurrence만 대상으로 하며, `failure_logs`, `APPLIED` 또는 복귀당 피해 상한에 따른 `SKIPPED` event, 적용 시 player HP와 reconciliation cursor를 하나의 Room transaction에서 저장한다. Room v8의 data-only migration은 완료 기록이 없는 기존 `MISSED_DEADLINE` `APPLIED`·`SKIPPED` event에 대응하는 failure source만 event 처리 시각으로 한 번 backfill하고 재실행에도 중복하지 않는다. WorkManager는 best-effort이므로 occurrence의 정확한 deadline에 공격을 보장하지 않으며, event deadline과 실제 처리 시각을 별도로 다룬다.

아래에서 별도로 승인하는 Calendar Battle Map과 Post-MVP Combat Rewards v1 이외의 몬스터·전투 UI, 사망 디버프 자체, 몬스터 스킬·치명타, 전리품, 정확한 deadline alarm은 이번 승인 범위에서 제외한다. 장착 장비 modifier를 기존 전투 계산 입력에 연결하는 범위는 Post-MVP Equipment Shop and Inventory v1에서 별도로 승인하지만 이 Monster Combat v1 결정의 범위를 넓히지는 않는다. 전투 event 처리 실패는 일정 완료를 되돌리지 않으며, 완료 취소 후 재완료해도 이미 만든 공격을 삭제하거나 다시 만들지 않는다.

## 승인된 후속 범위: Post-MVP Combat Rewards v1

Post-MVP Combat Rewards v1은 신규 occurrence의 난이도 기반 직접 XP·골드 지급을 player attack 결과 기반 보상으로 교체한다. 기존 사용자에게 확정된 XP·골드와 과거 attack 결과는 보존하고 소급 보상하지 않는다.

- 모든 새 occurrence 최초 완료는 하루 순번과 무관하게 player attack outbox 하나를 만든다. 기존 하루 20회 공격 상한은 적용하지 않는다.
- 비치명 공격도 몬스터 level에 비례한 소량의 hit XP를 지급한다.
- 몬스터 처치 시 level과 NORMAL/ELITE/BOSS 등급에 비례한 추가 XP와 골드를 지급한다. 장착 장비의 `GOLD_GAIN_BONUS`는 처치 골드에만 적용한다.
- XP·골드·레벨업·미배분 포인트·HP 비율·처치 회복·몬스터 HP·Stage와 attack 결과 snapshot은 하나의 Room transaction에서 occurrence attack key당 한 번만 확정한다.
- 일정 완료 transaction은 직접 XP·골드를 지급하지 않으며 전투 처리 실패를 이유로 롤백하지 않는다. PENDING attack은 reconciliation이 재시도한다.
- 실제 전투 보상은 Battle Map의 replay 없는 `600ms` badge와 Character Flow 기반 HUD에 표시하며 새 완료에 기존 Calendar reward snackbar를 함께 표시하지 않는다.
- Room v9은 신규/과거 보상 방식을 version으로 구분한다. v8의 ledger와 PENDING/APPLIED attack은 legacy version `0`으로 보존하며 새 보상을 소급 지급하지 않는다.

## 승인된 후속 범위: Post-MVP Combat Rewards v2와 장비 가격 완화

Combat Reward v2는 v1의 occurrence·attack 멱등 transaction과 replay 없는 표시를 유지하면서 새 attack의 체감 보상을 높인다. v1 공식을 삭제하거나 기존 PENDING event에 덮어쓰지 않는다.

- 새 reward version `2` attack의 base는 hit XP `3`, kill bonus XP `20`, kill gold `15`다. level band 분모 `10/5/10`, NORMAL/ELITE/BOSS `1×/2×/4×`와 `GOLD_GAIN_BONUS`의 처치 gold 전용 적용은 유지한다.
- level 1 NORMAL 처치는 `23 XP / 15골드`, level 55 BOSS 처치는 장비 gold bonus가 없을 때 `128 XP / 80골드`다.
- reward version `0`은 무보상 legacy, version `1` PENDING은 기존 `1/10/5` base 공식, version `2`는 승인 뒤 새 attack 전용이다. APPLIED attack은 version과 관계없이 저장된 snapshot을 유지하며 다시 계산하거나 소급 지급하지 않는다.
- 18종 가격은 `낡은 검 20`, `철 장검 360`, `가죽 모자 27`, `철 투구 340`, `천 상의 22`, `가죽 갑옷 130`, `철 흉갑 1200`, `천 바지 22`, `가죽 바지 120`, `강철 각반 1150`, `가죽 장갑 140`, `여행자의 장화 380`, `마법사의 반지 1350`, `수호자의 목걸이 3400`, `강철 건틀릿 410`, `바람걸음 장화 430`, `물푸레나무 창 25`, `강철 철퇴 390` 골드로 완화한다.
- 장비 row는 canonical id·name key·type·slot과 기존 가격이 모두 일치할 때만 조건부 갱신한다. 이미 구매한 장비와 현재 gold는 그대로 두고 가격 차액을 환불하지 않는다.
- 기존 Room v12 column만 사용하므로 schema version과 identity schema를 올리지 않는다.

**구현 상태**: `CombatRewardBalanceCatalog`는 version `1`의 `1/10/5`와 version `2`의 `3/20/15`를 함께 보존하고 `CURRENT_VERSION = 2`를 새 completion attack에 snapshot한다. `RoomCombatRepository`는 PENDING version `0`을 무보상으로, version `1`·`2`를 저장된 version의 config로 처리하며 미지원 version은 transaction을 실패시켜 source를 보존한다. 이미 APPLIED인 row는 저장된 operand/result snapshot을 반환한다. `EquipmentCatalogSeeder`는 fresh 18종을 승인 가격으로 `INSERT OR IGNORE`하고 기존 row는 DAO transaction에서 canonical id·name key·type·slot과 기존 가격이 모두 일치할 때만 갱신한다. custom row, ownership·장착과 character gold는 수정하거나 환불하지 않는다. Shop은 이 Repository snapshot 가격을 목록·상세·구매 확인·부족 안내·성공 잔액의 단일 원천으로 사용한다.

이 범위는 가격과 전투 보상량만 조정한다. 기존 장비 modifier·소유·장착, 구매 transaction, 일정 완료와 전투 처리의 분리, 권한 실패 독립성과 외부 Google Calendar 제외를 변경하지 않는다.

## 승인된 후속 범위: Task Difficulty Combat Balance v1과 Reminder Delivery Reliability v2

Task Difficulty Combat Balance v1은 Combat Reward v2의 base 공식과 occurrence별 attack 멱등성을 유지하면서 새 player attack의 피해와 XP에 completion 시점의 일정 난이도를 반영한다.

- 난이도 배율은 쉬움 `100%`, 보통 `150%`, 어려움 `200%`다. 캐릭터 공격력과 MOMENTUM을 먼저 합성하고 난이도 배율을 정수 내림으로 적용한 뒤 기존 치명타와 대상 방어력을 계산한다.
- Combat Reward v2가 계산한 hit XP와 kill bonus XP 각각에 같은 배율을 정수 내림으로 적용하고 양수 결과는 최소 `1`로 유지한다. level 1 NORMAL 처치 총 XP는 쉬움 `23`, 보통 `34`, 어려움 `46`이고 비치명 hit XP는 `3/4/6`이다.
- kill gold, `GOLD_GAIN_BONUS`, 몬스터 level band와 NORMAL/ELITE/BOSS grade 공식은 변경하지 않는다. Calendar도 난이도가 피해와 XP를 높인다고 안내하되 gold 증가를 암시하지 않는다.
- 난이도와 별도 difficulty balance version은 completion 시점 player attack source에 snapshot한다. 기존 PENDING/APPLIED attack은 version `0`의 중립 `100%`로 보존하고 현재 task 난이도를 소급 조회하거나 재계산하지 않는다. 새 attack만 current difficulty version을 사용한다.
- 같은 `(taskId, occurrenceDate)`의 completion, RewardLedger, player attack, 피해와 XP 지급은 계속 한 번만 확정한다. 완료 취소·재완료와 process retry는 기존 attack source나 APPLIED 결과를 바꾸지 않는다.

Reminder Delivery Reliability v2는 Task Reminder v1과 Notification Permission Entry v2의 사용자 선택을 유지하면서 실제 전달 capability와 exact alarm 등록 경합을 보강한다.

- runtime permission과 앱 전체 notification switch뿐 아니라 `todo_task_reminders` 알림 채널 차단을 독립적으로 판정한다. 채널만 꺼졌으면 `CHANNEL_DISABLED` capability를 typed `NOTIFICATION_CHANNEL_DISABLED` schedule status로 보존하고 일정과 reminder setting은 성공시키며, 한국어 안내와 package-scoped 채널 설정 CTA를 제공한다.
- 다음 exact alarm plan을 Room에 `PENDING`으로 먼저 stage한 뒤 AlarmManager에 등록하고, 같은 key가 current일 때만 `SCHEDULED`로 확정한다. 바로 도착한 callback은 persisted key를 검증해 한 번 claim할 수 있다.
- scheduler 실패 시 같은 key orphan alarm을 best-effort로 취소하고 key가 아직 current일 때만 `ERROR`로 바꾼다. receiver가 먼저 claim한 `DELIVERED`는 후속 scheduler 정리가 덮어쓰지 않는다.
- DND, 사용자의 알림 채널 선택, Force Stop과 제조사 background 정책은 우회하지 않는다. notification·channel·exact capability 실패도 일정·완료·보상·전투·구매 transaction을 차단하거나 롤백하지 않는다.
- Calendar task 목록은 설정된 reminder에만 occurrence 기준 실제 local 발화 시각을 `10분 전 · 전날 23:50`, `직접 설정 · 당일 08:00` 형식으로 표시하고 막힌 capability에는 해당 설정 복구 CTA를 제공한다.
- `완료`·`실패`는 텍스트와 아이콘이 있는 content-sized 시각 버튼으로 줄이되 최소 `48dp` 터치 영역을 유지한다. Battle EXP는 `EXP`를 bar 왼쪽, 현재/필요 값을 bar 오른쪽에 맞추고 기존 최소 폭·progress·TalkBack 계약을 유지한다.

**구현 상태**: 순수 Kotlin `TaskDifficultyCombatBalanceCatalog`·policy와 Room v14의 `player_attack_events.sourceTaskDifficulty`·`taskDifficultyBalanceVersion`을 구현했다. `MIGRATION_13_14`는 기존 PENDING/APPLIED row를 nullable 난이도·version `0`으로 보존하고 현재 task 난이도를 backfill하지 않는다. 신규 completion transaction은 current 난이도와 version `1`을 attack source에 snapshot하고, PENDING 처리만 저장된 배율로 피해와 hit/kill XP를 확정하며 APPLIED 결과는 재계산하지 않는다. Repository는 exact plan을 `PENDING`으로 먼저 stage하고 callback-safe 조건부 `SCHEDULED`·`ERROR` 전이를 소유한다. Android capability adapter는 앱 알림 capability와 채널 차단을 분리하고 package-scoped 채널 설정 intent를 제공하며, API 23~25 notification은 high priority와 기본 소리·진동을 요청한다. Calendar는 occurrence 기준 mode·당일/전날 실제 발화 시각, typed 복구 CTA, 48dp target을 유지하는 compact action과 EXP 양끝 정렬을 렌더링한다.

이 범위는 외부 Google Calendar 읽기/쓰기, 계정·서버 동기화, DND 우회, inexact alarm fallback, kill gold 변경과 새 전리품을 추가하지 않는다.

## 승인된 후속 범위: Calendar Battle Map v1

Calendar Battle Map v1은 구현된 Monster Combat v1 backend를 메인 Calendar에 읽기 전용으로 시각화하는 별도 Post-MVP 후속 기능이다. 최초 구현은 시스템 inset 다음 `상단 플레이어 진행 HUD를 포함한 Battle Map → 월간 캘린더 → 선택 날짜의 할 일 목록`을 하나의 세로 scroll 안에 배치했다. 이 단일 scroll 계약은 아래 Calendar Combat Feedback v1에서 고정 Battle Map과 독립 Calendar scroll로 명시적으로 교체한다. 앱명·선택 날짜·성장 요약을 표시하던 기존 상단 정보 Header와 그 고정 공간을 제거하고 단일 활성 몬스터·독립 layer·권한 비의존 흐름을 유지하는 나머지 계약은 계속 유효하다.

- `CalendarViewModel`은 실제 `CombatRepository.observeCombat()`을 관찰해 일정 상태와 분리된 Battle Map presentation state를 만든다. 전투 상태의 로딩이나 실패는 일정 조회·편집·완료 및 occurrence 단위 보상 성공을 막지 않는다.
- 운영 화면에는 backend의 단일 활성 몬스터 한 마리만 표시한다. UI 컴포넌트와 sample·Preview는 배치 검증을 위해서만 0~4마리를 지원하며, Room schema와 `CombatSnapshot`의 단일 활성 몬스터 계약은 변경하지 않는다.
- Battle Map은 배경, 장식, 지면 그림자, player, monster, overlay를 독립 레이어로 합성한다. overlay 상단에는 `CharacterSnapshot`에서 매핑한 레벨·현재 레벨 구간 XP·필요 XP·골드를 표시하는 플레이어 진행 HUD를 둔다. 캐릭터·몬스터·HUD·HP·damage·text는 배경 PNG에 포함하지 않는다.
- 배치 좌표는 `0f..1f` 정규화 범위를 사용하고 기준점은 발 위치의 `bottom-center`다. 큰 y가 앞쪽이며 더 높은 z-order로 렌더링한다.
- 맵 높이는 `availableWidth / 2.4f`를 기준으로 `190dp..320dp`에 제한한다. 기본 초원은 교체 가능한 opaque PNG resource로 제공하고 decode 실패 시 단색이 아닌 Canvas 초원 fallback을 그린다.
- 플레이어 진행 HUD는 Material theme 색상과 하나로 통합된 한국어 TalkBack 설명을 사용한다. 전투 HUD인 체력바·이름·상태 효과·Stage 표시, 공격 애니메이션과 damage text는 렌더링하지 않으며 후속 합성도 `BattleOverlayLayer`에서 확장한다.

이 승인은 Calendar presentation 범위만 추가한다. occurrence 완료·RewardLedger·공격 event의 멱등성, 알림·exact alarm 권한과 무관한 핵심 일정 흐름, Room v4와 전투 계산 정책은 변경하지 않는다.

**구현 상태**: Calendar 첫 콘텐츠에 Battle Map을 통합했고 실제 `CombatRepository`의 player와 단일 활성 몬스터 snapshot을 표시한다. `CharacterRepository`의 레벨 구간 XP와 골드는 독립 HUD로 표시하며, 로딩과 0·상한 초과·잘못된 필요 XP를 안전하게 처리한다. 기존 상단 정보 Header는 제거했고 월간 캘린더의 요일 머리글과 날짜 cell offset은 `일`부터 시작한다. 재사용 component의 0~4 monster slot, 반응형 발 anchor 배치와 경계 clamp, 독립 layer, 배경 decode 실패 시 Canvas fallback, 한국어 상태·TalkBack 설명을 구현했다. 최초의 화면 전체 단일 scroll과 visible HP·attack/damage 미표시 상태는 아래 Calendar Combat Feedback v1 구현으로 교체됐으며, 다중 활성 몬스터 backend와 몬스터 이름·상태 효과·Stage HUD는 여전히 구현하지 않았다.

## 승인된 후속 범위: Calendar Combat Feedback v1

Calendar Combat Feedback v1은 Calendar Battle Map에 occurrence 실패 입력과 현재 전투 결과의 일회성 시각 피드백을 추가하는 Post-MVP 후속 기능이다. 이 범위는 Calendar Battle Map v1의 화면 전체 단일 scroll 계약만 교체하며, 기존 일정·보상·전투 backend의 멱등성과 단일 활성 몬스터 계약은 변경하지 않는다.

- 시스템 inset 다음에는 고정 Battle Map을 두고 그 아래에 독립적으로 스크롤하는 Calendar content를 둔다. 고정 영역은 한 Row의 레벨·골드 아이콘/값·현재/필요 EXP와 그 아래 progress bar, player와 단일 active monster, actor 상단의 두 HP bar 및 모든 전투 effect를 포함한다. scroll 영역은 월 이동, 요일·날짜 grid, 선택 날짜, 완료/실패 요약, 추가 버튼, task 목록과 빈 안내를 포함한다.
- occurrence 표시 상태는 `TODO`, `COMPLETED`, `FAILED` 세 값이다. 사용자는 `TODO` occurrence를 수동 실패로 확정하고 선택한 `FAILED` occurrence의 실패를 취소할 수 있다.
- 실패 취소는 task 표시 상태만 `TODO`로 되돌린다. 이미 적용된 monster damage, 확정된 combat event와 그 결과는 삭제하거나 되돌리지 않는다. 실패 취소 뒤 늦게 완료하면 기존 occurrence RewardLedger와 player attack 계약을 따르며, 같은 occurrence를 다시 실패 처리해도 `(taskId, occurrenceDate)` monster event가 이미 존재하면 두 번째 damage나 animation을 만들지 않는다.
- 수동 실패는 실패 상태를 먼저 영속한 뒤 monster attack을 즉시 best-effort로 시도한다. 실패 영속에는 성공했지만 combat 처리만 실패하면 기존 reconciliation이 복구한다. `MANUAL_FAILURE`와 기존 `MISSED_DEADLINE`은 같은 `monster_attack_events` occurrence key를 사용하므로 원인과 관계없이 피해는 한 번만 적용된다.
- player/monster 현재 HP, 기존 occurrence reward, active monster와 Stage 진행은 Room source state다. attack·hit·death·spawn 및 damage text는 새 combat event가 처음 확정될 때만 소비하는 replay 없는 transient transition이며 process 재시작이나 같은 event 재조회 때 다시 재생하지 않는다.
- 이 feedback 범위 자체는 경제 보상을 추가하지 않았다. 이후 Post-MVP Combat Rewards v1이 신규 occurrence의 direct reward를 hit XP와 처치 추가 XP·gold로 교체하며 전리품 제외와 처치 `HP_RECOVERY`를 유지한다. monster 치명 피해 뒤 처리는 아래 Severe Injury v1이 교체한다.
- 일반 화면의 Battle Map 높이는 기존 `190dp..320dp` 계약을 유지한다. 저높이 화면에서는 Calendar scroll viewport를 확보하기 위해 `150dp..190dp` compact-height 범위를 허용한다. 두 HP bar는 별도 절대 좌표가 아니라 actor placement geometry에서 actor 상단 위치를 계산해 배치한다.
- 이 범위 당시 공격 effect는 Compose Material icon과 translation·shake·flash·alpha만 사용하고 새 bitmap 또는 sound asset을 요구하지 않았다. pixel bitmap의 기존 최근접 확대 계약은 유지하며, 후속 효과음은 Battle Sound Effects v1 범위에서만 추가한다.

이 승인은 UI가 Room DAO나 WorkManager를 직접 호출하도록 허용하지 않는다. 알림·exact alarm 권한이 거부되거나 전투 effect 표시가 실패해도 일정 생성·실패 취소·완료와 occurrence 보상은 계속 동작해야 한다.

**구현 상태**: Room v6의 occurrence별 `failure_logs`와 `monster_attack_events.trigger`, `TaskOccurrenceStatus`의 세 상태, 실패·실패 취소 UseCase를 구현했다. Room v8에서는 신규 자동 마감 실패의 failure source와 `MISSED_DEADLINE` event를 같은 reconciliation transaction에서 확정하고, 기존 자동 event의 누락 failure source를 `MIGRATION_7_8`에서 멱등 backfill한다. 실패 취소는 failure 원천만 삭제해 `TODO`로 복원하고 이미 확정된 monster event와 player HP는 롤백하지 않는다. application-scope `RoomCombatRepository`는 공격 transaction을 직렬화하고 새 `APPLIED` event에 대해서만 `replay = 0` transition을 방출하며, `CalendarViewModel`이 소유한 buffered actor가 event key 중복을 제거하고 attack·hit·death·spawn 및 전투 불능·중상·응급 회복 phase를 한 번에 하나씩 소비한다. Calendar는 고정 Battle Map과 독립 `LazyColumn`으로 구성되고 한 Row HUD, actor 상단 HP bar, 저체력·damage·사망·등장·상태 변화의 한국어 TalkBack/live region 피드백과 완료·실패·실패 취소 action을 제공한다.

## 승인된 후속 범위: Character Layer Runtime Composition v1

Character Layer Runtime Composition v1은 캐릭터 외형을 단일 시트 첫 타일이 아니라 독립 `64×64` layer로 합성하고, 같은 render state를 Character 화면과 Calendar Battle Map에서 공유하는 Post-MVP presentation·local persistence 기능이다.

- geometry canonical `body_base`와 hair·hands·default/adventure 의상·headgear·accessory·3분할 sword의 15개 source를 같은 원점과 승인된 z-order로 source-over 합성한다. 생성된 `512×128` 시트와 preview는 debug·golden 검증 자산이며 runtime source가 아니다.
- Room v5는 `character_appearance`와 `character_equipped_items`에 appearance와 equipped item id를 저장한다. v4→v5 migration은 기존 캐릭터 상태를 보존하면서 현재 대표 기본 loadout을 멱등하게 추가한다.
- `CharacterRepository`는 catalog 검증을 거친 appearance·equipped item update와 두 상태가 포함된 `CharacterSnapshot` 관찰을 제공한다. UI는 DAO를 직접 호출하지 않는다.
- Character 화면과 Calendar Battle Map player는 같은 `CharacterRenderState`와 cached composer를 사용한다. layer decode 실패는 빈 캐릭터 렌더로 격리하고 일정 조회·편집·완료·보상과 전투 상태를 실패 처리하지 않는다.

이번 Character Layer Runtime Composition v1 범위 자체에는 외형 선택 화면, inventory, item ownership·획득, 사용자가 수행하는 장착 흐름, 장비 stat 또는 전투 modifier가 없다. 이 중 gameplay 장비 소유·구매·장착·능력치와 Shop·Inventory UI는 아래 Post-MVP Equipment Shop and Inventory v1에서 별도 승인한다. 기존 appearance Repository와 ViewModel command는 외형 fallback 경계이며 gameplay ownership을 뜻하지 않는다. occurrence 완료, RewardLedger와 전투 event 멱등성, 알림 권한 독립성도 변경하지 않는다.

**구현 상태**: Room v5 migration과 4-row fresh character 초기화, slot catalog 검증, 15개 runtime asset, 2단계 cache composer 및 Character/Battle shared renderer를 구현했다. 64개 loadout 조합과 runtime golden equality, unit·Compose·에뮬레이터 검증을 완료했다.

## 승인된 후속 범위: Post-MVP Equipment Shop and Inventory v1

Post-MVP Equipment Shop and Inventory v1은 기존 골드 경제와 순수 Kotlin 장비 modifier 계산 계약 위에 gameplay 장비의 판매·소유·장착·능력치 적용과 사용자 UI를 추가하는 후속 기능이다. 이 승인은 MVP의 외형·수집형 상점 계약을 소급 변경하지 않으며, 기존 일정 occurrence 보상과 양방향 전투 event의 멱등 경계도 변경하지 않는다.

- gameplay 장비 type과 slot은 각각 `WEAPON`, `HELMET`, `CHEST`, `LEGS`, `GLOVES`, `SHOES`, `ACCESSORY` 일곱 값으로 고정한다. 기존 순수 검증용 `EquipmentSlot.HEAD`, `TOP`, `BOTTOM`, `SHOES`, `ACCESSORY`, `WEAPON`, `PET`는 이 일곱 slot으로 교체하고 `PET`은 승인 범위에서 제거한다.
- gameplay 무기는 단일 `WEAPON` slot을 유지하면서 `WeaponType.LONGSWORD`, `DAGGER`, `SPEAR`, `BLUNT` 속성으로 세분화한다. subtype은 장비와 함께 저장·복원하고 상점·인벤토리에 `무기 · 장검`처럼 한국어로 표시한다. 새 subtype은 slot이나 구매 transaction을 늘리지 않고 enum·문자열 mapping·catalog 항목을 추가하는 방식으로 확장한다.
- 기존 네 기본 스탯 `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`와 8개 `DerivedStats`를 그대로 사용한다. 콘텐츠의 민첩 계열 효과는 `FOCUS`, 지능 계열 효과는 `WILLPOWER`에 대응하며 agility·intelligence 원천 스탯이나 Room 컬럼을 추가하지 않는다.
- 장비를 구매하거나 소유한 것만으로는 능력치가 오르지 않는다. 캐릭터별 `character_equipment`에 실제 장착된 gameplay 장비의 modifier만 파생 스탯 계산에 포함한다. `CHEST`와 `LEGS`는 동시에 장착할 수 있고 장비 교체는 대상 slot 하나만 바꾼다.
- 캐릭터별 같은 equipment 소유는 한 번만 허용하고 quantity를 두지 않는다. 구매 transaction은 최신 골드 잔액, 판매 상태, 요구 레벨, 중복 소유, type/slot mapping을 다시 검증한 뒤 골드 차감과 `owned_equipment` row 추가를 함께 확정한다.
- 장착 transaction은 소유권과 type/slot 일치를 검증하고 대상 `character_equipment` slot 교체와 `MAX_HP` 변화에 따른 현재 HP 비율 보존을 함께 확정한다. 계산된 level과 8개 파생값은 계속 Room에 저장하지 않는다.
- 해제 transaction은 대상 `character_equipment` row만 제거하고 소유권은 보존한다. 활성 상태이상을 포함한 `MAX_HP` 변화의 현재 HP 비율과 사용자가 해제한 대상 appearance fallback의 기본 복장 전환을 함께 확정하며, 이미 빈 slot은 멱등 성공한다.
- Room v6의 `character_equipped_items.topId`와 `bottomId`는 이미 분리된 appearance layer이며 gameplay ownership이 아니다. 이 테이블은 기존 사용자 진행을 보존하는 외형 fallback으로 유지하고, 실제 소유·장착·modifier source는 새 정규화 테이블로 분리한다.
- 저장 호환 입력의 `ARMOR`와 `TOP`은 `CHEST`, `BOTTOM`은 `LEGS`, `HEAD`는 `HELMET`으로 해석한다. 기존 Room v6에는 `ARMOR` slot 값이 없으며, 상·하의를 구분할 수 없는 외부·레거시 `ARMOR`는 단일 상체 방어구라는 가장 보수적인 의미를 택해 `CHEST`로 변환한다.
- `Shop`은 `Calendar`, `Character`에 이은 세 번째 하단 top-level destination이고 `Inventory`는 Shop에서 여는 nested destination이다. 구매 성공 후에는 `바로 장착`, `인벤토리로 이동`, `계속 쇼핑` 세 선택을 제공한다.
- Shop·Inventory·구매·장착·해제의 사용자 노출 문구와 TalkBack 설명은 `app/src/main/res/values/strings.xml`의 한국어 기본 문자열을 사용한다. UI는 Repository 또는 명확한 UseCase만 호출하며 Room DAO를 직접 호출하지 않는다.

**구현 상태**: Room v7에 `equipment`, `equipment_modifiers`, `owned_equipment`, `character_equipment`를 추가했고 Room v12에서 현재 18종 장비 catalog를 명시적 ID로 멱등 seed한다. `MIGRATION_6_7`은 기존 일정·보상·캐릭터·appearance·전투 source를 보존하고 네 장비 테이블을 빈 상태로 추가하며, v6에 존재하지 않는 `ARMOR` row나 기존 `topId`·`bottomId`를 gameplay 소유권으로 변환하지 않는다. `MIGRATION_10_11`은 `character_equipped_items`에 nullable `glovesId`를 추가하고, `MIGRATION_11_12`는 `equipment.weaponType` nullable 컬럼을 추가해 기존 `WEAPON` row만 `LONGSWORD`로 backfill한다. 비무기 row는 null을 유지하며 일정·보상·소유·장착·전투·알림 원천 상태를 보존한다. 구매는 최신 Room 상태를 transaction 안에서 다시 검증해 골드 차감과 unique 소유 row를 함께 확정하고, 장착은 소유권·type/slot을 검증해 대상 slot과 HP 비율을 함께 갱신한다. 해제는 대상 장착 row만 제거해 소유권을 보존하고, 대상 appearance fallback과 활성 상태이상을 포함한 HP 비율을 같은 transaction에서 갱신하며 빈 slot을 멱등 성공으로 처리한다. 실제 `character_equipment` modifier는 Character 파생 능력치, 새 일정 보상·플레이어 공격 snapshot과 현재 Combat 계산에 연결했다.

이 범위에서 `Calendar`, `Character`, `Shop` 세 하단 destination과 Shop의 nested `Inventory`를 연결했고, 이후 Monster Compendium v1이 네 번째 `Compendium` destination을 추가했다. Shop은 top-level back이 없는 고정 app bar와 고정 하단 navigation 사이의 단일 scroll을 사용하며, 콘텐츠는 `압축 대장장이 배너 → 캐릭터·일곱 slot·최종 능력치 통합 preview card → 판매 장비 제목 → category filter → 판매 목록` 순서다. 대장장이는 최소 `88dp` 배너 안의 `60dp` 고정 PNG와 한국어 인사만 제공하는 비대화형 presentation이며 Room 경제, 장비 catalog·소유·장착, RewardLedger 또는 ViewModel state의 source가 아니다. shared character renderer를 사용하는 preview는 실제 gameplay 장착 상태를 기본으로 보여주고, 판매 상품 card 본문을 선택하면 같은 slot의 검증된 외형과 modifier를 비영속 교체한 구매 전 모습 및 공격력·최대 체력·방어력 delta를 표시한다. 이 선택은 Room, 골드·소유, current HP를 바꾸지 않으며 실제 공용 render와 능력치는 장착 transaction commit 뒤에만 갱신된다. slot 선택은 관리 dialog에서 같은 type category의 장비 목록으로 이동하거나 현재 장비를 해제할 수 있고, Inventory의 장착 중 장비도 직접 해제할 수 있다. 구매·장착·해제 transaction이 commit되면 별도 새로고침 없이 각 source의 골드·소유·slot·같은 slot 비교·최종 능력치·검증된 render loadout을 갱신하며, 해제한 item은 계속 소유 목록에 남는다. `CHEST`와 `LEGS`는 preview에서도 독립 slot으로 유지한다.

상점은 일곱 type filter, 상품 본문 preview 선택과 분리된 48dp 상세 action, card의 직접 구매 action, 장비 상세의 같은 slot 비교, 구매 확인과 세 가지 구매 성공 action을 제공하고, 인벤토리는 소유 장비의 장착·교체·해제 상태를 제공한다. 상점 카드는 요구 레벨을 항상 표시하고 부족하면 잠금과 한국어 이유를 함께 제공한다. 보유 장비는 색·테두리·체크 icon·`보유 중` text를 함께 사용하며, 일반 판매 장비에는 중복 `판매 중` badge를 두지 않고 판매 중지 장비에만 `판매 중지`를 표시한다. 일반 preview는 캐릭터 `144dp`·slot `68dp`, 폭 `340dp` 미만 또는 font scale `1.5` 이상의 compact preview는 캐릭터 `120dp`·slot `64dp`를 사용하며 일곱 slot 모두 최소 48dp target을 유지한다. 모든 사용자 문구와 장비 fallback 설명은 한국어 resource를 사용한다. 18종 중 액세서리 2종을 제외한 16종에는 canonical과 byte-identical runtime bitmap 및 유효한 `imageKey`·`layerKey` mapping이 연결됐다. 기존 `낡은 검(1001)`과 `철 장검(1002)`은 `LONGSWORD`, 신규 `물푸레나무 창(1017)`은 `SPEAR`·일반·레벨 1·50 골드·공격력 +4, `강철 철퇴(1018)`는 `BLUNT`·희귀·레벨 12·780 골드·공격력 +12·힘 +4·치명타 피해 +400bp로 승인한다. 액세서리 2종과 null·unknown·decode 실패만 type별 Material icon placeholder와 기존 appearance fallback을 유지하며, 이 fallback을 gameplay 소유권으로 해석하지 않는다.

이 후속 기능은 로컬 저장만 사용한다. 외부 Google Calendar 읽기/쓰기, 계정·서버 동기화, 결제·유료 재화는 계속 제외하며 알림·exact alarm 권한이 거부되어도 일정 생성·완료·보상과 로컬 장비 조회는 동작해야 한다.

## 승인된 후속 범위: Post-MVP Severe Injury v1

Severe Injury v1은 치명 monster attack의 결과를 지속 가능한 상태이상과 명시적인 회복 조건으로 연결한다. 기존 occurrence·RewardLedger·양방향 attack key, 실패 reconciliation, 장비와 알림의 독립 transaction은 유지한다.

- 치명 피해는 먼저 `0 HP` 전투 불능으로 확정한다. 같은 transaction에서 중상을 적용 또는 갱신하고, 중상 적용 뒤 유효 최대 체력의 `50%`를 내림한 최소 1 HP로 응급 회복한다.
- 중상 v1은 활성 동안 유효 최대 체력과 공격력을 각각 20% 낮춘다. base stat, 장비 modifier와 계산된 파생값을 저장 변경하지 않으며 결과는 최소 1이다.
- 중상은 적용 뒤 24시간 또는 서로 다른 occurrence 완료 3회 중 먼저 충족된 조건으로 해제된다. 완료 credit은 character·effect type·revision·task·occurrence date 조합으로 한 번만 기록하므로 완료 취소 뒤 재완료와 중복 command가 회복을 앞당기지 않는다.
- 재패배는 중상을 중첩하지 않고 revision을 증가시켜 24시간과 완료 3회 조건을 갱신한다. 이전 revision의 완료 credit은 새 revision을 감소시키지 않는다.
- 세 번째 완료는 completion·RewardLedger·player attack outbox·회복 credit·중상 해제를 하나의 Room transaction으로 확정하고, 복원된 스탯을 그 공격 source snapshot에 사용한다. 실패하면 모두 rollback하며 XP·골드 지급 공식은 변경하지 않는다.
- 치명 attack의 replay 없는 표시 순서는 `전투 불능 → 중상 적용 또는 갱신 → 응급 회복`이다. 화면 회전·탭 재진입·process 재시작과 기존 event 재조회로 다시 재생하지 않으며, 상태 제거·만료는 별도 안내만 한 번 표시하고 current HP를 자동 치유하지 않는다.
- Character와 Calendar Battle Map은 한국어 중상 배지, 20% 감소와 남은 완료 수를 합친 TalkBack 설명, 최소 48dp 상세 action과 한국어 live region을 제공한다. 만료는 Repository가 `AppClock`을 기준으로 조정하며 UI는 DAO를 직접 호출하지 않는다.
- Battle Map의 중상 badge는 player actor의 `left`, `top`, `width`, `height`를 바꾸지 않는다. HP bar와 badge만 HUD·map 경계 안에서 clamp하거나 축소하므로 중상 적용 전후 캐릭터 좌표는 같다.

**구현 상태**: Room v13의 `character_status_effects`와 `status_effect_recovery_occurrences`, `MIGRATION_12_13`, `RoomStatusEffectRepository`, 공통 유효 스탯 계산, 치명 attack transaction과 ordered lifecycle, Character·Battle Map UI를 구현했다. migration은 두 테이블을 빈 상태로 추가하고 기존 일정·보상·캐릭터·장비·알림·Stage·attack event와 경제 상태를 보존하며 과거 치명 event를 소급 변환하지 않는다.

## 승인된 후속 범위: Monster Compendium v1

Monster Compendium v1은 로컬 전투 이력을 읽어 사용자가 만난 몬스터를 확인하는 네 번째 top-level `도감` 화면이다. `캘린더`, `캐릭터`, `상점`의 기존 navigation과 상태 복원 계약을 유지하며, 도감 안의 목록과 상세는 nested destination으로 둔다.

- 도감 root에는 현재 동작하는 `몬스터` category 하나만 표시한다. 펫과 맵은 향후 같은 category·목록·상세 route 구조로 확장할 수 있지만, v1에는 해당 category·route·placeholder를 구현하지 않는다.
- 몬스터 목록은 다섯 종의 고정 수집 slot을 3~5열 grid로 표시한다. 발견한 slot만 실제 한국어 이름과 검증된 pixel sprite를 공개하고, 미발견 slot은 공통 `???`, `?`와 잠금 표시만 사용해 실제 이름·sprite·설명 resource를 노출하지 않는다.
- 몬스터가 활성 전투 인스턴스로 처음 등장하면 즉시 발견한 것으로 본다. 업데이트 전에 저장된 현재·처치 몬스터 인스턴스 이력도 앱 시작 또는 재관찰 시 소급 반영하되, 별도 발견 ledger나 Room migration은 만들지 않는다.
- 목록은 발견 수/전체 수와 수집률을 표시하고, 앱바 이름 검색과 `전체`·`발견`·`미발견` filter를 제공한다. 검색은 발견한 종의 resource 기반 한국어 이름만 대상으로 하므로 미발견 이름을 입력해도 결과나 단서를 만들지 않는다.
- 발견 card 선택은 같은 화면의 선택 preview를 갱신하고 preview action은 공용 외형 상세를 `ModalBottomSheet`로 연다. 미발견 card는 선택 가능하지만 route나 상세를 열지 않고 아직 발견하지 못했다는 replay 없는 한국어 안내만 표시한다.
- 기존 `compendium/monsters/{species}` 상세 route는 호환을 위해 유지한다. 발견 종은 공용 상세 본문을 사용하고, 미발견 종의 직접 route는 실제 이름·sprite·설명 resource가 없는 일반 잠금 상태만 표시한다.
- 발견 상세는 종족의 시각적 특징만 설명한다. 종족별 고정 능력치, biome, 스킬, 처치 횟수와 전리품은 표시하거나 암시하지 않는다.

**구현 상태**: Room v14의 기존 `monster_instances` 전체 Flow를 `MonsterDiscoveryPolicy`로 투영해 현재·과거 HP 0 행을 포함한 발견 종족 집합을 만들고, 새 활성 몬스터 행이 생성되면 즉시 갱신한다. `MonsterCompendiumViewModel`은 발견 resource와 resource 없는 미발견 slot을 privacy-preserving UI model로 투영하고 발견 count/percent, 검색·filter·선택과 replay 없는 안내를 비영속 화면 상태로 관리한다. 고정 앱바 아래 단일 3~5열 grid가 수집 요약·filter·선택 preview·card를 스크롤하며, 발견 상세는 기본적으로 sheet를 사용하고 `compendium → compendium/monsters → compendium/monsters/{species}` 호환 navigation은 유지한다. 펫 재화·장착·보너스·농장·맵, 새 몬스터와 새 art는 추가하지 않았다.

## 승인된 후속 범위: Battle Sound Effects v1

Battle Sound Effects v1은 새 전투 결과의 replay 없는 시각 transition에 짧은 원본 효과음을 결합하는 presentation 후속 기능이다. HP Flow나 현재 체력값에서 소리를 추론하지 않으며 Room 전투 원천과 밸런스를 변경하지 않는다.

- `RoomCombatRepository.events`가 fresh Room 공격 결과에 대해 처음 방출한 `CombatTransition`만 효과음 source로 사용하고, `BattleAnimationController`의 단일 buffered actor가 animation과 효과음 순서를 함께 조정한다. persisted `APPLIED` 재처리, 화면 회전·재구성·재진입과 다음 monster 생성은 과거 음향을 재생하지 않는다.
- `BattleSfx`는 `PLAYER_ATTACK`, `MONSTER_ATTACK`, `MONSTER_HIT`, `PLAYER_HIT`, `MONSTER_DEFEATED`, `PLAYER_DEFEATED` 여섯 값이다. 한 공격의 시작·피격·치명 effect는 같은 안정적 combat event id를 공유할 수 있고 `eventId + BattleSfx` 조합으로 각각 한 번만 재생한다.
- player는 `PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시 → 치명 시 MONSTER_DEFEATED → MONSTER_DYING`, monster는 `MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시 → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능` 순서다. defeat 음은 hit 음 뒤 death animation 시작에 재생한다.
- `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있는 공격에만 허용한다. 중상 적용·갱신, 응급 회복, 상태 제거와 다음 monster spawn에는 death 또는 revive 음을 추가하지 않으며 사용자 표현은 `전투 불능`을 유지한다.
- 앱에는 기존 전용 설정 화면이나 DataStore가 없으므로 application-scope SharedPreferences 설정을 기본 켜짐으로 추가한다. 하단 navigation은 `캘린더 → 캐릭터 → 상점 → 도감 → 설정` 다섯 top-level destination이 되며 설정 화면은 `효과음` Switch 하나를 제공한다. off는 음향만 억제하고 animation, damage text, HP bar와 status text는 유지한다.
- application-scope `SoundPool`은 process당 한 번 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 만들고 여섯 raw WAV를 preload한다. load 전·background·released 상태의 요청은 지연 재생하지 않고 폐기하며 복귀 뒤에는 새 effect만 재생한다. 시스템 media volume과 DND를 우회하거나 audio focus를 독점하지 않는다.
- 여섯 WAV는 저장소의 결정론적 합성기로 직접 생성하고 외부 음원을 다운로드하지 않는다. audio 준비·재생 실패와 설정 off는 일정·보상·전투·알림·장비 transaction을 차단하거나 롤백하지 않는다.

Room database version은 v14를 유지하고 새 migration을 만들지 않는다. 피해·보상·Stage·spawn·중상 수식, occurrence·RewardLedger·양방향 공격 event 멱등성과 외부 Google Calendar 제외도 변경하지 않는다. Calendar Combat Feedback v1의 sound 비포함 설명과 Monster Compendium v1의 네 번째 `도감` 설명은 각 당시 범위이며, 이 후속 승인이 효과음과 현재 다섯 destination navigation을 추가한다.

## 승인된 후속 범위: Gameplay UI·Equipment·Notification Permission Polish v1

이 범위는 ADR-025에 따라 전투·상점·설정 presentation과 장비 appearance 호환을 다듬는다. ADR-024 당시의 `효과음` Switch 하나와 Room v14 무 migration 설명은 그 당시 범위이며, 현재 앱은 아래 Room v15·일반 알림 권한 계약을 함께 사용한다.

- 빈 gameplay 장착 상태는 body·hair·기본 손을 유지하고 `top_default`·`bottom_default`·`shoes_default`의 회갈색 중립 훈련복을 표시한다. 투구·장갑·장신구·무기 overlay는 비워 두며, 이 appearance fallback을 장비 소유권이나 실제 장착으로 해석하지 않는다.
- 기존 adventure layer는 고정 ID `1019..1025`의 모험가 7부위 상점 세트로 제공한다. 일곱 장비는 모두 `UNCOMMON`, 요구 레벨 5, 두 modifier를 가지며 `gloves_adventure`와 기존 3분할 검 source를 포함한다. migration은 이 세트를 자동 소유·장착하지 않는다.
- Room v15의 비파괴 `MIGRATION_14_15`는 `character_equipped_items`의 appearance fallback만 빈 loadout으로 바꾼다. `owned_equipment`, `character_equipment`, HP·상태이상, 일정·완료·RewardLedger·전투 event와 알림 상태는 보존한다.
- Shop card와 detail은 같은 typed action을 사용한다. 미소유이며 구매 가능하면 `구매`, 미소유이며 조건을 충족하지 못하면 `구매 불가`, 소유했지만 미장착이면 `장착`, 현재 장착 중이면 `해제`를 표시한다. action은 `104dp × 48dp`로 우측 하단에 고정하고, 공격력·최대 체력·방어력 summary는 현재값과 delta 유무 또는 큰 글꼴에서도 같은 cell bounds를 유지한다.
- 선택한 장비의 해제가 성공하면 선택·상세·확인과 구매 전 preview를 함께 해제하고 최신 실제 빈 slot 외형으로 돌아간다. 다른 장비를 해제했거나 transaction이 실패하면 현재 선택과 preview를 유지한다.
- Settings는 효과음과 별도로 일반 알림 권한 상태를 `확인 중`, `허용됨`, `권한 필요`, `알림 채널 꺼짐`, `상태 확인 실패`로 표시한다. 사용자의 action으로 Android 13 이상 runtime permission 또는 앱 전체·`todo_task_reminders` 채널 설정을 열고, launcher 복귀와 화면 resume에서 다시 확인한다.
- Settings의 일반 알림 관리에는 exact-alarm permission·special access를 포함하지 않는다. exact alarm은 기존 일정 reminder 설정 흐름에서만 다룬다. 일반 알림 권한 거부·dismiss·조회 실패는 효과음, navigation, 일정·완료·보상·전투·구매·장착을 차단하거나 롤백하지 않는다.

**구현 상태**: Room v15 migration과 `15.json`, 25종 장비 catalog, schema v6 캐릭터 layer·preview, equipment id 기반 owned projection과 공통 Shop action, 고정 stat/action layout, 선택 장비 해제 cleanup, Settings의 typed 일반 알림 capability와 Android launcher 경계를 테스트 우선으로 구현했다.

## 성공 기준

- 사용자는 앱을 열고 10초 안에 오늘의 할 일을 확인할 수 있다.
- 사용자는 새 일정을 30초 안에 등록할 수 있다.
- 반복 일정 완료 시 해당 날짜만 완료 처리된다.
- 같은 일정을 반복 완료해도 XP와 골드가 중복 지급되지 않는다.
- 알림 권한이 거부되어도 핵심 일정 관리와 보상 흐름은 정상 동작한다.

## 디자인 방향

- 전체 톤은 픽셀 RPG 감성이지만, 캘린더와 목록은 생산성 도구처럼 읽기 쉬워야 한다.
- 캐릭터, XP, 골드, 상점 영역에서 게임성을 강하게 보여준다.
- 일정 제목, 날짜, 시간, 완료 상태는 장식보다 가독성을 우선한다.
