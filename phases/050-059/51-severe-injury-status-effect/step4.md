# Step 4: sequence-status-effect-presentation

## 읽어야 할 파일

- /AGENTS.md
- /docs/UI_GUIDE.md
- /phases/050-059/51-severe-injury-status-effect/index.json
- /phases/050-059/51-severe-injury-status-effect/step3.md
- /app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt
- /app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt
- /app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt
- /app/src/main/java/com/todoquest/feature/character/CharacterViewModel.kt
- /app/src/main/java/com/todoquest/app/TodoQuestApp.kt
- /app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt
- /app/src/test/java/com/todoquest/feature/battle/BattleMapUiModelTest.kt
- /app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt
- /app/src/test/java/com/todoquest/feature/character/CharacterViewModelTest.kt

## 작업

테스트를 먼저 작성하고 일회성 도메인 이벤트를 회전/재진입에 안전한 presentation 상태 순서로 변환한다.

1. 기존 application-scope combat event Flow와 BattleAnimationController의 buffered channel/consumed-key 방식을 확장한다. 한 attack batch의 lifecycle event를 모두 순서대로 enqueue하고 event ID별로 한 번만 소비한다. replay=0 원칙을 유지해 화면 재진입이 과거 패배를 재생하지 않게 한다.
2. 연출 phase를 MONSTER_ATTACKING → PLAYER_HIT → PLAYER_DYING → PLAYER_DEFEATED → STATUS_EFFECT_APPLYING 또는 STATUS_EFFECT_REFRESHING → PLAYER_EMERGENCY_RECOVERING으로 구성한다. PLAYER_REVIVING과 완전 회복/부활 의미의 상태를 제거한다.
3. 쓰러짐 장면에서는 HP 0/기존 유효 최대 체력, 중상 적용 장면에서는 0/중상 유효 최대 체력, 응급 회복 뒤에는 recoveredHp/중상 유효 최대 체력 snapshot을 사용한다. 상태 제거 event는 짧은 회복 효과와 중상 회복 메시지를 한 번만 만들며 현재 HP를 완전 회복시키지 않는다.
4. Character/Calendar ViewModel UI state에 활성 상태이상, 남은 정상 완료 개수, 만료까지 남은 시간을 포함한다. AppClock을 주입해 진입/resume 때 만료 reconciliation을 실행하고 표시 남은 시간은 expiresAt-now의 올림 시간으로 계산한다. 1시간 미만 표시는 별도 한국어 문자열 상태로 표현할 수 있다.
5. 애니메이션이 비활성화된 환경에서도 전투 불능, 중상, 갱신/응급 회복 결과를 정적 presentation state로 확인할 수 있게 한다.
6. recomposition 자체는 event 소비나 Repository mutation을 트리거하지 않아야 하며, controller/ViewModel 재구독·화면 회전·화면 재진입 테스트로 반복되지 않음을 고정한다.

## Acceptance Criteria

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest" --tests "com.todoquest.feature.character.CharacterViewModelTest" --console=plain
git diff --check
~~~

## 검증 절차

1. lifecycle batch가 누락/덮어쓰기 없이 정확한 순서로 소비되는 실패 테스트부터 작성한다.
2. 같은 event ID 재전달, controller 재생성, 화면 재구독에서 패배와 응급 회복이 반복되지 않는지 확인한다.
3. status 제거 event가 한 번만 노출되고 currentHp를 바꾸지 않는지 확인한다.
4. now == expiresAt에서 해제되고 화면 복귀 시 DB 상태를 다시 reconcile하는지 확인한다.
5. AC를 실행하고 phase index의 step 4를 completed와 한국어 summary로 갱신한다.

## 금지사항

- HP가 0인지 Composable에서 감시해 도메인 처리를 시작하지 마라. 이유: UI lifecycle은 일회성 mutation의 권위가 아니다.
- 이벤트를 boolean flag나 단일 nullable 최신 이벤트로 축약하지 마라. 이유: 여러 lifecycle event가 덮어써진다.
- UI 타이머 종료만으로 DB의 중상을 해제하지 마라. 이유: 앱 재실행과 백그라운드 경과를 처리할 수 없다.
- 부활, 되살아남, PLAYER_REVIVING 표현을 남기지 마라.
- Preview state가 실제 Repository를 변경하게 하지 마라.
