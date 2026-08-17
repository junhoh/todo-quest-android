# 게임 설계 문서 인덱스

게임 수치와 기능별 상세 규칙을 찾기 위한 진입점이다. 제품 범위와 MVP 포함 여부는 [PRD](../PRD.md)가 우선한다.

## 정식 진입점

- [캐릭터 핵심 스탯 및 전투 계산 설계](character-stats-design.md) — **스탯·성장·Combat Rewards v1·장비·전투 UI 구현 계약**
- [몬스터 능력치와 성장](monster-stats-and-growth.md) — **몬스터 능력치·유형·등급·Stage·피해·Room v9 보상 영속 경계 canonical 계약**

Post-MVP Character Growth v1에서 level 50 상한, 기본·파생 스탯 계산, 포인트 배분·초기화, 정시·일일/반복 효율·연속일 비전투 보상 기반, Room v3와 Character 화면을 구현했다. 순수 Kotlin `CombatCalculator`와 장비 modifier 검증 계약도 계산 기반으로 존재한다.

Post-MVP Monster Combat v1에서는 세 몬스터 능력치와 10칸 Stage 정책, Room v4 원천 상태, occurrence 플레이어 공격 outbox, 마감 실패 몬스터 공격, 복귀당 3회 피해 상한, 앱 시작·15분 주기 WorkManager reconciliation을 구현했다. Severe Injury v1은 Room v13에서 치명 피해를 `0 HP 전투 불능 → 중상 적용/갱신 → 유효 최대 체력 50% 응급 회복`으로 확장하고, 24시간 또는 서로 다른 occurrence 완료 3회의 멱등 회복을 제공한다.

Calendar Battle Map, gameplay 장비 source와 hit/처치 XP·gold는 구현했다. 몬스터 이름·상태 효과·Stage HUD, 사망 디버프, 몬스터 스킬·치명타, 전리품과 정확한 deadline alarm은 후속 범위다. 분할 하위 문서는 위 진입 인덱스의 권장 읽기 순서와 주제별 링크를 통해 탐색하며, 각 문서의 구현 상태 표기를 함께 확인한다.
