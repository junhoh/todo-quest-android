# 캐릭터 핵심 스탯 및 전투 계산 설계

## 문서 지위와 적용 범위

이 문서는 Todo Quest MVP 이후 캐릭터 성장과 전투·장비 능력치를 확장하기 위한 설계 인덱스다. [PRD의 Post-MVP Character Growth v1](../PRD.md#승인된-후속-범위-post-mvp-character-growth-v1), [Post-MVP Monster Combat v1](../PRD.md#승인된-후속-범위-post-mvp-monster-combat-v1), [Post-MVP Combat Rewards v1](../PRD.md#승인된-후속-범위-post-mvp-combat-rewards-v1), [Post-MVP Equipment Shop and Inventory v1](../PRD.md#승인된-후속-범위-post-mvp-equipment-shop-and-inventory-v1), [Post-MVP Severe Injury v1](../PRD.md#승인된-후속-범위-post-mvp-severe-injury-v1)과 ADR-008·009·013·014·019가 승인한 **스탯·성장, 로컬 전투·hit/처치 보상, 중상 상태와 gameplay 장비 소유·장착·능력치 적용·UI는 구현 완료**됐다. MVP 요구사항과 제외 범위는 그대로 유지된다.

현재 `CharacterStatBalanceConfig(version = 1)`과 성장 정책에서 레벨 `L`은 `1..50`으로 계산한다. level과 8개 파생 능력치는 Room에 저장하지 않고 `PlayerCharacter` 원천 상태와 versioned config에서 계산하며, level 50 이후에도 누적 XP를 보존한다.

## 구현 상태와 남은 후속 범위

| 영역 | 현재 상태 | 남은 후속 범위 |
|---|---|---|
| 제품 범위 | MVP의 일정·occurrence 멱등 보상을 유지한 채 Character Growth v1, Monster Combat v1 backend, Severe Injury v1과 Equipment Shop and Inventory v1을 구현했다. | Stage HUD, 몬스터 스킬·치명타와 추가 상태이상 catalog는 계속 후속 범위다. |
| 캐릭터 성장 | `PlayerCharacter`, `CharacterCurrentState`, level 50 cap, 레벨당 2포인트, 배분·무료/유료 초기화와 HP 비율 보존을 구현했다. Room v13도 캐릭터 원천 상태와 계산된 파생값의 비저장 원칙을 유지한다. | 전투 중 성장 command 제한과 중상 외의 추가 버프·디버프 lifecycle은 후속이다. |
| 파생 스탯 | version 1 설정, 기존 네 기본 스탯과 8개 파생 스탯, modifier bucket·clamp와 전투 수식의 순수 Kotlin 계산기를 구현했다. Room v7 `character_equipment`과 Room v13 활성 상태이상 modifier를 Character·Task·Combat 계산에 연결했고 민첩 계열은 `FOCUS`, 지능 계열은 `WILLPOWER`를 재사용한다. | 새 원천 스탯과 계산된 파생값의 Room 저장은 도입하지 않는다. |
| 완료·전투 보상 | 정시 판정, streak·`MOMENTUM`, occurrence별 reward mode와 모든 신규 완료의 player attack outbox를 구현했다. 공격 적중은 소량 XP, 처치는 level·grade 기반 추가 XP·gold를 지급하며 Battle Map badge로 표시한다. | 과거 `TODO_COMPLETION` reward는 legacy로 보존하고 신규 공식으로 소급하지 않는다. |
| 자동 전투 | PENDING 플레이어 공격 outbox, seed·roll·피해·보상 확정, 실패 몬스터 공격, 3회 복귀 피해 상한, `0 HP 전투 불능 → 중상 → 50% 응급 회복`, Room v13 상태와 WorkManager reconciliation을 구현했다. | Stage HUD, 추가 상태이상, 몬스터 스킬·치명타, 전리품과 정확한 deadline alarm이 필요하다. |
| 장비 | `EquipmentType`·`EquipmentSlot`의 일곱 값, modifier 검증·같은 slot 비교, Room v7 catalog/modifier/소유/장착, 원자 구매·장착, Shop·Inventory UI를 구현했다. `CHEST`와 `LEGS`는 독립이고 기존 appearance loadout은 외형 fallback으로 보존한다. | seed 장비 전용 bitmap·대부분의 appearance `layerKey`는 없으므로 type별 placeholder와 기존 appearance fallback을 사용한다. quantity·`PET`·유료 재화는 범위 밖이다. |

## 권장 읽기 순서

아래 순서로 읽으면 TODO 완료에서 전투 event가 만들어지고, 캐릭터 수치와 장비 modifier를 거쳐 전투 결과와 구현 계약으로 이어지는 흐름을 확인할 수 있다. 이 호환 인덱스는 후속 게임 설계 디렉터리 인덱스에서도 그대로 연결할 수 있도록 모든 하위 문서를 현재 경로 기준 상대 링크로 참조한다.

| 순서 | 문서 | 책임 |
|---:|---|---|
| 1 | [TODO 완료와 자동 전투 보상](character-stats/todo-combat-rewards.md) | occurrence 멱등 event, hit/kill 보상 공식, 정시·실패 처리, MOMENTUM, Room v9 ledger 테스트 경계 |
| 2 | [기본 스탯과 성장](character-stats/stats-and-progression.md) | 기본 스탯, 레벨 성장과 스탯 포인트, 파생 능력치 |
| 3 | [modifier와 장비](character-stats/modifiers-and-equipment.md) | 숫자 표현과 반올림, 7개 gameplay type/slot, 장비 효과·소유·장착과 저장 호환 mapping |
| 4 | [전투 계산](character-stats/combat-calculation.md) | 확률·상태·전리품, 방어·피해, 최대 HP 변경 시 현재 HP |
| 5 | [몬스터 능력치와 성장](monster-stats-and-growth.md) | 몬스터 세 능력치, 유형·등급, 10칸 Stage, Room v4 영속과 후속 경계 |
| 6 | [구현 계약과 검증](character-stats/implementation-and-validation.md) | 실제 public 타입·패키지, Room v4, 재계산, 골든 수치와 test checklist |

## TODO 보상과 자동 전투 연동 계약

[TODO 보상과 자동 전투 연동 계약](character-stats/todo-combat-rewards.md#todo-보상과-자동-전투-연동-계약)에서 확인한다.

## 기본 스탯

[기본 스탯](character-stats/stats-and-progression.md#기본-스탯)에서 확인한다.

## 레벨 성장과 스탯 포인트

[레벨 성장과 스탯 포인트](character-stats/stats-and-progression.md#레벨-성장과-스탯-포인트)에서 확인한다.

## 파생 능력치

[파생 능력치](character-stats/stats-and-progression.md#파생-능력치)에서 확인한다.

## 숫자 표현과 반올림 계약

[숫자 표현과 반올림 계약](character-stats/modifiers-and-equipment.md#숫자-표현과-반올림-계약)에서 확인한다.

## 장비와 효과 적용

[장비와 효과 적용](character-stats/modifiers-and-equipment.md#장비와-효과-적용)에서 확인한다.

## 장비 부위 역할

[장비 부위 역할](character-stats/modifiers-and-equipment.md#장비-부위-역할)에서 확인한다.

## 장비 소유·장착과 저장 호환

[장비 소유·장착과 저장 호환](character-stats/modifiers-and-equipment.md#장비-소유장착과-저장-호환)에서 확인한다.

## 확률 판정, 상태 적용 및 전리품

[확률 판정, 상태 적용 및 전리품](character-stats/combat-calculation.md#확률-판정-상태-적용-및-전리품)에서 확인한다.

## 방어와 피해 계산

[방어와 피해 계산](character-stats/combat-calculation.md#방어와-피해-계산)에서 확인한다.

## 최대 HP 변경 시 현재 HP

[최대 HP 변경 시 현재 HP](character-stats/combat-calculation.md#최대-hp-변경-시-현재-hp)에서 확인한다.

## 구현용 데이터 계약

[구현용 데이터 계약](character-stats/implementation-and-validation.md#구현용-데이터-계약)에서 확인한다.

## 파생 능력치 재계산 이벤트

[파생 능력치 재계산 이벤트](character-stats/implementation-and-validation.md#파생-능력치-재계산-이벤트)에서 확인한다.

## 밸런스 설정과 확정 결과

[밸런스 설정과 확정 결과](character-stats/implementation-and-validation.md#밸런스-설정과-확정-결과)에서 확인한다.

## 골든 수치 검증

[골든 수치 검증](character-stats/implementation-and-validation.md#골든-수치-검증)에서 확인한다.

## 순수 Kotlin unit test 우선 checklist

[순수 Kotlin unit test 우선 checklist](character-stats/implementation-and-validation.md#순수-kotlin-unit-test-우선-checklist)에서 확인한다.
