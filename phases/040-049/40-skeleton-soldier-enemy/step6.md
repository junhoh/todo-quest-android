# Step 6: verify-skeleton-battle-ui

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
- `/app/src/main/res/drawable-nodpi/todo_quest_skeleton_soldier_front_idle.png`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

Compose instrumentation test를 먼저 추가해 실패를 확인한 뒤 Battle Map의 쓰러짐 announcement가 target unit의 `deathAnnouncementResId`를 사용하도록 연결한다. sprite Canvas, placement, HP와 effect layer의 기존 역할은 유지한다.

다음 시나리오를 검증한다.

- 해골 unit은 새 drawable로 렌더링되고 `해골 병사, 체력 40/50` 접근성 설명과 불투명 외곽선 픽셀을 가진다.
- 해골의 공격·피격 안내에는 `해골 병사` 이름이 사용되고 쓰러짐 live region은 `해골 병사가 쓰러졌습니다.`를 제공한다.
- 고블린 쓰러짐 안내는 기존 한국어 문구를 유지한다.
- 처치 후 spawn scene에서 새 종족의 drawable과 이름이 표시되고 이전 종족 bitmap이 남지 않는다.
- 64×64 source frame, `FilterQuality.None`, integer nearest-neighbor 확대, 발 anchor와 지면 그림자 placement는 변경되지 않는다.
- 2·8번째 일반 attack encounter mapping은 해골이고 5번째 elite mapping은 고블린이라는 end-to-end presentation 사례를 포함한다.
- 권한과 무관한 Calendar 일정 생성·완료·실패·보상 기능의 기존 Compose 테스트를 깨뜨리지 않는다.

새 screenshot artifact가 필요하면 `app/build/verification/40-skeleton-soldier-enemy/` 아래에만 두고 source tree에 커밋하지 않는다. Android 도구 또는 연결 가능한 기기가 없으면 설치를 시도하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. 해골 렌더링·semantics 테스트를 먼저 작성하고 UI 연결 전 실패를 확인한다.
2. death announcement 연결과 필요한 test fixture를 수정한 뒤 AC 명령을 실행한다.
3. 실제 1배율 해골 PNG와 Battle Map 확대 결과에서 안티앨리어싱·알파 fringe·발 anchor 이탈이 없는지 확인한다.
4. AGENTS.md의 UI state, 한국어 문자열 resource와 권한 실패 독립 규칙을 확인한다.
5. task index의 step 6을 `completed`로 변경하고 UI·접근성·연결 테스트 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- death announcement를 monster type 하나로 하드코딩하지 마라. 이유: 고블린과 해골의 사용자 노출 문구가 달라야 한다.
- PNG에 HP, damage text, ground shadow 또는 effect를 합성하지 마라. 이유: Battle Map의 독립 layer 계약을 유지해야 한다.
- Android 도구나 emulator를 임의 설치하지 마라. 이유: AGENTS.md는 도구 부재 시 blocked 기록을 요구한다.
- 기존 테스트를 깨뜨리지 마라.
