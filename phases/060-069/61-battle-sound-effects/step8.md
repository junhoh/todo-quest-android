# Step 8: verify-battle-sfx-regressions

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/audio/README.md`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/audio/BattleSfxPlayer.kt`
- `/app/src/main/java/com/todoquest/audio/AndroidBattleSfxPlayer.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/java/com/todoquest/feature/settings/SettingsScreen.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApplication.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/scripts/build_battle_sfx.py`
- `/phases/060-069/61-battle-sound-effects/step0.md`
- `/phases/060-069/61-battle-sound-effects/step1.md`
- `/phases/060-069/61-battle-sound-effects/step2.md`
- `/phases/060-069/61-battle-sound-effects/step3.md`
- `/phases/060-069/61-battle-sound-effects/step4.md`
- `/phases/060-069/61-battle-sound-effects/step5.md`
- `/phases/060-069/61-battle-sound-effects/step6.md`
- `/phases/060-069/61-battle-sound-effects/step7.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

구현을 새로 확장하지 말고 Battle Sound Effects v1의 전체 acceptance와 문서/코드 동기화를 마무리한다. 빠진 요구사항이나 test가 있으면 해당 최소 layer에서 test를 먼저 추가한 뒤 수정한다.

필수 검증 matrix는 다음을 모두 포함해야 한다.

- player attack 한 번, monster hit only, lethal `MONSTER_HIT → MONSTER_DEFEATED`, nonlethal defeat 없음, overkill defeat 한 번.
- monster attack 한 번, player hit only, lethal `PLAYER_HIT → PLAYER_DEFEATED`, 생존 시 defeat 없음.
- 중상 적용/갱신 및 응급 회복에서 추가 death/revive 없음, 중상 중 새 defeat는 새 event당 한 번.
- 동일 `eventId + effect` 중복 없음, 서로 다른 event id의 빠른 연속 공격은 모두 재생.
- controller 재구독, Activity 회전, Calendar 재진입, 다음 monster spawn에서 과거 음향 replay 없음.
- 설정 off에서 delegate request 없음, on으로 바꾼 뒤 새 event만 재생, repository 재생성 뒤 설정 유지.
- SoundPool load 전·load 실패·play 실패·released/background 상태가 combat timeline, DB result와 app 생명주기에 영향 없음.
- 기존 피해 계산, occurrence reward 멱등성, kill reward, next monster/Stage, severe injury revision·50% emergency recovery와 status removal 회귀 없음.

`docs/DEVELOPMENT.md`에 실제 최종 test 수, 사용한 JDK/SDK/device, audio validator 결과와 Room v14/no migration 보존을 기록한다. connected device가 없거나 필수 Android 도구가 없으면 설치를 시도하지 말고 이 step을 `blocked`로 기록한다. 연결 기기에서는 시스템 media volume, DND, notification permission과 사용자 설정을 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_build_battle_sfx.py scripts/test_execute.py --basetemp .\build\pytest-battle-sfx-final
.\.venv\Scripts\python.exe scripts/build_battle_sfx.py --check
.\gradlew.bat test lint assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. AC 명령을 순서대로 실행하고 failure/skip 없이 통과시킨다.
2. AGENTS.md CRITICAL 규칙, ADR-024, 한국어 strings, no Room migration, replay 없는 transition과 audio failure isolation을 점검한다.
3. WAV 여섯 개의 format·duration·hash와 resource mapping을 다시 확인한다.
4. task index의 step 8을 `completed`로 바꾸고 전체 test 수·도구·기기·핵심 불변 범위를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- test 실패를 skip 또는 assertion 완화로 숨기지 마라. 이유: 효과음 순서와 기존 전투 회귀가 acceptance 핵심이다.
- Android 도구나 AVD를 임의 설치하지 마라. 이유: AGENTS.md의 구현 phase 도구 정책을 위반한다.
- 시스템 media volume, DND 또는 사용자 권한을 변경하지 마라. 이유: 사용자 환경을 보존해야 한다.
- phase 전체 구현을 한 커밋으로 squash하지 마라. 이유: harness는 step 단위 단일 커밋과 summary를 계약으로 사용한다.
- 기존 테스트를 깨뜨리지 마라.
