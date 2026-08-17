# Step 2: implement-monster-compendium-screen

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/README.md`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMapUiModel.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/battle/BattleMapUiModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/54-monster-compendium-menu/step0.md`
- `/phases/050-059/54-monster-compendium-menu/step1.md`
- `/phases/050-059/54-monster-compendium-menu/index.json`

## 작업

ViewModel 및 Compose 테스트를 먼저 작성해 실패를 확인한 뒤 공용 몬스터 visual catalog와 도감 화면을 구현한다.

`app/src/main/java/com/todoquest/ui/monster/`에 Android resource를 domain에서 분리한 공용 presentation 계약을 둔다.

```kotlin
data class MonsterVisual(
    @DrawableRes val spriteResId: Int,
    @StringRes val nameResId: Int,
)

object MonsterVisualCatalog {
    fun forSpecies(species: MonsterSpecies): MonsterVisual
}
```

기존 5종 sprite와 한국어 이름 mapping을 이 catalog의 단일 원천으로 옮기고, `BattleMonsterVisualCatalog`는 공용 sprite/name에 전투 전용 death announcement만 결합해 기존 공개 결과와 테스트를 보존한다.

`app/src/main/java/com/todoquest/feature/compendium/`에 명시적 5종 순서의 `MonsterCompendiumCatalog`, UI state, ViewModel과 화면을 추가한다. UI entry는 다음처럼 발견 상태에 따라 데이터 자체를 분리한다.

```kotlin
sealed interface MonsterCompendiumEntryUiModel {
    val species: MonsterSpecies
    @get:StringRes val nameResId: Int

    data class Undiscovered(/* species, nameResId only */) : MonsterCompendiumEntryUiModel
    data class Discovered(
        /* species, nameResId, spriteResId, descriptionResId */
    ) : MonsterCompendiumEntryUiModel
}
```

`MonsterCompendiumViewModel(CombatRepository)`는 `observeDiscoveredMonsterSpecies()`를 관찰해 `Loading`, `Content`, `Error` state를 제공하고 retry event로 collection을 다시 시작한다. `MonsterDetailViewModel(CombatRepository, MonsterSpecies)`는 `Loading`, `Discovered`, `Locked`, `Error`를 제공해 route를 직접 구성해도 미발견 sprite/설명이 노출되지 않게 한다. ViewModel과 Compose에 사용자 노출 영문을 하드코딩하지 않는다.

화면 계약은 다음과 같다.

- `CompendiumScreen(onOpenMonsters)`는 `도감` 제목과 작동하는 `몬스터` category 하나만 표시한다.
- `MonsterCompendiumScreen`은 adaptive grid에 고블린 정찰병, 해골 병사, 타락한 나무 정령, 하피, 슬라임 순으로 5종 이름을 항상 표시한다.
- 미발견 entry는 시각적으로 이름만 표시한다. sprite, silhouette, lock icon, 설명 조각, click action을 만들지 않는다. TalkBack semantics에는 해당 이름과 미발견 상태를 함께 알린다.
- 발견 entry는 이름과 `FilterQuality.None` sprite를 표시하고 최소 48dp target으로 상세를 연다.
- `MonsterDetailScreen`은 발견한 종의 이름, 화면에 맞춘 큰 최근접 보간 sprite와 한국어 설명만 표시한다. 능력치, 최초 조우, 조우 횟수, 처치 횟수는 표시하지 않는다.
- 폭 320dp와 font scale 2.0에서도 이름, back action과 grid가 잘리거나 겹치지 않게 한다.

`strings.xml`에 도감 제목, category, loading/error/retry, 미발견 TalkBack과 다음 설명을 정확히 한국어 기본 리소스로 추가한다.

- 고블린 정찰병: `뾰족한 귀와 붉은 눈, 낡은 장비와 단검이 특징인 정찰병입니다.`
- 해골 병사: `큰 해골과 낡은 투구, 검과 붉은 눈이 특징인 해골 병사입니다.`
- 타락한 나무 정령: `왕관처럼 뻗은 가지와 뿌리발, 부패 균열과 검은 수액이 특징인 타락한 나무 정령입니다.`
- 하피: `좌우로 펼친 날개와 짧은 다리, 날카로운 발톱과 붉은 눈이 특징인 하피입니다.`
- 슬라임: `낮은 물방울 모양의 몸과 붉은 눈, 몸속 핵과 표면 하이라이트가 특징인 슬라임입니다.`

## Acceptance Criteria

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.todoquest.feature.compendium.*" --tests "com.todoquest.feature.battle.BattleMapUiModelTest" --console=plain
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.feature.compendium.CompendiumScreenTest" --console=plain
git diff --check
```

## 검증 절차

1. catalog/ViewModel unit test와 Compose connected test를 먼저 추가해 구현 전 실패를 확인한다.
2. 발견·미발견 모델이 resource 노출 수준에서 분리되는지 검증한다.
3. 미발견 card에 Image node와 click action이 없고 발견 card만 상세 event를 내는지 검증한다.
4. 일반 폭과 320dp/font scale 2.0에서 한국어·48dp target·TalkBack semantics를 검증한다.
5. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. task index의 step 2를 `completed`로 바꾸고 공용 visual catalog와 도감 화면 산출물을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 미발견 entry 모델이나 UI에 sprite 또는 description resource를 넣지 마라. 이유: 이름-only 발견 계약을 구조적으로 보장해야 한다.
- 미발견 종에 silhouette나 잠금 이미지를 노출하지 마라. 이유: 사용자는 미발견 시 이름만 보이도록 승인했다.
- 종족별 전투 능력치나 biome, 스킬, 전리품을 설명에 추가하지 마라. 이유: 종족 presentation metadata와 전투 계산 계약은 분리되어 있다.
- 새 bitmap을 생성하거나 기존 monster PNG를 수정하지 마라. 이유: 검증된 canonical/runtime 자산을 재사용해야 한다.
- Compose 화면에서 DAO를 직접 호출하지 마라. 이유: AGENTS.md의 Repository 경계를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
