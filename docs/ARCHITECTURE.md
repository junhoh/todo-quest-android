# 아키텍처

## 개요

Todo Quest는 서버 없이 동작하는 로컬 우선 Android 앱이다. UI는 Jetpack Compose로 작성하고, 도메인 상태는 ViewModel과 Repository를 통해 관리한다. 현재 구현된 영속 데이터는 Room v15에 저장한다. v8은 기존 자동 마감 attack event의 누락 failure source를 복구하고, v9은 전투 보상 snapshot을, v10은 task 원본과 분리된 `task_reminders` source setting·예약 상태를, v11은 appearance fallback에 nullable `glovesId`를, v12는 equipment에 nullable `weaponType`을, v13은 상태이상과 revision별 occurrence 회복 credit을, v14는 player attack의 nullable completion 난이도와 별도 difficulty balance version을 추가한다. v15의 비파괴 `MIGRATION_14_15`는 `character_equipped_items` appearance fallback만 빈 중립 loadout으로 갱신하고 실제 ownership·`character_equipment`와 다른 원천 상태를 보존한다. 앱 composition root는 단일 Room database와 `AppClock`을 일정·캐릭터·전투·장비·상태이상·알림 Repository가 공유하게 하고, Navigation Compose로 Calendar·Character·Shop·Compendium·Settings 다섯 top-level 화면, Shop의 nested Inventory와 Compendium의 nested Monster 목록·호환 상세를 연결한다. Task Reminder v1과 Delivery Reliability v2는 `RoomReminderRepository → ReconcileTaskReminderUseCase/DeliverReminderUseCase → ReminderScheduler/ReminderPublisher` 경계로 staged exact alarm과 notification을 처리하며, UI는 Android scheduler나 DAO를 직접 호출하지 않는다. Monster Combat v1의 reconciliation worker와 Calendar의 Battle Map presentation은 같은 application 범위 container의 `CombatRepository`를 사용한다. Monster Compendium은 이 Repository의 발견 종족 projection만 관찰하고, privacy-preserving ViewModel projection에서 발견 count·검색·filter·선택과 sheet 상태를 합성한다. `RoomStatusEffectRepository`는 활성 중상 관찰, `AppClock` 만료 조정과 제거 event를 소유하고 Character·Calendar ViewModel에 도메인 상태만 제공한다. 캐릭터 appearance와 equipped item id는 `CharacterRepository`가 관찰·변경하고, Character와 Battle Map은 같은 독립 layer render state를 사용한다. Calendar Combat Feedback v1의 occurrence 실패 원천 상태, application-scope replay 없는 전투 transition, ViewModel-scope 직렬 presentation actor와 고정 Battle Map·독립 Calendar scroll까지 구현했다. Equipment Shop and Inventory v1은 정규화된 gameplay 소유·장착 source, 원자 구매·장착, 실제 장착 modifier의 Character·Task·Combat 계산 연결과 Shop·Inventory UI를 구현하되 appearance loadout은 외형 fallback으로 보존한다. Shop은 `EquipmentStoreSnapshot` 안의 equipment id 기반 owned lookup, 실제 장착 상태와 판매 장비별 비영속 구매 전 projection을 함께 관찰하고, `ShopViewModel`의 선택 command로만 preview 외형·최종 stat delta와 typed action을 전환한다.

첫 Calendar 진입의 one-shot notification permission과 reminder 저장 시 `알림 설정` CTA는 application-scope preference store와 UseCase, Calendar의 replay 없는 event로 구현했다. Settings의 일반 알림 권한 관리는 `ReminderScheduler` capability를 typed state로 바꿔 Compose launcher와 Android adapter에 전달하며 exact alarm을 포함하지 않는다. Combat Reward v2는 versioned balance catalog와 attack snapshot routing으로, 기존 18종 가격 완화는 v12에서 도입한 조건부 catalog update로 구현했고 v15에서도 보존한다. 고정 ID `1019..1025`의 adventure 7부위를 더해 현재 catalog는 25종이다. Task Difficulty Combat Balance v1은 combat reward version과 독립된 difficulty version `1`을 신규 completion attack에 snapshot하고, EXP HUD는 최소 `104dp`와 intrinsic content 폭을 유지하면서 label/value를 bar 양끝에 정렬한다. Severe Injury v1의 Room v13 원천과 `MIGRATION_12_13`은 v15에서도 보존한다.

Character Stat Allocation Guide v1은 Room 밖의 application-scope `CharacterGuideRepository`가 신규 설치 자동 표시 대상과 확인 상태만 보존한다. composition root는 Room을 초기화하기 전에 `todo-quest.db` 파일 존재 여부를 캡처하고, `SharedPreferencesCharacterGuideRepository → PrepareCharacterStatGuideUseCase/AcknowledgeCharacterStatGuideUseCase → CharacterViewModel → Compose Dialog` 경계로 전달한다. 이 안내는 Room v15 schema·migration, `CharacterRepository`의 stat allocation transaction과 기본·파생 능력치 공식을 변경하지 않는다.

ADR-020의 Task Difficulty Combat Balance v1과 Reminder Delivery Reliability v2는 Room v14와 Calendar·Android delivery 경계에 테스트 우선으로 구현했다.

ADR-024의 Battle Sound Effects v1은 현재 Room v14 source를 변경하지 않고 `RoomCombatRepository.events(replay = 0) → BattleAnimationController의 단일 buffered actor → BattleSfx effect → application-scope SoundPool` 경계를 추가한다. application-scope SharedPreferences 설정은 Room 밖에서 기본 켜짐 상태만 보존하며 Settings 화면은 Repository·ViewModel을 통해 이를 렌더링한다.

## ADR-020 구현 아키텍처 경계

난이도 전투 배율은 combat reward version과 분리된 `TaskDifficultyCombatBalanceConfig`와 player attack source snapshot으로 다룬다. difficulty balance version `0`은 nullable 난이도와 무관한 중립 `100%`, current version `1`은 `EASY` `100%`, `MEDIUM` `150%`, `HARD` `200%`다. Room v14는 `player_attack_events`에 nullable `sourceTaskDifficulty`와 기본값 `0`인 `taskDifficultyBalanceVersion`만 additive column으로 추가했다. `MIGRATION_13_14`는 기존 PENDING/APPLIED row를 null/version `0`으로 유지하고 현재 `todo_tasks.difficulty`를 join하거나 backfill하지 않는다.

신규 completion의 쓰기 흐름은 다음과 같다.

```text
최초 occurrence 완료
→ CompletionLog + RewardLedger + 상태이상 recovery credit transaction
→ completion 시점 task 난이도 + current difficulty version을 player attack source에 snapshot
→ 같은 (taskId, occurrenceDate) PENDING outbox 한 건 확정
→ 재완료·retry·반복 분할은 기존 source를 변경하지 않음
```

PENDING player attack은 저장된 `sourceAttack`에 저장된 MOMENTUM을 적용한 뒤 난이도 배율, 기존 치명타, 대상 방어력 순서로 피해를 계산한다. Combat Reward v2의 base hit XP와 kill bonus XP에는 같은 배율을 각각 정수 내림으로 적용하고 양수 결과를 최소 `1`로 유지한 뒤 합산한다. level 1 NORMAL 처치 총 XP는 쉬움 `23`, 보통 `34`, 어려움 `46`이며 비치명 hit XP는 `3/4/6`이다. kill gold, `GOLD_GAIN_BONUS`, 몬스터 level·grade와 reward version routing은 입력과 순서를 변경하지 않는다. APPLIED row는 저장된 피해·XP·gold snapshot만 반환한다.

알림 capability는 `POST_NOTIFICATIONS` runtime permission·앱 전체 notification switch와 API 26 이상 `todo_task_reminders` 채널 importance를 Android adapter에서 판정하고 Repository·UseCase에는 typed domain 상태만 전달한다. 앱 권한이 막히면 `POST_NOTIFICATIONS_REQUIRED`, 채널만 차단되면 `ReminderCapabilityStatus.CHANNEL_DISABLED`를 `ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED`로 mapping하며 UI는 각각 앱 알림 설정과 채널 설정을 여는 one-shot event만 처리한다. API 26 이상은 기존 high-importance channel을 보존하고 API 23~25는 notification 자체의 high priority와 기본 소리·진동을 요청한다. DND와 사용자 채널 선택은 capability를 우회하거나 자동 변경하는 입력이 아니다.

새 exact alarm의 materialization 순서는 다음과 같다.

```text
다음 유효 occurrence plan 계산
→ Room에 occurrence key + trigger + PENDING 조건부 stage
→ AlarmManager.setExactAndAllowWhileIdle()
→ 같은 key가 current이면 SCHEDULED 조건부 확정
→ 실패면 같은 key alarm best-effort 취소 + current key만 ERROR 정리
```

receiver의 발화 직전 조회는 persisted occurrence key와 `PENDING` 또는 `SCHEDULED` 상태에서만 active `TODO` occurrence를 반환한다. receiver가 scheduler 호출 중 먼저 plan을 `DELIVERED`로 claim하면 후속 `SCHEDULED` 또는 `ERROR` 갱신은 조건에 실패해야 한다. 따라서 바로 발화한 callback, stale callback, duplicate callback과 scheduler 실패가 일정 source나 이미 확정된 delivery를 역행시키지 않는다.

Calendar occurrence projection은 task·completion·failure와 task reminder Flow를 Repository read 경계에서 함께 조합한다. reminder가 설정된 task만 occurrence 기준 actual local trigger를 `10분 전 · 전날 23:50`, `직접 설정 · 당일 08:00` 형식으로 렌더링하고 `POST_NOTIFICATIONS_REQUIRED`, `NOTIFICATION_CHANNEL_DISABLED`, `EXACT_ALARM_ACCESS_REQUIRED`에는 typed 복구 CTA를 제공한다. Compose는 AlarmManager·NotificationManager·DAO를 직접 호출하지 않는다.

Calendar의 `완료`·`실패`는 content-sized 시각 container와 텍스트·아이콘을 사용하되 실제·semantics target은 최소 `48dp`다. `PlayerProgressHud`는 기존 최소 `104dp`, intrinsic 폭, progress clamp와 merged 한국어 TalkBack을 유지하고 EXP label/value Row를 bar와 같은 폭으로 만들어 `EXP` 왼쪽 edge와 현재/필요 값 오른쪽 edge를 bar 양 끝에 맞춘다. 이 presentation 변경은 actor geometry, input lock과 occurrence command를 변경하지 않는다.

## ADR-024 승인 아키텍처 경계

Battle Sound Effects v1은 HP나 다른 영속 관찰값에서 effect를 역산하지 않는다. fresh Room 공격 transaction이 최초 확정되어 `RoomCombatRepository.events`가 replay 없는 `CombatTransition`을 방출한 경우에만 기존 `BattleAnimationController`가 전투 effect를 만든다.

```text
fresh player/monster attack transaction commit
→ RoomCombatRepository.events: MutableSharedFlow(replay = 0)
→ CalendarViewModel이 BattleAnimationController의 단일 buffered actor에 enqueue
→ 같은 timeline에서 BattlePresentationState와 typed BattleSfx effect를 순서대로 확정
├── Compose: animation·damage text·HP bar·status text 렌더링
└── application-scope BattleSfxPlayer: 설정·foreground·loaded 상태를 확인하고 즉시 재생 또는 폐기
```

`BattleSfx`는 `PLAYER_ATTACK`, `MONSTER_ATTACK`, `MONSTER_HIT`, `PLAYER_HIT`, `MONSTER_DEFEATED`, `PLAYER_DEFEATED` 여섯 값이다. `PlayerAttackStarted|MonsterAttackStarted`, `EntityHit`, `MonsterDefeated|PlayerDefeated`는 같은 공격의 안정적 combat event id를 공유할 수 있고 재생 identity는 `eventId + BattleSfx`다. persisted `APPLIED` 재처리, Flow 재구독, 화면 회전·재구성·재진입과 다음 monster 생성은 새 transition을 만들지 않으므로 과거 음향도 만들지 않는다.

player actor 순서는 `PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시 → 치명 시 MONSTER_DEFEATED → MONSTER_DYING`이고 monster actor 순서는 `MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시 → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능`이다. defeat effect는 hit 뒤 death animation 시작에 배치한다. `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있을 때만 만들고 상태 적용·갱신, 응급 회복, 상태 제거와 spawn에는 death/revive effect를 만들지 않는다.

기존 앱에는 전용 설정 화면과 DataStore가 없다. `BattleSfxSettingsRepository`는 application-scope SharedPreferences를 숨기고 key 부재를 enabled로 읽으며, `SettingsViewModel`은 `효과음` 한 개의 state와 변경 command만 노출한다. off는 `BattleSfxPlayer`만 억제하고 controller와 Compose 상태는 계속 진행한다. SharedPreferences 읽기·쓰기 실패나 audio 실패는 일정·보상·전투 command를 rollback하지 않는다.

application composition root는 `SoundPool`을 process당 한 번 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 만들고 여섯 raw WAV를 preload한다. load 완료 전·background·released 요청은 buffer하지 않고 폐기하며 foreground 복귀 뒤 새 effect만 받는다. 시스템 media volume과 DND를 우회하지 않고 audio focus를 독점하지 않는다. WAV는 저장소의 결정론적 합성기가 생성하며 외부 음원을 다운로드하지 않는다. 이 경계는 Room v14 schema와 migration, 피해·보상·Stage·spawn·중상 수식을 변경하지 않는다.

## ADR-025 승인 아키텍처 경계

Room v15의 `MIGRATION_14_15`는 `character_equipped_items`의 appearance fallback만 중립 빈 loadout으로 바꾼다. nullable overlay는 비우고 `topId`, `bottomId`, `shoesId`는 `top_default`, `bottom_default`, `shoes_default`로 설정한다. `owned_equipment`, `character_equipment`, equipment catalog·modifier, HP·상태이상, 일정·완료·RewardLedger·전투·알림 table은 읽거나 초기화하지 않는다. 고정 ID `1019..1025`의 adventure 7부위는 Repository의 멱등 seeder가 catalog로 추가하며 자동 ownership·장착을 만들지 않는다. schema v6 layer catalog는 중립 fallback, `gloves_adventure`와 legacy 3분할 sword key를 모두 해석한다.

Shop action projection은 Room row id와 catalog equipment id를 구분한다.

```text
RoomEquipmentRepository read transaction
→ EquipmentStoreSnapshot.ownedEquipmentByEquipmentId
   └── key == OwnedEquipment.equipmentId, character·catalog·equipped source 일관성 검증
→ ShopViewModel이 판매 장비별 ShopEquipmentAction 계산
   ├── 미소유 + 구매 가능 → Purchase(equipmentId)
   ├── 미소유 + 구매 불가 → PurchaseUnavailable(reason)
   ├── 소유 + 미장착 → Equip(ownedEquipmentId, slot)
   └── 소유 + 장착 중 → Unequip(equipmentId, slot)
→ Compose card/detail 공통 구매·구매 불가·장착·해제 action
```

선택 장비의 해제 transaction이 성공한 경우에만 `selectedEquipmentId`, 상세·확인 state와 temporary preview를 함께 비운다. 다른 장비 해제 또는 실패는 선택을 유지한다. stat summary는 세 동일 폭 cell의 label·현재값·delta 영역을 고정하고 card/detail action은 `104dp × 48dp` 우측 하단 경계를 공유한다. 이 state 정리는 Room ownership을 삭제하지 않고 Repository Flow가 제공한 최신 실제 loadout으로 복귀하는 presentation command다.

Settings의 일반 알림 권한 경계는 다음과 같다.

```text
ReminderScheduler.checkCapability(POST_NOTIFICATIONS)
→ SettingsViewModel: Loading | Available | Required | ChannelDisabled | CheckFailed
→ Settings Compose: 한국어 상태 + typed action
→ Compose launcher
→ AndroidReminderCapabilityAdapter.notificationPermissionSettingsAction()
   ├── Android 13+ runtime permission
   ├── package app notification settings
   └── todo_task_reminders channel settings
→ launcher result / ON_RESUME → 최신 capability 재조회
```

이 Settings 경계는 exact-alarm capability나 special-access 설정을 조회·요청하지 않는다. 기존 Calendar reminder 경계만 exact alarm을 소유한다. capability 조회·launcher·권한 거부 실패는 Settings presentation 안에 격리하고 효과음·navigation·일정·완료·RewardLedger·전투·구매·장착 transaction을 바꾸지 않는다. Compose는 DAO, `AlarmManager`, `WorkManager` 또는 Android capability 세부를 직접 호출하지 않는다.

Battle Map의 중상 status layout은 기존 actor placement를 입력으로 받아 player `left`, `top`, `width`, `height`를 그대로 반환한다. HP bar와 badge만 HUD reserved area와 map 경계 안에서 clamp하거나 축소하므로 status 유무는 actor geometry의 새로운 입력이 아니다.

## 디렉터리 구조

Android 프로젝트 생성 후 기본 구조는 다음을 따른다.

```text
app/src/main/java/com/todoquest/
├── MainActivity.kt
├── app/                  # 앱 composition root, navigation
├── core/                 # 공통 시간, 결과 타입, dispatcher, utility
├── data/
│   ├── local/            # Room database, entity, DAO, migration
│   ├── mapper/           # entity와 domain model 변환
│   └── repository/       # repository 구현
├── domain/
│   ├── model/            # 순수 Kotlin 도메인 모델
│   ├── repository/       # repository interface
│   └── usecase/          # 일정 완료, 보상 지급, 구매, reminder 계획·조정
├── feature/
│   ├── battle/           # Battle Map presentation 모델, 순수 배치 계산, Compose 레이어
│   ├── calendar/         # Battle Map을 통합한 월간/일간 캘린더 화면과 ViewModel
│   ├── task/             # 일정 작성/수정 화면
│   ├── character/        # 캐릭터 성장 화면
│   ├── shop/             # 상점/인벤토리 화면
│   ├── compendium/       # 도감 root와 몬스터 목록·상세 화면 및 ViewModel
│   └── settings/         # 효과음·일반 알림 권한 설정 화면과 ViewModel
├── audio/                # application-scope BattleSfx SoundPool player와 lifecycle
├── background/           # 전투 reconciliation WorkManager worker와 예약
├── notification/         # exact scheduler, publisher, alarm/restore receiver와 restore worker
└── ui/                   # theme, reusable Compose components, 캐릭터 layer catalog·composer
```

## 레이어 규칙

- UI는 ViewModel의 state를 렌더링하고 event를 전달한다.
- ViewModel은 UseCase를 호출하고 UI state를 Flow로 노출한다.
- UseCase는 UI command를 도메인 작업으로 전달하고 순수 정책을 호출한다.
- Repository 구현은 Room DAO와 scheduler를 조합하고, 여러 저장 변경이 필요한 command의 Room transaction과 멱등성 경계를 소유하되 UI 타입을 알지 않는다.
- DAO는 SQL과 entity만 다룬다.

## 주요 도메인 모델

- `TodoTask`: 반복 원본을 포함한 사용자의 일정 정의
- `RecurrenceRule`: 없음, 매일, 매주, 매월 반복 규칙
- `TaskOccurrence`: 특정 날짜에 표시되는 일정 발생분
- `TaskOccurrenceStatus`: 각 occurrence의 표시 상태 `TODO`, `COMPLETED`, `FAILED`. 완료와 실패 원천 기록에서 계산하며 반복 원본 상태를 바꾸지 않는다.
- `ReminderSetting`: `NONE`, `TEN_MINUTES_BEFORE`, `ONE_HOUR_BEFORE`, `CUSTOM_TIME` mode와 custom local time을 담는 task 원본 설정
- `ReminderOccurrenceKey`·`ReminderPlan`: 실제 alarm을 `(taskId, occurrenceDate)`와 trigger instant로 식별하는 materialized 다음 예약
- `ReminderScheduleState`: task별 설정, typed `ReminderScheduleStatus`와 현재 materialized plan을 묶은 source state
- `ReminderRepository`: Room의 task·reminder·completion·failure source를 조합해 다음 유효 plan과 발화 직전 유효 task를 제공하고 occurrence key 조건부 상태 갱신을 소유한다.
- `ReminderScheduler`·`ReminderPublisher`: Android exact alarm capability·예약·취소와 notification 게시를 각각 격리하는 도메인 경계. `POST_NOTIFICATIONS` capability 조회는 Settings에도 typed 상태로 제공하되 exact-alarm 설정 진입은 Calendar reminder 흐름만 소유한다.
- `CompletionLog`: occurrence 완료 기록과 완료 시각
- `RewardLedger`: occurrence별 실제 XP·골드, 정시·효율 판정, 반복 계보, 보상일과 balance version을 확정한 snapshot
- `PlayerCharacter`: 누적 XP·골드, 네 기본 스탯, 미배분 포인트와 무료 초기화 사용 여부를 담는 캐릭터 원천 상태
- `StatAllocation`: 힘·활력·집중·의지별 0 이상의 저장 대기 증가량을 한 command로 전달하는 typed 일괄 배분 값. 합계와 stat별 값을 제공하며 ViewModel draft 자체는 Room에 저장하지 않는다.
- `CharacterCurrentState`: 현재 HP, balance version과 갱신 시각처럼 계산만으로 복원할 수 없는 현재 상태
- `CharacterStatusEffect`: character id, typed effect, definition version, 적용·만료 시각, 남은 회복 완료 수, 활성 여부, revision과 마지막 mutation id를 담는 상태이상 원천 상태. 유효 여부는 `AppClock` instant와 현재 revision에서 계산한다.
- `StatusEffectDefinition`·`TemporaryStatEffect`: versioned 상태이상 정책과 파생 스탯 계산 입력. 중상 v1은 24시간 또는 occurrence 완료 3회까지 `MAX_HP`·`ATTACK`에 각각 `-2,000bp`를 적용하며 계산 결과는 Room에 저장하지 않는다.
- `StatusEffectRepository`: 활성 상태 관찰, `AppClock` 기반 만료 조정, 명시적 제거와 replay 없는 제거 event를 제공하는 도메인 경계. occurrence 회복 credit 쓰기는 완료 transaction을 소유한 `RoomTaskRepository`가 같은 database transaction에서 수행한다.
- `CharacterAppearance`: 현재 hair id를 담는 캐릭터 외형 원천 상태
- `EquippedItems`: head·top·bottom·gloves·shoes·accessory·weapon slot의 현재 appearance item id를 담는 render loadout 원천 상태. nullable slot은 미장착을 뜻하며 gameplay inventory·ownership을 뜻하지 않는다. ADR-013 이후에도 기존 사용자 진행을 위한 외형 fallback으로 유지한다.
- `DerivedStats`: level과 기본 스탯 및 modifier 입력에서 계산하는 8개 파생 능력치로, Room에는 저장하지 않는다.
- `CharacterSnapshot`: `PlayerCharacter`, appearance·equipped items, 계산된 level·XP 진행·파생 스탯, 현재 HP와 연속일·MOMENTUM을 화면 관찰용으로 묶은 값
- `CharacterRepository`: 기준 날짜의 `CharacterSnapshot`을 `Flow`로 관찰하고 catalog 검증을 거친 appearance·equipped item update, typed `StatAllocation` 일괄 배분과 stat reset command를 제공한다. `RoomCharacterRepository`가 profile·current state·appearance·equipped items·정시 ledger를 조합하고 배분 transaction에서 최신 profile과 실제 장착 modifier를 다시 읽는다.
- `CharacterStatGuideStatus`: 신규 설치에서 한 번 고정한 자동 표시 대상 여부와 사용자가 자동 안내를 확인했는지를 분리한 Android 비의존 상태다.
- `CharacterGuideRepository`: Room과 독립된 안내 preference를 읽고 자동 안내 확인을 멱등 저장한다. `SharedPreferencesCharacterGuideRepository`는 `stat_allocation_auto_eligible_v1`과 `stat_allocation_acknowledged_v1`을 별도 key로 사용한다.
- `CharacterRenderState`: `CharacterAppearance`와 `EquippedItems`를 UI 합성 입력으로 묶는 immutable presentation 상태. Room entity나 generated sheet 좌표를 포함하지 않는다.
- `MonsterDefinition`: backend `nameKey`와 유형, 세 능력치의 base·growth를 담는 versioned catalog 정의
- `MonsterInstance`: definition 식별자와 level·등급·현재 HP·balance version을 담는 Room 원천 상태. 유형은 definition에서 읽는다.
- `MonsterSpecies`: Battle Map이 고블린 정찰병·해골 병사·타락한 나무 정령·하피·슬라임의 sprite·한국어 이름·쓰러짐 안내를 선택할 때 쓰는 파생 presentation metadata. Room 원천 상태나 전투 상성이 아니다.
- `MonsterDiscoveryPolicy`: 저장된 모든 `MonsterInstance`의 Stage·encounter·grade·balance version을 명시적인 `MonsterSpeciesPolicy`에 전달하고 중복 종족을 제거해 발견 집합을 만드는 순수 Kotlin projection. HP가 0인 과거 인스턴스도 제외하지 않으며 지원하지 않는 balance version은 현재 정책으로 추측하지 않는다.
- `MonsterStats`: definition·instance·config에서 계산한 `maxHp`·`damage`·`defense`이며 Room에는 저장하지 않는다.
- `StageProgress`: Stage 진행, 활성 몬스터와 `lastReconciledAt` cursor를 노출하는 도메인 상태
- `CombatSnapshot`: Stage·활성 몬스터의 계산 스탯과 플레이어 현재·최대 HP를 관찰용으로 묶은 값
- `PlayerAttackSnapshot`·`PlayerAttackResult`: occurrence 플레이어 공격의 확정 seed·roll·피해 결과와 처리 결과
- `CombatReconciliationResult`: pending 플레이어 공격 수와 마감 실패 몬스터 공격의 적용·skip 수
- `MonsterAttackTrigger`: 같은 occurrence event key를 공유하는 `MANUAL_FAILURE`와 `MISSED_DEADLINE` 원인
- `CombatLifecycleEvent`: 치명 monster attack에서 `PlayerDefeated → StatusEffectApplied|StatusEffectRefreshed → PlayerEmergencyRecovered` 순서를 확정하고 상태 제거를 별도 event로 나타내는 typed presentation 입력. event id는 occurrence attack key와 effect revision에서 파생한다.
- `CombatTransition`: 새 공격 event의 최초 확정 결과에서만 만드는 attack·hit·death·spawn·damage text와 ordered lifecycle presentation 입력. Room source state가 아니며 replay하지 않는다.
- `BattleSfx`·`BattleEffectEvent`: 공격·피격·전투 불능의 여섯 효과음 종류와 기존 combat event id를 결합한 replay 없는 typed presentation event다. 개별 소비 identity는 `eventId + BattleSfx`이며 HP Flow에서 만들지 않는다.
- `BattleSfxSettingsRepository`: application-scope SharedPreferences의 기본 켜짐 `효과음` 설정을 domain state로 숨기고 Settings UI에 관찰·변경 command를 제공한다. Room이나 DataStore를 사용하지 않는다.
- `BattleSfxPlayer`: `SoundPool` preload·foreground·release 상태와 설정을 확인해 effect를 즉시 재생하거나 폐기하는 application-scope audio 경계다. 재생 요청을 지연 queue하지 않는다.
- `CombatRepository`: 몬스터·Stage 원천 상태와 양방향 공격 event를 Room transaction으로 처리하고 실패 occurrence reconciliation을 제공한다. 치명 공격에서는 attack event, `0 HP` 패배 snapshot, 중상 revision, 유효 스탯과 50% 응급 회복 HP, current state를 한 transaction으로 확정한다. `observeDiscoveredMonsterSpecies()`는 Room entity를 노출하지 않고 현재·과거 몬스터 이력의 발견 종족 집합만 제공한다.
- `EquipmentType`·`EquipmentSlot`: gameplay 장비 분류와 장착 부위. 둘 다 `WEAPON`, `HELMET`, `CHEST`, `LEGS`, `GLOVES`, `SHOES`, `ACCESSORY` 일곱 값만 사용한다.
- `Equipment`: 가격, 판매 상태, 요구 레벨, type·slot과 검증된 modifier를 담는 catalog 장비 정의
- `OwnedEquipment`: 캐릭터별 소유 equipment. 같은 캐릭터와 equipment 조합은 하나뿐이며 quantity를 갖지 않는다.
- `EquippedEquipment`: `OwnedEquipment`와 slot을 결합한 캐릭터의 실제 gameplay 장착 모델. Room의 `CharacterEquipmentEntity`는 `CHEST`와 `LEGS`를 독립 row로 동시에 저장하고 `DerivedStats`에는 이 source의 modifier만 반영한다.
- `EquipmentPreviewProjection`: 판매 후보 하나를 현재 같은 slot의 장비와 비영속 교체해 계산한 render loadout과 `DerivedStats`다. 실제 gameplay 장착, 골드·소유권, current HP를 쓰지 않는다. 지원하지 않는 type/slot은 두 결과를 현재 상태로 격리하고, layer 누락·검증 실패는 stat projection과 독립적으로 render loadout만 현재 상태로 유지한다.
- `EquipmentStoreSnapshot`: 판매 catalog·골드·레벨·소유·실제 gameplay 장착과 별도로 보존한 `CharacterAppearance` fallback, gameplay `layerKey`를 catalog로 검증해 투영한 실제 render loadout, 실제 장착 modifier로 기존 공식이 계산한 `DerivedStats`, 판매 장비별 `EquipmentPreviewProjection`을 하나의 읽기 projection으로 묶는다. `ownedEquipmentByEquipmentId`는 catalog equipment id를 key로 실제 `OwnedEquipment`를 제공하며 key·character·catalog·equipped source 일관성을 생성 시 검증한다. 외형 fallback과 preview는 소유권이나 gameplay 장착으로 승격하지 않는다.
- `EquipmentRepository`: 판매 catalog·골드·소유·slot 장착을 조합한 store·inventory `Flow`와 최신 Room source를 재검증하는 구매·장착·해제 command를 제공한다. `RoomEquipmentRepository`가 transaction과 HP 비율 보존을 소유하며, 해제할 장비가 없는 slot은 `AlreadyEmpty`로 멱등 성공한다.

## 캐릭터 성장 기반과 후속 전투 경계

Post-MVP Character Growth v1에서는 `CharacterStatBalanceConfig(version = 1)` 기반의 레벨 50 상한, 기본·파생 스탯 계산, typed 일괄 포인트 배분·초기화, 정시·일일/반복 효율·연속일 정책과 캐릭터 화면까지 구현했다. Character 화면의 `-/+`는 ViewModel 전용 draft만 바꾸고 단일 저장 action이 `AllocateStatPointsUseCase`를 호출한다. [구현 및 검증 계약](game-design/character-stats/implementation-and-validation.md)은 이 기반의 실제 상태와 남은 후속 범위를 함께 기록한다.

`CombatCalculator`와 장비·상태이상 modifier 모델·검증기는 Android와 Room에 의존하지 않는 순수 Kotlin 계산 계약으로 존재한다. Post-MVP Monster Combat v1에서는 몬스터 능력치·10칸 Stage 진행, occurrence별 양방향 일반 공격 event, 앱 시작 및 WorkManager reconciliation을 backend로 구현했다. Severe Injury v1은 치명 피해를 `0 HP 전투 불능 → 중상 적용 또는 갱신 → 중상 적용 뒤 유효 최대 체력 50% 응급 회복`으로 확장한다. Calendar Battle Map v1은 그 backend의 현재 활성 몬스터 한 마리를 읽기 전용으로 표시하고, Character Growth의 레벨·현재 레벨 구간 XP·골드를 상단 진행 HUD로 제공한다.

Character Layer Runtime Composition v1은 Room v5에 appearance·equipped item id를 원천 상태로 저장하고, 독립 `64×64` asset을 cached composer로 합성해 Character와 Battle Map에 공유한다. 이 `character_equipped_items` source는 gameplay ownership이 아니며 Equipment Shop and Inventory v1에서도 appearance fallback으로 유지한다. Calendar Combat Feedback v1은 Room v6 failure 원천 상태와 전투 effect를, Equipment Shop and Inventory v1은 Room v7의 정규화된 gameplay 장비 source와 UI를 구현했다. Room v8은 누락 failure source를 보정하고 Room v9은 completion reward mode와 player attack reward snapshot을, Room v10은 Task Reminder source·예약 상태를, Room v11은 nullable 장갑 appearance fallback을, Room v12는 nullable 무기 subtype 저장소를, Room v13은 상태이상 원천과 회복 credit ledger를, Room v14는 attack 난이도 source와 balance version을 추가한다. Room v15는 appearance fallback만 중립 빈 loadout으로 바꾸고 actual ownership·equipment를 보존한다. 실제 `character_equipment` modifier와 활성 상태이상 modifier는 Character·Task·Combat 계산에 전달되며, level과 8개 파생값은 계속 저장하지 않는다. 현재 상태이상 catalog는 `SEVERE_INJURY` 하나이며 몬스터 이름·Stage HUD, 몬스터 스킬·치명타, 전리품과 정확한 전투 deadline 공격은 계속 미구현 후속 범위다.

## 앱 navigation과 composition root

```text
TodoQuestApp
├── 단일 TodoQuestDatabase + AppClock
├── RoomTaskRepository + RoomCharacterRepository + RoomCombatRepository + EquipmentRepository
├── RoomStatusEffectRepository → 활성 effect 관찰 + AppClock 만료 조정 + 제거 event
├── RoomReminderRepository + AndroidReminderScheduler + AndroidReminderPublisher
├── SharedPreferencesFirstLaunchNotificationPromptStore → PrepareFirstLaunchNotificationPromptUseCase
├── DB 파일 존재 사전 캡처 → SharedPreferencesCharacterGuideRepository → Prepare/Acknowledge CharacterStatGuide UseCase
├── SharedPreferences BattleSfxSettingsRepository + application-scope SoundPool BattleSfxPlayer
├── CombatRewardBalanceCatalog(v1·v2) → RoomTaskRepository snapshot → RoomCombatRepository version routing
├── EquipmentCatalogSeeder → 기존 18종 + 고정 ID 1019..1025 adventure catalog 멱등 seed
├── Create/Update/DeleteTaskUseCase → TaskRepository commit → ReconcileTaskReminderUseCase
├── CompleteOccurrenceUseCase → TaskRepository + CombatRepository → reminder best-effort 재조정
├── FailOccurrenceUseCase → TaskRepository에서 FAILED 영속 → CombatRepository의 즉시 best-effort monster attack → reminder 재조정
├── UndoComplete/UndoFailOccurrenceUseCase → occurrence 표시 복원 → reminder 재조정
├── background.CombatReconciliationWorker → ReconcileCombatUseCase → CombatRepository
├── notification.ReminderAlarmReceiver → DeliverReminderUseCase → ReminderPublisher → 반복 reminder 재조정
├── notification.ReminderReconciliationWorker → ReconcileAllRemindersUseCase
├── PurchaseEquipmentUseCase → 판매·레벨·중복·mapping·골드 재검증 → 골드 차감 + owned_equipment 추가 transaction
├── EquipOwnedEquipmentUseCase → 소유·slot 검증 → character_equipment 교체 + HP 비율 보존 transaction
├── UnequipEquipmentUseCase → 대상 character_equipment 제거 + 대상 appearance fallback 기본화 + HP 비율 보존 transaction
└── NavigationBar / NavHost
    ├── Calendar → CalendarViewModel → 일정·완료·reminder UseCase + CharacterRepository.observeCharacter() + CombatRepository.observeCombat() + StatusEffectRepository
    │   └── CharacterSnapshot loadout → CharacterRenderState → shared LayeredCharacterSprite
    ├── Character → CharacterViewModel draft + StatusEffectRepository + CharacterStatGuide UseCase → AllocateStatPointsUseCase → CharacterRepository 일괄 transaction
    │   └── CharacterUiState loadout → CharacterRenderState → shared LayeredCharacterSprite
    ├── Shop → ShopViewModel → EquipmentRepository·구매/장착/해제 UseCase
    │   └── Inventory nested destination → InventoryViewModel → 소유·장착 state와 장착/해제 command
    ├── Compendium
    │   └── Monster 목록 선택·상세 sheet + 호환 상세 destination → CombatRepository.observeDiscoveredMonsterSpecies() → MonsterCompendiumViewModel / MonsterDetailViewModel
    └── Settings → SettingsViewModel
        ├── BattleSfxSettingsRepository → 효과음 Switch
        └── ReminderScheduler POST_NOTIFICATIONS capability → Compose launcher → AndroidReminderCapabilityAdapter
```

Calendar가 시작 destination이다. `Calendar`, `Character`, `Shop`, `Compendium`, `Settings` 다섯 top-level destination의 ViewModel은 각 back stack entry에서 factory로 생성되며 Repository 구현, SharedPreferences 또는 DAO를 Compose 화면에 노출하지 않는다. `Inventory`는 Shop에서, Monster 목록과 호환 상세는 Compendium에서 여는 nested destination이며 하단 navigation 항목을 추가하지 않는다. 목록의 발견 card는 route를 push하지 않고 back-stack entry 범위 `MonsterCompendiumViewModel`의 선택을 바꾸며, 선택 preview가 공용 상세 `ModalBottomSheet`를 연다. sheet가 열린 back은 sheet를 먼저 닫고 다음 back이 Compendium root로 이동한다. 기존 `compendium/monsters/{species}` route는 호환을 위해 유지하고, 미발견 직접 route는 실제 이름·sprite·설명 resource를 갖지 않는 generic 잠금 상태로 제한하며 알 수 없는 argument는 Monster 목록으로 복구한다. nested 화면에서도 각각 Shop·Compendium tab을 선택 상태로 유지한다. `CalendarViewModel`은 주입된 도메인 `CombatRepository` 인터페이스의 `observeCombat()`을 별도의 Battle Map UI state로 변환하고, `CharacterRepository.observeCharacter()`의 snapshot을 플레이어 진행 HUD state로 매핑한다. Monster 목록·호환 상세 ViewModel은 같은 application-scope `CombatRepository.observeDiscoveredMonsterSpecies()`만 관찰한다. Settings는 application-scope `BattleSfxSettingsRepository` state와 `ReminderScheduler`의 일반 알림 capability만 렌더링하고, Android permission/settings launcher는 ViewModel의 일회성 action을 수행한 뒤 최신 state를 재조회한다. exact alarm은 Settings 경계가 아니다. Calendar의 알림 저장은 task UseCase 결과의 typed status를 렌더링하고, permission prompt와 exact-alarm settings는 replay하지 않는 `CalendarEvent`로 요청한다. notification content intent는 `MainActivity`가 검증한 occurrence key를 one-shot navigation event로 바꿔 Calendar 날짜를 선택하며 malformed·삭제된 task는 오늘 Calendar로 안전하게 fallback한다. 탭 이동은 단일 top-level destination과 저장·복원되는 navigation state를 사용하고, 구매·장착 dialog 같은 command side effect는 새 ViewModel이나 Activity 재생성 뒤 replay하지 않는다.

## 데이터 흐름

```text
사용자 입력
→ Compose 화면 event
→ ViewModel
→ UseCase
→ Repository
→ Room transaction 또는 scheduler
→ Flow 재구독
→ UI state 갱신
```

Monster Compendium의 읽기 흐름은 Room v14에서 확정된 전투 이력을 Room v15에서도 변경하지 않고 읽는 projection이다.

```text
Room v15 monster history (현재 + HP 0 과거 row, v14와 같은 source)
→ CombatRepository.observeDiscoveredMonsterSpecies()
→ MonsterCompendiumViewModel privacy-preserving projection
→ count + search/filter + selection state
→ collection summary + preview + adaptive grid + detail sheet
```

Repository 내부에서는 `CombatDao.observeMonsterInstances()`가 전체 이력을 관찰하고 `MonsterDiscoveryPolicy(stage + encounter + grade + balanceVersion)`가 중복 없는 `Set<MonsterSpecies>`를 만든다. 첫 collector는 `commandMutex` 안에서 기존 combat 초기화를 보장해 활성 몬스터가 생성되는 즉시 발견 집합에 포함한다. 처치 뒤 다음 `MonsterInstance`가 생성되면 같은 table invalidation으로 목록과 상세가 갱신된다. ADR-021의 발견 기능 자체는 별도 discovery row나 Room migration을 만들지 않았다. 현재 `15.json`과 `MIGRATION_14_15`도 monster history를 변경하지 않으므로 기존 인스턴스는 계속 읽을 때 소급 투영한다.

`MonsterCompendiumViewModel`은 발견 entry에만 name·sprite·description resource를 연결하고 미발견 entry에는 slot 식별자만 남긴다. 검색은 `MonsterNameResolver`로 해석한 발견 이름만 비교하며 filter·선택·sheet는 Room에 쓰지 않는 화면 상태다. Compose는 이 UI state를 렌더링하고 typed event를 전달할 뿐 DAO나 Repository 구현을 직접 호출하지 않는다. grade·type·region은 도감 표시용 종족 metadata로 추가하지 않는다. 호환 상세 route도 발견 여부를 같은 Repository Flow에서 판단하며 locked state에는 표시 resource를 전달하지 않는다.

일정 원본과 reminder setting의 저장 및 실제 alarm materialization은 서로 다른 실패 경계를 가진다.

```text
첫 Calendar 진입
→ SharedPreferencesFirstLaunchNotificationPromptStore.consumeFirstLaunchCheck()
→ 최초 확인 flag를 application-scope preference에 동기 commit
→ PrepareFirstLaunchNotificationPromptUseCase가 POST_NOTIFICATIONS capability만 조회
→ 필요하면 CalendarViewModel이 FIRST_LAUNCH 한국어 안내 state 표시
→ 확인 시 replay 없는 RequestPostNotificationsPermission event
→ AndroidReminderCapabilityAdapter가 runtime permission / package settings / no-op으로 분기
→ 거부·보류·재생성·process 재시작 뒤 자동 재요청하지 않음
```

최초 확인 flag는 capability 조회나 UI 표시 실패와 별개로 한 번만 소비하며 이 onboarding 실패는 Calendar 로딩을 막지 않는다. `POST_NOTIFICATIONS`가 없는 상태에서 이후 `NONE`이 아닌 reminder를 저장하면 system dialog를 다시 띄우지 않고 typed status와 package notification settings를 여는 `알림 설정` CTA를 제공한다. exact-alarm special access는 첫 실행에 확인하거나 요청하지 않는다.

```text
Calendar editor 저장
→ CalendarViewModel
→ CreateTaskUseCase / UpdateTaskUseCase
→ RoomTaskRepository transaction
→ todo_tasks + task_reminders setting·초기 status 함께 commit
→ ReconcileTaskReminderUseCase
→ 기존 materialized PendingIntent 취소
→ POST_NOTIFICATIONS runtime/app/channel capability 확인
→ 앱 권한 없음은 POST_NOTIFICATIONS_REQUIRED와 알림 설정 CTA, 채널 차단은 NOTIFICATION_CHANNEL_DISABLED와 채널 설정 CTA, task commit 유지
→ capability가 있고 NONE이 아니면 한국어 exact-alarm 설명 → EXACT_ALARM capability 확인
→ RoomReminderRepository가 완료·실패 occurrence를 제외한 엄격한 미래 plan 한 건 계산
→ task_reminders에 occurrence key·trigger·PENDING을 조건부 stage
→ AndroidReminderScheduler.setExactAndAllowWhileIdle()
→ 같은 key가 current일 때만 SCHEDULED로 조건부 갱신
→ 권한·scheduler 실패는 orphan alarm best-effort 취소와 current key ERROR만 남기고 task commit 및 선점된 DELIVERED는 유지
```

일정 수정이 반복 원본을 미래 시점부터 분할하면 원 segment의 저장된 occurrence key는 먼저 취소할 수 있도록 남겨 두고, 새 segment에 독립 reminder row를 만든다. UseCase가 old/current task id를 차례로 재조정해 stale alarm을 제거하고 새 segment의 첫 유효 occurrence만 예약한다. 생성·수정·삭제뿐 아니라 완료·실패·각 취소도 자신의 Room·전투 command가 끝난 뒤 같은 재조정 경계를 호출한다.

alarm 발화는 persisted source를 다시 확인하고 notification 게시를 한 번만 claim한다.

```text
AlarmManager explicit occurrence PendingIntent
→ ReminderAlarmReceiver.goAsync()
→ DeliverReminderUseCase
→ RoomReminderRepository.getActiveTodoTaskForDelivery(key)
→ PENDING 또는 SCHEDULED의 저장 key 일치 + active task + occursOn + completion/failure 없음 확인
→ task_reminders를 DELIVERED·plan 없음으로 occurrence key 조건부 claim
→ AndroidReminderPublisher가 private high-importance notification 게시
→ 반복 task면 ReconcileTaskReminderUseCase가 다음 미래 occurrence 한 건 예약
```

stale·중복 callback은 조건부 claim에 실패해 게시하지 않는다. publisher가 실패하면 `ERROR`를 남기되 이미 성공한 task·occurrence·보상·전투 transaction은 변경하지 않는다. notification tap은 별도 immutable content `PendingIntent`가 검증된 key를 `MainActivity`로 전달해 Calendar 날짜를 연다.

Character 능력치 배분은 화면의 임시 선택과 영속 command를 분리한다. 저장 전에는 확정 기본 능력치, 예상 기본 능력치, stat별 pending 증가량과 남은 포인트만 `CharacterUiState`에서 합성한다. 이 draft는 ViewModel 메모리에만 있으며 Room profile·current HP·`CharacterSnapshot.derivedStats`를 바꾸거나 파생값·HP를 미리 보여주지 않는다.

```text
Character의 -/+ event
→ CharacterViewModel의 비영속 StatAllocation draft
→ 예상 기본 능력치·남은 포인트 UI 갱신, 초기화 차단
→ 능력치 배분 저장 event
→ AllocateStatPointsUseCase(typed StatAllocation 한 건)
→ CharacterRepository Room transaction
→ 최신 profile·current state·실제 장착 modifier 재조회
→ 전체 배분의 포인트·stat cap 재검증
→ 배분 전/후 DerivedStats 계산
→ profile 갱신 + MAX_HP가 바뀐 경우 current HP 비율을 전체 배분에 대해 한 번 보존
→ Room Flow의 확정 CharacterSnapshot으로 UI 갱신
```

level과 8개 `DerivedStats`는 이 흐름에서도 저장하지 않는다. 저장 실패나 최신 상태 재검증 실패는 profile과 current state를 부분 갱신하지 않으며 ViewModel draft를 유지해 사용자가 조정하거나 재시도할 수 있게 한다.

능력치 배분 안내는 위 Room transaction과 독립된 다음 흐름을 사용한다.

```text
TodoQuestAppContainer.create()
→ Room 초기화 전에 todo-quest.db 파일 존재 여부 캡처
→ SharedPreferencesCharacterGuideRepository 최초 eligibility 고정
→ PrepareCharacterStatGuideUseCase가 eligible && !acknowledged 계산
→ CharacterViewModel이 Character source Loaded 뒤 automatic origin으로 Dialog state 노출
→ CharacterScreen이 한국어 안내와 96dp 요정 sprite를 렌더링
→ 자동 Dialog dismiss/primary action
→ AcknowledgeCharacterStatGuideUseCase가 acknowledged를 멱등 저장
```

확인 전에 process가 종료되면 `acknowledged = false`가 유지되어 다음 Character 진입에서 자동 안내가 다시 표시된다. `기본 능력치` 도움말은 manual origin만 열고 닫을 때 acknowledge를 쓰지 않으므로 모든 사용자가 재열람할 수 있으며 자동 확인 상태를 되돌리지 않는다. preference 읽기·쓰기와 sprite decode 실패는 안내 안에 격리하고 Character 조회·stat allocation·reset을 실패 처리하지 않는다. 이 흐름은 Room v15와 `AllocateStatPointsUseCase → CharacterRepository` transaction을 읽거나 변경하지 않는다.

Severe Injury v1의 치명 공격과 회복은 attack event, 상태 원천, occurrence 완료의 기존 transaction 경계를 확장한다.

```text
치명 monster attack
→ MonsterAttackEvent에 피해 결과와 playerHpAfter=0 snapshot 확정
→ character_status_effects의 SEVERE_INJURY 적용 또는 revision 갱신
→ 활성 장비 + 현재 revision 상태 modifier로 유효 MAX_HP·ATTACK 재계산
→ 유효 MAX_HP의 50% 응급 회복 HP와 CharacterCurrentState를 같은 transaction에 저장
→ commit 뒤 PlayerDefeated → StatusEffectApplied|Refreshed → PlayerEmergencyRecovered 방출

최초 occurrence 완료
→ CompletionLog + RewardLedger + player attack outbox transaction
→ 현재 effect revision의 (characterId, effectType, revision, taskId, occurrenceDate) credit INSERT OR IGNORE
→ 세 번째 서로 다른 credit이면 effect 비활성화
→ 같은 transaction에서 복원된 스탯을 player attack source snapshot에 사용
```

중복 완료, 완료 취소 뒤 재완료와 같은 occurrence 재처리는 credit primary key에 막혀 남은 횟수를 다시 줄이지 않는다. 반복 일정 분할은 이미 기록한 미래 occurrence credit의 task id를 새 segment id로 재배정해 동일 occurrence 의미를 보존한다. 어느 쓰기든 실패하면 completion·RewardLedger·attack outbox·credit·effect 변경을 함께 rollback한다. `RoomStatusEffectRepository.reconcileExpired()`는 `AppClock.now()`가 만료 시각 이상인 현재 revision만 비활성화하고, Character·Calendar ViewModel은 lifecycle resume에서도 이를 best-effort로 호출한다. 제거는 스탯 계산을 복원하지만 current HP를 자동 치유하지 않는다.

Equipment Shop and Inventory v1의 구매 흐름은 stale UI state를 신뢰하지 않고 transaction에서 조건을 다시 확인한다.

```text
Shop 구매 event
→ ShopViewModel
→ PurchaseEquipmentUseCase
→ EquipmentRepository transaction
→ 최신 골드·판매 상태·요구 레벨·중복 소유·type/slot mapping 재검증
→ 골드 차감 + owned_equipment 추가
→ 구매 성공: 바로 장착 / 인벤토리로 이동 / 계속 쇼핑
```

장착 흐름은 appearance fallback을 직접 수정하지 않고 gameplay source와 HP만 원자적으로 갱신한다.

```text
Shop 또는 Inventory 장착 event
→ ViewModel
→ EquipOwnedEquipmentUseCase
→ EquipmentRepository transaction
→ 소유권·type/slot 일치 검증
→ 대상 character_equipment slot 교체
→ old MAX_HP/new MAX_HP로 current HP 비율 보존
→ Character·Combat 관찰 state 갱신
```

해제 흐름은 gameplay ownership과 appearance source를 계속 분리하되, 사용자가 명시적으로 해제한 대상 slot의 shared-renderer fallback만 같은 transaction에서 기본 복장으로 바꾼다.

```text
Shop slot 관리 dialog 또는 Inventory 해제 event
→ ShopViewModel / InventoryViewModel
→ UnequipEquipmentUseCase
→ RoomEquipmentRepository transaction
→ 빈 slot이면 AlreadyEmpty 멱등 성공, source 변경 없음
→ 대상 character_equipment row만 삭제, owned_equipment 보존
→ 대상 character_equipped_items fallback만 null 또는 승인된 기본 복장으로 갱신
→ 활성 상태이상을 포함한 old/new MAX_HP로 current HP 비율 보존
→ store/inventory/character/combat Flow 재관찰
→ Shop·Character·Calendar Battle Map shared LayeredCharacterSprite에 같은 기본 복장 반영
```

해제 뒤 새 Character·Task·Combat 계산은 제거된 modifier를 사용하지 않지만 이미 확정된 RewardLedger, completion player attack source/result와 combat event snapshot은 다시 계산하지 않는다. `AlreadyEmpty`는 성공 결과이므로 재시도나 화면 재생성으로 command side effect를 만들지 않는다.

Room v15에 보존된 v7 장비 source의 계산 연결은 다음과 같다. `EquipmentCatalogSeeder`는 store·inventory 관찰 또는 command 준비 transaction에서 기존 명시적 ID 18종과 고정 ID `1019..1025` adventure 7부위의 definition·modifier를 `INSERT OR IGNORE`로 seed한다. 같은 DAO transaction은 기존 18종 row의 id·name key·type·slot과 legacy 가격이 모두 일치할 때만 승인 가격으로 갱신하고 custom row·소유·장착·gold는 변경하지 않는다. gameplay 무기는 같은 `WEAPON` slot을 사용하면서 `LONGSWORD`·`DAGGER`·`SPEAR`·`BLUNT` subtype을 가지며 비무기 row의 subtype은 null이다. 구매와 소유만으로는 calculator 입력이 바뀌지 않는다.

```text
Room v15 character_equipment
→ owned_equipment → equipment + equipment_modifiers 검증·mapping
→ 실제 장착 modifier 목록
├── RoomCharacterRepository → CharacterSnapshot 파생값·성장 command의 old/new MAX_HP
├── RoomTaskRepository → 새 RewardLedger gold bonus·player attack source snapshot
└── RoomCombatRepository → 현재 player HP/방어·공격·처치 회복 계산
```

렌더링은 계산 source와 분리한다. gameplay 장비에 `layerKey`가 있고 `CharacterLoadoutCatalog`가 해당 slot 값을 허용할 때만 `character_equipped_items` fallback 위에 투영하며, `layerKey`가 없거나 검증에 실패하면 기존 appearance를 그대로 사용한다. 기존 18종 중 액세서리 2종을 제외한 16종과 adventure 7종이 다음 두 독립 경로에 연결된다.

```text
equipment.imageKey
→ EquipmentArtworkCatalog
→ character/layers의 같은 runtime PNG
→ 불투명 bounds-fit 최근접 thumbnail (Shop 목록·상세·slot, Inventory)

equipment.layerKey
→ RoomEquipmentRepository의 renderedEquippedItems projection
→ CharacterLayerCatalog
→ 64×64 전체 원점 source-over 캐릭터 합성
```

thumbnail의 null·unknown key 또는 decode 실패는 type별 Material icon placeholder로, layer mapping 실패는 기존 appearance로 격리한다. 두 presentation 실패는 구매·장착 transaction이나 일정·RewardLedger·전투·알림 상태를 실패 또는 rollback시키지 않는다. 기존 액세서리 2종 seeded 장비는 이 fallback을 유지한다. 장갑 projection은 nullable `glovesId`를 통해 기본 `hands_front` source를 대체하고, adventure 상품은 `gloves_adventure`를 사용한다. 신발 projection은 선택된 gameplay source로 하의와 `y=53..54` 발목 interface 및 발바닥 `y=58`을 보존한다. schema v6는 legacy 3분할 기본 검과 gameplay 단일 `weapon_front`를 손·머리·투구·액세서리 뒤의 최상단 weapon group으로 합성하며 모든 weapon pixel을 다른 body layer보다 앞에 보존한다.

Shop 프리뷰의 읽기 흐름은 같은 분리를 유지한 채 Repository의 실제 source snapshot·순수 구매 전 projection과 ViewModel 선택 상태를 합성한다. `RoomEquipmentRepository.observeStore()`는 관련 table invalidation마다 단일 Room read transaction에서 snapshot을 다시 읽고, `EquipmentPreviewProjectionCalculator`는 각 판매 후보의 같은 slot modifier와 검증된 layer만 교체한다.

```text
Room v15 character_profile + character_appearance + character_equipped_items
    + equipment + equipment_modifiers + owned_equipment + character_equipment
→ RoomEquipmentRepository.loadStoreSnapshot() read transaction
→ EquipmentStoreSnapshot
   ├── appearance fallback source
   ├── ownedEquipmentByEquipmentId (catalog equipment id → OwnedEquipment)
   ├── equippedBySlot gameplay source (7개 slot, CHEST/LEGS 독립)
   ├── renderedEquippedItems (유효한 layerKey만 fallback 위에 투영)
   ├── DerivedStatsCalculator의 실제 장착 공식 결과
   └── previewByEquipmentId
       └── 같은 slot 후보 modifier + 유효 layer의 순수 구매 전 projection
→ ShopViewModel: store snapshot + selectedEquipmentId 등 화면 command를 combine().stateIn()
   └── Purchase | PurchaseUnavailable | Equip | Unequip typed action
→ 단일 ShopUiState StateFlow
→ 선택 없음: 실제 render loadout·현재 stat
→ 상품 선택: 비영속 preview render loadout·현재 대비 stat delta
→ 통합 캐릭터/7 slot/stat card · category · 상태별 판매 목록
```

이 projection은 `ShopViewModel`의 `selectedEquipmentId`가 선택한 표시 입력일 뿐 Room row, `character_equipment`, gold, ownership과 current HP를 변경하지 않는다. `ownedEquipmentByEquipmentId`는 Room owned row의 primary key가 아니라 catalog equipment id로 lookup하고, `Equip`만 실제 `ownedEquipmentId`를 command에 전달한다. card와 detail은 동일 typed action을 사용한다. 구매 transaction은 기존대로 gold·ownership만 확정하고, 실제 shared render loadout·파생 능력치·HP는 장착 transaction commit 뒤 새 `EquipmentStoreSnapshot`과 Character·Combat Flow에서만 갱신한다. 선택 장비 해제 성공은 선택·상세·확인·preview를 비우고, 다른 장비 해제나 실패는 선택을 유지한다. invalid type/slot 후보는 현재 외형·능력치로, layer 누락·검증 실패는 현재 외형으로 격리하며 실제 source를 보정하거나 쓰지 않는다.

Shop의 첫 스크롤 항목인 압축 대장장이 배너는 위 data flow와 분리된 정적 presentation이다. canonical 원천은 [대장장이 상점 주인 PNG](art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png)와 [schema v1 JSON 계약](art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json)이고, Android는 byte-identical `app/src/main/res/drawable-nodpi/todo_quest_blacksmith_shopkeeper_front_idle.png`를 읽는다. Compose는 최소 `88dp` 배너의 `60dp` frame에 전체 `64×64` logical canvas를 `ContentScale.Fit`과 `FilterQuality.None`으로 그리며 decode 실패를 `Build` Material icon fallback으로 Shop presentation 안에 격리한다. 이 배너는 Room, Repository, UseCase, ViewModel, `ShopUiState`와 application composition wiring을 추가하거나 변경하지 않으며 장비 조회·구매·장착·해제 및 일정·보상 transaction의 성공 여부에 참여하지 않는다.

slot 관리와 category filter는 `selectedCategory`를 통해 같은 부위 목록으로 이동하고, 판매 상품 preview 선택은 별도 `selectedEquipmentId`로 유지한다. 선택한 slot은 관리 dialog에서 같은 부위 장비 목록으로 이동하거나 장착 장비를 해제할 수 있다. 구매·장착·해제 결과 dialog는 일회성 command state이고, gold·소유·실제 장착·비교·공용 render loadout·실제 최종 능력치는 각 source transaction commit 뒤 새 `EquipmentStoreSnapshot`에서만 갱신한다.

구매·장착·해제는 일정 보상 transaction 및 occurrence별 전투 event와 독립적이다. 장비 command 실패는 일정 저장·완료·보상과 이미 확정된 combat event를 롤백하지 않으며, 알림 또는 exact alarm 권한에도 의존하지 않는다.

Battle Map의 읽기 흐름은 다음과 같이 기존 일정 command 흐름과 분리한다.

```text
CombatRepository.observeCombat()
→ CalendarViewModel
→ 독립 Battle Map presentation state
→ Calendar의 Battle Map
```

활성 몬스터의 종족도 기존 전투 원천 상태에서 읽을 때마다 파생하고 별도 저장하지 않는다.

```text
저장된 MonsterInstance.stageNumber + encounterNumber + grade + balanceVersion
→ MonsterSpeciesPolicy의 결정적 다섯 종족 스케줄
→ CombatSnapshot.activeMonsterSpecies
→ BattleMonsterVisualCatalog
→ 종족별 drawable·한국어 이름·쓰러짐 안내
```

명시적 목록은 `GOBLIN_SCOUT`, `SLIME`, `CORRUPTED_TREE_SPIRIT`, `SKELETON_SOLDIER`, `HARPY`다. NORMAL Stage의 8개 encounter는 다섯 종족을 모두 포함하고 각 종족이 1~2회 나타나도록 결정적으로 섞으며, ELITE·BOSS Stage는 유일한 encounter에 한 종족을 결정적으로 선택한다. `MonsterType`은 `MonsterCatalog` definition과 능력치 계산에만 사용하고 종족 스케줄 입력으로 사용하지 않는다. 해골의 `undead`·`dark`, 나무 정령의 `forest`·`dark`·`corruption`, 하피의 `wind`·`flight`·`mountain`·`grassland`, 슬라임의 `water`·`slime`은 시각 콘셉트와 종족 식별 metadata일 뿐 비행 동작, biome, 전투 상성·상태이상·스킬·전리품 계산이나 새 맵 배경에는 입력하지 않는다. 따라서 이 projection은 Room v15 schema, monster balance version 값, 능력치·피해·보상 transaction을 변경하지 않는다.

플레이어 진행 HUD의 읽기 흐름도 일정 command와 분리한다.

```text
CharacterRepository.observeCharacter()
→ CalendarViewModel의 CalendarCharacterSummary
→ BattleOverlayLayer 상단 PlayerProgressHud
```

수동 실패와 복구 흐름은 실패 상태 영속을 전투 처리보다 먼저 확정한다.

```text
Calendar의 실패 event
→ CalendarViewModel
→ FailOccurrenceUseCase
→ TaskRepository: occurrence FAILED 영속
→ CombatRepository: 같은 occurrence key로 MANUAL_FAILURE attack 즉시 시도
→ combat 실패 시 기존 reconciliation이 같은 key를 복구
→ 새 event가 처음 확정된 경우에만 replay 없는 CombatTransition 방출
```

실패 취소는 `UndoFailOccurrenceUseCase`를 통해 occurrence 표시만 `TODO`로 되돌리며 `monster_attack_events`와 이미 적용된 HP를 변경하지 않는다. 실패 취소 뒤 늦은 완료는 기존 완료 UseCase로 들어가 RewardLedger와 player attack 멱등 경계를 그대로 사용한다.

자동 마감 실패는 수동 실패와 달리 failure source와 attack event를 분리해 먼저 저장하지 않는다. reconciliation이 한 transaction 안에서 두 원천을 함께 확정한다.

```text
CombatReconciliationWorker
→ ReconcileCombatUseCase
→ CombatRepository Room transaction
→ completion·failure·monster event가 모두 없는 마감 occurrence 선택
→ failure_logs에 occurrence FAILED source 추가
→ 앞선 최대 3건은 MISSED_DEADLINE APPLIED event + player HP 갱신
→ 나머지는 MISSED_DEADLINE SKIPPED event로 영구 확정
→ reconciliation cursor 갱신
→ commit 뒤 새 APPLIED event에만 replay 없는 CombatTransition 방출
```

failure, event, HP 또는 cursor 쓰기 중 하나라도 실패하면 transaction 전체를 rollback한다. 따라서 재시작 뒤 `MISSED_DEADLINE` event만 있고 occurrence가 `TODO`로 보이는 신규 불일치는 만들지 않는다.

캐릭터 layer의 읽기 흐름은 두 화면에서 같은 상태와 renderer를 사용한다.

```text
Room v5 character_appearance + character_equipped_items
→ CharacterRepository.observeCharacter()
→ CharacterSnapshot의 appearance + equippedItems
→ CharacterRenderState
→ CharacterLayerCatalog
→ CharacterBitmapComposer의 layer cache + render-state LRU cache
→ LayeredCharacterSprite
→ Character 화면 / Calendar Battle Map player
```

`CharacterRepository.updateAppearance()`와 `updateEquippedItems()`는 domain catalog를 먼저 검증하고 Room transaction에서 character 원천 row를 보장한 뒤 해당 loadout row를 갱신한다. ViewModel에도 command가 있지만 현재 사용자 선택 UI는 이 command를 노출하지 않는다. Compose는 loadout DAO나 asset manager를 직접 다루지 않는다.

전투 Flow의 로딩·실패는 Battle Map state 안에서만 처리한다. 캐릭터 snapshot이 도착하기 전 HUD는 임의 값을 표시하지 않고 로딩 설명을 제공한다. 일정 조회·편집·완료 state와 occurrence 보상 결과는 두 presentation 흐름과 관계없이 계속 갱신되며 presentation 문제로 차단하거나 실패 처리하지 않는다.

## Calendar Battle Map과 Combat Feedback presentation

- Calendar는 시스템 inset 다음 `고정 Battle Map → 독립 Calendar scroll`로 구성한다. 이 계약은 Calendar Battle Map v1의 화면 전체 단일 scroll을 교체한다. 고정 영역은 진행 HUD, player와 단일 active monster, 두 HP bar 및 attack·hit·death·spawn·damage text effect 전체를 포함한다. scroll 영역은 월 이동, Sunday-first 요일·날짜 grid, 선택 날짜, 완료/실패 요약, 추가 action, task 목록과 빈 안내를 포함한다.
- 앱명·선택 날짜·성장 요약을 표시하던 기존 상단 정보 Header와 대체 spacer는 두지 않는다. 선택 날짜·완료/실패 개수·추가 action은 Calendar scroll의 목록 문맥에 유지한다.
- 운영 state는 `CombatSnapshot`의 단일 활성 몬스터를 최대 한 마리 표시한다. 재사용 UI 컴포넌트와 sample·Preview만 0~4마리 입력을 허용하며 이는 Room schema나 combat domain을 다중 활성 몬스터로 확장하지 않는다.
- 구현 경계는 `feature/battle/BattleMapUiModel.kt`의 source state, `BattleAnimationController.kt`의 transient transition actor, `BattleMapLayout.kt`의 Android 비의존 actor·HP bar 배치 계산, `BattleMap.kt`의 Compose 합성, `PlayerProgressHud.kt`의 진행률·HUD, `feature/calendar/CalendarViewModel.kt`의 domain→presentation 매핑과 actor 소유권, `CalendarScreen.kt`의 고정 map과 독립 scroll 소유권으로 나눈다. player sprite는 drawable frame crop 대신 `BattleSpriteUiModel.LayeredCharacter`가 가진 `CharacterRenderState`를 shared `LayeredCharacterSprite`로 전달하며 monster drawable renderer는 분리해 유지한다.
- 기본 `BattleMapTheme.backgroundResId`는 `R.drawable.battle_map_grassland`이고 `showDecorations`로 장식 Canvas를 끌 수 있다. 다른 stage 배경은 `drawable-nodpi/battle_map_*` resource를 추가한 뒤 호출부에서 새 `backgroundResId`를 가진 theme을 전달해 교체하며 domain·Room 상태는 변경하지 않는다.
- 월간 캘린더는 `일·월·화·수·목·금·토` 요일 머리글과 같은 Sunday-first cell offset을 사용한다. `YearMonth.calendarCells()`의 leading empty cell 수는 `firstDay.dayOfWeek.value % 7`로 계산해 일요일을 0열에 둔다.

실제 `BattleMap` layer tree와 z-order는 다음과 같다.

```text
BattleMap
├── BattleBackground                    z=0   (PNG 또는 다층 Canvas fallback)
├── BattleDecorations                   z=0.5 (선택)
├── BattleGroundLayer                   z=1   (unit별 지면 그림자)
├── PlayerLayer / MonsterLayer          z=2+  (모든 unit을 y 오름차순 stable sort)
├── ActorHpLayer                         z=50  (placement geometry 기준 player/monster HP bar)
├── StatusEffectBadgeLayer               z=55  (player HP와 actor 사이 중상 배지·48dp action)
├── TransientCombatEffectLayer           z=60  (attack·hit·death·spawn·damage text, replay 없음)
├── TransientCombatRewardLayer           z=90  (600ms XP·gold badge, replay 없음)
├── Loading / Unavailable status        z=90
└── BattleOverlayLayer                  z=100
    └── PlayerProgressHud               상단 중앙 (map 안쪽 8dp inset)
```

`PlayerProgressHud`는 Material theme의 반투명 `surface`, `outline`, `onSurface`, `primary`, `secondary` 토큰만 사용해 stage 배경과 독립적으로 대비를 유지한다. 정상 상태는 `레벨 · 골드 아이콘/값 · EXP current/required`를 한 Row에 두고 progress bar만 그 아래에 둔다. EXP group 폭은 `max(104dp, intrinsic content width)`로 계산하고 bar 좌우 경계를 `EXP` label 왼쪽부터 값 오른쪽까지의 실제 content 경계와 맞춘다. `surfaceVariant` track과 1dp outline을 사용해 `0/100`에서도 숫자와 bar가 잘리지 않고 경계가 보인다. 전체 값은 하나의 한국어 TalkBack 설명으로 합치고 골드 아이콘은 decorative로 둔다. 필요 XP가 0 이하이거나 현재 XP가 0 이하이면 bar 진행률은 0, 현재 XP가 필요 XP 이상이면 1, 그 외에는 `currentExp / requiredExp`를 `0f..1f`로 제한한다. player/monster placement와 map 높이 계산은 이 최소 폭 변경의 영향을 받지 않는다.

정규화 좌표를 pixel top-left로 바꾸는 계약은 다음과 같다. 모든 actor는 전체 `64×64` source frame의 발 anchor `(0.5, 58/64)`를 사용하며, `groundOffsetPx`는 양수면 아래로 이동한다.

```text
normalMapHeightDp = clamp(availableWidthDp / 2.4, 190, 320)
compactMapHeightDp = clamp(heightConstrainedMapHeightDp, 150, 190)
mapHeightDp       = compact height가 필요한 저높이 화면이면 compactMapHeightDp, 아니면 normalMapHeightDp
baseUnitHeightDp  = compact이면 48, 아니면 clamp(mapHeightDp × 0.35, 72, 112)
spriteHeightPx    = baseUnitHeightPx × unit.scale
spriteWidthPx     = spriteHeightPx × sourceWidth / sourceHeight
unclampedLeft     = x × mapWidthPx  - spriteWidthPx  × anchorX
unclampedTop      = y × mapHeightPx - spriteHeightPx × anchorY + groundOffsetPx
left              = clamp(unclampedLeft, 0, max(mapWidthPx  - spriteWidthPx,  0))
top               = clamp(unclampedTop,  0, max(mapHeightPx - spriteHeightPx, 0))
```

일반 화면은 기존 `190dp..320dp` map 범위를 유지한다. 저높이 화면에서는 하단 navigation 위에 Calendar scroll viewport를 확보할 때만 `150dp..190dp` compact-height 범위를 사용한다. 두 HP bar는 actor별 placement 결과에서 계산하고 map 전체의 독립 절대 좌표로 두지 않는다.

```text
hpBarCenterX = actor.left + actor.spriteWidth / 2
hpBarLeft    = clamp(hpBarCenterX - hpBarWidth / 2, 0, mapWidth - hpBarWidth)
hpBarTop     = clamp(actor.top - hpBarGap - hpBarHeight, hudReservedBottom, mapHeight - hpBarHeight)
```

중상 status layout은 위 actor placement 결과를 변경하지 않는다. 전달받은 player `left`, `top`, `spriteWidth`, `spriteHeight`를 그대로 반환하고 HP bar·badge만 HUD와 map 경계 안에서 clamp하거나 축소한다. 따라서 일반·compact map의 중상 적용 전후 player actor bounds는 동일하다.

큰 normalized y를 가진 unit이 뒤에 그려져 앞쪽에 보인다. 같은 y에서는 입력 순서를 유지한다. `BattleMonsterSlots.forCount()`의 component/sample 배치 preset은 아래와 같고 범위를 벗어난 개수는 거부한다.

| 몬스터 수 | 정규화 `(x, y)` slot |
|---:|---|
| 0 | 없음 |
| 1 | `(0.76, 0.82)` |
| 2 | `(0.66, 0.83)`, `(0.84, 0.78)` |
| 3 | `(0.60, 0.82)`, `(0.76, 0.78)`, `(0.90, 0.84)` |
| 4 | `(0.57, 0.77)`, `(0.69, 0.86)`, `(0.82, 0.76)`, `(0.92, 0.85)` |

Calendar Combat Feedback v1은 player/monster HP bar와 attack·hit·death·spawn·damage text를 구현한다. Severe Injury v1은 player HP bar와 actor 사이의 독립 중상 배지, 상세 dialog와 `PLAYER_DEFEATED → STATUS_EFFECT_APPLYING|STATUS_EFFECT_REFRESHING → PLAYER_EMERGENCY_RECOVERING` effect를 추가한다. application-scope `RoomCombatRepository`는 새 attack event가 최초 확정된 경우에만 `MutableSharedFlow(replay = 0)` transition을 방출한다. `CalendarViewModel`이 소유한 `BattleAnimationController`의 buffered `Channel` actor는 attack key와 lifecycle event id를 한 번만 받아 직렬 표시하고 Flow 재구독·화면 회전·process 재시작·기존 event 재조회에는 replay하지 않는다. Calendar Combat Feedback v1 당시에는 Compose Material icon과 translation·shake·flash·alpha만 사용하며 캐릭터·몬스터 pixel bitmap의 최근접 확대를 바꾸거나 새 bitmap·sound asset을 추가하지 않았다. ADR-024는 이 동일한 replay 없는 actor timeline에 여섯 `BattleSfx` raw WAV만 후속으로 결합하며 sprite 합성은 변경하지 않는다. Stage HUD, 추가 상태이상과 몬스터 상태 기술은 여전히 후속 범위다. 이 합성은 배경 resource와 sprite layer를 수정하거나 합치지 않는다.

이 presentation은 Room에 저장한 player/monster HP, attack reward snapshot, active monster·Stage와 활성 상태이상을 source state로 사용한다. 현재 reward version의 player attack transition은 실제 지급 XP·gold를 `600ms` badge로 한 번 표시하고, animation phase·input lock·queue 시간을 늘리지 않는다. transition 표시 실패는 이 원천 상태를 되돌리지 않는다. Room v15는 v9의 단일 활성 몬스터와 독립 멱등 event 계약을 보존한다. Compose는 `CombatRepository`·`StatusEffectRepository` 구현, Room DAO 또는 WorkManager를 직접 호출하지 않는다.

## 반복 일정 처리

- 월간/일간 화면은 `TodoTask`와 `RecurrenceRule`을 기반으로 조회 기간의 `TaskOccurrence`를 계산한다.
- `CompletionLog`는 원본 task id와 occurrence date를 함께 저장한다.
- occurrence 표시 상태는 정확히 `TODO`, `COMPLETED`, `FAILED` 중 하나다. `CompletionLog`가 있으면 `COMPLETED`, 완료 기록 없이 활성 실패 원천 기록이 있으면 `FAILED`, 둘 다 없으면 `TODO`다. 수동 실패 command와 자동 마감 reconciliation은 각각의 transaction 경계에서 완료와 활성 실패가 동시에 노출되지 않게 하며, 자동 마감은 failure source와 `MISSED_DEADLINE` event도 함께 확정한다.
- 사용자는 선택한 `FAILED` occurrence의 실패를 취소해 `TODO`로 되돌릴 수 있다. 이 변경은 반복 원본이나 다른 날짜 occurrence에 전파되지 않는다.
- 반복 원본 수정은 미래 occurrence 계산에 반영한다.
- 과거 occurrence의 완료·실패·보상 기록은 반복 원본 수정으로 삭제하지 않는다.

## 보상 처리

- 완료 UseCase가 호출하는 `RoomTaskRepository`는 Room transaction 안에서 완료 기록과 reward mode snapshot을 확정하고 player attack PENDING outbox를 만든다. 새 `COMBAT_ATTACK` 완료는 캐릭터 XP·gold·HP를 직접 변경하지 않는다.
- `CompletionLog`와 `RewardLedger`는 각각 task id와 occurrence date 조합을 unique key로 사용한다. ledger가 이미 있으면 완료·outbox를 중복 생성하지 않는다. 과거 `TODO_COMPLETION` ledger의 실제 지급량은 그대로 보존하고 새 `COMBAT_ATTACK` ledger에는 실제 지급량 `0/0`을 기록한다.
- 정시 여부와 실제 완료 로컬 날짜, 반복 series·일일 순번, 효율·gold bonus source, `combatEligible`, balance version과 reward mode는 `RewardLedger`에 snapshot으로 확정하며 이후 설정으로 소급 재계산하지 않는다. 신규 ledger의 `combatEligible`은 순번과 무관하게 항상 true다.
- 반복 원본이 미래 시점부터 분할돼 task id가 바뀌어도 `recurrenceSeriesId` 계보와 ledger snapshot을 유지한다.
- 완료 취소는 completion state를 되돌리되, MVP에서는 지급된 보상을 자동 회수하지 않는다. 보상 회수 정책이 필요하면 별도 ADR로 결정한다.
- 실패 취소 뒤 `TODO` occurrence를 늦게 완료해도 최초 보상 transaction은 기존 정책대로 RewardLedger를 확정한다. 이미 ledger가 있으면 XP·골드를 다시 지급하지 않으며 player attack도 같은 occurrence key로 한 번만 만든다.
- 구매 UseCase는 최신 골드 잔액, 판매 상태, 요구 레벨, 캐릭터별 중복 소유와 type/slot mapping을 확인하고 골드 차감과 `owned_equipment` 추가를 하나의 transaction으로 처리한다. 같은 equipment는 캐릭터별 한 row만 허용하며 quantity를 두지 않는다.
- 장착 UseCase는 소유권과 slot 일치를 확인하고 해당 slot의 `character_equipment` 교체와 `MAX_HP` 변화에 따른 current HP 비율 보존을 하나의 transaction으로 처리한다. `CHEST`와 `LEGS`는 서로 독립적이며 다른 slot row를 변경하지 않는다.
- `CombatRewardBalanceCatalog`의 `CURRENT_VERSION = 2`를 새 player attack에 기록한다. version `0`은 무보상 legacy 특례, version `1` PENDING은 catalog의 기존 hit/kill/gold base `1/10/5`, version `2`는 base `3/20/15` 공식을 사용한다. level band `10/5/10`, grade `1×/2×/4×`와 처치 gold 전용 `GOLD_GAIN_BONUS`는 유지하며 level 1 NORMAL 처치는 `23 XP / 15골드`, level 55 BOSS 처치는 bonus가 없을 때 `128 XP / 80골드`다. 미지원 version은 최신 공식으로 추측하지 않고 transaction을 rollback해 PENDING source를 보존한다.
- APPLIED player attack은 version과 관계없이 저장된 operand/result snapshot을 반환하고 재계산하지 않는다. 따라서 업데이트 전에 생성된 v1 PENDING과 업데이트 뒤의 새 v2 attack은 각 version의 공식으로 한 번만 처리된다.

## 전투 event 처리

- `RoomTaskRepository`의 최초 완료 transaction은 completion과 `COMBAT_ATTACK` ledger를 확정하고, 하루 20회 상한 없이 같은 occurrence의 플레이어 공격을 `pending` outbox에 멱등하게 추가한다.
- outbox 처리 실패는 일정 완료 또는 UI 성공 응답을 롤백하지 않는다. `CombatRepository`는 pending 공격을 별도 transaction에서 처리하고 공격 event, hit XP, 처치 추가 XP·gold, 캐릭터 성장·HP, 몬스터·Stage 원천 상태를 함께 갱신한다.
- 완료 취소는 기존 계약대로 completion state만 되돌리며 경제 ledger, outbox, 공격 event를 삭제하지 않는다. 같은 occurrence를 재완료해도 플레이어 공격을 다시 만들지 않는다.
- 수동 실패 command는 `TODO` occurrence의 `FAILED` 상태를 먼저 영속한 뒤 monster attack을 즉시 best-effort로 시도한다. 실패 영속 성공 뒤 combat 처리만 실패하면 reconciliation이 복구하므로 task 상태 성공을 롤백하지 않는다.
- `MANUAL_FAILURE`와 `MISSED_DEADLINE` monster attack은 플레이어 공격 및 경제 보상과 독립된 같은 `(taskId, occurrenceDate)` `monster_attack_events` key를 사용한다. 어느 원인이 먼저 처리되든 피해는 한 번만 적용한다. 첫 combat 초기화 시 cursor를 현재 탐색 경계에 두어 이전의 과거 누락 일정에는 피해를 소급 적용하지 않는다. 이후 자동 마감 후보는 failure row와 event를 같은 transaction에 저장하며, 복귀당 처음 3건은 `APPLIED`, 나머지는 `SKIPPED_RECONCILIATION_CAP`으로 영구 확정해 모두 `FAILED`로 관찰한다.
- 실패 취소는 occurrence 표시 상태만 `TODO`로 되돌리고 기존 monster attack event와 이미 적용된 player damage를 삭제하거나 역연산하지 않는다. 다시 실패해도 event가 이미 있으면 두 번째 damage와 `CombatTransition`을 만들지 않는다.
- `TodoQuestApplication`은 앱 시작 one-time work와 15분 unique periodic work를 등록하고, `background.CombatReconciliationWorker`가 같은 `CombatRepository` reconciliation을 호출한다. WorkManager 실행은 best-effort이며 알림·exact alarm 권한 또는 예약 성공 여부와 무관하다.
- 실패 판정의 event deadline은 `MissedOccurrencePolicy`가 시간 일정은 예정 시각+15분, 무시간 일정은 occurrence 날짜 종료로 계산한다. 실제 처리 시각은 attack event의 `processedAtEpochMillis`와 failure source의 `failedAtEpochMillis`에 함께 확정한다. WorkManager 지연 때문에 deadline과 처리 시각은 다를 수 있으며 정확한 deadline 공격을 보장하지 않는다.
- 치명 피해는 공격 event에 `playerHpAfter = 0`과 `wasLethal = true`를 확정한다. 같은 transaction에서 현재 중상 revision을 적용 또는 갱신하고, 감소된 유효 최대 HP의 `50%`를 내림한 최소 1 HP로 응급 회복한다. 재처리는 저장된 event를 반환해 중상·HP·lifecycle을 다시 적용하지 않는다.
- 중상은 활성 동안 유효 `MAX_HP`와 `ATTACK`을 각각 20% 낮추되 base stat·장비 modifier·파생값을 저장 변경하지 않는다. 서로 다른 occurrence 완료 3회 또는 24시간 만료가 현재 revision을 제거하고, 제거 자체는 current HP를 치유하지 않는다.
- player/monster 현재 HP, RewardLedger, active monster와 Stage 진행은 Room source state다. attack·hit·death·spawn·damage text는 새 event 최초 확정 때만 생성해 한 번 소비하는 transient presentation이며 영속 event 재조회나 앱 재시작에 replay하지 않는다.
- 비치명 player attack도 level band에 따른 hit XP를 지급한다. 처치 transaction은 hit XP에 level·grade 기반 kill bonus XP와 `GOLD_GAIN_BONUS`가 반영된 gold를 더하고, level-up 최대 HP 비율 보존 뒤 player `HP_RECOVERY`를 적용한다. 전리품은 만들지 않는다.
- 몬스터 최종 `MAX_HP`, `DAMAGE`, `DEFENSE`는 저장하지 않고 versioned definition/config와 인스턴스의 원천 상태에서 계산한다.
- 몬스터 종족은 저장된 instance의 Stage·encounter·grade·balance version을 `MonsterSpeciesPolicy`에 전달해 관찰 시 파생한다. NORMAL Stage는 다섯 종족을 1~2회씩 배치하고 ELITE·BOSS Stage는 한 종족을 선택한다. 능력치용 `MonsterType`은 이 스케줄 입력이 아니며, 종족 metadata는 공격·보상 계산과 Room persistence에 참여하지 않는다.
- 이 backend 상태 중 `observeCombat()` snapshot만 Calendar의 Battle Map presentation에 노출한다. 별도 전투 destination은 추가하지 않으며 UI가 Room DAO, WorkManager 또는 `CombatRepository` 구현을 직접 호출하지 않는 레이어 규칙도 유지한다.

## Room schema v15

- `todo_tasks.recurrenceSeriesId`가 반복 원본 분할 전후의 계보를 보존한다.
- `character_profile`은 `Long` XP·골드, 네 기본 스탯, 미배분 포인트와 무료 초기화 사용 여부만 저장한다. level과 파생 능력치는 저장하지 않는다.
- `character_current_state`는 current HP, balance version과 갱신 시각을 저장한다.
- `reward_ledger`는 `(taskId, occurrenceDateEpochDay)` unique key와 지급 당시의 reward snapshot 및 `TODO_COMPLETION`/`COMBAT_ATTACK` reward mode를 저장한다.
- `monster_instances`는 definition id, 등급, Stage/encounter, level, current HP와 monster balance version을 저장한다. definition과 계산된 `MAX_HP`·`DAMAGE`·`DEFENSE`는 저장하지 않는다. 현재·과거 행 전체가 도감 발견 projection의 원천이다. ADR-021의 발견 기능은 별도 discovery table·column이나 migration을 만들지 않았고, 이후 `MIGRATION_14_15`도 이 table을 변경하지 않는다.
- singleton `combat_progress`는 Stage와 잠근 level, 활성 monster id, `lastReconciledAtEpochMillis` cursor와 balance version을 저장한다.
- `player_attack_events`는 `(taskId, occurrenceDateEpochDay)` primary key를 사용하는 PENDING outbox이자 확정 플레이어 공격 event다. source snapshot, reward version, nullable `sourceTaskDifficulty`, 기본값 `0`인 `taskDifficultyBalanceVersion`, 처리 뒤 seed·roll·피해와 hit/kill XP·gold operand/result를 한 row에 보존한다. version `0`은 nullable 난이도와 무관한 중립 배율이고 version `1`은 non-null 난이도를 요구한다.
- `monster_attack_events`도 독립된 `(taskId, occurrenceDateEpochDay)` primary key를 사용해 적용 또는 영구 skip된 마감 실패 공격과 HP 전후·치명 여부를 보존한다. 별도 테이블이므로 같은 occurrence의 경제 ledger·플레이어 공격과 서로 막지 않는다.
- `character_appearance`는 character id별 hair id를, `character_equipped_items`는 nullable head·accessory·weapon·gloves와 필수 top·bottom·shoes id를 저장한다. 두 테이블은 선택 catalog나 inventory가 아니라 현재 render loadout의 원천 상태다. nullable `glovesId`는 장갑 gameplay projection이 없을 때 기본 `hands_front`를 사용하는 appearance fallback이다.
- `equipment`는 명시적 ID, 한국어 resource key, type·slot·rarity, 가격·요구 레벨·판매 상태와 선택적 image/layer key를 저장한다. `equipment_modifiers`는 `(equipmentId, sortOrder)` key로 modifier 순서를 보존한다.
- `owned_equipment`는 `(characterId, equipmentId)` unique index로 중복 소유를 막고 quantity를 두지 않는다. `character_equipment`는 `(characterId, slot)` primary key로 slot별 실제 gameplay 장착을 저장하며 하나의 owned row가 여러 slot에 중복 장착되지 않도록 unique index를 둔다.
- `task_reminders`는 task별 한 row에 mode, custom minute-of-day, typed schedule status, 현재 materialized occurrence epoch day·trigger epoch millis와 갱신 시각을 저장한다. `taskId`는 `todo_tasks`를 `ON DELETE CASCADE`로 참조하고 mode·status index를 갖지만, soft delete task의 row는 실제 PendingIntent를 취소할 때까지 보존한다.
- `character_status_effects`는 `(characterId, effectType)` primary key로 현재 상태 한 건을 저장한다. definition version, 적용·만료 epoch millis, 남은 회복 완료 수, 활성 여부, revision과 마지막 mutation id를 보존하며 character 삭제에 cascade한다.
- `status_effect_recovery_occurrences`는 `(characterId, effectType, revision, taskId, occurrenceDateEpochDay)` primary key로 완료 credit을 한 번만 저장한다. effect row 삭제에 cascade하고 같은 occurrence의 중복 완료·취소 후 재완료가 남은 회복 횟수를 다시 줄이지 못하게 한다.
- 수동 `MIGRATION_1_2`부터 `MIGRATION_14_15`까지 연결한다. v1/v2/v3→v4에서 일정·완료·기존 보상·XP·골드를 보존하고 전투 기록은 소급 생성하지 않으며, v4→v5는 기존 character id마다 기본 appearance loadout을 `INSERT OR IGNORE`로 추가한다. v5→v6은 빈 `failure_logs`와 기존 monster event에 기본 `MISSED_DEADLINE` trigger를 추가한다. v6→v7은 기존 source를 변경하지 않고 네 장비 테이블만 빈 상태로 추가한다. v7→v8은 schema table을 바꾸지 않고 누락 failure source만 멱등 backfill한다. v8→v9는 기존 ledger에 `TODO_COMPLETION`, 기존 player attack에 reward version `0`과 award `0` 기본값만 추가해 과거 경제·전투 결과를 변경하거나 소급 지급하지 않는다. v9→v10은 빈 `task_reminders` table과 index만 추가하고 기존 task에 reminder row나 alarm을 소급 생성하지 않는다. v10→v11은 `character_equipped_items`에 nullable `glovesId` column만 추가하고 기존 row를 미장착 장갑으로 보존한다. v11→v12는 `equipment`에 nullable `weaponType`을 추가하고 기존 `WEAPON` row만 `LONGSWORD`로 backfill한다. v12→v13은 두 상태이상 테이블을 빈 상태로 추가해 기존 source를 그대로 보존하고 과거 치명 attack을 backfill하지 않는다. v13→v14는 `player_attack_events`에 nullable 난이도와 default `0` difficulty version만 additive로 추가하고 PENDING/APPLIED row 모두를 null/version `0`으로 보존하며 `todo_tasks`를 조회해 backfill하지 않는다. v14→v15는 `character_equipped_items`의 nullable overlay를 비우고 top·bottom·shoes를 중립 default key로 바꾸는 data-only migration이며 `owned_equipment`, `character_equipment`, HP·상태·일정·보상·전투·알림 source를 보존한다. reminder row가 없는 legacy task는 Repository mapper에서 `NONE`·`DISABLED`로 읽는다. fresh character는 profile·current state·appearance·equipped items 네 row를 한 transaction에서 함께 생성하고, 장비 catalog는 Repository 준비 transaction에서 별도로 멱등 seed한다.
- 최신 identity schema는 `app/schemas/com.todoquest.data.local.TodoQuestDatabase/15.json`에 고정하고 production builder는 모든 수동 migration을 순서대로 등록한다. 호환 물리 컬럼 `monster_attack_events.revivedHp`는 삭제하지 않으며 v13 이후 신규 치명 event에서는 응급 회복 HP snapshot을 담는다.
- Combat Reward v2와 장비 가격 완화는 기존 reward version·snapshot·equipment price column을 사용한다. canonical identity와 기존 가격이 모두 일치하는 18종 row만 조건부 갱신하며 Room database version과 identity schema를 올리지 않고, 기존 소유 row·현재 gold·구매 이력은 환불 없이 보존한다.

### 자동 마감 실패 정합성의 Room v8 보정

- 신규 reconciliation은 occurrence별 completion·failure·monster event 존재를 transaction 안에서 확인한 뒤 failure row와 `MISSED_DEADLINE` event를 함께 추가한다. 피해 적용 event면 player current HP를, 상한 초과 event면 `SKIPPED_RECONCILIATION_CAP` 결과를 같은 transaction에서 저장하고 마지막에 cursor를 갱신한다.
- `MIGRATION_7_8`은 v7까지 이미 확정된 자동 event와 UI 실패 source의 불일치만 보정하는 data-only migration이다. `MANUAL_FAILURE`, completion이 존재하는 occurrence, 이미 failure row가 있는 occurrence는 변경하지 않는다.
- migration은 기존 attack event·HP·RewardLedger·Stage·appearance·장비 source를 수정하지 않고 transition을 생성하지 않는다. `INSERT OR IGNORE`와 occurrence unique key로 재실행에도 같은 failure row를 한 번만 유지한다.

### Calendar Combat Feedback v1의 Room v6 확장

- occurrence 실패 원천 상태는 `(taskId, occurrenceDateEpochDay)`를 식별자로 영속해 `TODO`·`COMPLETED`·`FAILED` 표시를 재시작 뒤에도 재구성한다. 실패 취소는 표시 상태를 `TODO`로 복원하되 combat event에는 cascade하지 않는다.
- `monster_attack_events`의 기존 occurrence primary key는 유지하고 공격 원인만 `MANUAL_FAILURE` 또는 `MISSED_DEADLINE`로 구분한다. 두 원인이 같은 occurrence에 별도 row나 별도 damage를 만들 수 없다.
- Room v5의 character·appearance·reward·monster·Stage·양방향 event 원천 상태와 migration chain을 보존한다. v6 migration은 과거 occurrence를 임의로 `FAILED` 처리하거나 과거 전투 transition을 생성하지 않는다.
- attack·hit·death·spawn·damage text transition은 Room에 replay queue로 저장하지 않는다. event transaction의 최초 적용 결과만 presentation에 전달한다.

`FailureLogEntity`·`FailureLogDao`, `MonsterAttackEventEntity.trigger`, `MIGRATION_5_6`과 schema export `6.json`으로 기본 계약을 구현했다. 당시 v6 migration은 과거 occurrence를 `FAILED`로 backfill하지 않고 기존 `monster_attack_events`만 `MISSED_DEADLINE` 원인으로 해석했다. 이후 Room v8의 data-only 보정은 완료되지 않은 기존 자동 event에 한해 누락 failure source를 backfill하며 과거 damage나 transition은 재생하지 않는다.

## Equipment Shop and Inventory의 Room v7 확장

ADR-013의 gameplay 장비 source는 Room v7에서 다음과 같이 구현했다.

- `EquipmentCatalogSeeder`는 명시적 ID의 18종 catalog와 modifier를 `INSERT OR IGNORE`로 seed한다. 가격, 판매 상태, 요구 레벨, `EquipmentType`, `EquipmentSlot`과 modifier를 제공하며 type과 slot은 모두 `WEAPON`, `HELMET`, `CHEST`, `LEGS`, `GLOVES`, `SHOES`, `ACCESSORY`만 허용하고 서로 일치하는 mapping을 검증한다. `weaponType`은 gameplay 무기에만 필수이며 `낡은 검`·`철 장검`은 `LONGSWORD`, `물푸레나무 창`은 `SPEAR`, `강철 철퇴`는 `BLUNT`로 seed한다.
- 가격 완화는 id `1001..1018`의 canonical id·name key·type·slot과 기존 가격을 모두 비교한 조건부 data update다. 승인 가격은 순서대로 `20, 360, 27, 340, 22, 130, 1200, 22, 120, 1150, 140, 380, 1350, 3400, 410, 430, 25, 390` 골드이며 이미 구매한 장비에는 환불이나 ownership 변경을 만들지 않는다.
- Room v15의 현재 seeder는 고정 ID `1019..1025`의 모험가 7부위를 더한다. 모두 `UNCOMMON`, 요구 레벨 5, 두 modifier이며 기존 adventure layer key, `gloves_adventure`와 3분할 검 compatibility mapping을 사용한다. 이 catalog seed와 `MIGRATION_14_15`는 ownership·장착 row를 만들지 않는다.
- `owned_equipment`는 캐릭터와 equipment 조합을 unique key로 사용한다. 같은 equipment의 중복 구매와 quantity는 허용하지 않는다.
- `character_equipment`는 캐릭터와 slot 조합을 unique key로 사용하고 반드시 해당 캐릭터의 `owned_equipment`를 참조한다. 파생 스탯 계산은 이 테이블에 실제 장착된 equipment modifier만 읽는다.
- 기존 `character_appearance`와 `character_equipped_items`는 삭제하지 않고 외형 fallback으로 보존한다. 특히 v6에서 이어진 `topId`와 `bottomId` 및 v11의 nullable `glovesId`는 별도 appearance layer이며 새 gameplay ownership row로 간주하지 않는다.
- v6에는 `ARMOR` slot 값이 없으므로 `MIGRATION_6_7`은 `ARMOR` 변환이나 appearance 기반 ownership backfill을 수행하지 않는다. 호환 입력의 `ARMOR`와 `TOP`은 `CHEST`, `BOTTOM`은 `LEGS`, `HEAD`는 `HELMET`으로 Repository mapper 경계에서 정규화하고 `PET`과 알 수 없는 값은 거부한다.
- `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`와 8개 `DerivedStats`는 그대로 유지한다. 민첩 계열은 `FOCUS`, 지능 계열은 `WILLPOWER`로 변환하며 새 원천 스탯 컬럼이나 계산된 파생값 컬럼을 만들지 않는다.
- migration은 기존 profile·current HP·appearance·RewardLedger·Stage·failure·양방향 attack event를 보존하고 gameplay 소유권을 appearance row에서 생성하지 않는다. schema export `7.json`과 v1/v2/v3/v4/v5/v6→v7 migration test가 destructive migration이나 기존 occurrence·combat key 변경이 없음을 검증한다.

## 알림 처리

Task Reminder v1은 Room v10, application-scope Repository·UseCase graph, Android scheduler·publisher·manifest receiver, restore worker와 Calendar editor까지 구현했고 Delivery Reliability v2는 Room v14에서 staged plan과 채널 capability를 보강했다. UI는 scheduler나 Android framework를 직접 호출하지 않고 ViewModel → task/reminder UseCase → Repository·`ReminderScheduler` 경계를 사용한다. Compose의 permission launcher와 settings launcher는 ViewModel의 일회성 event만 수행하며 alarm 예약 상태를 직접 변경하지 않는다.

Settings는 같은 `ReminderScheduler`의 `POST_NOTIFICATIONS` capability만 읽어 `Loading`·`Available`·`Required`·`ChannelDisabled`·`CheckFailed`로 투영한다. Compose launcher는 `AndroidReminderCapabilityAdapter`가 만든 runtime permission 또는 package 앱·채널 settings action만 실행하고 결과 복귀·`ON_RESUME`에 `SettingsViewModel` refresh를 요청한다. exact-alarm capability와 special access는 이 Settings 흐름에서 제외하며 Calendar reminder 경계에 남긴다.

ADR-017의 system notification permission 진입점은 `SharedPreferencesFirstLaunchNotificationPromptStore → PrepareFirstLaunchNotificationPromptUseCase → CalendarViewModel`로 구현했다. Android 13 이상에서 아직 요청하지 않은 사용자의 첫 Calendar 진입에 한국어 안내와 one-shot `POST_NOTIFICATIONS` request를 두며, 거부·보류 뒤 자동 재요청하지 않는다. API 33 이상 runtime permission, notification 비활성 package settings와 capability 충족 no-op은 `AndroidReminderCapabilityAdapter`의 typed action으로 분기한다. reminder 저장 시 capability가 없으면 package notification settings를 여는 `알림 설정` CTA만 제공한다.

- `task_reminders`에는 `NONE`, `TEN_MINUTES_BEFORE`, `ONE_HOUR_BEFORE`, `CUSTOM_TIME` 설정과 typed status를 저장하고 실제 예약은 `ReminderOccurrenceKey(taskId, occurrenceDate)`와 trigger instant로 식별한다. 기본은 `NONE`이며 reminder row가 없는 v9 이전 task도 같은 값으로 읽는다.
- `TEN_MINUTES_BEFORE`와 `ONE_HOUR_BEFORE`는 task local time이 있어야 하며 이전 local date로 넘어갈 수 있다. `CUSTOM_TIME`은 occurrence 날짜의 독립 local time으로 task time이 없어도 유효하고 task time보다 늦을 수 있지만 날짜 offset은 갖지 않는다.
- 반복 일정은 현재 활성 task segment의 occurrence를 순서대로 계산하되 현재 instant보다 엄격히 뒤이고 `TODO`인 첫 occurrence 하나만 materialize해 exact alarm으로 예약한다. 원본 전체나 무한 occurrence 목록을 예약하지 않는다.
- 예약 상태는 `DISABLED`, `PENDING`, `SCHEDULED`, `POST_NOTIFICATIONS_REQUIRED`, `NOTIFICATION_CHANNEL_DISABLED`, `EXACT_ALARM_ACCESS_REQUIRED`, `DELIVERED`, `NO_FUTURE_OCCURRENCE`, `ERROR`의 typed 값으로 표준화한다. 권한·scheduler 상태는 task mutation 결과와 분리하며 문자열 예외 메시지를 제어 흐름으로 사용하지 않는다.
- `RoomTaskRepository`는 task 생성·수정과 reminder setting row를 같은 Room transaction에서 저장한다. 반복 분할은 원 segment의 현재 예약 key를 취소할 수 있게 보존하고 새 segment의 reminder row를 독립 생성한다. soft delete와 occurrence 완료·실패·각 취소가 commit된 뒤 관련 old/current task id를 재검토한다. 알림 취소·예약 실패는 이미 성공한 일정, RewardLedger, combat event transaction을 rollback하지 않는다.
- `ReconcileTaskReminderUseCase`는 다음 유효 plan을 계산한 뒤 occurrence key·trigger와 `PENDING`을 Room에 먼저 조건부 stage하고 `AlarmManager`를 호출한다. 같은 key가 current일 때만 `SCHEDULED`로 바꾸며 scheduler 실패는 같은 key alarm을 best-effort 취소한 뒤 current key만 plan 없는 `ERROR`로 정리한다. scheduler 호출 중 receiver가 먼저 claim한 `DELIVERED`는 후속 성공·실패 update가 덮어쓰지 않는다.
- alarm 발화 시 `DeliverReminderUseCase`가 persisted occurrence key, `PENDING` 또는 `SCHEDULED` 상태, 활성 task segment, `occursOn` 결과와 occurrence의 `TODO` 상태를 다시 확인하고 `(taskId, scheduledOccurrenceEpochDay)` 조건부 update로 게시 권한을 claim한다. stale·중복 key와 완료·실패·삭제 occurrence는 notification을 게시하지 않고 상태를 정리하며, 유효한 반복 occurrence만 게시한 뒤 다음 미래 occurrence를 예약한다.
- 지난 일회성 trigger는 즉시 재생하지 않고 `NO_FUTURE_OCCURRENCE`로 확정한다. 반복 일정은 과거 occurrence를 건너뛰며 앱 복귀·재부팅·프로세스 재생성 때 missed notification을 일괄 게시하지 않는다.
- `AndroidReminderScheduler`는 API 31 이상에서 `canScheduleExactAlarms()`가 true일 때만 `RTC_WAKEUP` `AlarmManager.setExactAndAllowWhileIdle()`을 사용한다. alarm `PendingIntent`는 title·memo를 넣지 않은 explicit receiver intent, occurrence URI와 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`로 식별한다. manifest에는 `SCHEDULE_EXACT_ALARM`을 선언하고 `USE_EXACT_ALARM`과 inexact fallback은 사용하지 않는다. API 33 이상 notification 게시에는 `POST_NOTIFICATIONS` runtime permission이 필요하다.
- notification system permission은 첫 Calendar 진입의 한국어 안내 뒤 한 번만 요청한다. 사용자가 거부·보류하면 자동 재요청하지 않고, `NONE`이 아닌 reminder 저장 시 `POST_NOTIFICATIONS_REQUIRED`와 package `알림 설정` CTA를 표시한다. 앱 권한은 사용 가능하지만 API 26 이상 `todo_task_reminders` 채널 importance가 `IMPORTANCE_NONE`이면 `NOTIFICATION_CHANNEL_DISABLED`와 package·channel scoped `알림 채널 설정` CTA를 제공한다. notification capability가 확보된 뒤 exact access가 필요한 reminder를 저장한 경우에만 한국어 rationale dialog에서 용도를 설명한 후 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 설정을 연다. exact access는 첫 실행에 요청하지 않는다. 거부·dismiss·scheduler 오류는 한국어 경고와 typed status로 표시하며 핵심 일정·보상·전투·구매 command를 차단하지 않는다.
- `AndroidReminderPublisher`는 API 26 이상에서 앱 시작 또는 게시 전에 생성하는 높은 중요도, 기본 소리·진동 channel과 `PRIORITY_HIGH` private notification으로 가능한 환경에서 heads-up 표시를 요청한다. API 23~25에서는 notification 자체에 기본 소리·진동과 high priority를 지정한다. 잠금 화면 public version은 task 제목을 숨긴다. 사용자 channel importance·sound·vibration 설정과 방해 금지 모드가 최종 동작을 결정하며 앱이 이를 우회하지 않는다. immutable content intent는 Calendar의 occurrence 날짜와 task 문맥을 연다.
- `TodoQuestApplication` 시작과 비exported `ReminderRestoreReceiver`가 받는 `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`는 `reminder-reconciliation-startup` unique one-time work를 enqueue한다. `ReminderReconciliationWorker`는 persisted reminder task를 모두 순회하고 transient database failure만 최대 3회 재시도한다. WorkManager는 이 재등록·상태 복구의 best-effort 실행만 담당하고 정확한 notification trigger로 사용하지 않는다. 15분 주기 전투 reconciliation work와도 별도 이름·진입점으로 유지한다.
- Force Stop의 stopped package와 Android background restricted 상태처럼 OS가 PendingIntent·alarm·job 또는 boot broadcast를 중단하는 환경은 정확한 전달 보장 범위에서 제외한다. 사용자가 앱을 다시 시작하면 missed notification을 게시하지 않고 현재 source state에서 미래 예약만 복구한다.

## 상태 관리

- 화면 상태는 immutable UI state data class로 표현한다.
- 날짜 선택, 필터, 입력 중 폼 값은 ViewModel state로 관리한다.
- Room에서 관찰 가능한 데이터는 Flow로 노출한다.
- 일회성 snackbar, permission prompt 같은 이벤트는 별도 event stream으로 분리한다.
- Calendar의 legacy `TODO_COMPLETION` 양수 보상 snackbar만 `CompletionResult`에서 만들어지는 일회성 `CalendarEvent`로 전달한다. 새 `COMBAT_ATTACK` 완료는 snackbar를 만들지 않고 실제 attack reward transition을 Battle Map badge로 표시한다.
- `CombatTransition`은 application-scope Repository의 replay 0 event flow에서 새 attack event 최초 확정 때만 방출한다. `CalendarViewModel`이 소유한 buffered `Channel` actor는 event key를 controller lifetime 동안 한 번만 받아 scene override를 한 번에 하나씩 직렬 소비한다. 화면 재구성에 필요한 HP·Stage는 Room Flow에서 다시 읽되 transition은 recomposition, collector 재구독 또는 process 재시작에 replay하지 않는다.

## 테스트 전략

- 반복 occurrence 계산은 순수 Kotlin unit test로 검증한다.
- 레벨·파생 스탯·전투 계산 계약, modifier validation, 정시·효율·연속일 정책은 순수 Kotlin unit test로 검증한다.
- Room v1/v2→v3 migration, occurrence 보상 멱등성, 캐릭터 성장·HP·초기화 transaction은 Robolectric repository/DAO test로 검증했다.
- Room v1/v2/v3→v4 migration, 플레이어·몬스터 공격의 종류별 멱등성, outbox 재시도와 첫 초기화 이전 피해 비소급은 Robolectric repository/DAO test로 검증했다. 현재 치명 피해 후 처리는 Room v13 중상 transaction 테스트가 담당한다.
- Room v4→v5 migration의 기존 상태 보존·기본 loadout, fresh 4-row 원자 초기화, slot catalog 검증과 invalid update의 부분 저장 방지를 Robolectric repository/DAO test로 검증했다.
- 독립 layer catalog의 schema v6 z-order·동일 원점, 중립 training fallback·`gloves_adventure`·legacy 3분할 검을 포함한 runtime path와 loadout 조합, 최상단 weapon pixel, 2단계 cache와 generated preview/sheet raw RGBA equality를 unit·Python test로 검증했다.
- 실패 occurrence 탐색 순서와 reconciliation당 피해 상한, 몬스터 능력치·Stage 성장 및 피해 계산은 순수 Kotlin unit test로 검증했다.
- `MonsterSpeciesPolicyTest`와 `MonsterStagePolicyTest`는 balance version 1 golden vector, 반복 호출 동일성, 각 NORMAL Stage의 다섯 종족 포함·1~2회 균형, ELITE·BOSS 단일 선택, invalid input과 종족 조회 전후 능력치·피해·보상 불변을 검증한다. `RoomCombatRepositoryTest`는 재시작 뒤에도 저장된 Stage·encounter·grade·balance version에서 같은 종족을 복원하고 처치 transition이 이전·다음 종족을 보존하는지 확인한다.
- `MonsterDiscoveryPolicyTest`, `CombatDaoTest`, `RoomCombatRepositoryTest`는 빈 이력, 중복 종족 축약, 다섯 종족 결정 mapping, HP 0 과거 row, balance version 불일치, 초기 활성 몬스터와 재시작 소급 복원 및 승리 뒤 Flow 즉시 갱신을 검증한다. 발견 기능은 Room v15의 monster source를 변경하지 않는다.
- Calendar Combat Feedback v1의 `TODO`·`COMPLETED`·`FAILED` 전이와 실패 취소 뒤 늦은 완료는 `OccurrenceCalculatorTest`, `CombatUseCaseTest`, `RoomTaskRepositoryTest`, `CalendarViewModelTest`로 검증했다. `MANUAL_FAILURE`·`MISSED_DEADLINE` 경합과 같은 occurrence의 단일 damage·단일 transition은 `RoomCombatRepositoryTest`로 검증했다.
- Room v5→v6 migration의 기존 completion·RewardLedger·character HP·active monster·Stage·attack event 보존과 과거 failure·transition 비소급은 `TodoQuestDatabaseMigrationTest`로 검증했다.
- Room v1/v2/v3/v4/v5/v6/v7→v8 migration의 기존 source 보존, 빈 장비 테이블 추가와 appearance 비승격, 완료·수동 failure 제외 및 기존 자동 `APPLIED`·`SKIPPED` event의 멱등 failure backfill은 `TodoQuestDatabaseMigrationTest`로 검증한다. v10→v11 nullable `glovesId`, v11→v12 nullable `weaponType`, v14→v15 중립 fallback 갱신과 ownership·`character_equipment`·HP·상태·일정·보상·전투 보존도 같은 migration suite가 담당한다. 25종 seed·modifier 순서·고정 ID `1019..1025`의 두 modifier, foreign key·unique 소유·`CHEST`/`LEGS` 독립 장착은 `EquipmentDaoTest`가 담당한다.
- 구매 최신 상태 재검증·중복 경합·rollback, 장착 소유/slot 검증·대상 slot 교체, 해제의 소유 보존·대상 fallback 기본화·`AlreadyEmpty` 멱등성·상태이상 포함 HP 비율과 0 HP 보존은 `RoomEquipmentRepositoryTest`로 검증한다. 실제 modifier의 Character·Task·Combat 연결은 `RoomCharacterRepositoryTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest`가 담당한다.
- startup·15분 unique WorkManager 등록, 제한 재시도와 concurrent worker 멱등성은 `CombatReconciliationWorkerTest`로 검증했다.
- typed 일괄 능력치 배분의 0·포인트 부족·안정된 stat cap 순서는 `StatAllocationPolicyTest`, 최신 Room 상태 재검증·전체 stat 원자 저장·장비 modifier 기반 배분 전후 HP 비율 1회 보존·0 HP·rollback은 `RoomCharacterRepositoryTest`로 검증한다. `CharacterViewModelTest`는 비영속 draft·단일 batch UseCase·오류 시 draft 유지·초기화 차단을, `CharacterScreenTest`와 `AppNavigationTest`는 `-/+`·pending·저장 UI 및 저장 전 파생값/HP 비preview와 저장 후 application-scope Flow 갱신을 검증한다.
- `CharacterGuideUseCasesTest`, `SharedPreferencesCharacterGuideRepositoryTest`, `TodoQuestAppContainerTest`는 신규·기존 설치 eligibility, 최초 값 고정, 자동 확인 멱등성, 저장 실패 격리와 Room 초기화 전 DB 존재 판별을 검증한다. `CharacterViewModelTest`, `CharacterScreenTest`, `AppNavigationTest`는 Loaded 뒤 자동 표시, 확인 전 재생성·process 재시작 재표시, 확인 뒤 비재생, 수동 도움말 재열람, 확정 한국어 대사, 96dp 최근접 sprite·fallback, 본문 독립 scroll과 `320dp`·font scale `2.0`·TalkBack 접근성을 검증한다.
- ViewModel은 fake repository와 test dispatcher로 검증한다. Shop·Inventory의 filter·같은 slot 비교, `ownedEquipmentByEquipmentId` 일관성, `Purchase`·`PurchaseUnavailable`·`Equip`·`Unequip` action 우선순위, 중복 입력 억제와 side effect 비재생은 `ShopViewModelTest`와 `InventoryViewModelTest`가 담당한다. Shop 프리뷰는 snapshot 기반 7개 slot·최종 3개 능력치, 상품 선택과 상세 상태 분리, 비영속 외형·stat delta, 선택 장비 해제 성공 cleanup과 다른 장비·실패 시 선택 유지, 구매·장착·해제 commit 전후와 `CHEST`/`LEGS` 독립 갱신을 추가로 검증한다.
- Compose UI test는 `BattleMapTest`, `CalendarDayIndicatorTest`, `CalendarScreenTest`, `ShopScreenTest`, `InventoryScreenTest`, `SettingsScreenTest`, `AppNavigationTest`에서 shared layered renderer와 monster drawable 경로, 고정 Battle Map·독립 Calendar scroll, actor 상단 HP bar, 일회성 effect, 실패/취소 흐름, Shop 압축 대장장이 배너·캐릭터 중심 통합 preview/stat card·7개 소형 slot, 상품 선택 전후 외형·delta, card/detail 공통 네 action, Inventory 장착, Settings 일반 알림 권한 상태, navigation, 작은 폭·큰 글꼴 scroll과 한국어 접근성 label을 검증한다. Shop 검증은 stat cell의 `112dp/170dp` 고정 bounds와 우측 하단 `104dp × 48dp` action, 선택 장비 해제 cleanup을 포함한다. 장비 artwork UI 검증은 기존 visual과 adventure 7부위·중립 fallback·`gloves_adventure`·3분할 검, unknown/decode fallback, Room 기반 구매·장착·재시작 복원, Character·Battle Map shared renderer projection을 포함한다. animation phase 순서와 중복 key 직렬 소비는 `BattleAnimationControllerTest`의 virtual time 검증이 담당한다.
- `MonsterCompendiumViewModelTest`와 `CompendiumScreenTest`는 명시적 5종 slot, 미발견 표시 resource 부재, 발견 count·percent, 발견 이름 resolver 검색, 전체/발견/미발견 filter, 선택 preview, 3~5열 grid, 미발견 click의 replay 없는 안내, 발견 detail sheet, 최근접 `Fit` 렌더링과 `320dp`·font scale `2.0` 접근성을 검증한다. `AppNavigationTest`는 다섯 top-level destination 순서·선택, sheet 우선 back, 목록 back, 탭·Activity 재생성 뒤 ViewModel 상태 복원, 미발견 privacy, 호환 상세 route와 잘못된 route 복구를 기존 Calendar·Character·Shop·Inventory·notification navigation 회귀와 함께 검증한다.
- Battle Sound Effects v1은 순수 event mapping test로 `eventId + BattleSfx` identity와 player/monster attack·hit·defeat 순서, 실제 `CombatLifecycleEvent.PlayerDefeated` 없는 death 음 금지를 검증한다. controller virtual-time test는 직렬 소비·중복 억제·설정 off의 시각 상태 불변을, SharedPreferences test는 기본 켜짐과 process 재생성 복원을, `SoundPool` adapter test는 여섯 preload·`USAGE_GAME`·`CONTENT_TYPE_SONIFICATION`·`maxStreams = 6` 및 load 전·background·released 요청 폐기를 검증한다. navigation 연결 test는 다섯 탭과 `효과음` Switch를 확인하며 Room migration test 수는 늘리지 않는다.
- `ReminderPlannerTest`는 네 mode, preset의 이전 날짜 trigger, 무시간 custom, occurrence 기준 local trigger, 엄격한 미래·DST gap/overlap과 완료·실패 occurrence skip을 순수 Kotlin으로 검증한다.
- `TodoQuestDatabaseMigrationTest`와 `TaskReminderDaoTest`는 v1~v15 migration chain, v9→v10 reminder 무 backfill, v10→v11 nullable gloves, v11→v12 nullable weapon subtype, v12→v13 상태이상, v13→v14 nullable 난이도/version `0`, v14→v15 appearance-only fallback 전환과 gameplay source 보존, FK cascade·index, 전체 reminder Flow와 occurrence key 조건부 갱신을 검증한다. `RoomTaskRepositoryTest`와 `RoomReminderRepositoryTest`는 task+setting 원자 저장, 반복 분할, legacy `NONE` fallback, 신규 completion 난이도 snapshot, reminder occurrence projection, staged plan과 stale key 경합을 검증한다.
- `ReminderUseCaseTest`, `AndroidReminderSchedulerTest`, `ReminderAlarmReceiverTest`, `ReminderReconciliationWorkerTest`는 앱 권한·채널 capability 순서, 실패 격리, PENDING-before-schedule, callback 선점 뒤 DELIVERED 보존, exact PendingIntent identity, API 23~25 default alert, high-importance private notification, 발화 직전 재검증·단일 claim, restore action·unique work와 제한 재시도를 검증한다.
- `FirstLaunchNotificationPermissionUseCaseTest`, `NotificationPermissionEntryPointsTest`, `CalendarViewModelTest`는 preference one-shot 소비, 앱 권한·채널 실패 격리, API별 runtime/app settings/channel settings/no-op 분기, occurrence mode·당일/전날 발화 시각, task별 복구 event와 거부 뒤 비재생을 검증한다. `SettingsViewModelTest`는 `POST_NOTIFICATIONS` 전용 Loading·Available·Required·ChannelDisabled·CheckFailed, 최신 refresh 우선·취소 격리와 효과음 독립성을 검증한다. `SettingsScreenTest`와 `AppNavigationTest`는 한국어 상태/action, runtime·앱·채널 launcher, 복귀·`ON_RESUME` 재조회와 navigation 독립성을 검증하며 exact alarm action이 없음을 확인한다. `ReminderIntegrationTest`와 `TodoQuestDatabaseIsolationTest`는 production graph의 Room v15→AlarmManager 연결, PENDING callback 경합, receiver 중복 억제, notification tap과 current v15 database 재개방을 검증한다. 실제 notification·exact-alarm capability가 필요한 process-absent smoke fixture는 일반 connected suite에서 제외하고 `scripts/verify_task_reminder_delivery.ps1`이 명시적으로 시작한다.
- `TaskDifficultyCombatPolicyTest`, `CombatRewardPolicyTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest`는 difficulty version `0` 중립, version `1`의 EASY/MEDIUM/HARD `100%/150%/200%`, MOMENTUM→난이도→critical→defense 순서, 비치명 XP `3/4/6`, level 1 NORMAL 처치 XP `23/34/46`, kill gold `15` 불변, APPLIED snapshot 불변과 미지원 version/source rollback을 검증한다. 기존 combat reward v0 무보상, PENDING v1 `1/10/5`, v2 `3/20/15` routing도 유지한다. `EquipmentDaoTest`와 `RoomEquipmentRepositoryTest`는 fresh 18종 승인 가격, canonical identity·old price 조건부 갱신, custom 가격·소유·장착·gold 보존을 검증한다.
- `StatusEffectPolicyTest`, `DerivedStatsCalculatorTest`, `MonsterCombatPolicyTest`는 HP 0 clamp, 20% 유효 스탯 감소·base 불변·최소 1, 중상 적용 뒤 50% 응급 회복과 24시간 경계를 검증한다. `StatusEffectDaoTest`, `RoomStatusEffectRepositoryTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest`는 revision별 occurrence 완료 3회, duplicate·rollback, 재패배 refresh·비중첩, 재시작 지속성·만료, 해제 뒤 스탯 복원과 XP·gold·기존 completion/failure/attack 불변을 검증한다. `TodoQuestDatabaseMigrationTest.migrationFromVersion12ToVersion13PreservesSourcesAndCreatesEmptyStatusEffectTables`가 12→13 migration을 검증한다.
- `BattleAnimationControllerTest`는 `PlayerDefeated → StatusEffectApplied|Refreshed → PlayerEmergencyRecovered`와 제거 event 순서, event id 중복 제거·재구독 비재생을 virtual time으로 검증한다. `CharacterScreenTest`, `BattleMapTest`는 한국어 merged semantics, 48dp target, 상세 dialog, 큰 글꼴 배치와 live region을, `AppNavigationTest.severeInjuryPersistsAcrossCharacterReentryAndActivityRecreationWithoutReplayingLifecycle`은 실제 Room 상태의 탭 재진입·Activity 재생성 지속성과 lifecycle 비재생을 검증한다.
- `BattleMapTest`는 EXP group의 최소 `104dp`·intrinsic 확장, label/value와 bar 양끝 정렬, `0/100` track·outline, 큰 글꼴과 actor/map 경계 불변 및 중상 status 전후 player 좌표 동일성을 검증하고 `ShopScreenTest`는 Repository 가격과 공통 typed action을 목록·상세에 일관되게 표시하는지 검증한다.
- `ReminderBackgroundSmokeFixtureTest`는 notification·exact capability가 허용된 연결 기기에서 15~60초 뒤 custom alarm을 실제 production graph로 seed한다. `scripts/verify_task_reminder_delivery.ps1 -Serial <emulator-serial>`은 disposable emulator에서 APK 설치·capability 준비, instrumentation, `cmd activity stop-app`, 최대 90초 polling을 표준화하고 exact alarm delivery count 증가, occurrence notification tag와 TodoQuestReminder 오류·target crash 부재를 `app/build/reminder-diagnostics/<serial>/`에 기록한다. PendingIntent를 의도적으로 취소하는 Force Stop은 성공 경로 smoke로 사용하지 않는다.
