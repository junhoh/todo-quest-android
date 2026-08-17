# Step 3: synchronize-and-verify-compendium-redesign

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/feature/compendium/`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/app/src/test/java/com/todoquest/feature/compendium/MonsterCompendiumViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/compendium/CompendiumScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/050-059/54-monster-compendium-menu/index.json`
- `/phases/060-069/60-monster-compendium-redesign/step2.md`
- `/phases/060-069/60-monster-compendium-redesign/index.json`

## 작업

canonical 문서를 실제 구현과 동기화하고 전체 회귀 검증을 실행한다.

`docs/PRD.md`와 `docs/UI_GUIDE.md`의 기존 `미발견 실제 이름 상시 표시`, `silhouette/lock 금지`, `비클릭`, `발견 card 즉시 상세 route` 계약을 새 privacy-preserving 수집 UI로 교체한다. 새 계약은 미발견 `???`·공통 `?`/lock·실제 sprite/name 비노출·click 안내, 발견 count/percent, 앱바 검색, 전체/발견/미발견 filter, 선택 preview, 3~5열 grid와 ModalBottomSheet를 기록한다. pet 재화·장착·보너스·농장·맵, 새 몬스터나 새 art는 범위 밖임을 유지한다.

`docs/ADR.md`에는 다음 ADR 번호로 Monster Compendium presentation v2 결정을 추가한다. ADR-021의 Room v14 전투 이력 기반 발견 projection, 기존 사용자 소급, balance version routing과 무 migration 결정은 그대로 유지하고, ADR-021의 name-only locked presentation 부분만 새 ADR이 대체함을 명시한다. 미발견 UI model에서 name/sprite resource 자체를 제거한 privacy 이유, resource resolver 기반 발견 이름 검색, 선택/filter/search의 비영속 ViewModel 상태, bottom sheet 기본과 legacy detail route 호환 tradeoff를 기록한다.

`docs/ARCHITECTURE.md`에는 다음 흐름을 반영한다.

```text
Room v14 monster history
→ CombatRepository.observeDiscoveredMonsterSpecies()
→ MonsterCompendiumViewModel privacy-preserving projection
→ count + search/filter + selection state
→ collection summary + preview + adaptive grid + detail sheet
```

Compose가 DAO를 직접 호출하지 않고, grade/type/region을 종족 metadata로 만들지 않으며, detail 호환 route의 locked state도 resource를 노출하지 않음을 기록한다. navigation은 four top-level destination과 Compendium root/list/detail 호환 route를 유지한다.

`docs/DEVELOPMENT.md`에는 phase 60의 실제 구현과 검증 경계를 기록한다. Room database version, schema exports, migrations, `CombatRepository`, `MonsterDiscoveryPolicy`, five canonical 64×64 PNG와 bottom navigation이 변경되지 않았음을 명시하고 실제 실행한 JVM/connected test 수와 명령 결과만 기록한다.

전체 JVM unit/Robolectric, lint, debug assemble, connected Android test와 harness pytest를 실행한다. connected device, Android SDK나 필요한 기존 개발 도구가 없으면 임의 설치하지 말고 step과 phase를 `blocked`로 기록한다. test failure 또는 skip을 성공으로 기록하지 않는다. 표준 화면과 320dp/font scale 2.0 Preview를 최종 검토하고 다음을 확인한다.

- 3열 첫 화면 밀도와 4~5열 wide behavior
- count/percent와 filter/search 결과 일치
- selected preview/card와 detail sheet
- 미발견 actual name/sprite 부재와 lock 안내
- TopAppBar/bottom navigation 고정, last card bounds와 back 순서
- pixel art의 Fit, nearest-neighbor와 original aspect ratio

성공 후 step 3과 phase index를 `completed`로 갱신하고 phase summary에는 변경 파일, state/UI/navigation/privacy 결정과 실제 test 결과를 한국어로 기록한다. manager `Sync`로 root index와 README를 갱신한 뒤 `Validate`와 `Sync -Check`를 통과시킨다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. PRD, Architecture, 새 ADR, UI Guide와 DEVELOPMENT를 실제 구현에 맞춰 동기화한다.
2. 전체 AC를 실행하고 실패·오류·skip 또는 미실행 명령을 성공으로 기록하지 않는다.
3. AGENTS.md의 UI/Repository 경계, occurrence 보상 멱등성, 반복 일정 원본/발생분 분리, 로컬 저장과 권한 거부 독립성이 변경되지 않았는지 확인한다.
4. standard와 small/large-font UI, locked privacy, sheet/back 및 bottom inset을 최종 검토한다.
5. step 3과 phase를 `completed`로 갱신한 뒤 `Sync`, `Validate`, `Sync -Check`를 실행해 registry drift가 없는지 확인한다.

## 금지사항

- 수행하지 못한 connected test나 Preview 검토를 성공으로 기록하지 마라. 이유: UI redesign 완료에는 실제 layout 검증 근거가 필요하다.
- Room migration, 새 discovery ledger, 종족 metadata나 새 asset을 구현했다고 문서화하지 마라. 이유: 이번 phase 범위가 아니다.
- ADR-021의 발견 source와 소급 projection 결정을 폐기하지 마라. 이유: presentation privacy만 새 ADR로 대체한다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows/Codex 표준 진입점은 `run_harness.ps1`이다.
- 관련 없는 화면이나 기능을 수정하지 마라.
- 기존 테스트를 깨뜨리지 마라.
