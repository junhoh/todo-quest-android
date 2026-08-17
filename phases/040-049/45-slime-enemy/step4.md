# Step 4: map-slime-battle-presentation

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
- `/app/src/main/res/drawable-nodpi/todo_quest_slime_front_idle.png`
- `/app/src/main/res/values/strings.xml`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

presentation 단위 테스트를 먼저 작성해 step 2의 compile-only `SLIME` error 분기에서 예상대로 실패하는 것을 확인한 뒤 기존 종족 기반 visual catalog를 다섯 종족으로 완성한다. UI가 Stage, encounter, grade 또는 seed를 직접 해석하지 않게 한다.

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

- step 2의 compile-only `SLIME -> error(...)`를 제거한다.
- `SLIME`은 `todo_quest_slime_front_idle`, `battle_monster_slime_name`, `battle_monster_slime_death_announcement`을 사용한다.
- 기존 고블린·해골·나무 정령·하피 매핑은 변경하지 않는다.
- `BattlePresentationMapper.mapSnapshot`과 `BattleAnimationController`는 `CombatSnapshot.activeMonsterSpecies`를 catalog로 변환하는 기존 흐름을 유지한다.
- player 치명 공격 transition은 `before` 종족을 death scene에, `after` 종족을 spawn alert·spawn scene에 사용한다. 다섯 종족의 25개 before/after 조합에서 이름·drawable·쓰러짐 안내가 섞이지 않아야 한다.
- monster attack과 nonlethal transition은 각 다섯 종족의 동일 source visual을 유지한다.
- `BattleMapDefaults.MONSTER_FRAME`의 source `(0,0,64,64)`와 ground anchor `(0.5f,58/64f)`, 공통 unit scale을 유지한다. 슬라임의 작은 크기를 보정하려고 종족별 crop이나 확대를 추가하지 않는다.
- Preview/sample에는 슬라임 단일 사례를 추가하되 새로운 이미지나 animation frame을 만들지 않는다.

Repository는 아직 호환 2-인자 정책 API를 사용하므로 production snapshot 종족 순서는 이 step에서 바꾸지 않는다. 새 스케줄 연결은 step 5가 담당한다.

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --tests "com.todoquest.feature.battle.BattleAnimationControllerTest" --tests "com.todoquest.feature.calendar.CalendarViewModelTest"
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. catalog와 transition mapping 테스트를 먼저 작성하고 compile-only 분기에서 예상된 실패를 확인한다.
2. catalog, controller test fixture와 Preview를 수정하고 AC 명령을 실행한다.
3. `CalendarViewModel`이 Stage/encounter를 해석하지 않고 snapshot을 mapper에 전달하는 기존 경계를 확인한다.
4. player layered renderer와 monster single-resource renderer가 분리되어 있는지 확인한다.
5. task index의 step 4를 `completed`로 변경하고 다섯 종족 catalog, 치명 transition 조합과 공통 frame 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Stage, encounter, grade 또는 random seed 해석을 Compose나 ViewModel에 넣지 마라. 이유: 도메인 스케줄과 presentation resource mapping을 분리해야 한다.
- monster renderer를 layered player renderer로 변경하지 마라. 이유: 몬스터는 독립 single-sprite 계약을 유지한다.
- 슬라임만 source frame을 crop하거나 unit scale을 키우지 마라. 이유: 64×64 공통 원점과 플레이어 대비 46.2% 크기를 유지해야 한다.
- 처치 scene 전체에 하나의 종족 visual을 재사용하지 마라. 이유: outgoing death와 incoming spawn이 서로 다른 종족일 수 있다.
- 테스트보다 구현을 먼저 작성하지 마라. 이유: AGENTS.md의 CRITICAL 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
