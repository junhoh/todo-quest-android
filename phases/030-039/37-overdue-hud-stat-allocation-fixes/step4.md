# Step 4: refine-player-progress-hud-layout

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/test/java/com/todoquest/feature/battle/PlayerProgressHudTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/battle/BattleMapTest.kt`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

연결 Compose test의 bounds assertion을 먼저 새 요구에 맞게 작성해 현재 HUD가 좌측 level, 중간 gold, 우측 EXP의 세 균등 영역처럼 보이는 문제와 EXP bar 경계 회귀를 실패로 확인한다. 이후 `PlayerProgressHud`의 값 Row를 좌측 level zone과 우측 summary zone으로 재구성한다.

좌측에는 level만 두고 남는 공간이 우측 summary를 화면 끝으로 밀어내게 한다. 우측 summary는 `gold icon/value → 12dp gap → EXP label/value와 bar` 순서로 한 그룹을 이루며 HUD content 우측에 정렬한다. gold icon/value 사이와 EXP label/value 사이는 기존 정확한 3dp를 유지한다. EXP `LinearProgressIndicator`는 EXP label/value 결합 content의 intrinsic width만 사용하고 좌우 경계가 정확히 같아야 한다. level 또는 gold 영역 아래로 bar를 확장하지 않는다.

긴 숫자는 기존 한국어 compact formatting을 유지한다. 320dp 폭과 font scale 2.0에서 level, gold, EXP가 서로 겹치거나 HUD 밖으로 나가지 않아야 하며 필요한 경우 gold/EXP text의 현재 ellipsis 경계를 사용한다. HUD 전체 한국어 TalkBack description, decorative gold icon, loading과 progress clamp, Material theme token을 유지한다. 새 사용자 표시 문구가 필요하면 `strings.xml`의 한국어 resource로 추가한다.

Compose test에는 새 우측 summary tag를 추가해 level과 summary 비중첩, summary의 HUD 우측 정렬, gold/EXP 12dp 간격, 두 3dp 내부 간격, EXP content와 bar 경계 일치를 실제 bounds로 검증한다. standard/compact map과 actor/HP 영역 비침범 회귀도 유지한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.PlayerProgressHudTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.battle.BattleMapTest" --console=plain
git diff --check
```

## 검증 절차

1. 새 bounds 계약의 Compose test를 production UI보다 먼저 작성하고 연결 기기에서 실패를 확인한다.
2. AC를 실행해 320dp·font scale 2.0, standard·compact map과 큰 숫자 case를 확인한다.
3. 한국어 semantics와 Material theme token, 기존 actor/HP/effect layer 분리를 확인한다.
4. 연결 기기나 emulator가 없으면 도구를 설치하지 말고 step을 `blocked`로 기록하며 `blocked_reason`에 필요한 기기 조건을 적는다.
5. 성공하면 task index의 step 4를 `completed`로 바꾸고 HUD 구획과 EXP 경계를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- progress bar를 level 또는 gold 아래까지 확장하지 마라. 이유: EXP label/value의 가로 길이 안에 제한해야 한다.
- 긴 숫자를 숨기기 위해 exact TalkBack 값을 compact 값으로 바꾸지 마라. 이유: 시각적 축약과 접근성 원본 값 계약을 분리해야 한다.
- HUD 색상을 stage별 하드코딩 값으로 바꾸지 마라. 이유: Material theme과 dark UI 일관성을 유지해야 한다.
- Android emulator나 SDK 패키지를 임의 설치하지 마라. 이유: 개발 도구 변경은 별도 승인 phase에서만 허용된다.
- 기존 테스트를 깨뜨리지 마라.
