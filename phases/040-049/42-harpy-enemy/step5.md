# Step 5: map-harpy-battle-presentation

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
- `/app/src/test/java/com/todoquest/feature/battle/BattleMapUiModelTest.kt`
- `/app/src/test/java/com/todoquest/feature/battle/BattleAnimationControllerTest.kt`
- `/app/src/test/java/com/todoquest/feature/calendar/CalendarViewModelTest.kt`
- `/app/src/main/res/drawable-nodpi/todo_quest_harpy_front_idle.png`
- `/phases/040-049/42-harpy-enemy/index.json`

## 작업

presentation 단위 테스트를 먼저 작성하고 구현 전 예상된 실패를 확인한 뒤 기존 종족 기반 visual catalog를 네 종족으로 확장한다. UI가 definition id, type 또는 grade를 직접 해석하지 않게 한다.

기존 공개 인터페이스를 유지한다.

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

- `HARPY`는 `todo_quest_harpy_front_idle`, `battle_monster_harpy_name`, `battle_monster_harpy_death_announcement`을 사용한다.
- 기존 고블린·해골·나무 정령 매핑은 변경하지 않는다.
- `BattlePresentationMapper.mapSnapshot`은 `CombatSnapshot.activeMonsterSpecies`를 catalog로 변환하는 흐름을 유지한다.
- player 치명 공격 transition은 `before` 종족을 death scene에, `after` 종족을 spawn alert·spawn scene에 사용한다. 하피가 outgoing 또는 incoming일 때 이름·drawable·쓰러짐 안내가 섞이지 않아야 한다.
- monster attack과 nonlethal transition은 동일 source 종족을 유지한다.
- `BattleMapDefaults.MONSTER_FRAME`의 source `(0,0,64,64)`와 ground anchor `(0.5f,58/64f)`를 유지한다.
- Preview/sample에는 하피 단일 사례를 추가하되 새 이미지나 animation frame을 만들지 않는다.

테스트는 catalog 매핑, DEFENSE+NORMAL snapshot, Stage 1 encounter 3의 하피, 하피→나무 정령·하피→해골·나무 정령/해골→하피 치명 transition, nonlethal·monster attack 유지 사례를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. presentation mapping 테스트를 먼저 작성하고 catalog 수정 전 예상된 실패를 확인한다.
2. catalog, mapper와 Preview fixture를 수정하고 AC 명령을 실행한다.
3. `CalendarViewModel`이 resource를 직접 고르지 않고 snapshot을 mapper에 전달하는 기존 경계를 확인한다.
4. player layered renderer와 monster single-resource renderer가 분리되어 있는지 확인한다.
5. task index의 step 5를 `completed`로 변경하고 네 종족 catalog와 transition 매핑 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- `definitionId`, type 또는 grade 비교를 Compose나 ViewModel에 넣지 마라. 이유: 도메인 종족 정책과 presentation mapping을 분리해야 한다.
- monster renderer를 layered player renderer로 변경하지 마라. 이유: 몬스터는 독립 single-sprite 계약을 유지한다.
- 치명 scene 전체에 하나의 종족 visual을 재사용하지 마라. 이유: outgoing death와 incoming spawn이 서로 다른 종족일 수 있다.
- 날갯짓 animation frame을 만들지 마라. 이유: 이번 결과물은 정면 기본 대기 프레임 한 장이다.
- 기존 테스트를 깨뜨리지 마라.
