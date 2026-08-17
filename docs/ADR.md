# Architecture Decision Records

## 철학

Todo Quest는 일정관리 앱이 먼저이고 RPG 요소는 행동 강화를 위한 보조 시스템이다. MVP는 로컬에서 안정적으로 작동하는 최소 기능을 우선하며, 서버와 외부 캘린더 연동은 초기 복잡도를 줄이기 위해 제외한다.

---

### ADR-001: Android 네이티브와 Jetpack Compose 선택

**결정**: Kotlin 기반 Android 네이티브 앱과 Jetpack Compose를 사용한다.

**이유**: Android 일정, 알림, 권한, 로컬 DB 기능과 직접 통합하기 쉽고, Compose는 캘린더와 게임형 UI를 선언적으로 구성하기 적합하다.

**트레이드오프**: iOS와 웹을 동시에 지원하지 않는다. 크로스플랫폼 확장은 별도 프로젝트로 다룬다.

### ADR-002: 로컬 우선 Room 저장소 선택

**결정**: MVP 데이터는 Room에 저장하고 서버 동기화는 구현하지 않는다.

**이유**: 개인 일정 앱의 핵심 흐름은 오프라인에서도 동작해야 하며, MVP에서 인증과 서버 운영 비용을 줄일 수 있다.

**트레이드오프**: 여러 기기 동기화와 계정 복구는 지원하지 않는다. 추후 백업과 내보내기 정책을 별도 phase에서 설계한다.

### ADR-003: MVVM, UseCase, Repository 구조 선택

**결정**: Compose UI, ViewModel, UseCase, Repository, Room DAO로 레이어를 분리한다.

**이유**: 반복 일정 계산, 보상 멱등성, 구매 transaction처럼 테스트가 중요한 규칙을 UI 밖에서 검증할 수 있다.

**트레이드오프**: 작은 MVP에 비해 파일 수가 늘어난다. 대신 일정과 게임 로직이 섞이는 위험을 줄인다.

### ADR-004: 반복 일정은 occurrence 단위로 완료한다

**결정**: 반복 일정 원본과 날짜별 occurrence를 분리하고, 완료 기록은 task id와 occurrence date 조합으로 저장한다.

**이유**: 매일 또는 매주 반복되는 일정에서 특정 날짜만 완료하는 것이 사용자의 기대와 맞다.

**트레이드오프**: 조회 시 occurrence 계산이 필요하다. 기간 기반 계산 로직은 순수 Kotlin 함수로 분리해 테스트한다.

### ADR-005: 보상 지급은 ledger 기준으로 멱등 처리한다

**결정**: XP와 골드 지급은 RewardLedger로 기록하고, 같은 occurrence에 대해 한 번만 지급한다.

**이유**: 완료 버튼 중복 탭, 앱 재시작, 동시 업데이트 상황에서도 보상 중복 지급을 막아야 한다.

**트레이드오프**: 단순 완료 상태보다 저장해야 할 기록이 늘어난다. 대신 캐릭터 성장과 재화 흐름의 신뢰성을 확보한다.

**관련 후속 문서**: [TODO 보상과 자동 전투 연동](game-design/character-stats/todo-combat-rewards.md)은 전투 확장 시의 추가 event 경계를 다룬다. 이 후속 계약보다 같은 occurrence의 RewardLedger 지급을 한 번으로 제한하는 현재 결정이 우선한다.

### ADR-006: 알림은 권한 실패와 일정 저장을 분리한다

**결정**: 일정 저장은 알림 예약 성공 여부와 분리하고, 알림 예약은 scheduler 인터페이스를 통해 처리한다.

**이유**: 알림 권한을 거부한 사용자도 앱의 핵심 일정관리와 보상 루프를 사용할 수 있어야 한다.

**트레이드오프**: UI에 알림 예약 상태와 권한 안내를 표시해야 한다.

### ADR-007: 픽셀 RPG 감성은 보상 영역에 집중한다

**결정**: 픽셀 RPG 표현은 캐릭터, 보상, 상점, 배지에 집중하고 캘린더와 일정 목록은 가독성을 우선한다.

**이유**: 앱의 핵심 사용 빈도는 일정 확인과 완료이므로, 장식이 정보 탐색을 방해하면 안 된다.

**트레이드오프**: 완전한 게임 UI보다 절제된 하이브리드 UI가 된다.

**관련 문서**: 픽셀 자산의 역할과 정확한 계약으로 이동할 때는 [캐릭터 아트 인덱스](art/character/README.md)와 [몬스터 아트 인덱스](art/monster/README.md)를 사용한다.

### ADR-008: Post-MVP Character Growth v1의 원천 상태와 확정 ledger를 저장한다

**결정**: Character Growth v1은 레벨 50 상한, 기본·파생 스탯, 레벨 포인트, 스탯 초기화, 정시·효율 감소·연속일 비전투 보상과 별도 캐릭터 화면으로 제한한다. 영속 계층에는 누적 XP·골드·배분한 스탯 포인트 같은 원천 상태와 occurrence별 확정 보상 ledger만 저장한다. 레벨과 파생 스탯은 저장하지 않고 원천 상태와 versioned config에서 계산한다.

기존 XP와 골드는 보존한다. 전환 시 현재 XP로 계산한 capped level까지 `2 × (level - 1)` 포인트를 소급 지급하며, 이 전환도 재실행 시 중복 지급되지 않아야 한다. 미래 occurrence의 조기 완료는 정시 보상으로 확정하고, 일일 효율은 실제 완료 로컬 날짜에, 연속일은 occurrence 날짜에 귀속한다. `MOMENTUM`은 정시 완료 occurrence 날짜와 그 다음 로컬 날짜 종료까지 유지한다.

기존 일정 생성·수정·삭제, occurrence 단위 완료와 RewardLedger 보상 멱등성은 그대로 유지한다. 자동 전투, 실패 몬스터 공격과 WorkManager reconciliation은 이 결정의 범위가 아니며 후속 ADR-009에서 별도로 승인·구현했다. 캐릭터 appearance와 equipped item id의 최소 영속·렌더 경계는 ADR-011에서, inventory·ownership·획득·사용자 장착 UI와 장비 능력치 효과는 ADR-013에서 각각 별도로 승인한다.

**이유**: 원천 상태와 확정 ledger를 보존하면 중복 보상을 방지하면서도 밸런스 규칙이 바뀔 때 level과 파생값을 versioned config로 일관되게 다시 계산할 수 있다. 날짜별 귀속 기준을 명시하면 조기 완료와 자정 경계에서도 보상 결과가 결정적이다.

**트레이드오프**: 조회 시 레벨과 파생 스탯을 계산해야 하고, config version 및 전환 완료 상태를 관리해야 한다. 대신 계산 결과를 중복 저장해 서로 어긋나는 문제와 정책 변경 시 대규모 파생값 migration을 줄인다.

**관련 문서**: 세부 계산 및 검증 계약은 [캐릭터 핵심 스탯 및 전투 계산 설계](game-design/character-stats-design.md)를 따르되, 이 ADR에서 승인하지 않은 전투·장비 범위는 구현하지 않는다.

### ADR-009: Post-MVP Monster Combat v1은 원천 상태와 독립된 멱등 event로 처리한다

**결정**: Monster Combat v1의 몬스터 최종 `MAX_HP`, `DAMAGE`, `DEFENSE`는 Room에 저장하지 않고 versioned definition/config와 몬스터 인스턴스의 레벨·유형·등급·현재 HP 같은 원천 상태에서 계산한다. Stage 진행과 몬스터 인스턴스, 양방향 공격 event 및 reconciliation cursor만 영속화한다.

`combatEligible` occurrence 최초 완료의 플레이어 공격과 마감 실패 occurrence의 몬스터 공격은 경제 보상 ledger와 서로 독립된 종류의 멱등 event다. 일정 완료와 XP·골드 보상 transaction은 전투 처리 성공을 기다리지 않으며, 플레이어 공격은 transaction 안에서 `pending` attack outbox로 확정한 뒤 별도로 처리한다. 따라서 전투 처리 실패가 일정 완료·보상 성공을 되돌리지 않는다.

완료 취소와 재완료는 기존 공격 event 또는 outbox를 삭제하거나 다시 만들지 않는다. 실패 공격 reconciliation은 첫 combat 초기화 시점을 cursor 기준으로 삼고, 그 이전의 과거 누락 occurrence에는 몬스터 피해를 소급 적용하지 않는다. cursor 이후 새로 마감된 occurrence는 완료·기존 실패·기존 monster event가 모두 없을 때만 처리하며, occurrence `FAILED`의 원천인 failure row와 `MISSED_DEADLINE` `APPLIED` 또는 복귀당 피해 상한 `SKIPPED` event를 같은 transaction에서 확정한다. 피해가 적용되면 player HP도, 모든 후보 처리가 끝나면 reconciliation cursor도 그 transaction에서 함께 갱신한다. 실패 공격은 앱 시작과 WorkManager의 best-effort 작업으로 처리하며 알림·exact alarm 권한에 의존하지 않는다. 이 ADR 당시 치명 피해 뒤 즉시 회복 규칙은 ADR-019의 전투 불능·중상·응급 회복 lifecycle로 교체한다.

이 결정은 몬스터 능력치 성장, Stage 진행, 양방향 일반 공격의 backend와 기존 캐릭터 HP 상태까지만 승인한다. 몬스터·전투 UI, 사망 디버프, 몬스터 스킬·치명타, 처치 추가 XP·골드·전리품은 후속 결정으로 남긴다. 장착 gameplay 장비 modifier를 기존 전투 계산 입력에 연결하는 범위는 ADR-013에서 별도 승인하지만 이 결정의 공격 event 경계를 변경하지 않는다.

**구현 상태**: 위 backend는 Room v4의 몬스터 인스턴스·전투 진행·독립 양방향 event, occurrence 플레이어 공격 outbox, 마감 실패 공격, 복귀당 3회 피해 상한과 앱 시작·15분 주기 WorkManager reconciliation로 구현했다. 치명 피해 후 처리만 ADR-019의 Room v13 중상 계약이 현재 구현을 소유한다. Room v8의 `MIGRATION_7_8`은 완료 기록이 없는 기존 `MISSED_DEADLINE` `APPLIED`·`SKIPPED` event 중 failure source가 빠진 occurrence만 event 처리 시각으로 `INSERT OR IGNORE` backfill한다. schema를 바꾸거나 과거 피해와 transient transition을 다시 만들지 않으며 재실행에도 중복되지 않는다. WorkManager는 best-effort이므로 정확한 deadline 공격을 보장하지 않으며, deadline과 실제 event 처리 시각은 구분한다. 정확한 deadline alarm은 후속 범위다. 이후 추가한 `MonsterSpecies`는 저장 컬럼이 아니라 저장된 `stageNumber + encounterNumber + grade + balanceVersion`에서 `MonsterSpeciesPolicy`가 계산하는 metadata다. 명시적 다섯 종족 목록을 `java.util.Random`과 `Collections.shuffle`로 결정적으로 배치해 NORMAL Stage의 8개 encounter에는 모든 종족이 최소 한 번, 각 1~2회 나타나고 ELITE·BOSS Stage에는 한 종족만 선택된다. 능력치용 `MonsterType`은 스케줄 입력이 아니다. 이 확장은 Room migration, monster balance version 값과 능력치·피해·보상 공식을 변경하지 않았다. 해골의 `undead`·`dark`, 나무 정령의 `forest`·`dark`·`corruption`, 하피의 `wind`·`flight`·`mountain`·`grassland`, 슬라임의 `water`·`slime`은 시각 콘셉트와 종족 식별 metadata이며 비행 동작, biome, 상성·스킬·전리품과 새 맵 배경은 후속 범위다.

**이유**: 계산 가능한 결과와 원천 상태를 분리하면 balance version 변경에도 계산을 재현할 수 있다. 경제 보상과 전투 event를 분리하고 outbox를 사용하면 생산성 기능의 성공을 보존하면서 앱 재시작과 reconciliation 재실행에도 공격을 정확히 한 번만 적용할 수 있다.

**트레이드오프**: pending outbox와 event 처리 상태, 초기화 cursor를 추가로 관리해야 하며 일정 완료 직후 전투 결과가 지연될 수 있다. 대신 권한과 실행 시점에 관계없이 누락·중복 피해를 막고 기존 일정·보상 계약을 유지한다.

**관련 문서**: 몬스터 능력치·Stage·영속 경계는 [몬스터 능력치와 성장](game-design/monster-stats-and-growth.md), occurrence별 전투 event 경계는 [TODO 보상과 자동 전투 연동](game-design/character-stats/todo-combat-rewards.md), 피해 계산은 [전투 계산](game-design/character-stats/combat-calculation.md)을 따른다.

### ADR-010: Calendar Battle Map은 전투 backend와 독립된 presentation으로 둔다

**결정**: Post-MVP Calendar Battle Map v1을 메인 Calendar의 첫 콘텐츠로 승인한다. 시스템 inset 다음 화면 순서는 `상단 플레이어 진행 HUD를 포함한 Battle Map → 월간 캘린더 → 선택 날짜의 할 일 목록`이며 전체를 하나의 세로 scroll container에 둔다. 앱명·선택 날짜·성장 요약을 표시하던 기존 상단 정보 Header와 그 고정 공간은 제거하되, 일정 목록 Header의 선택 날짜·완료 개수와 추가 action은 목록 문맥으로 유지한다. 새 top-level destination은 추가하지 않는다.

위 단일 scroll 결정은 ADR-012 Calendar Combat Feedback v1에서 고정 Battle Map과 독립 Calendar scroll로 교체한다. ADR-012가 바꾸지 않은 단일 활성 몬스터, 독립 layer, presentation 실패 격리와 접근성 결정은 계속 유효하다.

`CalendarViewModel`은 주입된 실제 `CombatRepository.observeCombat()`을 관찰해 일정 UI state와 독립된 Battle Map presentation state를 만든다. 전투 로딩·실패는 Battle Map 안에서만 처리하며 일정 조회·편집·완료, occurrence 단위 RewardLedger 보상과 알림 권한에 무관한 핵심 흐름을 막거나 롤백하지 않는다.

운영 화면은 현재 `CombatSnapshot`의 단일 활성 몬스터 한 마리만 표시한다. 재사용 UI 컴포넌트와 sample·Preview는 0~4마리 배치를 받을 수 있지만 이는 시각 검증용이며 Room schema와 combat domain의 단일 활성 몬스터 계약을 변경하지 않는다.

맵은 배경, 장식, 지면 그림자, player, monster, overlay를 독립 레이어로 관리한다. `CharacterRepository`가 제공하는 레벨·현재 레벨 구간 XP·필요 XP·골드는 `BattleOverlayLayer` 상단의 독립 플레이어 진행 HUD로 합성하며 캐릭터·몬스터·HUD·HP·damage·text를 배경 PNG에 합성하지 않는다. actor 좌표는 `0f..1f` 범위로 정규화하고 발 위치의 `bottom-center`를 기준점으로 삼으며, 큰 y가 앞쪽이고 더 높은 z-order로 렌더링한다. 맵 높이는 `availableWidth / 2.4f`를 기준으로 `190dp..320dp`에 제한한다. 기본 초원은 교체 가능한 opaque PNG resource로 두고 decode 실패 시 단색이 아닌 Canvas 초원 fallback을 사용한다.

플레이어 진행 HUD는 map 전용 ARGB 색상 대신 Material theme의 surface·outline·onSurface·primary·secondary 계열을 사용하고, 장식 골드 아이콘을 제외한 레벨·XP·골드 값을 하나의 한국어 TalkBack 설명으로 제공한다. XP bar는 필요 XP가 0 이하이거나 현재 XP가 0 이하이면 0, 현재 XP가 필요 XP 이상이면 1, 그 외에는 비율을 `0f..1f`로 제한한다. 전투 HUD인 체력바·이름·상태 효과·Stage 표시, 공격 애니메이션과 damage text는 렌더링하지 않으며 `BattleOverlayLayer`는 이 후속 기능의 확장 지점으로 유지한다.

**구현 상태**: `feature/battle`의 presentation model·순수 layout·Compose layer와 `CalendarViewModel`의 `CombatRepository`·`CharacterRepository` mapping을 구현했다. 상단 정보 Header나 대체 spacer는 남기지 않았고, 월간 캘린더는 요일 머리글과 cell offset 모두 Sunday-first다. 운영 mapping은 항상 한 active monster만 만들며 0~4 slot은 component·sample 검증에만 사용한다. 배경은 `BattleMapTheme.backgroundResId`로 교체하고 resource decode 실패나 unavailable state에서는 다층 Canvas fallback을 사용한다. ADR-011 이후 player는 `CharacterSnapshot`의 appearance·equipped items를 shared layered renderer로 표시한다. monster drawable renderer는 `CombatSnapshot.activeMonsterSpecies`를 `BattleMonsterVisualCatalog`에 전달해 고블린 정찰병·해골 병사·타락한 나무 정령·하피·슬라임의 drawable, 한국어 이름과 쓰러짐 안내를 선택하며, 치명 transition은 처치 전 종족과 spawn 뒤 종족을 각각 보존한다. 결정적 다섯 종족 스케줄과 슬라임 presentation은 단일 활성 몬스터와 독립 layer 계약을 유지하고 Room migration, 전투 balance, 능력치·피해·보상 또는 새 배경을 만들지 않는다. 이 ADR에서 구현했던 화면 전체 단일 `LazyColumn`과 전투 피드백 미표시 상태는 ADR-012 구현에서 고정 Battle Map·독립 Calendar scroll과 HP/effect layer로 교체됐다.

**이유**: 사용자는 Calendar를 벗어나지 않고 일정 행동과 backend 전투 상태의 연결을 볼 수 있어야 한다. 관찰 상태를 일정 command와 분리하면 전투 presentation 장애가 생산성 핵심 흐름의 신뢰성을 떨어뜨리지 않으며, 독립 레이어와 정규화 좌표는 배경과 actor 자산을 교체하거나 여러 화면 폭을 지원하기 쉽다.

**트레이드오프**: 첫 Calendar 콘텐츠의 세로 공간이 늘어나므로 하나의 scroll과 제한 높이가 필요하다. 상단 HUD가 actor 영역 일부를 사용할 수 있어 작은 폭과 큰 글꼴에서 비중첩 검증이 필요하다. 컴포넌트가 여러 monster 입력을 받을 수 있어도 운영 데이터는 한 마리뿐이며, 몬스터 상태와 전투 피드백은 후속 overlay 구현 전까지 보이지 않는다.

**불변 범위**: ADR-004의 occurrence 완료 분리, ADR-005의 RewardLedger 멱등성, ADR-006의 권한 실패 독립성, ADR-009의 v4 전투 schema를 보존한 Room v5·단일 활성 몬스터·독립 공격 event와 전투 계산 정책은 변경하지 않는다.

### ADR-011: 캐릭터 외형은 독립 layer loadout을 저장하고 shared cached composer로 렌더링한다

**결정**: Character Layer Runtime Composition v1은 geometry canonical `body_base`와 hair·hands·default/adventure 의상·headgear·accessory·3분할 sword를 포함한 15개 독립 `64×64` PNG를 같은 원점에서 source-over 합성한다. schema v4의 semantic anchor와 z-order가 canonical 계약이며, 생성된 `512×128` sheet와 preview는 deterministic debug·golden 결과일 뿐 runtime source가 아니다.

Room schema를 v5로 올려 character id별 `character_appearance`와 `character_equipped_items`를 저장한다. `MIGRATION_4_5`는 기존 profile을 보존하고 현재 대표 기본 loadout을 `INSERT OR IGNORE`로 추가하며, fresh character의 profile·current state·appearance·equipped items 네 row는 하나의 Room transaction에서 생성한다. `CharacterRepository`는 catalog 검증을 거친 appearance·equipped item update와 두 상태를 포함하는 `CharacterSnapshot` 관찰을 소유한다.

Character 화면과 Calendar Battle Map player는 같은 `CharacterRenderState`와 `LayeredCharacterSprite`를 사용한다. `CharacterBitmapComposer`는 asset path별 decode cache와 render-state별 composite LRU cache를 사용해 immutable `64×64` bitmap을 만들고 화면에서 한 번만 최근접 보간으로 확대한다. decode 실패는 캐릭터 bitmap을 비워 presentation 안에 격리하며 일정 command·보상·전투 backend 성공을 되돌리지 않는다.

이번 결정 자체는 사용자 외형 선택 UI, inventory, ownership·획득, 사용자가 수행하는 gameplay 장착 흐름과 장비 stat·전투 modifier를 승인하지 않는다. 이 gameplay 범위는 ADR-013에서 별도 승인하며, 여기서 만든 Repository와 ViewModel의 appearance update command는 기존 외형 fallback 경계로 유지한다.

**구현 상태**: Room v5 entity·DAO·migration·Repository API, 15개 byte-identical runtime asset, schema v4 layer catalog, 2단계 cache composer와 Character/Battle shared renderer를 구현했다. 64개 loadout 조합과 generated `runtime-equipped-reference` raw RGBA golden equality, v4→v5 migration·원자 초기화·invalid update, unit·Compose·에뮬레이터 검증을 완료했다. production code는 generated sheet resource를 참조하지 않는다.

**이유**: 외형 원천 상태와 렌더 결과를 분리하면 같은 loadout을 여러 화면에서 일관되게 재사용하고, 조합이 늘어도 완성 sprite sheet를 조합별로 영속하거나 중복 decode하지 않을 수 있다. Repository가 허용 id와 저장 transaction을 소유하므로 UI가 Room 또는 asset 파일명 계약을 직접 알 필요도 없다.

**트레이드오프**: 현재는 선택 UI 없이 appearance loadout 상태와 command가 먼저 존재하고, asset catalog와 domain catalog를 함께 유지해야 한다. decode·composite cache도 메모리를 사용한다. 대신 generated sheet 의존과 화면별 렌더 차이를 제거하고 ADR-013의 Inventory·gameplay 장착 UI가 외형 fallback으로 사용할 안정된 경계를 확보한다.

**불변 범위**: ADR-004의 occurrence 단위 완료, ADR-005의 RewardLedger 멱등성, ADR-006의 권한 실패 독립성, ADR-009의 독립 공격 event와 전투 계산, ADR-010의 Calendar 단일 활성 몬스터·독립 layer presentation은 변경하지 않는다. ADR-010의 단일 scroll 배치는 아래 ADR-012에서만 교체한다.

### ADR-012: Calendar Combat Feedback은 영속 결과와 replay 없는 transition을 분리한다

**결정**: Calendar Combat Feedback v1은 ADR-010의 Calendar 전체 단일 scroll을 명시적으로 교체한다. 시스템 inset 다음에는 Battle Map을 고정하고 그 아래 Calendar content만 독립적으로 스크롤한다. 고정 Battle Map은 한 Row의 레벨·골드 아이콘/값·현재/필요 EXP와 그 아래 progress bar, player와 단일 active monster, actor placement geometry에서 계산한 두 HP bar, attack·hit·death·spawn·damage text를 포함한 전투 effect 전체를 소유한다. Calendar scroll은 월 이동, 요일·날짜 grid, 선택 날짜, 완료/실패 요약, 추가 버튼, task 목록과 빈 안내를 소유한다.

occurrence의 사용자 표시 상태는 `TODO`, `COMPLETED`, `FAILED` 세 값으로 제한한다. 수동 실패는 `FAILED`를 먼저 영속한 뒤 monster attack을 즉시 best-effort로 시도한다. 실패 상태 저장에 성공하고 combat 처리만 실패하면 reconciliation이 복구한다. 자동 마감 실패는 새 `MISSED_DEADLINE` event와 `FAILED` 원천을 같은 reconciliation transaction에서 확정한다. 수동 원인 `MANUAL_FAILURE`와 deadline 원인 `MISSED_DEADLINE`은 같은 `(taskId, occurrenceDate)` `monster_attack_events` key를 사용한다.

사용자가 선택한 실패 취소는 occurrence 표시 상태만 `TODO`로 되돌린다. 이미 확정된 monster attack event와 적용된 player damage는 삭제하거나 역연산하지 않는다. 실패 취소 뒤 늦게 완료하면 기존 RewardLedger와 player attack 계약을 그대로 적용한다. 이후 같은 occurrence를 다시 실패 처리하더라도 기존 monster event가 있으면 두 번째 damage와 transient animation을 만들지 않는다.

player/monster 현재 HP, 기존 occurrence reward, active monster와 Stage는 Room source state다. attack·hit·death·spawn·damage text는 새 attack event의 최초 확정 결과에서만 만드는 replay 없는 transient transition이다. 이 transition은 표시 뒤 소비하고 process 재시작, Flow 재구독 또는 기존 event 재조회로 재생하지 않는다. 몬스터 처치 추가 XP·골드·전리품은 만들지 않으며 기존 occurrence reward와 처치 시 player `HP_RECOVERY`를 유지한다. monster attack의 치명 결과는 ADR-019의 ordered lifecycle을 따른다.

일반 화면의 map 높이는 `availableWidth / 2.4f`를 기준으로 한 `190dp..320dp`를 유지한다. 저높이 화면은 Calendar scroll viewport를 확보하기 위해 `150dp..190dp` compact-height를 허용한다. 진행 HUD는 레벨·골드·EXP를 한 Row에 두고 progress bar만 아래에 둔다. 이 결정 당시 공격 effect는 Compose Material icon과 translation·shake·flash·alpha만 사용하고 새 bitmap·sound asset을 요구하지 않았다. 후속 효과음 범위는 ADR-024만 승인한다.

**이유**: 전투 상태를 화면에 고정하면 task를 스크롤하는 동안에도 행동 결과를 즉시 연결해 볼 수 있다. 영속 source state와 소비성 transition을 분리하면 앱 재시작과 reconciliation 재실행에도 정확한 HP·Stage를 복원하면서 이미 본 animation을 중복 재생하지 않는다. 같은 occurrence key를 두 실패 원인이 공유하면 수동 입력과 deadline 복구가 경합해도 피해가 한 번만 적용된다.

**트레이드오프**: 고정 map이 Calendar viewport를 줄이므로 저높이 compact-height와 독립 scroll 접근성 검증이 필요하다. 실패 취소가 전투 결과를 되돌리지 않아 task 표시와 과거 damage가 의도적으로 비대칭이며, UI는 이를 한국어 안내로 명확히 설명해야 한다. transient transition은 replay하지 않으므로 process 종료 중 놓친 animation은 복원하지 않는다.

**구현 상태**: Room v6은 occurrence별 unique `failure_logs`와 기존 `monster_attack_events`의 `trigger`를 추가했고, `MIGRATION_5_6`은 기존 HP·RewardLedger·Stage·양방향 attack event를 보존하면서 과거 failure나 transition을 생성하지 않는다. Room v8의 data-only `MIGRATION_7_8`은 완료 기록이 없는 기존 `MISSED_DEADLINE` `APPLIED`·`SKIPPED` event에 누락된 failure source만 멱등 복구한다. `RoomTaskRepository`는 completion과 failure 충돌을 transaction에서 거부하고 실패 취소 시 failure row만 삭제한다. application-scope `RoomCombatRepository`는 command `Mutex`와 `(taskId, occurrenceDateEpochDay)` monster event primary key로 수동·deadline 경합을 직렬화하며, 신규 자동 failure·event·HP·cursor transaction이 성공한 뒤 새 `APPLIED` event에만 `MutableSharedFlow(replay = 0)` `CombatTransition`을 방출한다.

`CalendarViewModel`이 소유한 `BattleAnimationController`는 buffered `Channel` actor와 controller-lifetime `CombatEventKey` 집합으로 transition을 중복 없이 직렬 소비한다. player 공격은 attack→hit과 필요 시 monster death→spawn alert→spawn, monster 공격은 attack→hit과 필요 시 player death→revive 순서의 scene override를 제공하고 queue가 끝날 때까지 occurrence 입력을 잠근다. `CalendarScreen`은 시스템 inset 안의 고정 Battle Map과 그 아래 독립 `LazyColumn`을 사용하며, 한 Row HUD·actor geometry 기반 HP bar·한국어 TalkBack/live region effect와 `TODO`·`COMPLETED`·`FAILED` action을 렌더링한다.

**불변 범위**: ADR-004의 반복 원본과 occurrence 분리, ADR-005의 RewardLedger 멱등성, ADR-006의 권한 실패 독립성, ADR-009의 피해·Stage·event 계산, ADR-010의 단일 활성 몬스터와 ADR-011의 shared layered character renderer는 변경하지 않는다. 치명 피해 후 처리는 ADR-019가 교체한다. Google Calendar 연동과 몬스터 처치 추가 보상은 승인하지 않는다. 이 결정 당시 제외한 audio 중 후속 Battle Sound Effects v1만 ADR-024가 별도로 승인한다.

### ADR-013: Equipment Shop and Inventory는 gameplay 소유·장착 source를 외형 fallback과 분리한다

**결정**: Post-MVP Equipment Shop and Inventory v1을 승인한다. gameplay 장비 type과 slot은 각각 `WEAPON`, `HELMET`, `CHEST`, `LEGS`, `GLOVES`, `SHOES`, `ACCESSORY` 일곱 값으로 고정하고, 현재 순수 검증용 `EquipmentSlot.HEAD`, `TOP`, `BOTTOM`, `SHOES`, `ACCESSORY`, `WEAPON`, `PET`를 새 일곱 slot으로 교체한다. `PET`은 v1 gameplay 장비에서 제거한다.

Room v6에는 `ARMOR` slot 값이 없고 `character_equipped_items.topId`와 `bottomId`가 이미 별도 appearance layer로 저장된다. 기존 `character_appearance`와 `character_equipped_items`는 migration에서 삭제하거나 gameplay ownership으로 승격하지 않고 기존 사용자 render 진행을 보존하는 외형 fallback으로 유지한다. 실제 소유권은 캐릭터와 equipment 조합이 unique인 `owned_equipment`, 실제 slot별 장착과 능력치 source는 캐릭터와 slot 조합이 unique인 `character_equipment` 정규화 테이블로 분리한다. 캐릭터별 같은 equipment를 중복 소유하지 않고 quantity를 도입하지 않는다.

저장·콘텐츠 호환 mapping은 `ARMOR`와 `TOP`을 `CHEST`, `BOTTOM`을 `LEGS`, `HEAD`를 `HELMET`으로 해석하며 나머지 같은 이름의 slot은 그대로 사용한다. v6에 `ARMOR`가 존재하지 않으므로 이 mapping은 기존 Room row를 다시 해석하기 위한 것이 아니라 외부·과도기 입력을 정규화하기 위한 것이다. 상의와 하의를 구분할 정보가 없는 `ARMOR`는 한 slot만 선택해야 할 때 상체 방어구라는 일반 의미와 기존 `topId` 경계에 가장 가까운 `CHEST`로 둔다. `CHEST`와 `LEGS`는 독립 slot이므로 동시에 장착할 수 있고, 교체는 대상 slot 하나만 바꾼다.

기존 캐릭터 원천 스탯 `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`와 계산 결과인 8개 `DerivedStats`를 유지한다. 초기 장비 효과에서 민첩 계열 입력은 `FOCUS`, 지능 계열 입력은 `WILLPOWER`로 모델링하며 agility·intelligence 원천 스탯 또는 Room 컬럼을 추가하지 않는다. 장비 구매와 소유만으로는 능력치가 변하지 않고 `character_equipment`에 실제 장착된 equipment의 modifier만 `DerivedStatsCalculator` 입력에 포함한다. 계산된 level과 파생값은 계속 저장하지 않는다.

구매 command는 transaction 안에서 최신 골드 잔액, 판매 상태, 요구 레벨, 중복 소유, equipment type과 slot mapping을 다시 검증하고 골드 차감과 `owned_equipment` 추가를 함께 확정한다. 장착 command도 transaction 안에서 소유권과 type/slot 일치를 검증하고 대상 `character_equipment` slot만 교체한다. 장착 전후 `MAX_HP`가 다르면 기존 비율 보존 정책으로 `currentHp`를 계산해 같은 transaction에서 갱신하며 `0 HP`를 장착만으로 전투 가능 상태로 만들지 않는다.

해제 command는 대상 `character_equipment` row만 제거하고 `owned_equipment` 소유권은 보존한다. 같은 transaction에서 활성 상태이상을 포함한 장착 전후 능력치를 다시 계산하고 `MAX_HP`가 달라지면 현재 HP 비율을 보존한다. 사용자가 명시적으로 해제한 slot은 shared renderer에도 같은 의도로 보여야 하므로 해당 slot의 `character_equipped_items` fallback만 기본 복장으로 원자 갱신한다. `WEAPON`·`HELMET`·`GLOVES`·`ACCESSORY`는 nullable layer를 비우고 `CHEST`·`LEGS`·`SHOES`는 각각 `top_default`·`bottom_default`·`shoes_default`로 바꾸며 다른 appearance slot은 보존한다. 이는 gameplay 소유권과 appearance source를 합치는 것이 아니라, 분리된 source 중 사용자가 해제한 대상 fallback만 명시적으로 동기화하는 command 경계다. 이미 빈 slot은 `AlreadyEmpty`로 성공하며 어느 source도 다시 쓰지 않는다.

`Shop`은 `Calendar`, `Character`에 이은 세 번째 하단 top-level destination으로 두고 `Inventory`는 Shop에서 여는 nested destination으로 둔다. 구매 성공 UI는 `바로 장착`, `인벤토리로 이동`, `계속 쇼핑` 세 action을 제공한다. UI는 ViewModel state를 렌더링하고 Repository 또는 명확한 UseCase를 호출하며 DAO를 직접 호출하지 않는다. 새 사용자 노출 문구와 TalkBack 설명은 한국어 기본 문자열 resource를 사용한다.

**구현 상태**: Room v7의 `equipment`, `equipment_modifiers`, `owned_equipment`, `character_equipment` entity·DAO와 `MIGRATION_6_7`, Room v12 기준 18종 명시 ID catalog의 멱등 runtime seeder를 구현했다. migration은 기존 일정·RewardLedger·character HP·appearance·failure·Stage·양방향 attack event를 보존하고 네 장비 테이블을 빈 상태로 추가하므로 기존 `topId`·`bottomId`를 소유권으로 backfill하지 않는다. v6에 저장된 `ARMOR` row가 없으므로 migration에서 `ARMOR → CHEST` 변환은 수행하지 않으며, 해당 mapping은 외부·과도기 문자열 입력의 compatibility boundary에서만 적용한다. `MIGRATION_10_11`은 기존 `character_equipped_items` row에 nullable `glovesId`를 추가하고, `MIGRATION_11_12`는 `equipment.weaponType`을 추가해 기존 WEAPON row만 `LONGSWORD`로 backfill한다.

`RoomEquipmentRepository`는 최신 Room 상태를 다시 읽는 구매·장착·해제 transaction과 store·inventory Flow를 소유한다. 구매는 골드 차감과 unique `owned_equipment` insert를 함께 확정하고, 장착은 대상 slot의 `character_equipment` 교체와 `MAX_HP` 비율 기반 current HP 갱신을 함께 확정한다. 해제는 소유 row를 남긴 채 대상 장착 row와 대상 appearance fallback, 필요 시 current HP를 함께 갱신하고 빈 slot은 `AlreadyEmpty`로 멱등 처리한다. 실제 장착 modifier는 Character·Task·Combat 계산 경로에 연결했고, 유효한 `layerKey`만 기존 appearance fallback 위에 투영한다. `Calendar`·`Character`·`Shop` 세 top-level destination과 nested `Inventory`, 일곱 type filter·같은 slot 비교·구매 확인·구매 성공 세 action·Shop slot 관리 dialog·Inventory 장착/해제 UI 및 한국어 접근성 문구를 구현했다. seeded 18종 중 액세서리 2종을 제외한 16종에는 canonical과 byte-identical runtime bitmap 및 유효한 `imageKey`·`layerKey` mapping을 연결했다. 액세서리 2종은 type별 Material icon placeholder를 사용하고, layer mapping이 없으면 기존 appearance를 유지한다. 장갑 gameplay projection은 `hands_front` source를 교체하고, 신발 projection은 기존 하의와 `y=53..54` 발목 interface를 공유하며, 모든 weapon source는 schema v5의 최상단 group에 합성한다.

Room v14의 현재 Shop은 `RoomEquipmentRepository`가 한 read transaction으로 만든 `EquipmentStoreSnapshot`에 실제 장착 render/stat과 판매 후보별 `EquipmentPreviewProjection`을 함께 제공한다. 후보 projection은 같은 slot의 modifier와 검증된 layer만 순수 계산으로 교체하고 `ShopViewModel.selectedEquipmentId`가 고른 외형과 현재 대비 stat delta를 presentation에 매핑한다. 이는 비영속 구매 전 상태로서 `character_equipment`, gold, ownership, current HP를 쓰지 않는다. invalid type/slot 후보는 현재 외형·능력치로, layer 누락·검증 실패는 stat projection과 독립된 현재 외형으로 격리한다. 상품 card 본문 선택, 별도 상세 action, 별도 구매 action을 분리하고 구매 확인은 최신 snapshot을 다시 사용한다. 구매는 기존 gold·ownership transaction만, 실제 공용 render/stat/HP 갱신은 장착 transaction commit 뒤 Repository Flow만 소유한다.

Shop presentation은 top-level back이 없는 고정 app bar와 불투명 하단 navigation 경계 안의 단일 `LazyColumn`으로 구성한다. 첫 콘텐츠는 최소 `88dp`·`60dp` sprite의 압축 대장장이 배너이며, 다음 surface는 캐릭터·일곱 개 `64dp..68dp` slot과 공격력·최대 체력·방어력 current/delta를 통합한다. 골드 icon/value 간격은 `4dp`다. 이 리디자인은 Room v14 schema·migration chain, 18종 equipment catalog·가격·modifier, canonical/runtime asset, 구매·장착·해제 transaction과 occurrence·RewardLedger·전투 event 경계를 변경하지 않았다.

**이유**: appearance layer id와 gameplay ownership을 한 테이블에 합치면 기존 사용자의 외형 진행, 판매 가능한 equipment catalog, 캐릭터별 소유권과 slot별 능력치 source가 서로 다른 lifecycle을 공유하게 된다. 외형 fallback을 보존하고 소유·장착을 정규화하면 migration 손실 없이 구매·장착·해제 검증과 modifier 계산의 원천을 명확히 할 수 있다. 구매·장착·해제를 각각 원자 transaction으로 처리하면 동시 탭이나 stale UI state에서도 골드 중복 차감, 미소유 장착, 소유권 손실과 HP 불일치를 막는다.

**트레이드오프**: appearance와 gameplay 장착 상태가 별도 source이므로 gameplay equipment에 대응하는 render asset이 없거나 mapping이 실패할 때 외형 fallback 정책이 필요하고, 관찰 시 profile·owned·equipped·modifier를 조합해야 한다. 대신 구매가 외형 row를 우연히 덮거나 소유만으로 능력치가 적용되는 문제를 피한다.

**불변 범위**: ADR-004의 occurrence 단위 완료, ADR-005의 RewardLedger 보상 멱등성, ADR-006의 권한 실패 독립성, ADR-009와 ADR-012의 방향별 독립 공격 event 및 replay 없는 transition을 변경하지 않는다. 장비 구매·장착·해제 transaction은 일정 보상 transaction과 별개이며 어느 한쪽의 실패가 다른 쪽을 롤백하지 않는다. 외부 Google Calendar 읽기/쓰기, 계정·서버 동기화, 결제·유료 재화는 승인하지 않는다.

### ADR-014: 새 일정 완료 보상은 player attack 결과로 확정한다

**결정**: Post-MVP Combat Rewards v1부터 새 occurrence 최초 완료는 캐릭터에 XP·골드를 직접 더하지 않는다. `RewardLedger`에는 `rewardMode = COMBAT_ATTACK`, 실제 지급량 `0/0`, 정시 여부·반복/일일 순번·MOMENTUM 계산 source를 기록하고, 같은 transaction에서 모든 새 완료에 PENDING player attack outbox를 하나 만든다. 기존 하루 20회 `combatEligible` 상한은 신규 완료에 적용하지 않는다. 일정 완료는 전투 처리와 분리된 best-effort 성공으로 유지하며, 처리 실패 시 PENDING event를 reconciliation이 재시도한다.

player attack 적용 transaction은 대상 `MonsterInstance`에 저장된 level `L`과 grade를 사용한다. NORMAL/ELITE/BOSS 보상 배율은 각각 `1×/2×/4×`이며 다음 정수 내림 공식을 적용한다.

```text
hitXp = 1 + floor((L - 1) / 10)
killBonusXp = isKill ? (10 + floor((L - 1) / 5)) × gradeMultiplier : 0
killGold = isKill ? floor((5 + floor((L - 1) / 10)) × gradeMultiplier × (1 + GOLD_GAIN_BONUS)) : 0
totalXp = hitXp + killBonusXp
```

모든 중간 계산은 `Long` exact arithmetic과 basis points를 사용한다. 비치명 공격도 hit XP를 지급하고, 처치 공격은 hit XP에 추가 XP와 골드를 함께 지급한다. XP 적용, level·미배분 포인트 갱신, 최대 HP 변화의 비율 보존, 처치 `HP_RECOVERY`, 몬스터 HP·Stage 진행, attack APPLIED 결과 snapshot은 하나의 Room transaction에서 확정한다. 동일 attack key를 재처리해도 캐릭터·몬스터·Stage·보상을 다시 변경하지 않는다.

Room v9은 `reward_ledger.rewardMode`와 player attack의 reward version·operand/result snapshot을 추가한다. v8의 기존 ledger와 APPLIED/PENDING attack은 각각 `TODO_COMPLETION`, reward version `0`으로 보존하여 과거 XP·골드를 다시 계산하거나 새 전투 보상을 소급 지급하지 않는다. 누락 event 복구도 ledger mode에 따라 legacy version `0`과 현재 version `1`을 구분한다.

**표시 계약**: EXP bar는 `EXP` label과 값의 실제 폭만 사용하고 0 진행률에서도 track·outline이 보인다. 현재 version player attack 보상은 Battle Map에서 replay 없는 `600ms` badge로 표시하며 Calendar 완료 snackbar와 합치지 않는다. Character 기본·파생 능력치는 한국어 설명 dialog를 제공하고 기본 능력치 control cluster는 pending 문구와 무관하게 고정된다.

**이유**: 일정 난이도 기반 직접 보상은 몬스터의 level·grade와 처치 결과를 경제 성장에서 분리했다. 공격 event를 보상 ledger로 사용하면 hit와 kill의 차이를 보여주면서 occurrence·공격 key 기반 멱등성과 best-effort 일정 완료 경계를 유지할 수 있다.

**트레이드오프**: 일정 완료 직후 전투 처리가 지연되면 XP·골드도 지연되며, 과거 direct reward와 새 combat reward가 서로 다른 version으로 공존한다. 대신 기존 사용자의 확정 경제 상태를 손대지 않고 재시도 가능한 outbox에 실제 지급 결과를 보존한다.

**불변 범위**: 반복 원본과 날짜별 occurrence 분리, 권한 거부 시 핵심 일정 동작, replay 없는 transition, 방향별 독립 attack event, 장비 구매 transaction은 유지한다. 전리품, 몬스터 스킬·치명타, 외부 캘린더 연동은 승인하지 않는다.

### ADR-015: Task Reminder는 task 설정과 occurrence exact alarm을 분리한다

**결정**: Todo Quest Task Reminder v1을 승인한다. 일정 editor는 기본 `설정 없음`과 `10분 전`, `1시간 전`, `직접 설정` 네 mode를 제공한다. `10분 전`과 `1시간 전`은 일정 시간이 있는 task에서만 유효하며 계산된 trigger가 이전 날짜로 넘어갈 수 있다. `직접 설정`은 occurrence 당일의 독립 local time으로 시간이 없는 task에도 사용할 수 있고 일정 시간보다 늦어도 허용하지만, 이전 날짜나 일수 offset은 지원하지 않는다.

task 원본의 reminder setting과 실제 `(taskId, occurrenceDate)` 예약 key를 분리한다. 반복 일정은 현재 활성 task segment에서 완료·실패하지 않았고 trigger가 미래인 occurrence 하나만 예약한다. alarm callback은 게시 직전에 persisted key, task 활성 상태와 occurrence `TODO` 상태를 재검증하고, 유효한 반복 occurrence를 게시한 뒤 다음 하나를 예약한다. 생성·수정·반복 분할·삭제·완료·실패·완료 취소·실패 취소는 관련 task 예약을 재검토한다. 지난 일회성 trigger는 즉시 울리지 않고 `NO_FUTURE_OCCURRENCE`로 남기며, 반복 일정은 과거 occurrence를 건너뛴다. 재부팅이나 프로세스 부재 중 놓친 notification은 복귀 시 몰아서 표시하지 않는다.

정확한 시각은 explicit occurrence key를 가진 manifest `BroadcastReceiver`와 `AlarmManager.setExactAndAllowWhileIdle()`로 처리한다. Android 12 이상은 manifest `SCHEDULE_EXACT_ALARM`과 실행 전 `canScheduleExactAlarms()` 확인을 사용하고, Android 13 이상은 `POST_NOTIFICATIONS` runtime permission을 추가로 사용한다. 사용자가 `설정 없음`이 아닌 알림을 저장한 행동 뒤 notification permission과 exact-alarm special access를 설명과 함께 순차적으로 처리한다. `USE_EXACT_ALARM`, listener 기반 우회와 inexact alarm fallback은 사용하지 않는다.

일정·occurrence command와 알림 scheduling은 실패 경계를 분리한다. `DISABLED`, `PENDING`, `SCHEDULED`, `POST_NOTIFICATIONS_REQUIRED`, `EXACT_ALARM_ACCESS_REQUIRED`, `DELIVERED`, `NO_FUTURE_OCCURRENCE`, `ERROR`를 표준 reminder status로 사용한다. 권한 거부·dismiss 또는 scheduler 오류가 발생해도 일정 저장·수정·삭제, occurrence 완료·실패·각 취소, 보상·전투 transaction은 성공 상태를 유지하고 status와 한국어 안내만 별도로 갱신한다.

notification channel은 높은 중요도와 기본 소리·진동을 요청해 가능한 기기에서 heads-up으로 표시한다. 사용자 channel 설정과 방해 금지 모드는 우회하지 않는다. notification tap은 Calendar의 해당 occurrence 날짜를 연다. 앱 시작, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, exact-alarm 권한 부여 뒤 예약을 재검토한다. WorkManager는 재등록과 상태 복구만 담당하며 정확한 시각 발화에는 사용하지 않는다. Force Stop 또는 Android background restricted 상태처럼 OS가 alarm·PendingIntent·background work를 의도적으로 중단하는 경우는 보장 범위에서 제외한다.

**구현 상태**: Room v10은 기존 task를 임의 backfill하지 않는 `task_reminders`와 `MIGRATION_9_10`을 추가했다. `RoomTaskRepository`는 일정 원본과 reminder setting을 한 transaction에서 저장하고 반복 분할 시 원 segment의 materialized key를 취소 가능하게 보존한 채 새 segment 설정을 독립 생성한다. `RoomReminderRepository`와 `ReconcileTaskReminderUseCase`는 완료·실패·삭제를 포함한 source 변경 뒤 기존 alarm 취소, `POST_NOTIFICATIONS`·exact-alarm capability 확인, 현재 시각보다 엄격히 뒤인 다음 `TODO` occurrence 한 건의 예약 상태를 조건부 갱신한다.

`AndroidReminderScheduler`는 occurrence URI를 identity로 가진 immutable explicit `PendingIntent`와 `RTC_WAKEUP` `setExactAndAllowWhileIdle()`을 사용한다. manifest에는 `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, 비exported alarm receiver와 restore receiver를 선언하고 `USE_EXACT_ALARM`은 선언하지 않는다. alarm receiver는 `DeliverReminderUseCase`를 통해 persisted key·활성 task·occurrence `TODO` 상태를 다시 확인하고 예약 key를 조건부 claim한 뒤 private notification을 한 번 게시한다. 앱 시작과 boot·package replace·시각·시간대·exact 권한 부여 broadcast는 전투 work와 별도인 unique one-time `ReminderReconciliationWorker`를 enqueue한다.

Calendar editor는 네 mode, 무시간 preset 비활성화, custom time picker·저장 전 validation과 한국어 semantics를 제공한다. `NONE`이 아닌 설정을 저장한 뒤 Android 13 이상 notification runtime permission을 먼저 처리하고, 필요하면 정확한 알림의 용도를 설명한 뒤 Android 12 이상 special-access 설정을 연다. channel은 높은 중요도와 기본 소리·진동을 요청하고 notification tap은 Calendar의 해당 occurrence 날짜를 연다. 권한 거부·dismiss, 미래 occurrence 없음과 scheduler·publisher 실패는 typed status·한국어 안내로 격리하며 이미 성공한 일정·보상·전투 결과를 롤백하지 않는다.

**이유**: 사용자가 고른 local time을 occurrence 단위 exact alarm으로 materialize하면 반복 원본과 날짜별 상태를 섞지 않으면서도 완료·실패·수정과 경합하는 stale 알림을 발화 직전에 억제할 수 있다. source setting과 scheduler status를 분리하면 Android 권한이나 background 실행 실패가 생산성·보상·전투 transaction의 신뢰성을 떨어뜨리지 않는다.

**트레이드오프**: 다음 occurrence 하나만 예약하므로 발화와 모든 source 변경 뒤 reconciliation이 필요하고, exact-alarm·notification 두 capability 및 OS background 제한을 사용자에게 설명해야 한다. 대신 무한 반복 alarm materialization, missed notification 폭주와 부정확한 fallback을 피하고 사용자에게 약속한 시각의 의미를 유지한다.

**불변 범위**: ADR-004의 반복 원본·occurrence 분리, ADR-005의 RewardLedger 멱등성, ADR-006의 권한 실패와 일정 저장 분리, ADR-009·ADR-012·ADR-014의 독립 전투 event·보상 transaction을 변경하지 않는다. 외부 Google Calendar 읽기/쓰기, 계정·서버 동기화, exact deadline 전투 공격은 승인하지 않는다.

### ADR-016: 무기 subtype은 장비 속성으로 저장하고 shared renderer의 최상단 overlay로 합성한다

**결정**: gameplay 무기는 단일 `EquipmentType.WEAPON`·`EquipmentSlot.WEAPON`을 유지하고 `WeaponType.LONGSWORD`, `DAGGER`, `SPEAR`, `BLUNT` 속성으로 세분화한다. 모든 무기 장비는 non-null subtype을 가져야 하며 비무기 장비는 null이어야 한다. Room v12의 nullable `equipment.weaponType`에 enum 이름을 저장하고 `MIGRATION_11_12`는 기존 `type = 'WEAPON'` row만 `LONGSWORD`로 backfill한다.

무기 artwork는 상점 thumbnail과 캐릭터 착용 layer가 동일한 `64×64` runtime PNG를 사용한다. thumbnail은 불투명 bounds만 surface에 맞추고, 착용은 crop·offset·개별 scale 없이 전체 원점 `(0, 0)`에서 합성한다. schema v5는 기본 3분할 검과 gameplay 단일 `weapon_front`를 모두 body·hands·hair·headgear·accessory 뒤의 최상단 weapon group으로 이동한다. 따라서 무기 불투명 pixel은 앞선 모든 캐릭터 layer를 덮되 얼굴 보호 영역 `[20, 7, 44, 28]`은 침범하지 않는다.

**구현 상태**: catalog를 18종으로 확장하고 16종에 실제 visual/layer mapping을 연결했다. `낡은 검(1001)`·`철 장검(1002)`은 `LONGSWORD`, `물푸레나무 창(1017)`은 `SPEAR`·일반·레벨 1·50 골드·공격력 +4, `강철 철퇴(1018)`는 `BLUNT`·희귀·레벨 12·780 골드·공격력 +12·힘 +4·치명타 피해 +400bp다. `DAGGER`는 후속 상품 확장을 위해 domain·storage·표시 mapping에 포함하지만 현재 판매 상품은 추가하지 않는다. 네 무기의 canonical/runtime PNG, 장비별 1×/8× preview와 2×2 matrix, Shop·Inventory·Character·Battle shared renderer 및 한국어 subtype 표시를 연결했다. 액세서리 2종은 기존 placeholder를 유지한다.

**이유**: slot을 무기 종류별로 늘리면 구매·장착·비교 transaction과 한 손 장착 정책이 불필요하게 분기된다. subtype을 장비 속성으로 두면 단일 slot 교체 불변식을 유지하면서 창·둔기·후속 단검을 데이터와 표시 계층에서 확장할 수 있다. 같은 PNG를 thumbnail과 전체 원점 layer에 재사용하면 상점에서 본 실루엣과 장착 결과가 달라지는 문제를 막는다.

**트레이드오프**: gameplay 무기는 기존 3분할 기본 검처럼 손 앞뒤를 나누지 않고 최상단 단일 overlay로 그리므로 손과의 물리적 가림 표현보다 상품 식별성과 비가림을 우선한다. 새 subtype 추가 시 enum, Room mapper와 한국어 문자열 mapping을 함께 갱신해야 한다. 대신 임의 문자열 subtype, slot 증가와 item별 좌표 보정을 금지해 migration과 합성 계약을 단순하게 유지한다.

**불변 범위**: 구매·장착은 계속 Repository transaction을 통해 처리하고 UI는 Room DAO를 호출하지 않는다. occurrence 단위 보상 멱등성, 반복 원본과 발생분 분리, 알림 권한 실패 독립성, 외부 Google Calendar 제외를 변경하지 않는다. artwork decode 실패는 placeholder로 격리하며 일정·보상·구매·장착 원천 transaction을 롤백하지 않는다.

### ADR-017: notification runtime permission은 첫 Calendar 진입에서 한 번만 요청한다

**결정**: Android 13 이상에서 `POST_NOTIFICATIONS`가 필요하고 앱이 아직 system permission request를 시도하지 않았다면 첫 Calendar 진입에서 한국어 사전 안내를 먼저 표시한 뒤 system permission dialog를 한 번만 요청한다. 사용자가 거부하거나 dialog를 보류·dismiss한 뒤에는 화면 재진입, process 재시작, Activity 재생성 또는 reminder reconciliation을 이유로 자동 재요청하지 않는다.

notification capability가 없는 상태에서 사용자가 `NONE`이 아닌 reminder를 저장하면 일정과 reminder setting은 기존 transaction 경계대로 보존한다. Calendar는 `POST_NOTIFICATIONS_REQUIRED` 상태와 함께 package notification settings를 여는 한국어 `알림 설정` CTA를 제공하며, CTA 선택만 사용자가 권한 결정을 다시 여는 명시적 진입점이다. 첫 Calendar 진입 요청 전에 이미 capability가 있거나 Android 12 이하라면 system notification permission dialog를 만들지 않는다.

`SCHEDULE_EXACT_ALARM` special access는 첫 Calendar 진입이나 첫 실행에 요청하지 않는다. notification capability가 확보된 뒤 사용자가 `NONE`이 아닌 reminder를 저장한 경우에만 기존 exact-alarm 용도 설명 dialog를 표시하고, 사용자가 계속하기를 선택하면 package exact-alarm settings를 연다. notification capability가 없으면 exact access 요청은 진행하지 않고 먼저 notification settings CTA를 제공한다.

이 결정은 ADR-015의 task setting·occurrence alarm 분리, exact `PendingIntent`, 발화 직전 재검증과 typed reminder status를 유지한다. 다만 ADR-015에서 `NONE`이 아닌 reminder 저장 직후 notification system dialog를 요청하던 진입점은 이 결정부터 첫 Calendar 진입의 one-shot 요청과 reminder 저장 시 settings CTA로 교체한다.

**구현 상태**: application-scope `SharedPreferencesFirstLaunchNotificationPromptStore`가 최초 확인을 동기 commit으로 한 번만 소비하고 `PrepareFirstLaunchNotificationPromptUseCase`가 그 뒤 `POST_NOTIFICATIONS` capability를 조회한다. 실패는 prompt 없음으로 격리한다. `CalendarViewModel`은 `FIRST_LAUNCH`와 `REMINDER` origin의 한국어 안내를 구분하고 각각 replay 없는 `RequestPostNotificationsPermission`과 `OpenNotificationSettings` event를 보낸다. `AndroidReminderCapabilityAdapter.firstLaunchNotificationPermissionAction()`은 API 33 이상 미허용 상태에서 runtime permission, 시스템 notification 비활성 상태에서 package settings, 이미 사용 가능한 상태에서 no-op을 반환한다. reminder 저장 후 `POST_NOTIFICATIONS_REQUIRED`는 `알림 설정` CTA만 제공하고, settings 복귀로 notification capability가 확보된 경우에만 exact-alarm rationale와 settings를 이어서 연다. Activity 재생성·탭 복원·process 재시작과 거부·dismiss로 최초 확인을 자동 재생하지 않으며 Room schema는 v12를 유지한다.

**이유**: Calendar 문맥에서 알림의 가치를 먼저 설명하면 첫 system dialog의 의미가 분명해지고, 거부 후 반복 prompt를 피하면서 reminder를 실제로 저장한 사용자에게는 복구 가능한 설정 경로를 제공할 수 있다. notification과 exact-alarm 요청을 분리하면 아직 reminder를 사용하지 않는 사용자에게 special access를 요구하지 않는다.

**트레이드오프**: 첫 Calendar 요청을 거부한 사용자는 reminder 저장 시 system runtime dialog 대신 package 설정 화면을 직접 열어야 한다. 대신 앱이 사용자의 거부를 반복해서 덮어쓰지 않고, exact access는 실제 정확한 알림을 설정하는 시점에만 설명할 수 있다.

**불변 범위**: notification 또는 exact-alarm 권한 거부는 일정 생성·수정·삭제, occurrence 완료·실패·각 취소, RewardLedger·양방향 전투 event, 장비 구매·장착을 차단하거나 롤백하지 않는다. occurrence key별 알림·보상·공격 멱등성, 외부 Google Calendar 제외와 로컬 우선 경계도 변경하지 않는다.

### ADR-018: Combat Reward v2와 장비 가격 완화는 version과 조건부 catalog 갱신으로 적용한다

**결정**: Combat Reward v2는 새 player attack에 reward version `2`를 부여하고 대상 몬스터 level `L`과 NORMAL/ELITE/BOSS 배율 `1×/2×/4×`에 다음 정수 내림 공식을 적용한다.

```text
hitXp = 3 + floor((L - 1) / 10)
killBonusXp = isKill ? (20 + floor((L - 1) / 5)) × gradeMultiplier : 0
killGold = isKill
    ? floor((15 + floor((L - 1) / 10)) × gradeMultiplier
        × (10,000 + GOLD_GAIN_BONUS_BP) / 10,000)
    : 0
totalXp = hitXp + killBonusXp
```

level band의 분모 `10/5/10`, grade 배율과 `GOLD_GAIN_BONUS`의 처치 gold 전용 적용은 v1과 같다. level 1 NORMAL 처치는 `23 XP / 15골드`, level 55 BOSS 처치는 장비 gold bonus가 없을 때 `128 XP / 80골드`다. 모든 중간값은 계속 `Long` exact arithmetic과 basis points를 사용한다.

version 호환성은 다음과 같이 고정한다.

- reward version `0` attack은 기존 무보상 legacy 의미를 유지하고 새 보상을 소급 계산하지 않는다.
- reward version `1` PENDING attack은 ADR-014의 기존 `1/10/5` base 공식으로 처리한다.
- reward version `2`는 이 결정 이후 새로 만드는 attack에만 사용한다.
- 모든 APPLIED attack은 version과 관계없이 저장된 operand/result snapshot을 확정 결과로 사용하고 다시 계산하지 않는다.

18종 장비 가격은 현재 canonical 가격의 정수 나눗셈 몫으로 다음과 같이 완화한다.

| id | 장비 | 기존 가격 | 승인 가격 |
|---:|---|---:|---:|
| 1001 | 낡은 검 | 40 | 20 |
| 1002 | 철 장검 | 720 | 360 |
| 1003 | 가죽 모자 | 55 | 27 |
| 1004 | 철 투구 | 680 | 340 |
| 1005 | 천 상의 | 45 | 22 |
| 1006 | 가죽 갑옷 | 260 | 130 |
| 1007 | 철 흉갑 | 2400 | 1200 |
| 1008 | 천 바지 | 45 | 22 |
| 1009 | 가죽 바지 | 240 | 120 |
| 1010 | 강철 각반 | 2300 | 1150 |
| 1011 | 가죽 장갑 | 280 | 140 |
| 1012 | 여행자의 장화 | 760 | 380 |
| 1013 | 마법사의 반지 | 2700 | 1350 |
| 1014 | 수호자의 목걸이 | 6800 | 3400 |
| 1015 | 강철 건틀릿 | 820 | 410 |
| 1016 | 바람걸음 장화 | 860 | 430 |
| 1017 | 물푸레나무 창 | 50 | 25 |
| 1018 | 강철 철퇴 | 780 | 390 |

catalog 갱신은 해당 row의 canonical identity인 id·name key·type·slot과 기존 가격이 모두 예상값과 일치할 때만 새 가격을 쓴다. 사용자가 수정했거나 예상하지 않은 row는 덮어쓰지 않는다. 이미 구매한 `owned_equipment`, 구매 당시 차감한 gold와 현재 gold는 유지하고 가격 차액을 환불하지 않는다. 이 data 갱신은 ADR-018 당시 Room v12 schema 안에서 수행했으며 Room v13에서도 같은 가격과 경제 상태를 보존한다.

**구현 상태**: `CombatRewardBalanceCatalog`는 v1 `1/10/5`와 v2 `3/20/15` config를 함께 보존하고 `CURRENT_VERSION = 2`를 새 completion의 PENDING attack에 snapshot한다. `CombatRewardPolicy.rewardFor(..., combatRewardVersion, ...)`는 저장된 version의 config를 선택하고 모든 곱셈·덧셈을 exact arithmetic으로 계산한다. `RoomCombatRepository`는 version `0`을 무보상으로, PENDING v1·v2를 각 공식으로 한 번만 적용하며 미지원 version은 transaction을 rollback한다. APPLIED row는 version과 관계없이 저장된 operand/result snapshot을 그대로 반환한다. `EquipmentCatalogSeeder.seed()`는 fresh 18종을 승인 가격으로 넣고 `EquipmentDao.seedCatalogIgnoreAndApplyCanonicalUpdates()` transaction 안에서 id·name key·type·slot·기존 가격 predicate가 모두 맞는 row만 갱신한다. custom 가격·기존 소유·장착·gold는 보존하며 Shop의 목록·상세·구매 확인·잔액 표시는 Repository snapshot을 공유한다. 이 구현은 Room v12와 기존 migration·identity schema를 변경하지 않는다.

**이유**: 보상 version을 attack source에 고정하면 업데이트 전에 만들어진 PENDING v1 attack과 업데이트 뒤의 v2 attack을 각각 원래 의미대로 처리할 수 있다. canonical identity와 기존 가격을 함께 확인하는 catalog 갱신은 앱이 소유한 seed row만 안전하게 조정하면서 확정된 구매와 사용자 경제 상태를 보존한다.

**트레이드오프**: 업데이트 직후 PENDING v1과 새 v2 attack의 지급량이 다를 수 있고, canonical 조건을 벗어난 장비 row는 자동 가격 완화 대상에서 제외된다. 대신 APPLIED 결과를 재평가하거나 구매 이력을 환불해 경제 ledger를 흔들지 않는다.

**불변 범위**: occurrence별 completion·RewardLedger·player attack 멱등 key, 일정 완료와 전투 처리의 best-effort 분리, replay 없는 transition, 장비 구매·장착 transaction, 권한 실패 독립성과 외부 Google Calendar 제외를 유지한다. 전리품, 결제·유료 재화와 Room schema 변경은 승인하지 않는다.

### ADR-019: Severe Injury v1은 전투 불능과 회복을 versioned 상태이상 lifecycle로 확정한다

**결정**: monster attack의 치명 결과는 먼저 `currentHp = 0`인 전투 불능으로 확정한 뒤, 같은 Room transaction에서 `SEVERE_INJURY` 상태를 적용 또는 갱신하고 중상 적용 뒤의 유효 최대 체력 `50%`로 응급 회복한다. 모든 계산은 `Long` exact arithmetic과 basis points를 사용하고 응급 회복 HP는 최소 1이다. 중상 v1은 적용 시점부터 24시간 유지되며 유효 `MAX_HP`와 `ATTACK`을 각각 `-2,000bp`로 계산하되 base stat, 장비 modifier와 저장된 파생값을 변경하지 않는다. 감소 결과도 기존 파생 스탯 최소값 1을 지킨다.

동일 캐릭터의 동일 effect type은 한 row만 저장한다. 재패배는 별도 중상을 중첩하지 않고 revision을 증가시켜 적용 시각·만료 시각·남은 회복 완료 수를 초기화한다. 회복 credit은 `(characterId, effectType, revision, taskId, occurrenceDateEpochDay)`를 멱등 key로 하며 서로 다른 occurrence 완료 3회 또는 `AppClock.now() >= expiresAt` 중 먼저 충족된 조건이 현재 revision을 제거한다. 완료 취소·재완료, 반복 segment 분할, 중복 command와 process 재시작은 같은 credit을 다시 차감하지 않는다. 세 번째 완료 transaction은 effect 제거와 player attack source snapshot을 함께 확정하므로 복원된 스탯으로 공격하며, write failure는 completion·RewardLedger·attack outbox·credit·effect 변경 전체를 rollback한다.

치명 monster attack의 lifecycle event 순서는 `PlayerDefeated → StatusEffectApplied|StatusEffectRefreshed → PlayerEmergencyRecovered`로 고정한다. event id는 monster attack occurrence key와 effect revision에서 파생하고 `BattleAnimationController`가 한 번에 하나씩 순차 소비한다. 이미 확정된 attack 조회, Flow 재구독, 화면 회전·재진입과 process 재시작은 이 transient lifecycle을 replay하지 않는다. 상태 제거·만료는 별도 `StatusEffectRemoved` event로 한 번 표시하고 원천 HP를 자동 치유하지 않는다.

Room v13은 `character_status_effects`와 `status_effect_recovery_occurrences`를 추가한다. `MIGRATION_12_13`은 두 테이블을 빈 상태로 만들고 기존 일정·completion·RewardLedger·캐릭터·장비·알림·Stage·양방향 attack event와 경제 상태를 보존하며 과거 치명 event에 중상을 소급 적용하지 않는다. `monster_attack_events.revivedHp`는 v12까지의 물리 컬럼명 호환을 위해 유지하지만, v13의 새 row에서는 ADR-019의 응급 회복 HP snapshot을 담는다.

Character 화면과 Calendar Battle Map은 `StatusEffectRepository`가 제공하는 활성 중상을 ViewModel UI state로 렌더링한다. 배지는 이름, 최대 체력·공격력 20% 감소, 남은 완료 수를 하나의 한국어 TalkBack 설명으로 제공하고 최소 48dp target으로 상세 dialog를 연다. Battle Map의 전투 불능·중상 적용/갱신·응급 회복·해제 안내는 한국어 content description과 polite live region을 사용하며 HUD·actor·기존 combat effect와 겹치지 않는다. 만료 reconciliation은 `AppClock`을 기준으로 Repository와 ViewModel lifecycle resume에서 수행하고 UI가 Room DAO를 직접 호출하지 않는다.

**이유**: 전투 불능, 장기 상태와 즉시 플레이 복귀를 분리하면 치명 피해를 숨기지 않으면서도 일정 관리 흐름을 막지 않는다. versioned source와 occurrence별 credit ledger는 재시도·반복 분할·재패배에서도 감소 스탯과 회복 횟수를 결정적으로 복원한다.

**트레이드오프**: 상태 row, revision별 회복 ledger와 일회성 lifecycle event가 추가되고 파생 스탯 조회마다 활성 modifier를 합성해야 한다. 대신 중복 상태, 완료 취소 악용, 재시작 후 회복 횟수 손실과 이미 본 연출의 재생을 막는다.

**불변 범위**: 기존 occurrence 완료·RewardLedger·player/monster attack key, 실패 reconciliation 상한, 전투 보상 version snapshot, 장비 구매·장착 transaction, 권한 실패 독립성과 외부 Google Calendar 제외를 유지한다. 중상은 XP·골드·전리품을 추가하거나 변경하지 않는다.

### ADR-020: 일정 난이도 전투 배율과 알림 전달은 versioned snapshot과 staged plan으로 확정한다

**결정**: Task Difficulty Combat Balance v1과 Reminder Delivery Reliability v2를 승인한다.

새 player attack은 completion 시점의 일정 난이도와 별도 difficulty balance version을 source snapshot으로 저장한다. difficulty balance version `1`은 `EASY`(쉬움) `100%`, `MEDIUM`(보통) `150%`, `HARD`(어려움) `200%`를 사용한다. 피해는 저장된 캐릭터 공격력에 저장된 MOMENTUM을 먼저 반영하고, 그 결과에 난이도 배율을 정수 내림으로 적용한 뒤 기존 치명타와 대상 방어력 계산을 순서대로 적용한다.

Combat Reward v2의 base `hitXp`와 `killBonusXp`에는 같은 난이도 배율을 각각 따로 정수 내림으로 적용하고, 원래 값이 양수인 결과는 최소 `1`을 보장한 뒤 합산한다. level 1 NORMAL 기준 처치 총 XP는 쉬움 `23`, 보통 `34`, 어려움 `46`이고 비치명 hit XP는 각각 `3/4/6`이다. kill gold와 `GOLD_GAIN_BONUS`, 몬스터 level band와 NORMAL/ELITE/BOSS grade 공식은 변경하지 않는다.

difficulty balance version `0`은 nullable 난이도와 무관하게 `100%` 중립 의미다. 업데이트 전에 생성된 PENDING/APPLIED player attack은 version `0`으로 보존하고 현재 task 난이도를 다시 조회하거나 결과를 재계산하지 않는다. 새 completion이 만드는 attack만 current difficulty version과 난이도를 함께 snapshot한다. 같은 `(taskId, occurrenceDate)`의 CompletionLog, RewardLedger, player attack과 XP 지급은 계속 한 번만 확정하며 APPLIED attack은 저장된 피해·XP·gold 결과를 그대로 반환한다.

알림 전달 capability는 Android 13 이상 runtime permission, 앱 전체 notification switch, API 26 이상 `todo_task_reminders` 알림 채널 차단을 서로 구분한다. runtime permission이나 앱 전체 switch가 막히면 기존 `POST_NOTIFICATIONS_REQUIRED`, 채널만 꺼졌으면 `ReminderCapabilityStatus.CHANNEL_DISABLED`를 `ReminderScheduleStatus.NOTIFICATION_CHANNEL_DISABLED`로 보존한다. 일정과 reminder setting은 두 경우 모두 성공으로 보존하고, Calendar는 한국어 안내와 각각 앱 알림 설정 또는 채널 설정 CTA를 제공한다.

API 26 이상은 기존 high-importance 채널의 사용자 설정을 보존하고, API 23~25는 notification 자체에 high priority와 기본 소리·진동을 지정한다. 앱은 어느 API에서도 DND나 사용자의 채널 설정을 변경하거나 새 channel id로 우회하지 않는다.

새 exact alarm은 다음 occurrence plan을 Room에 `PENDING`으로 먼저 stage한 뒤 `AlarmManager.setExactAndAllowWhileIdle()`에 등록하고, 같은 key가 여전히 current일 때만 `SCHEDULED`로 바꾼다. receiver는 persisted key와 `PENDING` 또는 `SCHEDULED` 상태를 검증해 바로 발화한 callback도 한 번 claim할 수 있다. scheduler 실패 또는 비정상 결과는 같은 key의 orphan alarm을 best-effort로 취소하고, 그 key가 여전히 current일 때만 plan 없는 `ERROR`로 바꾼다. scheduler 호출 중 receiver가 먼저 claim한 `DELIVERED` 상태는 후속 성공·실패 정리가 덮어쓰지 않는다.

Calendar task 목록은 reminder가 `NONE`이 아닐 때만 occurrence 기준 실제 local 발화 시각을 `10분 전 · 전날 23:50`, `직접 설정 · 당일 08:00` 형식으로 표시한다. capability가 막힌 task에는 typed 복구 CTA를 제공한다. `완료`·`실패` action은 텍스트와 아이콘을 유지한 content-sized 시각 버튼으로 줄이되 실제·semantics 터치 영역은 최소 `48dp`를 유지한다. Battle EXP 영역은 기존 최소 `104dp`, progress와 한국어 TalkBack 계약을 유지하면서 `EXP`를 bar 왼쪽 끝에, 현재/필요 값을 bar 오른쪽 끝에 맞춘다.

**구현 상태**: Android·Room 비의존 난이도 catalog와 policy, Room v14의 nullable `sourceTaskDifficulty`와 기본값 `0`인 `taskDifficultyBalanceVersion`, `MIGRATION_13_14`를 구현했다. migration은 기존 PENDING/APPLIED row를 null/version `0`으로 보존하고 `todo_tasks`에서 난이도를 backfill하지 않는다. `RoomTaskRepository`는 신규 completion transaction에서 current difficulty version `1`과 task 난이도를 snapshot하고 legacy repair는 null/version `0`을 사용한다. `RoomCombatRepository`는 저장된 version을 검증해 PENDING 공격의 MOMENTUM 뒤 난이도, critical, defense 순서로 피해를 계산하고 hit/kill XP를 각각 scale하며 kill gold는 변경하지 않는다. unsupported version/source는 transaction을 rollback하고 APPLIED row는 저장 snapshot만 반환한다.

알림은 task reminder 전체 Flow를 occurrence projection에 결합하고, `ReconcileTaskReminderUseCase`가 plan을 `PENDING`으로 stage한 뒤 exact scheduler를 호출한다. 같은 key에 대한 `SCHEDULED`·`ERROR` 조건부 갱신은 먼저 도착한 receiver의 `DELIVERED`를 덮어쓰지 않는다. Android adapter·publisher는 앱 권한과 채널 차단, package-scoped channel settings intent, API 23~25 기본 alert를 분리한다. Calendar ViewModel·Compose는 mode·당일/전날 local trigger, typed recovery CTA, 48dp target의 compact 완료·실패 action과 최소 104dp EXP 양끝 정렬을 렌더링한다. Room identity schema는 `14.json`이며 production migration chain은 `MIGRATION_13_14`까지 연결한다.

**이유**: 난이도를 completion source에 고정하면 task 편집이나 재시도 뒤에도 같은 공격의 피해와 XP를 재현할 수 있다. alarm 등록 전에 key를 영속하면 등록 직후 callback이 도착해도 receiver가 유효성을 확인할 수 있고, 알림 채널 차단을 앱 권한과 분리하면 사용자의 실제 복구 지점을 안내할 수 있다.

**트레이드오프**: attack마다 난이도 source와 version을 추가로 저장하고 alarm 등록에는 staged 상태와 조건부 정리가 필요하다. 대신 legacy 공격을 소급 변경하지 않고 scheduler와 receiver의 경합에서도 `DELIVERED`를 보존하며 일정 저장 성공과 알림 전달 실패를 계속 분리한다.

**불변 범위**: ADR-004·ADR-005의 occurrence별 완료·RewardLedger 멱등성, ADR-014·ADR-018의 reward version·base XP·kill gold·grade 공식, ADR-015·ADR-017의 task setting·occurrence alarm 분리와 one-shot permission 진입, ADR-019의 중상 lifecycle을 유지한다. DND, 사용자가 선택한 알림 채널 설정, Force Stop, 제조사 background 정책을 우회하지 않으며 외부 Google Calendar, 서버 동기화, inexact alarm fallback과 새 전리품을 승인하지 않는다.

### ADR-021: 몬스터 발견 상태는 versioned 전투 이력의 결정적 projection으로 계산한다

**결정**: Monster Compendium v1의 발견 상태는 별도 discovery ledger나 boolean column으로 저장하지 않는다. Room v14의 `monster_instances` 전체 이력을 Stage·encounter 순서로 관찰하고, HP가 0인 처치 이력을 포함한 각 행의 `stageNumber`, `encounterNumber`, `grade`, `balanceVersion`을 명시적 `MonsterSpeciesPolicy`에 전달한 뒤 중복을 제거한 종족 집합으로 계산한다. 첫 관찰 전에 기존 전투 초기화 경계를 실행해 현재 활성 몬스터도 등장 즉시 발견되도록 하며, 승리 transaction이 다음 활성 몬스터 행을 만들면 Room invalidation으로 같은 Flow를 즉시 갱신한다.

기존 사용자는 업데이트 전에 저장된 모든 몬스터 인스턴스에서 발견 상태를 소급 복원한다. 기존 이력이 없는 종족을 임의로 발견 처리하지 않고, 현재·과거 행을 수정하거나 새 row를 backfill하지 않는다. 따라서 database version은 v14를 유지하고 migration과 schema export를 추가하지 않는다.

발견 계산은 저장된 balance version의 의미를 보존해야 한다. 현재 v1 구현은 instance version과 `MonsterBalanceConfig.version` 일치를 검증한 뒤 같은 version의 Stage·종족 스케줄을 사용한다. 향후 balance version을 추가할 때는 저장 version을 해당 discovery policy/config로 명시적으로 routing해야 하며, 지원하지 않는 version을 최신 정책으로 추측하거나 현재 task·Stage 상태로 재계산하지 않는다.

도감은 `Calendar`, `Character`, `Shop` 다음 네 번째 top-level destination으로 두고 `compendium → compendium/monsters → compendium/monsters/{species}`를 root·목록·상세 route로 사용한다. root에는 현재 `몬스터` category 하나만 제공한다. 다섯 종의 이름은 항상 표시하지만, 발견한 종만 공용 pixel sprite·한국어 외형 설명과 상세 action을 갖고 미발견 종은 이름과 미발견 상태만 갖는다. 펫·맵은 향후 같은 구조로 확장할 수 있으나 이번 결정에는 category·route·placeholder를 포함하지 않는다.

이 문단의 name-only 미발견 presentation과 card에서 상세 route로 진입하는 방식은 ADR-023이 대체한다. Room v14 전투 이력 기반 발견 source, 기존 사용자 소급 projection, balance version routing과 migration을 추가하지 않는 결정은 그대로 유효하다.

**이유**: 몬스터 인스턴스는 이미 사용자가 실제로 마주친 Stage encounter의 versioned 원천 이력이다. 이 이력에서 발견 상태를 계산하면 별도 ledger와 전투 생성 transaction을 이중으로 맞출 필요가 없고, 기존 사용자도 migration이나 임의 backfill 없이 실제 기록만으로 도감을 복원할 수 있다. Repository projection은 Room entity와 종족 스케줄 세부를 ViewModel에서 숨겨 UI 레이어 경계도 유지한다.

**트레이드오프**: 몬스터 이력을 삭제하거나 장기 보존 정책을 바꾸면 발견 복원 계약도 함께 검토해야 하고, 새 balance version마다 과거 스케줄을 해석할 routing을 보존해야 한다. 대신 발견 write의 누락·중복 가능성과 schema 증가를 피하고 전투 승리 직후 같은 source에서 도감을 갱신한다.

**불변 범위**: 종족 metadata는 능력치·피해·보상·상태이상 계산에 참여하지 않는다. 도감 상세에 종족별 고정 능력치, biome, 스킬, 처치 횟수와 전리품을 추가하지 않으며, 기존 occurrence·RewardLedger·공격 event 멱등성, 알림 권한 실패 독립성, 로컬 우선과 외부 Google Calendar 제외를 변경하지 않는다.

### ADR-022: 능력치 배분 자동 안내는 신규 설치 eligibility와 확인 상태를 분리한다

**결정**: Character Stat Allocation Guide v1의 자동 표시 대상은 application composition root가 Room database를 열기 전에 `todo-quest.db` 파일 존재 여부로 한 번 판정한다. DB 파일이 없으면 신규 설치 후보, 이미 있으면 기존 설치로 보고 그 결과를 `stat_allocation_auto_eligible_v1`에 최초 한 번 저장한다. 저장된 eligibility는 이후 DB 생성·삭제 여부나 앱 업데이트로 다시 계산하거나 덮어쓰지 않는다.

자동 안내 확인은 별도 `stat_allocation_acknowledged_v1`에 저장한다. 신규 설치 대상이면서 확인되지 않은 경우에만 Character source state가 로드된 첫 진입에서 자동 Dialog를 표시한다. 자동 Dialog의 `능력치 보러 가기`, `닫기`, back·outside dismiss는 모두 확인을 시도한다. 사용자가 확인하기 전에 process가 종료되면 확인 key가 남지 않으므로 다음 Character 진입에서 다시 표시한다. 확인 저장이 실패해도 현재 Dialog는 닫되 다음 ViewModel 또는 process에서 다시 시도할 수 있게 확인되지 않은 원천 상태를 보존한다.

`기본 능력치` 제목의 도움말은 모든 사용자에게 같은 Dialog를 manual origin으로 연다. 수동 도움말을 닫아도 automatic acknowledged 상태를 쓰거나 되돌리지 않는다. 따라서 기존 사용자는 자동 안내를 받지 않지만 언제든 도움말로 재열람할 수 있고, 자동 안내를 이미 확인한 사용자도 재열람 때문에 자동 표시 대상이 되지 않는다.

`CharacterGuideRepository`와 Prepare/Acknowledge UseCase는 SharedPreferences 세부를 ViewModel과 Compose에서 숨긴다. preference 초기화·조회·확인 실패는 자동 안내를 생략하거나 다음 진입 재시도로 격리하며 Character 로딩, 능력치 배분·초기화와 일정·보상·전투·장비 transaction을 차단하거나 롤백하지 않는다. sprite decode 실패도 Dialog의 decorative fallback으로만 처리한다.

**이유**: 기존 사용자에게 이미 익숙한 기능의 자동 안내를 소급 표시하지 않으면서, 신규 사용자가 확인하기 전에는 process 종료로 안내를 잃지 않아야 한다. eligibility와 acknowledged를 분리하면 rollout 대상과 사용자 확인 lifecycle을 독립적으로 재현할 수 있고, 수동 도움말을 자동 onboarding 상태와 섞지 않을 수 있다.

**트레이드오프**: DB 파일 존재 여부는 계정이나 migration 이력이 아닌 로컬 설치 경계이므로 앱 data 복원·수동 DB 조작 같은 비표준 환경의 사용자 의도를 추론하지 않는다. SharedPreferences commit 실패 시 같은 사용자에게 다음 진입에서 안내가 다시 보일 수 있다. 대신 기존 Room schema와 stat transaction에 onboarding column이나 migration을 추가하지 않고 실패를 presentation에 격리한다.

**구현 상태**: `TodoQuestAppContainer.create()`가 `todoQuestDatabaseExists()`를 Room builder보다 먼저 호출하고 `SharedPreferencesCharacterGuideRepository`에 최초 eligibility를 전달한다. application-scope `PrepareCharacterStatGuideUseCase`와 `AcknowledgeCharacterStatGuideUseCase`를 `CharacterViewModel`에 주입했으며, ViewModel은 automatic·manual origin을 분리한다. `CharacterScreen`은 한국어 문자열 resource, 96dp `FilterQuality.None` 요정 sprite와 decode fallback, 독립 본문 scroll, 기본 능력치 이동 action과 도움말 재열람을 렌더링한다.

**불변 범위**: Room database version은 v14이고 `14.json` identity schema와 `MIGRATION_1_2`부터 `MIGRATION_13_14`까지의 chain은 변경하지 않는다. 기본·파생 능력치 공식, `StatAllocation` draft, `AllocateStatPointsUseCase → CharacterRepository` transaction, occurrence·RewardLedger·전투 event 멱등성, 권한 실패 독립성과 외부 Google Calendar 제외도 변경하지 않는다.

### ADR-023: Monster Compendium presentation v2는 미발견 resource를 숨기고 선택 상세를 sheet로 표시한다

**결정**: ADR-021의 Room v14 전투 이력 기반 발견 projection은 유지하고 presentation만 privacy-preserving 수집 UI로 교체한다. `MonsterCompendiumEntryUiModel.Undiscovered`는 고정 slot 식별을 위한 `MonsterSpecies`만 가지며 이름·sprite·설명 resource id를 갖지 않는다. 미발견 card와 선택 전 preview는 공통 `???`, `?`, 잠금 icon과 일반 안내만 표시하고 실제 종족 이름·sprite·외형 설명을 semantics에도 노출하지 않는다. 미발견 card 선택은 route를 열지 않고 `MutableSharedFlow(replay = 0)` 안내 effect만 방출한다.

발견 이름 검색은 Compose나 ViewModel에 문자열을 하드코딩하지 않고 발견 entry의 이름 resource를 `MonsterNameResolver`로 해석해 수행한다. 따라서 미발견 종은 검색 문자열과 일치하지 않는다. `MonsterCompendiumViewModel`은 발견 count·전체 count·정수 percent, 앱바 검색, `ALL`·`DISCOVERED`·`UNDISCOVERED` filter, 선택 종과 열린 상세를 Room 밖의 비영속 presentation state로 소유한다. 선택은 첫 발견 종으로 정규화하고 발견 집합이 바뀌어 현재 선택이 사라지면 다음 발견 종으로 안전하게 fallback한다.

고정 `TopAppBar` 아래의 단일 `LazyVerticalGrid`는 수집 현황, filter, 선택 preview와 다섯 slot을 함께 스크롤하며 폭에 따라 3~5열을 사용한다. 발견 card 선택은 route를 추가하지 않고 preview만 바꾸고, preview action은 공용 발견 상세를 `ModalBottomSheet`로 연다. sheet back·dismiss는 목록을 유지한 채 sheet를 먼저 닫는다. 기존 `compendium/monsters/{species}` 상세 route와 `MonsterDetailViewModel`은 deep link·저장된 navigation 호환을 위해 유지하되, 미발견 route는 generic 잠금 제목과 안내만 제공하고 name·sprite·description resource를 노출하지 않는다. 이 결정 당시 네 top-level destination과 고정 bottom navigation은 변경하지 않았으며, 후속 다섯 번째 `Settings` destination은 ADR-024가 추가한다.

**이유**: 미발견 UI model에 실제 표시 resource가 있으면 Compose의 조건 분기 실수, semantics 또는 검색 결과를 통해 정체가 새어 나갈 수 있다. resource를 모델 경계에서 제거하면 잠금 상태가 실제 이름과 art를 렌더링할 수 없고, 발견 이름만 resolver로 검색해 한국어 resource 정책과 privacy를 함께 지킬 수 있다. 선택 preview와 sheet는 작은 card를 정보 탐색과 상세 읽기에 동시에 쓰지 않으면서 list context와 scroll 위치를 보존한다.

**트레이드오프**: 미발견 이름 검색은 결과를 만들지 않아 사용자가 아직 만나지 못한 종을 이름으로 찾을 수 없다. 검색·filter·선택·sheet 상태는 ViewModel lifecycle 동안만 유지하고 Room source로 저장하지 않으므로 process 종료 뒤 화면 기본값으로 복원될 수 있다. sheet가 기본 상세 경로가 된 뒤에도 legacy detail route와 별도 ViewModel을 호환 경계로 유지해야 하지만, 오래된 route를 안전하게 복구하면서 새 목록의 back stack 증가를 막는다.

**불변 범위**: ADR-021의 `monster_instances` 전체 이력, 기존 사용자 소급, versioned `MonsterSpeciesPolicy` routing과 무 migration 결정은 변경하지 않는다. grade·type·region을 새 종족 metadata로 만들지 않고 펫 재화·장착·보너스·농장·맵, 새 몬스터·새 art, 능력치·피해·보상·전리품을 추가하지 않는다. Compose는 DAO를 직접 호출하지 않으며 occurrence·RewardLedger·공격 event 멱등성, 알림 권한 실패 독립성, 로컬 우선과 외부 Google Calendar 제외를 유지한다.

### ADR-024: Battle Sound Effects는 replay 없는 전투 timeline에 결합한다

**결정**: Battle Sound Effects v1은 기존 `RoomCombatRepository.events`의 `MutableSharedFlow(replay = 0)` `CombatTransition`만 효과음의 원천으로 사용하고, `BattleAnimationController`의 단일 buffered actor를 시각 효과와 음향의 직렬 조정 경계로 확장한다. Composable은 HP Flow나 현재 체력값 변화를 감시해 소리를 추론하지 않는다. transition은 fresh Room 공격 결과가 처음 확정된 경우에만 방출하므로 persisted `APPLIED` event 재처리, 화면 회전·재구성·재진입과 다음 monster 생성은 과거 음향을 재생하지 않는다.

효과음 종류 `BattleSfx`는 `PLAYER_ATTACK`, `MONSTER_ATTACK`, `MONSTER_HIT`, `PLAYER_HIT`, `MONSTER_DEFEATED`, `PLAYER_DEFEATED` 여섯 값이다. 한 공격의 `PlayerAttackStarted` 또는 `MonsterAttackStarted`, `EntityHit`, `MonsterDefeated` 또는 `PlayerDefeated` effect event는 같은 안정적 combat event id를 공유할 수 있으며, 개별 재생 identity는 `eventId + BattleSfx` 조합이다. 같은 조합은 controller 수명 동안 한 번만 소비한다.

player timeline은 `PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시 → 치명 시 MONSTER_DEFEATED → MONSTER_DYING`, monster timeline은 `MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시 → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능` 순서다. defeat 음은 hit 음이 끝난 뒤 death animation이 시작되는 시점에 재생한다. `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있는 치명 monster attack에만 만들며, 상태이상 적용·갱신, 응급 회복, 상태 제거와 monster spawn에는 death 또는 revive 음을 연결하지 않는다. 사용자 노출 표현은 기존 `전투 불능`을 유지한다.

현재 앱에는 전용 설정 화면이나 DataStore가 없다. Battle Sound Effects v1은 application-scope SharedPreferences를 감싼 설정 Repository에 `효과음` Boolean을 저장하고 key가 없을 때 기본값을 켜짐으로 해석한다. 하단 navigation은 `캘린더 → 캐릭터 → 상점 → 도감 → 설정`의 다섯 top-level destination으로 확장하고, 설정 화면은 한국어 문자열 resource를 사용하는 `효과음` Switch 하나만 제공한다. 설정 off는 음향 재생만 억제하며 animation, damage text, HP bar와 status text를 그대로 유지한다. Compose는 SharedPreferences나 audio API를 직접 호출하지 않는다.

application composition root는 process당 `SoundPool`을 한 번 만들고 application-scope player가 소유하게 한다. `AudioAttributes`는 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, stream 한도는 `maxStreams = 6`이며 여섯 raw WAV를 preload한다. load 완료 전, 앱 background 또는 player released 상태에서 받은 요청은 queue하거나 나중에 재생하지 않고 폐기한다. 앱 복귀 뒤에는 새 effect event만 재생한다. 시스템 media volume과 DND를 우회하지 않고 audio focus를 독점하지 않는다. 여섯 WAV의 원본은 저장소 안의 결정론적 합성기로 생성하며 외부 음원을 다운로드하거나 재배포하지 않는다.

**이유**: 영속 HP 상태가 아니라 최초 확정된 replay 없는 transition과 기존 단일 actor를 source로 삼으면 재구성과 복원에서 중복 소리가 나지 않고 화면 effect와 음향 순서를 한 곳에서 검증할 수 있다. effect id와 sound 종류를 결합하면 한 combat event id를 공유하는 공격·피격·전투 불능 음도 서로 충돌하지 않으면서 각각 한 번만 소비할 수 있다.

**트레이드오프**: load가 끝나기 전이나 background에 들어간 순간의 효과음은 의도적으로 유실되고 앱 복귀 시 보상 재생하지 않는다. 다섯 번째 top-level destination이 navigation 폭을 늘리고 application-scope audio 자원의 lifecycle 관리가 추가된다. 대신 전투 원천 상태나 animation을 음향 준비 상태에 종속시키지 않고 설정 off와 audio 실패를 transient presentation 안에 격리한다.

**구현 상태**: phase 61에서 typed `BattleEffectEvent`, 기본 켜짐 SharedPreferences 설정, 결정론적 PCM WAV 6개와 생성기, application-scope `SoundPool` player, `BattleAnimationController`의 phase별 효과음 조정, 한국어 Settings 화면과 다섯 번째 top-level navigation을 테스트 우선으로 구현했다. audio 준비·재생·설정 실패는 전투 timeline과 application lifecycle 밖으로 전파하지 않으며 Room v14 source와 migration chain은 변경하지 않았다.

**불변 범위**: Room database version은 v14이고 `14.json` identity schema와 `MIGRATION_1_2`부터 `MIGRATION_13_14`까지의 chain은 변경하지 않는다. 피해·보상·Stage·spawn·중상 수식, occurrence·RewardLedger·양방향 공격 event 멱등성, 알림 권한 실패 독립성, 로컬 우선과 외부 Google Calendar 제외도 변경하지 않는다. 과거 Calendar Combat Feedback 결정의 “새 sound asset을 요구하지 않는다”와 “audio는 승인하지 않는다”, ADR-023의 네 top-level destination은 각 당시 범위에 한정되며 이 후속 결정이 효과음과 현재 navigation 범위를 대체한다.

### ADR-025: Gameplay UI·장비 fallback과 일반 알림 권한은 호환 projection으로 확정한다

**결정**: Gameplay UI·Equipment·Notification Permission Polish v1을 승인한다. 중상 badge가 활성화되어도 Battle Map의 player sprite는 전달받은 `left`, `top`, `width`, `height`를 그대로 사용한다. 중상 HP bar와 badge만 HUD 예약 영역과 map 경계 안에서 clamp하거나 축소하며, 상태 적용 전후 actor 좌표를 바꾸지 않는다.

Room schema를 v15로 올리고 비파괴 `MIGRATION_14_15`를 추가한다. migration은 기존 `character_equipped_items` row의 appearance fallback만 빈 gameplay loadout에 맞춰 `headId`, `accessoryId`, `weaponId`, `glovesId`를 null로, `topId`, `bottomId`, `shoesId`를 각각 `top_default`, `bottom_default`, `shoes_default`로 갱신한다. `owned_equipment`, `character_equipment`, 장비 catalog·modifier, character HP·상태이상, 일정·완료·RewardLedger·전투 event·알림 source는 삭제하거나 초기화하지 않는다. 따라서 빈 gameplay slot은 body·hair·기본 손을 유지한 회갈색 중립 훈련복 fallback으로 보이지만, 실제 소유권과 장착 능력치는 정규화된 gameplay source에서 계속 복원한다.

고정 ID `1019..1025`는 순서대로 모험가의 검·모자·재킷·바지·장갑·신발·장식인 일곱 부위 상점 세트다. 모두 `UNCOMMON`, 요구 레벨 5, 장비별 두 modifier를 가지며 migration으로 자동 소유하거나 장착하지 않는다. 기존 adventure layer key를 상품 visual로 재사용하고 `gloves_adventure`를 추가한다. 모험가의 검은 legacy 논리 key `weapon_default_sword`와 `weapon_back_default_sword`·`weapon_held_default_sword`·`weapon_front_default_sword` 3분할 source의 호환 mapping을 유지하며, 합쳐진 단일 runtime 무기 layer를 새 원천으로 만들지 않는다.

`EquipmentStoreSnapshot.ownedEquipmentByEquipmentId`는 catalog equipment id를 key로 실제 `OwnedEquipment`를 제공하고 character·catalog·장착 source 일관성을 검증한다. Repository snapshot을 받은 `ShopViewModel`은 `미소유·구매 가능 → 구매`, `미소유·구매 불가 → 구매 불가`, `소유·미장착 → 장착`, `소유·장착 중 → 해제` 순서의 단일 typed action을 card와 detail에 공통 투영한다. 네 action은 `104dp × 48dp` 버튼으로 card·detail 우측 하단에 고정하고, 공격력·최대 체력·방어력 summary는 label·현재값·delta 영역을 항상 예약해 선택·차이·글꼴 크기에 따라 cell bounds가 움직이지 않게 한다. 현재 선택한 장비의 해제가 성공하면 상세·확인·선택과 temporary preview를 함께 닫고 최신 실제 빈 loadout으로 돌아가며, 다른 장비 해제나 실패에서는 선택을 유지한다.

Settings의 일반 알림 관리 범위는 `ReminderScheduler`가 조회하는 `POST_NOTIFICATIONS` capability와 앱 전체 알림 switch, `todo_task_reminders` 채널 상태까지다. `SettingsViewModel`은 이를 `Loading`, `Available`, `Required`, `ChannelDisabled`, `CheckFailed`로 투영하고 Compose는 replay 없는 launcher event를 받아 Android runtime permission 또는 package 앱·채널 설정을 연다. lifecycle resume과 launcher 복귀에서는 최신 상태를 다시 조회한다. 이 Settings 흐름에는 exact-alarm capability·special access를 포함하지 않으며, 기존 task reminder의 exact-alarm 설정 흐름은 그대로 유지한다. 일반 알림 권한 거부·dismiss·조회 실패는 효과음, navigation, 일정·완료·보상·전투·구매·장착을 차단하거나 롤백하지 않는다. UI는 DAO, `AlarmManager`, `WorkManager`와 SharedPreferences를 직접 호출하지 않는다.

**이유**: appearance fallback과 gameplay ownership을 migration에서 분리하면 기존 사용자의 경제·장착 상태를 보존하면서 빈 slot의 시각 의미만 명확히 바꿀 수 있다. equipment id 기반 typed lookup과 공통 action은 Room row id 혼동과 card/detail 동작 drift를 막고, 고정 layout은 선택 상태와 큰 글꼴에서도 핵심 수치·action 위치를 예측 가능하게 한다. 일반 알림 capability를 Settings에서 확인 가능한 typed state로 제공하면 사용자가 exact-alarm 흐름과 섞지 않고 현재 복구 지점으로 이동할 수 있다.

**트레이드오프**: appearance fallback과 gameplay 장착 source가 계속 분리되어 legacy layer key compatibility를 유지해야 하고, 선택 장비 해제는 presentation state 정리가 transaction 성공 뒤에 추가로 필요하다. 일반 알림 상태는 앱 복귀 때 재조회해야 하며 Settings만으로 exact alarm 문제를 해결하지 않는다. 대신 migration이 ownership을 추측하거나 지우지 않고, permission·layout·render 실패를 핵심 transaction 밖에 격리한다.

**구현 상태**: `MIGRATION_14_15`와 `15.json`, 25종 catalog 중 신규 고정 ID `1019..1025`, schema v6 중립 fallback·`gloves_adventure`·3분할 검 asset 계약, equipment id 기반 owned projection과 공통 typed action, 고정 stat/action layout, 선택 장비 해제 cleanup, 일반 알림 capability를 표시하는 Settings state·launcher 경계를 구현했다.

**대체 범위와 불변 범위**: ADR-013의 18종 catalog와 adventure appearance를 빈 slot fallback처럼 보던 당시 구현 상태, ADR-024의 Settings가 효과음 Switch 하나만 제공하던 당시 범위, ADR-019 이후 중상 badge가 actor 배치에 영향을 줄 수 있던 presentation 여지는 이 결정이 현재 구현에 한해 대체한다. ADR-021~ADR-024의 당시 결정 문장과 Room v14 역사, occurrence·RewardLedger·양방향 공격 event 멱등성, 피해·보상·Stage·중상 수식, reminder occurrence exact-alarm 계약, 로컬 우선과 외부 Google Calendar 제외는 변경하지 않는다.
