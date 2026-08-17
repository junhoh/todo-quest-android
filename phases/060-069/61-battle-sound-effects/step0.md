# Step 0: document-battle-sfx-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

Battle Sound Effects v1의 승인 계약을 문서에 먼저 확정한다. `docs/ADR.md`에는 다음 번호인 ADR-024를 추가하고 PRD, ARCHITECTURE, UI_GUIDE, DEVELOPMENT 및 monster combat 설계 문서를 같은 계약으로 동기화한다.

기존 `RoomCombatRepository.events`의 `replay = 0` transition과 `BattleAnimationController`의 단일 buffered actor를 그대로 효과음의 source와 조정 경계로 사용한다. Composable이 HP Flow나 체력값 변화를 감시해 음향을 추론하지 않는다. 한 공격의 `PlayerAttackStarted|MonsterAttackStarted`, `EntityHit`, `MonsterDefeated|PlayerDefeated`는 같은 안정적 combat event id를 공유할 수 있고 `eventId + BattleSfx`로 개별 재생을 식별한다고 명시한다. transition은 fresh Room 공격 결과에서만 방출되고 persisted APPLIED 재처리, 화면 회전, 재구성, 화면 재진입 및 다음 monster 생성은 과거 음향을 재생하지 않는다.

순서는 player가 `PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시 → (치명 시) MONSTER_DEFEATED → MONSTER_DYING`, monster가 `MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시 → (치명 시) PLAYER_DEFEATED → PLAYER_DYING/전투 불능`이다. defeat 음은 hit 음 뒤 death animation이 시작될 때 재생한다. `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있는 경우에만 허용하며 상태 적용·갱신, 응급 회복, 상태 제거에는 death 또는 revive 음을 연결하지 않는다. 사용자 표현은 기존 `전투 불능`을 유지한다.

앱에 기존 전용 설정 화면이나 DataStore가 없다는 사실을 기록하고 application-scope SharedPreferences 설정을 기본 켜짐으로 추가한다. 하단 navigation은 `캘린더 → 캐릭터 → 상점 → 도감 → 설정` 다섯 top-level destination이 되며 설정 화면은 `효과음` Switch 하나를 제공한다. 설정 off는 음향만 억제하고 animation, damage, HP bar와 status text를 유지한다.

SoundPool은 application-scope에서 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 한 번 만들고 여섯 raw WAV를 preload한다. load 전·background·released 상태의 요청은 지연 재생하지 않고 폐기하며 앱 복귀 때 과거 소리를 재생하지 않는다. 시스템 media volume과 DND를 우회하거나 audio focus를 독점하지 않는다. 원본 WAV는 repository의 결정론적 합성기로 생성하고 외부 음원을 다운로드하지 않는다.

기존 문서의 “새 sound asset을 요구하지 않는다”, “audio는 승인하지 않는다”, “네 top-level destination” 문구는 과거 Calendar Combat Feedback 범위 설명으로 한정하고 ADR-024가 후속 범위로 대체한다고 정리한다. Room v14 schema, migration, 피해·보상·spawn·중상 수식은 변경하지 않는다.

## Acceptance Criteria

```powershell
rg -n "ADR-024|Battle Sound Effects|BattleSfx|SoundPool|효과음|전투 불능|다섯 top-level|SharedPreferences" docs\PRD.md docs\ARCHITECTURE.md docs\ADR.md docs\UI_GUIDE.md docs\DEVELOPMENT.md docs\game-design\monster-stats-and-growth.md
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 모든 문서가 replay 없는 domain transition, 직렬 순서, 기본 켜짐 설정, application-scope audio 수명과 no-migration 경계를 동일하게 설명하는지 확인한다.
3. AGENTS.md의 CRITICAL 규칙과 충돌하지 않는지 확인한다.
4. task index의 step 0을 `completed`로 바꾸고 문서 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 효과음을 HP 값 변화에서 추론한다고 문서화하지 마라. 이유: 회전·재구성·회복에서 중복 재생될 수 있다.
- 중상 적용 또는 응급 회복에 revive 효과음을 승인하지 마라. 이유: 요청 범위는 확정된 전투 불능음 한 번뿐이다.
- Room schema나 전투 밸런스 변경을 승인하지 마라. 이유: 이번 기능은 transient presentation과 설정 확장이다.
- 외부 음원 다운로드를 승인하지 마라. 이유: 출처 불명확성과 재배포 위험을 제거해야 한다.
- 기존 테스트를 깨뜨리지 마라.
