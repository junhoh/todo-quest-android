# 몬스터 능력치와 성장

[게임 설계 문서 인덱스로 돌아가기](README.md)

> 문서 지위: 구현 완료된 Post-MVP Monster Combat v1 backend, Calendar Combat Feedback v1·Combat Rewards v1의 canonical 밸런스·영속 계약과 승인된 Battle Sound Effects v1 presentation 계약이다. [PRD](../PRD.md)가 승인한 범위만 다룬다.

## 범위와 불변식

이 문서는 version 1 몬스터의 능력치, 유형·등급 배율, Stage 배치, 양방향 피해와 영속 경계를 고정한다. 플레이어 쪽 능력치는 [기본 스탯과 성장](character-stats/stats-and-progression.md), 공통 피해 내림 순서는 [전투 계산](character-stats/combat-calculation.md)을 따른다.

- 몬스터 전투 능력치는 `MAX_HP`, `DAMAGE`, `DEFENSE` 세 개뿐이다. 별도 공격력, 스킬 배율, 치명타 확률은 두지 않는다.
- 최종 능력치는 definition, level, type, grade와 versioned balance config에서 계산한다. 계산 결과를 Room column으로 저장하지 않는다.
- 인스턴스의 `currentHp`와 진행·공격 event만 원천 상태로 저장한다. `isDefeated`는 언제나 현재 HP에서 계산한다.
- 계산에는 bp `Int`와 중간 `Long`을 사용한다. 런타임 공식에 `Float`나 `Double`을 사용하지 않는다.
- 이미 시작한 전투의 `maxHp`, `damage`, `defense`는 저장된 `balanceVersion`으로 계산하므로 전투 중 바뀌지 않는다.

## 구현 상태와 실제 경로

세 능력치와 Stage 계산, Room v6 전투 원천 상태, occurrence 플레이어 공격 PENDING outbox, 수동·마감 실패 몬스터 공격, 복귀당 3회 deadline 피해 상한과 영구 skip, 앱 시작·15분 주기 WorkManager reconciliation은 구현 완료됐다. Room v13의 Severe Injury v1은 치명 피해 뒤 전투 불능·중상·응급 회복과 occurrence별 회복 ledger를 추가한다.

- 몬스터 모델과 config: `app/src/main/java/com/todoquest/domain/model/Monster.kt`
- 전투 관찰·공격 결과 모델: `app/src/main/java/com/todoquest/domain/model/Combat.kt`
- 순수 계산·정책: `app/src/main/java/com/todoquest/domain/usecase/MonsterStatsCalculator.kt`, `MonsterStagePolicy.kt`, `MonsterCombatPolicy.kt`, `MissedOccurrencePolicy.kt`
- Room v6와 transaction: `app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`, `CombatEntities.kt`, `CombatDao.kt`, `FailureLogEntity.kt`, `FailureLogDao.kt`, `app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- 백그라운드 실행: `app/src/main/java/com/todoquest/background/CombatReconciliationWorker.kt`

Calendar Combat Feedback v1의 occurrence failure/undo, player/monster HP bar와 attack·hit·death·spawn·damage text effect, 고정 Battle Map·독립 Calendar scroll을 구현했다. Combat Rewards v1은 hit XP와 처치 추가 XP·골드, 장비 `GOLD_GAIN_BONUS`, replay 없는 reward badge를 구현했다. 몬스터 이름·상태 효과·Stage HUD, 사망 디버프, 몬스터 스킬·치명타, 전리품과 정확한 deadline alarm은 계속 후속 범위다.

## Calendar Combat Feedback v1 확장 계약

이 확장은 아래의 version 1 피해·Stage·회복 수식을 바꾸지 않고 occurrence 실패 입력과 전투 결과 presentation만 연결한다. Calendar Battle Map의 기존 전체 단일 scroll은 시스템 inset 다음 고정 Battle Map과 그 아래 독립 Calendar scroll로 교체한다. 고정 영역은 진행 HUD, player/monster, actor 상단의 두 HP bar와 전투 effect 전체를 소유하고, scroll 영역은 월 이동·요일/날짜 grid·선택 날짜·완료/실패 요약·추가 버튼·task 목록·빈 안내를 소유한다.

### occurrence 상태와 실패 전이

| 표시 상태 | 원천 조건 | 허용되는 핵심 전이 |
|---|---|---|
| `TODO` | completion과 활성 failure가 없음 | 완료하면 `COMPLETED`, 수동 실패 또는 deadline 실패가 확정되면 `FAILED` |
| `COMPLETED` | occurrence `CompletionLog`가 존재 | 완료 취소 시 기존 정책대로 `TODO`; RewardLedger와 player attack은 유지 |
| `FAILED` | completion 없이 occurrence 실패 원천 상태가 활성 | 사용자가 실패 취소하면 표시만 `TODO` |

실패 취소는 task 표시 상태만 `TODO`로 되돌리고 이미 적용된 monster damage와 `monster_attack_events`를 되돌리거나 삭제하지 않는다. 실패 취소 뒤 늦은 완료는 기존 RewardLedger·player attack 계약을 따른다. 그 뒤 같은 occurrence를 다시 실패 처리해도 기존 `(taskId, occurrenceDate)` monster event가 있으면 두 번째 damage와 animation을 만들지 않는다.

수동 실패는 `FAILED` 상태를 먼저 영속한 뒤 monster attack을 즉시 best-effort로 시도한다. 실패 상태 저장에는 성공했지만 combat 처리만 실패하면 기존 reconciliation이 복구한다. `MANUAL_FAILURE`와 `MISSED_DEADLINE`은 같은 `monster_attack_events` occurrence key를 사용하므로 처리 순서나 재시도와 관계없이 공격은 한 번만 적용된다.

### source state와 transient transition

| 구분 | 값 | 재시작·재구독 계약 |
|---|---|---|
| Room source state | player/monster 현재 HP, occurrence RewardLedger, active monster, Stage, 확정 attack과 reward snapshot | 앱 재시작 뒤 복원하고 같은 occurrence 피해·보상을 다시 적용하지 않음 |
| transient transition | attack·hit·death·spawn·damage text와 `600ms` XP·gold badge | 새 attack event가 최초 확정될 때만 한 번 표시하고 process 재시작·Flow 재구독·기존 event 조회에 replay하지 않음 |

player attack은 비치명 hit XP를 지급하고 처치 시 level·grade 기반 추가 XP와 `GOLD_GAIN_BONUS`가 반영된 gold를 지급한다. 처치 시 player `HP_RECOVERY`는 유지하고 monster attack의 치명 피해는 Severe Injury v1 lifecycle로 처리한다. 전리품은 만들지 않는다. Calendar Combat Feedback v1 당시 공격 effect는 Compose Material icon과 translation·shake·flash·alpha만 사용하고 새 bitmap·sound asset을 요구하지 않았다. Battle Sound Effects v1은 pixel bitmap과 이 밸런스를 바꾸지 않고 아래 replay 없는 효과음만 후속으로 승인한다.

## Battle Sound Effects v1 presentation 계약

효과음은 영속 HP나 현재 체력 변화에서 추론하지 않는다. fresh Room 공격 결과가 최초 확정될 때만 `RoomCombatRepository.events`의 `MutableSharedFlow(replay = 0)`가 방출하는 `CombatTransition`을 source로 사용하고, 기존 `BattleAnimationController`의 단일 buffered actor가 animation과 음향의 직렬 순서를 함께 소유한다. persisted `APPLIED` 재처리, Flow 재구독, 화면 회전·재구성·재진입과 다음 monster 생성은 과거 음향을 replay하지 않는다.

`BattleSfx`는 `PLAYER_ATTACK`, `MONSTER_ATTACK`, `MONSTER_HIT`, `PLAYER_HIT`, `MONSTER_DEFEATED`, `PLAYER_DEFEATED` 여섯 값이다. 한 공격의 `PlayerAttackStarted|MonsterAttackStarted`, `EntityHit`, `MonsterDefeated|PlayerDefeated` effect는 같은 안정적 combat event id를 공유할 수 있으며, 개별 재생 key는 `eventId + BattleSfx`다.

```text
player:  PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시
         → 치명 시 MONSTER_DEFEATED → MONSTER_DYING
monster: MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시
         → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능
```

defeat 음은 hit 음 뒤 death animation이 시작될 때 재생한다. `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있는 치명 attack에만 허용한다. `StatusEffectApplied|StatusEffectRefreshed`, `PlayerEmergencyRecovered`, `StatusEffectRemoved`와 다음 monster spawn에는 death 또는 revive 음을 만들지 않으며 사용자 표현은 기존 `전투 불능`을 유지한다.

현재 앱에는 전용 설정 화면과 DataStore가 없다. application-scope SharedPreferences 설정은 key 부재를 `효과음` 켜짐으로 해석하고, Settings의 Switch가 이를 Repository·ViewModel을 통해 변경한다. navigation은 `캘린더 → 캐릭터 → 상점 → 도감 → 설정` 다섯 top-level destination이다. off는 음향만 억제하고 animation, damage text, HP bar와 status text를 유지한다.

application-scope `SoundPool`은 process당 한 번 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 생성해 여섯 raw WAV를 preload한다. load 전·background·released 요청은 queue하지 않고 폐기하며 복귀 뒤 과거 소리를 재생하지 않는다. 시스템 media volume과 DND를 우회하거나 audio focus를 독점하지 않는다. WAV 원본은 저장소의 결정론적 합성기로 만들고 외부 음원을 다운로드하지 않는다.

이 presentation 확장은 Room v14 schema·migration과 `MonsterStats`, 피해·보상·Stage·spawn·중상 수식을 변경하지 않는다. audio 준비·설정·재생 실패도 occurrence 완료, RewardLedger와 양방향 attack event transaction을 차단하거나 롤백하지 않는다.

일반 화면의 map 높이는 기존 `190dp..320dp`를 유지하고 저높이 화면만 Calendar scroll viewport 확보를 위해 `150dp..190dp` compact-height를 허용한다. 진행 HUD는 레벨·골드 아이콘/값·EXP current/required를 한 Row에 두고 progress bar만 아래에 둔다. 두 HP bar는 actor별 placement geometry에서 sprite 상단과 중심을 계산해 배치한다.

## 능력치와 숫자 계약

| 설계 코드 / 도메인 필드 | 의미 | 타입과 범위 | 저장 여부 | 변경 규칙 |
|---|---|---|---|---|
| `MAX_HP` / `maxHp` | 최대 체력 | `Int 1..9,999` | 계산값이므로 저장하지 않음 | 인스턴스 생성 시 적용된 source와 version으로 결정하며 전투 중 불변 |
| `DAMAGE` / `damage` | 몬스터 일반 공격의 raw damage | `Int 1..2,000` | 계산값이므로 저장하지 않음 | 별도 공격력·스킬·치명타 없이 전투 중 불변 |
| `DEFENSE` / `defense` | 받는 피해의 점감 입력 | `Int 0..200` | 계산값이므로 저장하지 않음 | 전투 중 불변 |
| `currentHp` | 현재 체력 | `Int 0..maxHp` | `MonsterInstance` 원천 상태로 Room에 저장 | 피해를 받을 때 `max(0, currentHp - finalDamage)`로 변경 |
| `isDefeated` | 패배 여부 | `currentHp == 0`인 파생 `Boolean` | 별도 저장하지 않음 | `currentHp`를 읽을 때 계산 |

`MonsterStats(maxHp, damage, defense)`는 세 최종 계산값을 묶는 immutable 결과다. 계산 직후 각 절대 범위로 clamp하며, `MonsterInstance.currentHp`가 계산된 `maxHp` 범위 안인지 인스턴스 경계에서 검증한다.

## 기준 선형 성장

level은 `1..55`다. 유형과 등급을 적용하기 전 기준값은 다음 식으로 계산한다.

```text
MAX_HP  = 75 + 5 × (level - 1)
DAMAGE  = 12 + 2 × (level - 1)
DEFENSE = 7  + 2 × (level - 1)
```

### 골든 진행값

| level | `MAX_HP` | `DAMAGE` | `DEFENSE` |
|---:|---:|---:|---:|
| 1 | 75 | 12 | 7 |
| 10 | 120 | 30 | 25 |
| 30 | 220 | 70 | 65 |
| 50 | 320 | 110 | 105 |
| 55 | 345 | 120 | 115 |

회귀 검증용 압축 표기는 HP `75/120/220/320/345`, 데미지 `12/30/70/110/120`, 방어 `7/25/65/105/115`다. 예를 들어 level 30은 HP `75 + 5 × 29 = 220`, 데미지 `12 + 2 × 29 = 70`, 방어 `7 + 2 × 29 = 65`로 재현된다.

### 성장 방식 결정

| 후보 | 플레이어 공식과의 관계 | 정수 재현성 | 플레이 테스트 이후 조정 | 결정 |
|---|---|---|---|---|
| 선형 | 플레이어의 레벨 항과 같은 방향으로 완만하게 증가 | 계수와 정수 덧셈만으로 완전 재현 | 계수와 배율을 새 version에서 조정 가능 | **채택** |
| 완만한 지수 | 고레벨에서 플레이어의 현재 선형 성장보다 빠르게 벌어짐 | 정수 근사·거듭제곱 내림 규칙이 추가됨 | 작은 계수 변경도 후반 수치에 크게 전파 | 제외 |
| 구간별 선형 | 구간별 난이도 조절은 쉬우나 경계에서 성장 감각이 달라짐 | 재현 가능하지만 분기와 골든 값이 늘어남 | 플레이 테스트 근거가 생긴 뒤 새 `balanceVersion`에서만 허용 | 현재 제외 |

선형을 선택한 이유는 현재 플레이어 성장 공식과 전투 시간이 함께 완만하게 증가하고, 모든 플랫폼에서 같은 정수 결과를 간단히 재현할 수 있기 때문이다. 완만한 지수 성장은 플레이어와의 격차를 자동으로 키우므로 사용하지 않는다. 구간별 성장은 version 1에 소급 도입하지 않는다.

## 현재 플레이어 전투 시간 기준

현재 실제 Repository는 장비 modifier를 적용하지 않는다. 따라서 다음 무장비 균형 플레이어를 version 1 전투 시간의 기준으로 사용한다.

| 플레이어 | HP | ATK | DEF | 치명타 확률 | 치명타 피해 |
|---|---:|---:|---:|---:|---:|
| Lv1 | 110 | 20 | 8 | `750bp` | `15,250bp` |
| Lv10 | 214 | 43 | 17 | `950bp` | `15,500bp` |
| Lv30 | 434 | 93 | 37 | `1,450bp` | `16,000bp` |
| Lv50 | 654 | 143 | 57 | `1,950bp` | `16,500bp` |

권장 처치 공격 수는 치명타의 결정적 골든 평균을 다음 정수식으로 계산한다. 실제 공격 event의 일반·치명타 판정은 확정 roll을 사용하지만 밸런스 비교에는 실수를 쓰지 않는다.

```text
criticalRawDamage = floor(ATK × criticalDamageBp / 10,000)
expectedDamageNumerator =
    normalFinalDamage × (10,000 - criticalChanceBp)
    + criticalFinalDamage × criticalChanceBp
expectedHits = ceil(monsterMaxHp × 10,000 / expectedDamageNumerator)
```

`ceil(a / b)`도 `(a + b - 1) / b`의 `Long` 정수 나눗셈으로 계산한다.

## 유형과 등급

### 유형 배율

| 유형 | HP | `DAMAGE` | `DEFENSE` | 의도 |
|---|---:|---:|---:|---|
| `BALANCED` | 1.00 | 1.00 | 1.00 | 표준 기준 |
| `ATTACK` | 0.90 | 1.25 | 0.85 | 짧지만 강한 공격형 |
| `DEFENSE` | 1.10 | 0.85 | 1.15 | 오래 버티는 방어형 |
| `BOSS` | 1.30 | 1.15 | 1.15 | 보스 전용 전반 강화 |

### 등급 배율과 콘텐츠 목표

| 등급 | HP / `DAMAGE` / `DEFENSE` | 권장 처치 공격 수 | 빈도 | 보상 배율 | 난이도 |
|---|---|---:|---:|---:|---|
| `NORMAL` | `1.00 / 1.00 / 1.00` | 3~5 | Stage당 8 | `1.0` (`10,000bp`) | 표준 |
| `ELITE` | `1.75 / 1.25 / 1.05` | 6~9 | Stage당 1 | `2.0` (`20,000bp`) | 도전적 |
| `BOSS` | `2.75 / 1.40 / 1.10` | 12~18 | Stage당 1 | `4.0` (`40,000bp`) | 장기·고난도 |

보상 배율은 Combat Rewards v1의 처치 추가 XP·gold에 적용한다. 비치명 hit XP에는 grade 배율을 적용하지 않으며 전리품은 범위 밖이다.

### bp 결합과 상한

`10,000bp = 1.0`이다. 유형과 등급은 각각 `Int` bp로 보관하고, 두 배율을 먼저 곱한 `Long` 분자와 combined 상한을 비교한다.

| 능력치 | combined 배율 상한 | `combinedCapBp` | 최종 절대 범위 |
|---|---:|---:|---:|
| HP | 4.0 | 40,000 | `1..9,999` |
| `DAMAGE` | 1.75 | 17,500 | `1..2,000` |
| `DEFENSE` | 1.5 | 15,000 | `0..200` |

```text
combinedNumerator = min(
    typeBp × gradeBp,
    combinedCapBp × 10,000,
)
scaledStat = floor(
    levelStat × combinedNumerator / 100,000,000
)
finalStat = clamp(scaledStat, absoluteMin, absoluteMax)
```

중간 곱셈은 `Long`으로 승격한다. 유형 적용 후 한 번, 등급 적용 후 다시 한 번 내리지 않고 위 식의 나눗셈에서 **한 번만** 내린다. combined 상한을 먼저 적용하고 능력치의 절대 상한은 마지막에 적용한다.

`BOSS` 유형+BOSS 등급의 결합값은 HP `1.30 × 2.75 = 3.575`, `DAMAGE` `1.15 × 1.40 = 1.61`, `DEFENSE` `1.15 × 1.10 = 1.265`다. 세 값 모두 combined 상한 안에 있다. level 55 기준 골든 결과는 다음과 같다.

```text
maxHp  = floor(345 × 3.575) = 1,233
damage = floor(120 × 1.61)  = 193
defense = floor(115 × 1.265) = 145
```

## 피해 공식

### 후보 비교

방식 A는 `max(1, rawDamage - defense)`인 단순 차감이고, 방식 B는 기존 플레이어 전투 계산과 같은 비율 감소다.

| 기준 | 방식 A: 단순 차감 | 방식 B: 비율 감소 |
|---|---|---|
| 사용자 이해 | 즉시 계산하기 쉽다 | 식은 더 길지만 방어가 높을수록 점감된다는 설명이 일관된다 |
| 저레벨 | 공격과 방어의 작은 차이에 결과가 급변한다 | 공격 규모에 비례한 피해를 유지한다 |
| 고레벨 | 낮은 공격을 쉽게 최소 피해에 고정한다 | 같은 방어 상수 안에서 완만하게 감소한다 |
| 방어가 공격보다 클 때 | 대부분 최소 피해가 되어 공격 성장 차이가 사라진다 | 양수 피해를 유지하면서 raw damage 차이를 보존한다 |
| 최소 피해 | 별도 하한만으로 결과가 자주 1에 고정된다 | raw damage의 10% 하한과 1 피해를 함께 보장한다 |
| 장비 체감 | 방어 1점의 체감이 구간마다 급변한다 | 방어가 오를수록 추가 효율이 완만해진다 |
| 구현·테스트 | 가장 단순하지만 경계 골든 값이 불안정하다 | 기존 `CombatCalculator`와 테스트를 양방향으로 재사용할 수 있다 |

방식 B를 채택한다. 플레이어가 몬스터를 공격할 때는 몬스터 `DEFENSE`, 몬스터가 플레이어를 공격할 때는 플레이어 `DEFENSE`를 같은 식에 넣는다.

```text
reducedDamage = floor(rawDamage × 100 / (defense + 100))
minimumDamage = max(1, floor(rawDamage × 1,000 / 10,000))
finalDamage = max(minimumDamage, reducedDamage)
```

곱셈은 `Long`으로 수행한다. `reducedDamage`와 `minimumDamage`를 각 식에서 내린 뒤 마지막에 최댓값을 고르는 기존 순서를 바꾸지 않는다. 몬스터 `DAMAGE`가 몬스터 공격의 raw damage이며, 몬스터에게 별도 공격력·스킬·치명타를 추가하지 않는다.

몬스터 `DEFENSE` 절대 상한 200에서 설명용 감소율은 `200 / (200 + 100)`, 즉 약 `66.7%`다. 예를 들어 raw damage 12와 defense 200이면 감소 피해 `floor(12 × 100 / 300) = 4`, 최소 피해 `1`이므로 최종 피해는 `4`다. 방어가 raw damage보다 커도 단순히 1로 고정되지 않는다.

런타임 계산과 골든 검증에 `Float`와 `Double`을 사용하지 않는다.

## 전투 시간 골든 예시

### NORMAL 유형별 처치 공격 수

아래 결과는 같은 level의 `NORMAL` 등급과 현재 무장비 균형 플레이어를 사용한다. 일반·치명타 각각에 피해 공식을 적용한 뒤 위의 정수 기대 피해 분자로 `expectedHits`를 계산한다.

| level | `BALANCED` HP/DEF → 공격 수 | `ATTACK` HP/DEF → 공격 수 | `DEFENSE` HP/DEF → 공격 수 |
|---:|---:|---:|---:|
| 1 | `75/7 → 4` | `67/5 → 4` | `82/8 → 5` |
| 10 | `120/25 → 4` | `108/21 → 3` | `132/28 → 4` |
| 30 | `220/65 → 4` | `198/55 → 4` | `242/74 → 5` |
| 50 | `320/105 → 5` | `288/89 → 4` | `352/120 → 5` |

모든 결과가 `NORMAL` 목표인 3~5회 안에 든다.

### 등급별 대표 처치 공격 수

`ELITE` 대표는 Stage 5 순환 결과인 `ATTACK` 유형과 `stageLevel + 1`, 보스는 `BOSS` 유형+BOSS 등급과 `stageLevel + 2`를 사용한다.

| 플레이어 level | `ATTACK` ELITE 최종 HP/DEF → 공격 수 | BOSS 최종 HP/DEF → 공격 수 |
|---:|---:|---:|
| 1 | `126/8 → 7` | `303/13 → 18` |
| 10 | `196/24 → 6` | `464/36 → 15` |
| 30 | `354/59 → 6` | `822/87 → 16` |
| 50 | `511/95 → 7` | `1,179/137 → 18` |

`ELITE`는 6~9회, BOSS는 12~18회 목표를 만족한다.

### 양방향 피해 골든 예시

같은 level의 `BALANCED`·`NORMAL` 몬스터가 무장비 균형 플레이어를 공격하면 다음과 같다.

| level | 몬스터 `DAMAGE` | 플레이어 DEF | 최종 피해 | 플레이어 최대 HP 대비 |
|---:|---:|---:|---:|---:|
| 1 | 12 | 8 | 11 | 약 10.0% |
| 10 | 30 | 17 | 25 | 약 11.7% |
| 30 | 70 | 37 | 51 | 약 11.8% |
| 50 | 110 | 57 | 70 | 약 10.7% |

예를 들어 Lv10 플레이어의 일반 공격은 raw 43, 몬스터 방어 25이므로 `floor(43 × 100 / 125) = 34`다. 반대 방향은 몬스터 raw 30, 플레이어 방어 17이므로 `floor(30 × 100 / 117) = 25`다. 두 방향 모두 같은 내림 순서를 사용한다.

## Stage 계약

Stage는 현재 version에서 정확히 `NORMAL 1~4 → ELITE 5 → NORMAL 6~9 → BOSS 10`의 10칸이다. Stage 10 이후의 반복, 초기화 또는 추가 Stage는 이번 계약에 없으며 임의로 추론하지 않는다.

| Stage | 등급 | encounter 수 | 유형 결정 |
|---:|---|---:|---|
| 1~4 | `NORMAL` | 각 8 | 세 일반 유형을 encounter마다 순환 |
| 5 | `ELITE` | 1 | Stage 번호로 세 일반 유형을 순환 |
| 6~9 | `NORMAL` | 각 8 | 세 일반 유형을 encounter마다 순환 |
| 10 | `BOSS` | 1 | 항상 `BOSS` 유형·BOSS 등급 |

일반 유형 배열은 `[BALANCED, ATTACK, DEFENSE]` 순서다. `normalOrdinal`은 각 NORMAL Stage 안의 0-based encounter ordinal `0..7`이다.

```text
normalTypeIndex = (stageNumber - 1 + normalOrdinal) % 3
eliteTypeIndex = (stageNumber - 1) % 3
```

따라서 Stage 1의 NORMAL 유형은 `BALANCED → ATTACK → DEFENSE` 순환으로 시작한다. Stage 5 ELITE는 index 1인 `ATTACK`이다. Stage 10은 위 일반 유형 식을 사용하지 않는다.

종족 스케줄은 능력치 유형과 분리한다. enum ordinal에 의존하지 않는 명시적 목록은 `[GOBLIN_SCOUT, SLIME, CORRUPTED_TREE_SPIRIT, SKELETON_SOLDIER, HARPY]`다. `MonsterType`은 definition과 능력치 배율을 고르는 입력일 뿐 종족 스케줄에는 전달하지 않는다.

결정적 seed는 `BASE_SEED = 0x544F444F51554553`에서 시작해 아래 순서로 `Long` 연산한다. grade code는 NORMAL `1`, ELITE `2`, BOSS `3`으로 명시하며 enum ordinal을 사용하지 않는다. 생성과 shuffle은 각각 `java.util.Random`과 `java.util.Collections.shuffle`을 사용한다.

```text
seed = BASE_SEED
seed = seed × 31 + balanceVersion
seed = seed × 31 + stageNumber
seed = seed × 31 + gradeCode
```

NORMAL은 `encounterCount >= 5`를 요구한다. pool을 명시적 다섯 종족으로 시작하고, encounter 수에 도달할 때까지 같은 random으로 목록 사본을 shuffle해 필요한 수만 뒤에 붙인 다음 전체 pool을 한 번 더 shuffle한다. 현재 NORMAL Stage의 8개 encounter는 이 규칙으로 모든 종족을 최소 한 번 포함하고 각 종족이 1~2회 나타난다. ELITE와 BOSS는 `encounterCount = 1`, `encounterNumber = 1`만 허용하고 같은 seed 규칙으로 다섯 후보를 shuffle한 첫 종족 하나를 사용한다.

balance version 1의 대표 golden은 다음과 같다.

| Stage | grade | 결정적 종족 결과 |
|---:|---|---|
| 1 | `NORMAL` | `SKELETON_SOLDIER → HARPY → GOBLIN_SCOUT → SKELETON_SOLDIER → SLIME → HARPY → CORRUPTED_TREE_SPIRIT → SLIME` |
| 2 | `NORMAL` | `CORRUPTED_TREE_SPIRIT → GOBLIN_SCOUT → SLIME → SKELETON_SOLDIER → GOBLIN_SCOUT → SKELETON_SOLDIER → HARPY → HARPY` |
| 5 | `ELITE` | `CORRUPTED_TREE_SPIRIT` |
| 10 | `BOSS` | `HARPY` |

종족은 drawable·한국어 이름·쓰러짐 안내를 선택하는 metadata다. `MonsterStatsCalculator`, 피해 공식, hit/kill XP·gold 보상에는 종족을 전달하지 않으므로 다섯 종족 사이에 상성이나 수치 차이가 없다. 해골의 `undead`·`dark`, 나무 정령의 `forest`·`dark`·`corruption`, 하피의 `wind`·`flight`·`mountain`·`grassland`, 슬라임의 `water`·`slime`은 이번 phase에서 시각 콘셉트와 종족 식별 metadata일 뿐 비행 동작, biome, 상성, 상태 효과, 스킬, 전리품 또는 새 맵 배경을 추가하지 않는다.

Stage를 시작할 때 그 시점의 플레이어 level `1..50`을 `stageLevel`로 잠근다. Stage 도중 플레이어가 level up해도 활성 Stage의 `stageLevel`과 이미 생성된 몬스터 level은 바꾸지 않는다.

```text
gradeLevelOffset(NORMAL) = 0
gradeLevelOffset(ELITE) = 1
gradeLevelOffset(BOSS) = 2
monsterLevel = min(55, stageLevel + gradeLevelOffset)
```

예를 들어 `stageLevel = 50`이면 NORMAL/ELITE/BOSS level은 각각 `50/51/52`다. 몬스터 level의 절대 상한 55는 미래의 허용된 `stageLevel + offset` 확장에도 동일하게 적용하며, 입력과 결과를 모두 명시적으로 검증한다.

## HP 변경, 치명 피해와 회복

몬스터가 피해를 받으면 `newCurrentHp = max(0, currentHp - finalDamage)`를 공격 event와 같은 transaction에서 저장한다. `newCurrentHp == 0`이면 `isDefeated`가 참이다.

플레이어가 몬스터 공격으로 0 HP 이하가 되는 치명 피해를 받으면 공격 event에 `wasLethal = true`, `playerHpAfter = 0`을 확정한다. 같은 transaction에서 `SEVERE_INJURY`를 적용하거나 현재 row의 revision을 증가시켜 갱신하고, ordered lifecycle을 만든다.

```text
PlayerDefeated
→ StatusEffectApplied 또는 StatusEffectRefreshed
→ PlayerEmergencyRecovered

effectiveMaxHp = floor(base/equipment-derived maxHp × 8,000 / 10,000)
effectiveAttack = floor(base/equipment-derived attack × 8,000 / 10,000)
emergencyRecoveredHp = max(1, floor(effectiveMaxHp × 5,000 / 10,000))
```

중상은 적용 시점부터 24시간 또는 서로 다른 occurrence 완료 3회까지 유효하며 `MAX_HP`와 `ATTACK`을 각각 20% 감소시킨다. base stat과 장비 modifier를 변경하지 않고 최종값은 기존 최소 1을 유지한다. 응급 회복은 중상 적용 뒤 유효 최대 HP의 50%를 내림하고 최소 1을 보장한다. 재패배는 중첩 row를 만들지 않고 revision, 적용·만료 시각과 남은 완료 수를 초기화한다. `wasLethal`과 물리 컬럼 `revivedHp`는 호환 snapshot으로 보존하되, v13 신규 row의 `revivedHp` 값은 위 응급 회복 결과를 의미한다.

회복 credit key는 `(characterId, effectType, revision, taskId, occurrenceDateEpochDay)`다. 같은 occurrence의 완료 취소·재완료, 중복 command와 이전 revision의 credit은 현재 중상을 두 번 감소시키지 않는다. 세 번째 완료는 effect 제거와 복원된 스탯의 player attack source snapshot을 완료·RewardLedger·outbox와 같은 transaction에 확정한다. `AppClock.now() >= expiresAt`이면 Repository reconciliation이 상태를 제거하며 제거는 current HP를 자동 치유하지 않는다.

몬스터 처치가 처음 확정된 공격 transaction에서는 기존 플레이어 `HP_RECOVERY`를 정확히 1회 적용한다.

```text
playerHpAfterRecovery = min(playerMaxHp, playerCurrentHp + hpRecovery)
```

같은 공격 event의 재처리나 이미 패배한 몬스터에 대한 중복 처리는 회복과 XP·gold를 다시 적용하지 않는다. 전리품은 지급하지 않는다.

## 데이터 경계와 앱 종료 후 보존

### Definition, instance와 계산 결과

| 모델 | 소유 값 | source와 저장 경계 |
|---|---|---|
| `MonsterDefinition` | `id`, `nameKey`, `type`, 세 능력치의 base와 level당 growth | versioned catalog에서 읽는다. Room에 definition이나 grade별 복제본을 저장하지 않는다. `nameKey`는 안정적인 backend 현지화 key이고 사용자 표시 문자열이 아니다. UI가 추가될 때 `app/src/main/res/values/strings.xml`의 한국어 문자열 resource로 매핑한다. |
| `MonsterInstance` | instance id, definition id, `grade`, Stage/encounter, level, `currentHp`, `balanceVersion` | 전투 인스턴스 원천 상태로 Room에 저장한다. 생성 시 `currentHp = MonsterStats.maxHp`로 시작한다. grade는 같은 definition의 `NORMAL`·`ELITE`·`BOSS` 중복을 막기 위해 instance에 둔다. type은 definition에서 읽는다. |
| `MonsterSpecies` | `GOBLIN_SCOUT`, `SLIME`, `CORRUPTED_TREE_SPIRIT`, `SKELETON_SOLDIER`, `HARPY` | 저장된 `stageNumber + encounterNumber + grade + balanceVersion`을 `MonsterSpeciesPolicy`에 전달해 관찰 시 파생한다. Room에 종족이나 실제 난수 결과를 저장하지 않으며 `MonsterType`을 스케줄 입력으로 사용하지 않는다. |
| `MonsterStats` | 최종 `maxHp`, `damage`, `defense` | definition, instance와 해당 `balanceVersion` config로 매번 계산한다. Room에 저장하지 않는다. |
| `isDefeated` | `currentHp == 0` | 파생값으로만 노출하고 Room에 저장하지 않는다. |

`MonsterDefinition`의 base/growth가 version 1 기준 선형식의 `75/12/7`과 `5/2/2`를 제공한다. catalog와 config는 versioned이며, 기존 instance와 확정 attack event는 자신이 기록한 `balanceVersion`으로 해석한다.

### Room v13 진행, 공격, reward, failure, 상태이상과 cursor persistence

| 상태 | Room에 보존할 원천 값 | 재시작 동작 |
|---|---|---|
| Stage 진행 | 현재 `stageNumber`, 잠근 `stageLevel`, 활성 `MonsterInstance` id와 encounter 위치, `balanceVersion` | 같은 Stage와 encounter에서 재개한다. NORMAL은 8번째 처치 뒤 다음 Stage로, ELITE/BOSS는 첫 처치 뒤 다음 진행 경계로 이동한다. |
| 몬스터 인스턴스 | definition id, grade, Stage/encounter, level, `currentHp`, `balanceVersion` | 저장된 HP로 복원하고 최종 `MonsterStats`는 다시 계산한다. |
| 플레이어 공격 outbox와 양방향 공격 event | occurrence별 독립 멱등 key, 방향, 처리 상태, 계산 source·결과 snapshot, `wasLethal` | pending 공격은 재시도하되 같은 event의 피해·처치 회복을 다시 적용하지 않는다. |
| reconciliation cursor | 첫 combat 초기화 경계와 마지막으로 탐색한 실패 occurrence 경계 | cursor 이후의 누락 실패만 계속 탐색하며 과거 occurrence에 피해를 소급하지 않는다. |
| 플레이어 현재 HP | 기존 `CharacterCurrentState.currentHp`와 character balance version | 앱 종료 뒤에도 피해, 응급 회복과 처치 회복 결과를 유지한다. 치명 event 자체에는 `0 HP` 결과를 보존한다. |
| 상태이상 | `character_status_effects`의 type·definition version·적용/만료 시각·남은 완료 수·active·revision·mutation id | 현재 revision의 중상과 유효 스탯 감소를 재구성하고 `AppClock`으로 만료를 조정한다. |
| 회복 credit | `status_effect_recovery_occurrences`의 character·effect·revision·task·occurrence date 복합 key | 중복 완료나 완료 취소 뒤 재완료에도 같은 회복을 다시 차감하지 않는다. |
| occurrence failure | `failure_logs`의 `(taskId, occurrenceDateEpochDay)`, 반복 계보와 실패 시각 | `FAILED` 표시를 복원한다. failure undo는 이 row만 삭제하고 이미 확정된 monster event와 HP에는 cascade하지 않는다. |

Stage/encounter cursor는 몬스터 처치와 다음 인스턴스 활성화를 하나의 Repository transaction 경계에서 갱신한다. 공격 event는 completion `RewardLedger`와 독립된 실제 전투 보상 ledger이며, 전투 처리 실패가 일정 완료를 되돌리지 않는다.

앱 종료 시 계산 cache, 지역화된 이름, 아직 확정되지 않은 roll과 UI 표현은 버릴 수 있다. 반면 `currentHp`, Stage/encounter 진행, 활성 인스턴스, pending outbox, 확정 공격 event, `wasLethal`, reconciliation cursor와 각 balance version은 Room에 남긴다. 알림 또는 exact alarm 권한이 없어도 이 저장·복원과 reconciliation 계약은 바뀌지 않는다.

Room v6의 실제 전투 테이블은 v4에서 추가한 `monster_instances`, singleton `combat_progress`, `player_attack_events`, `monster_attack_events`와 v6의 `failure_logs`다. 플레이어 공격 row는 `PENDING` 상태에서 source snapshot을 먼저 확정한 뒤 seed·roll·피해 결과를 같은 row에 채우며, 몬스터 공격은 별도 테이블의 독립 occurrence key와 `MANUAL_FAILURE`·`MISSED_DEADLINE` trigger로 적용 또는 영구 skip을 기록한다. `combat_progress.lastReconciledAtEpochMillis`가 첫 combat 초기화 이전 피해의 비소급 cursor다.

Calendar Combat Feedback v1의 Room v6 확장은 occurrence 실패 원천 상태를 `(taskId, occurrenceDateEpochDay)`로 저장하고 기존 `monster_attack_events` primary key를 그대로 공유한다. monster attack 원인은 `MANUAL_FAILURE` 또는 `MISSED_DEADLINE`로 구분하지만 별도 event row나 별도 피해 key를 만들지 않는다. `MIGRATION_5_6`은 기존 HP·reward·active monster·Stage·attack event를 보존하고 과거 occurrence failure나 transient transition을 소급 생성하지 않으며, 기존 monster event에는 `MISSED_DEADLINE` 기본값을 부여한다.

마감 실패 event의 deadline은 시간 일정이면 예정 시각+15분, 무시간 일정이면 occurrence 날짜 종료다. 공격 event의 `processedAtEpochMillis`는 reconciliation이 실제 실행된 시각이므로 deadline과 같지 않을 수 있다. WorkManager는 앱 시작 one-time work와 15분 periodic work를 best-effort로 실행하며 OS 지연 때문에 정확한 시각 공격을 보장하지 않는다. 정확한 deadline alarm은 후속 범위다.

## 구현 범위 확인

- 순수 계산과 Room v6 backend 영속·reconciliation, Calendar Battle Map의 actor와 HP bar·effect·실패 UI를 구현했다.
- grade 보상 배율은 처치 추가 XP·gold에 적용하고 hit XP에는 적용하지 않는다. 전리품은 구현하지 않는다.
- 현재 상태이상 catalog는 `SEVERE_INJURY` 하나다. 추가 상태이상과 몬스터별 상태 기술은 후속이다.
- 장비 modifier는 Repository 전투 입력에 연결하며 `GOLD_GAIN_BONUS`는 처치 gold에만 적용한다.
- 저장된 Stage·encounter·grade·balance version 기반의 결정적 다섯 종족 스케줄과 Battle Map presentation은 구현했지만 비행 동작·biome·종족 상성·상태 효과·스킬·전리품·새 맵 배경은 구현하지 않는다.
- 몬스터 스킬·치명타와 정확한 deadline alarm도 후속이다.
- Battle Sound Effects v1은 replay 없는 presentation 후속 범위이며 Room v14 source, 피해·보상·spawn·중상 수식을 변경하지 않는다.

## Calendar Combat Feedback 검증 위치

| 계약 | 실제 테스트 위치 |
|---|---|
| 수동 `MANUAL_FAILURE` 즉시 공격, 중복 요청의 단일 damage·단일 replay 0 transition | `RoomCombatRepositoryTest.manualFailureAppliesOnceAndEmitsOneNonReplayableTransition`, `completedManualFailureDoesNotReplayToLateCollectorOrOnDuplicate` |
| pending 수동 failure 복구와 `MISSED_DEADLINE` reconciliation 순서·trigger 분리, 같은 occurrence key 멱등성 | `RoomCombatRepositoryTest.pendingFailureRepairUsesDeterministicOrderAndDoesNotApplyCapOrReplay`, `reconcileEmitsPlayerManualAndDeadlineTransitionsInThatOrderWithoutCollision`, `reconciliationSortsNewDueEventsCapsDamageAtThreeAndIsIdempotent` |
| completion RewardLedger·player attack 중복 방지와 failure undo 뒤 늦은 완료 | `RoomTaskRepositoryTest.completeOccurrenceAwardsRewardOnlyOnce`, `undoCompletionDoesNotReclaimOrDuplicateReward`, `RoomCombatRepositoryTest.failureAttackDoesNotBlockLateCompletionRewardOrPlayerAttack` |
| 몬스터 처치의 단일 HP 회복·다음 monster spawn, 치명 monster attack의 0 HP 전투 불능·중상·응급 회복과 transition 중복 방지 | `RoomCombatRepositoryTest.defeatingMonsterRecoversPlayerOnceAndAdvancesToNextEncounter`, `zeroHpTriggersTheStatusLifecycleAndEmergencyRecovery`, `lethalAttackAppliesSevereInjuryOnceAndDuplicateReturnsStoredResult`, `BattleAnimationControllerTest.lethalMonsterAttackConsumesTheEntireSevereInjuryLifecycleInOrder`, `queuedTransitionsRunOneAtATimeAndDuplicateKeysStayConsumedForControllerLifetime` |
| 중상 20% 감소·최소 1, 50% 응급 회복, 24시간·3 occurrence 회복, 재패배 refresh, 재시작·해제 복원 | `DerivedStatsCalculatorTest.severeInjuryFloorsMaxHpAndAttackToEightyPercentWithoutMutatingSources`, `severeInjuryKeepsLowMaxHpAndAttackAtExistingMinimumOne`, `StatusEffectPolicyTest.emergencyRecoveryUsesLongFloorMathAndKeepsAtLeastOneHp`, `RoomTaskRepositoryTest.threeDistinctRecurringCompletionsRecoverInjuryAndThirdAttackUsesRestoredStats`, `RoomCombatRepositoryTest.defeatWhileInjuredRefreshesOneEffectAndIgnoresPreviousRevisionCredits`, `restartRestoresActiveInjuryAndExactExpiryReconciliationOnlyRemovesModifier` |
| HP bar low state·220ms 보간, phase effect와 한국어 live region | `BattleMapLayoutTest.healthValueClampsCurrentFractionAndLowHealthBoundary`, `BattleMapTest.healthBarsClampLowHealthAndAnimateToTargetIn220Millis`, `presentationPhasesRenderAttackHitDeathSpawnAndKoAnnouncementsInsideMap` |
| shared player·monster bitmap의 실기기 불투명 합성 | `BattleMapTest.playerAndMonsterLayersDrawOpaquePixelSpriteOutlines`와 phase 33 portrait·landscape raw screenshot |
| 다섯 종족 golden schedule·NORMAL 1~2회 균형·ELITE/BOSS 단일 선택·능력치/피해/보상 불변 | `MonsterSpeciesPolicyTest.balanceVersionOneMatchesTheGoldenSpeciesSchedule`, `everyNormalStageContainsAllSpeciesWithBalancedCountsAndItsOwnOrder`, `speciesMetadataDoesNotChangeStatsDamageOrCombatRewards`, `MonsterStagePolicyTest.stageOneNormalEncountersKeepTypeCycleWhileSpeciesUseTheNewSchedule`, `specialStagePolicyOutputsSelectOneDeterministicSpecies` |
| 저장 source 기반 종족의 재시작·처치 전환 안정성 | `RoomCombatRepositoryTest.storedNormalEncountersFollowTheStageOneAndTwoGoldenSpeciesSchedules`, `storedEliteAndBossUseTheirDeterministicScheduledSpecies`, `storedScheduledSpeciesIsStableAcrossRepositoryRestartWithoutChangingCombatSource`, `defeatingMonsterPreservesBeforeSpeciesAndUsesNextEncounterScheduledSpecies` |
