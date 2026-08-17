# Step 6: verify-slime-battle-ui

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/calendar/CalendarDayIndicatorTest.kt`
- `/app/src/main/res/drawable-nodpi/todo_quest_slime_front_idle.png`
- `/docs/art/monster/todo-quest-slime-front-idle-spec.json`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

Compose instrumentation 테스트를 먼저 추가하고 구현 전 예상된 실패를 확인한 뒤 필요한 최소 UI fixture·연결만 수정한다. sprite Canvas, 공통 placement, HP와 effect layer의 기존 역할은 유지한다.

다음 시나리오를 검증한다.

- 슬라임 unit은 새 drawable로 렌더링되고 `슬라임, 체력 40/50` 한국어 접근성 정보와 불투명 외곽선 픽셀을 가진다.
- 슬라임의 공격·피격 안내에는 종족 이름이 사용되고 쓰러짐 live region은 `슬라임이 쓰러졌습니다.`를 제공한다.
- 기존 네 종족 이름과 쓰러짐 안내는 유지한다.
- 처치 후 spawn scene에서 슬라임으로 들어오거나 슬라임에서 다른 종족으로 나갈 때 새 drawable과 이름이 표시되고 이전 bitmap이 남지 않는다.
- Stage 1 NORMAL golden vector에서 encounter 5와 8이 슬라임으로 표현되는 end-to-end presentation 사례를 포함한다.
- 64×64 source frame, `FilterQuality.None`, integer nearest-neighbor 확대, 공통 발 anchor `(0.5,58/64)`, HP와 Battle Map 지면 그림자 placement는 변경되지 않는다.
- 실제 불투명 bounds는 `[17,35,47,58]`이고 player base body 높이의 약 46.2%로 보이며, 최종 렌더에 반투명 alpha fringe가 없어야 한다.
- 권한과 무관한 Calendar 일정 생성·완료·실패·보상 기능의 기존 Compose 테스트를 깨뜨리지 않는다.

검토 screenshot은 `app/build/verification/45-slime-enemy/` 아래에만 두고 source tree에 커밋하지 않는다. Android 도구 또는 연결 가능한 기기가 없으면 설치를 시도하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest
.\gradlew.bat :app:assembleDebug
git diff --check
```

현재 연결된 SM-A325N Android 13과 Pixel 9 Android 17에서 위 `BattleMapTest` class gate를 모두 실행한다. 전체 connected suite는 정확 알람 특별 접근을 테스트 설치 중 초기화하는 Gradle UTP 특성 때문에 이 step에서 중복 실행하지 않고, step 7의 보존된 UTP 결과와 승인된 direct instrumentation 결과를 합친 composite gate에서 검증한다.

## 검증 절차

1. 슬라임 렌더링·semantics·spawn 교체 테스트를 먼저 작성하고 필요한 fixture 연결 전 예상된 실패를 확인한다.
2. 필요한 최소 UI fixture와 test helper를 수정한 뒤 두 연결 장치에서 AC 명령을 실행한다.
3. 1배율 PNG와 Battle Map 확대 결과에서 안티앨리어싱·alpha fringe·발 anchor 이탈·잘못된 종족별 확대가 없는지 확인한다.
4. AGENTS.md의 UI state, 한국어 문자열 resource와 권한 실패 독립 규칙을 확인한다.
5. task index의 step 6을 `completed`로 변경하고 장치별 테스트 수를 포함한 UI·접근성·연결 테스트 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG에 HP, damage text, ground shadow, 물웅덩이 또는 effect를 합성하지 마라. 이유: Battle Map의 독립 layer 계약을 유지해야 한다.
- 슬라임을 크게 보이게 하려고 bitmap crop, 별도 unit scale 또는 보간을 추가하지 마라. 이유: 사용자가 요청한 플레이어 대비 40~50% 크기와 공통 frame 계약을 유지해야 한다.
- 종족 이름이나 쓰러짐 안내를 Compose 테스트 통과 목적으로 하드코딩하지 마라. 이유: strings resource와 visual catalog가 source of truth다.
- Android 도구나 emulator를 임의 설치하지 마라. 이유: AGENTS.md는 도구 부재 시 blocked 기록을 요구한다.
- 테스트보다 UI fixture를 먼저 수정하지 마라. 이유: AGENTS.md의 CRITICAL 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
