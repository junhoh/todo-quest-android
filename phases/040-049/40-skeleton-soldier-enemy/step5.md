# Step 5: map-skeleton-battle-presentation

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/model/Combat.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleAnimationController.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapPreview.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/main/res/drawable-nodpi/todo_quest_skeleton_soldier_front_idle.png`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

presentation unit test를 먼저 작성해 species별 resource·이름·쓰러짐 안내와 transition 전후 매핑 실패를 확인한 뒤 종족 기반 visual catalog를 구현한다. UI가 definition id 문자열을 직접 해석하지 않게 한다.

다음 인터페이스를 `feature/battle`에 둔다. 가시성은 현재 presentation 테스트에 필요한 최소 수준으로 제한한다.

```kotlin
@Immutable
data class BattleMonsterVisual(
    @DrawableRes val spriteResId: Int,
    @StringRes val nameResId: Int,
    @StringRes val deathAnnouncementResId: Int,
)

object BattleMonsterVisualCatalog {
    fun forSpecies(species: MonsterSpecies): BattleMonsterVisual
}
```

- `GOBLIN_SCOUT`는 기존 고블린 drawable·이름·쓰러짐 안내를 사용한다.
- `SKELETON_SOLDIER`는 새 해골 drawable·한국어 이름·해골 쓰러짐 안내를 사용한다.
- `BattleUnitUiModel`에 `@StringRes deathAnnouncementResId`를 추가하고 player에는 기존 player 쓰러짐 안내를 지정한다.
- `BattleMapDefaults.GOBLIN_FRAME`을 두 종족이 공유하는 `MONSTER_FRAME`으로 일반화한다. source `(0,0,64,64)`, ground anchor `(0.5f,58/64f)`와 최근접 보간 계약은 유지한다.
- `BattlePresentationMapper.mapSnapshot`은 `CombatSnapshot.activeMonsterSpecies`를 catalog로 변환한다.
- player attack 처치 transition은 `before`의 종족을 death scene에, `after`의 종족을 spawn alert·spawn scene에 각각 사용한다. 종족이 바뀌어도 이름·drawable·쓰러짐 안내가 섞이지 않아야 한다.
- monster attack transition은 동일 source 종족을 before/after에 유지한다.
- Preview는 기존 고블린 사례를 유지하고 해골 단일 preview를 추가해도 되지만 추가 아트나 여러 프레임을 생성하지 않는다.

테스트는 normal attack 해골 snapshot, elite attack 고블린 snapshot, 고블린→해골과 해골→고블린 lethal transition, nonlethal·monster attack 유지 사례를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. presentation mapping 테스트를 먼저 작성하고 구현 전 실패를 확인한다.
2. visual catalog, UI model과 transition mapping을 구현하고 AC 명령을 실행한다.
3. `CalendarViewModel`이 resource를 직접 고르지 않고 snapshot을 mapper에 전달하는 기존 경계를 유지하는지 확인한다.
4. player layered renderer와 monster single-resource renderer가 분리되어 있는지 확인한다.
5. task index의 step 5를 `completed`로 변경하고 catalog와 transition 매핑 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `definitionId` 문자열 비교를 Compose나 ViewModel에 넣지 마라. 이유: 도메인 종족 정책과 presentation resource mapping을 분리해야 한다.
- monster renderer를 layered player renderer로 변경하지 마라. 이유: 몬스터는 독립 single-sprite 계약을 유지한다.
- 처치 scene 전체에 하나의 종족 visual을 재사용하지 마라. 이유: outgoing death와 incoming spawn이 서로 다른 종족일 수 있다.
- 기존 테스트를 깨뜨리지 마라.
