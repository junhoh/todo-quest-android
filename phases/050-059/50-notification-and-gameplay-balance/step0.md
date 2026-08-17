# Step 0: approve-permission-and-economy-contract

## 읽어야 할 파일

- /AGENTS.md
- /docs/PRD.md
- /docs/ARCHITECTURE.md
- /docs/ADR.md
- /docs/UI_GUIDE.md
- /docs/DEVELOPMENT.md
- /docs/game-design/character-stats/todo-combat-rewards.md
- /phases/050-059/50-notification-and-gameplay-balance/index.json

## 작업

문서만 수정해 이번 phase의 제품·호환성 계약을 먼저 승인한다. ADR-017에는 첫 Calendar 진입에서 한국어 안내를 거쳐 POST_NOTIFICATIONS를 한 번만 요청하고, 거부·보류 뒤 자동 재요청하지 않으며 reminder 저장 시 package notification settings CTA를 제공하는 결정을 기록한다. exact-alarm special access는 첫 실행에 요청하지 않고, notification capability가 확보된 뒤 NONE이 아닌 reminder 저장 흐름에서만 기존 설명 dialog와 설정 화면을 순차 제공한다. 권한 거부는 일정·완료·보상·전투·구매를 차단하지 않는다.

ADR-018에는 Combat Reward v2와 가격 완화의 과거 데이터 보존 결정을 기록한다. v2는 hit XP base 3, kill bonus XP base 20, kill gold base 15이며 level band 10/5/10과 NORMAL/ELITE/BOSS 1x/2x/4x, GOLD_GAIN_BONUS의 처치 골드 전용 적용을 유지한다. level 1 NORMAL 처치는 23 XP/15골드, level 55 BOSS 처치는 128 XP/80골드다. 새 attack만 reward version 2를 사용하고 v0은 무보상, v1 PENDING은 기존 1/10/5 공식, APPLIED는 저장 snapshot을 유지한다.

18종 가격은 현재 가격을 2로 나눈 정수 몫으로 승인한다: 낡은 검 20, 철 장검 360, 가죽 모자 27, 철 투구 340, 천 상의 22, 가죽 갑옷 130, 철 흉갑 1200, 천 바지 22, 가죽 바지 120, 강철 각반 1150, 가죽 장갑 140, 여행자의 장화 380, 마법사의 반지 1350, 수호자의 목걸이 3400, 강철 건틀릿 410, 바람걸음 장화 430, 물푸레나무 창 25, 강철 철퇴 390 골드다. 기존 canonical identity와 old price가 모두 일치하는 row만 조건부 갱신하고 이미 구매한 장비는 환불하지 않는다. schema version은 올리지 않는다.

UI_GUIDE에는 EXP group이 최소 104dp와 최대 intrinsic content 폭을 사용해 0/100과 bar가 잘리지 않는 계약을 기록한다. Player/monster placement, map 높이, 한국어 TalkBack과 EXP bar/content 좌우 경계는 유지한다. 이 step에서는 Kotlin, XML resource, Room schema를 수정하지 않는다.

## Acceptance Criteria

~~~powershell
rg -n "ADR-017|ADR-018|Combat Reward v2|23 XP|128 XP|104dp|첫 실행|알림 설정" docs
git diff --check
~~~

## 검증 절차

1. 기존 ADR-014·ADR-015와 새 결정을 대조해 역사적 v1 계약을 삭제하지 않았는지 확인한다.
2. 권한 실패 독립성, occurrence/attack 멱등성, 외부 캘린더 제외 규칙을 확인한다.
3. AC를 실행하고 phase index의 step 0을 completed와 한국어 summary로 갱신한다.

## 금지사항

- 기존 v1 공식을 v2 값으로 덮어쓰지 마라. 이유: PENDING v1 event를 업데이트 후에도 같은 의미로 처리해야 한다.
- exact-alarm 권한을 첫 실행에 요청한다고 문서화하지 마라. 이유: 실제 reminder 설정 전에는 special access가 필요하지 않다.
- Kotlin, Room schema 또는 resource를 수정하지 마라. 이유: 이 step은 구현 전 계약 승인에 한정한다.
- 기존 테스트를 깨뜨리지 마라.
