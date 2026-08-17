# 개발 환경

## 문서 작업과 링크 검증

문서 작업의 진입점과 문서별 책임은 [문서 인덱스](README.md)에서 확인한다.

- Markdown 링크는 현재 파일 기준 상대 경로와 `/`를 사용한다.
- 디렉터리의 역할과 탐색 안내는 해당 `README.md`를 연결한다.
- 좌표, 팔레트, 레이어처럼 기계가 검증하는 계약은 canonical JSON을 직접 연결한다.

별도 외부 패키지 없이 저장소 루트에서 전체 `docs/**/*.md`의 로컬 링크를 검증한다.

```powershell
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
```

## Android 개발 도구

Todo Quest 구현 phase는 Windows 기준으로 다음 도구가 설치되어 있다고 가정한다.

- Android Studio 최신 안정 버전
- JDK 17
- Android SDK와 Platform Tools
- Gradle wrapper
- Android emulator 또는 USB 디버깅이 활성화된 실제 기기

현재 저장소에서 도구 설치를 자동화하지 않는다. 구현 step에서 `java`, Android SDK, emulator, `adb`가 없으면 임의 설치를 시도하지 말고 해당 step을 blocked로 기록한다.

예외적으로 별도 개발 도구 준비 phase에서 사용자가 명시 승인한 경우에만 설치와 사용자 환경 변수 변경을 수행한다. 설치 시 기존 JDK, Android Studio, Android SDK 패키지를 제거하거나 다운그레이드하지 않고 side-by-side 구성을 우선한다.

## 현재 로컬 도구 상태

`phases/000-009/1-dev-tools` 완료 기준으로 현재 로컬 Windows 환경은 다음 도구를 사용한다.

- JDK: Temurin JDK 17.0.19.10
- Android Studio: 2026.1.1.10
- Android SDK: `$env:LOCALAPPDATA\Android\Sdk`
- Android SDK 패키지: `cmdline-tools;latest`, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0`
- 사용자 환경 변수: `ANDROID_HOME`과 `ANDROID_SDK_ROOT`는 SDK 경로를 가리키며, User `Path`에는 `platform-tools`가 포함된다.

이미 열려 있던 터미널은 사용자 환경 변수 변경을 자동으로 반영하지 않을 수 있다. 도구 확인은 새 터미널에서 실행한다.

## Windows 환경 변수

Android Studio 설치 후 다음 값이 올바른지 확인한다.

```powershell
java -version
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
Get-Command adb -ErrorAction SilentlyContinue
```

`ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`는 일반적으로 다음 SDK 위치 중 하나를 가리킨다.

```text
C:\Users\<사용자>\AppData\Local\Android\Sdk
```

## Android 프로젝트 검증 명령

Android 프로젝트가 생성된 뒤 모든 구현 step은 가능한 범위에서 Gradle wrapper로 검증한다.

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
```

기기 또는 emulator가 필요한 검증은 환경이 준비된 경우에만 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

### Phase 50 notification·economy 구현과 검증 경계

phase 50은 ADR-017·ADR-018의 계약을 테스트 우선으로 구현했다. application-scope preference store와 UseCase가 첫 Calendar notification 안내를 한 번만 준비하고, Calendar의 origin별 한국어 dialog와 replay 없는 event가 runtime permission·package settings·exact settings 진입을 분리한다. Combat Reward는 versioned v1·v2 catalog와 저장된 attack version으로 라우팅하고, 18종 장비는 fresh 승인 가격과 canonical identity·legacy price 조건부 갱신을 사용한다. Battle HUD의 EXP group은 최소 `104dp`와 intrinsic content 폭을 함께 사용한다. 이 phase는 기존 Room v12 entity·schema export, database version과 `MIGRATION_1_2`부터 `MIGRATION_11_12`까지의 chain을 변경하지 않는다.

| 검증 대상 | unit/Robolectric test | 연결 test |
|---|---|---|
| 첫 Calendar notification one-shot | `FirstLaunchNotificationPermissionUseCaseTest`가 최초 확인 단일 소비와 capability 실패 격리를, `NotificationPermissionEntryPointsTest`가 SharedPreferences 영속과 API별 runtime/settings/no-op typed action을 검증 | `CalendarScreenTest`, `AppNavigationTest`가 한국어 최초 안내, 거부·dismiss, Activity 재생성·탭 복원 뒤 비재생을 검증 |
| reminder settings CTA·exact 순서 | `CalendarViewModelTest`가 `FIRST_LAUNCH`/`REMINDER` origin, `RequestPostNotificationsPermission`/`OpenNotificationSettings`, settings 복귀 뒤 exact rationale 순서를 검증 | `CalendarScreenTest`가 `알림 설정` CTA와 exact settings 진입, 일정 저장 성공·권한 실패 독립성을 검증 |
| Combat Reward v0/v1/v2 | `CombatRewardPolicyTest`가 v1 `1/10/5`, v2 `3/20/15`, level 1 NORMAL `23 XP / 15골드`, level 55 BOSS `128 XP / 80골드`와 exact arithmetic을 검증하고 `RoomTaskRepositoryTest`·`RoomCombatRepositoryTest`가 새 v2 snapshot, v0 무보상, PENDING v1·v2, APPLIED 불변·미지원 version rollback을 검증 | 기존 `BattleMapTest`가 저장된 실제 보상의 replay 없는 `600ms` badge와 HUD 반영을 검증 |
| 18종 가격 완화 | `EquipmentDaoTest`·`RoomEquipmentRepositoryTest`가 fresh 승인 가격, id·name key·type·slot·old price 조건부 update와 custom row·소유·장착·gold 보존을 검증 | `ShopScreenTest`가 목록·상세·구매 확인·부족 안내·성공 잔액의 Repository 단일 가격 원천을 검증 |
| EXP HUD 최소 폭 | `PlayerProgressHudTest`가 진행률·숫자 formatting 경계를 검증 | `BattleMapTest`가 `max(104dp, intrinsic content width)`, `0/100` track·outline, 큰 글꼴·actor/map 경계 불변을 검증 |

전체 acceptance는 다음 순서로 실행한다.

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

2026-08-05 phase 50 최종 검증은 첫 Calendar notification one-shot 안내와 runtime/settings 분기, reminder 저장 뒤 exact 권한 순서, Combat Reward v2의 `3/20/15` 공식과 v0·v1·v2 snapshot 호환, 18종 조건부 가격 완화 및 최소 `104dp` EXP HUD를 함께 대상으로 했다. debug·release JVM/Robolectric test 각 485개, `lint`, debug 앱·androidTest APK와 Pixel 9 Android 17(API 37) AVD의 일반 connected test 118개, harness pytest 88개를 실패·오류·skip 없이 통과했다. 일반 connected discovery는 notification·exact-alarm capability를 사용자가 미리 허용해야 하는 `ExternalReminderSmokeFixture`를 선택하지 않으며, process-absent smoke는 기존 명시적 class 실행 경계로만 유지한다. Windows에서 기존 `.venv/pytest-tmp`의 외부 ACL 때문에 첫 pytest setup이 시작되지 못해 해당 폴더를 삭제하거나 권한을 변경하지 않고 새 `.venv/pytest-tmp-step10-final`을 사용했으며, 88개 결과에는 영향이 없는 `.pytest_cache` 쓰기 warning 1건만 남았다. Room database version, v12 identity schema와 `MIGRATION_1_2`부터 `MIGRATION_11_12`까지의 chain은 변경하지 않았다.

### Phase 51 Severe Injury v1 구현·검증 경계

phase 51은 Room v13의 일반화된 상태이상 원천과 revision별 occurrence 회복 ledger, 중상 v1의 유효 `MAX_HP`·`ATTACK` 20% 감소, `0 HP 전투 불능 → 중상 적용/갱신 → 유효 최대 체력 50% 응급 회복`, 24시간 또는 서로 다른 완료 3회의 회복을 테스트 우선으로 구현했다. `RoomStatusEffectRepository`가 `AppClock` 기반 활성 상태 관찰·만료·제거를 소유하고, 일정 완료 transaction은 `(characterId, effectType, revision, taskId, occurrenceDateEpochDay)` credit key와 세 번째 완료의 복원 공격 snapshot을 함께 확정한다. Character와 Calendar는 ViewModel state로 중상 배지·상세 dialog와 한국어 TalkBack/live region을 렌더링하며 DAO를 직접 호출하지 않는다.

요구사항별 검증 위치는 다음과 같다.

| 요구사항 | unit/Robolectric test | 연결 test |
|---|---|---|
| HP 0/음수 clamp, 중상 20% 유효 스탯·base 불변·최소 1, 50% 응급 회복 | `MonsterCombatPolicyTest.hpTransitionsClampDefeatAndRecoveryWithIntegerMath`, `DerivedStatsCalculatorTest.severeInjuryFloorsMaxHpAndAttackToEightyPercentWithoutMutatingSources`, `severeInjuryKeepsLowMaxHpAndAttackAtExistingMinimumOne`, `StatusEffectPolicyTest.emergencyRecoveryUsesLongFloorMathAndKeepsAtLeastOneHp` | `BattleMapTest.presentationPhasesRenderAttackHitDeathSpawnAndKoAnnouncementsInsideMap`이 `0 HP` 전투 불능과 응급 회복 한국어 표시를 검증 |
| 서로 다른 occurrence 3회, duplicate·완료 취소·반복 분할·rollback, 세 번째 공격의 복원 스탯 | `StatusEffectDaoTest.recoveryOccurrenceIsCreditedOncePerRevisionTaskAndOccurrenceDate`, `RoomTaskRepositoryTest.threeDistinctRecurringCompletionsRecoverInjuryAndThirdAttackUsesRestoredStats`, `duplicateAndUndoRedoOfSameOccurrenceNeverGrantAnotherRecoveryCredit`, `recurringSplitReassignsRecoveryKeySoUndoRedoCannotCreditTheSameOccurrenceAgain`, `playerAttackFailureRollsBackRecoveryCreditAndDecrementWithCompletion` | `AppNavigationTest.severeInjuryPersistsAcrossCharacterReentryAndActivityRecreationWithoutReplayingLifecycle` |
| 24시간 경계, 재시작 지속성, 재패배 refresh·비중첩, 해제 후 스탯·HP | `RoomStatusEffectRepositoryTest.observeMapsPersistentActiveEffectAndExactExpiryReconciliationSurvivesRecreation`, `RoomCombatRepositoryTest.defeatWhileInjuredRefreshesOneEffectAndIgnoresPreviousRevisionCredits`, `restartRestoresActiveInjuryAndExactExpiryReconciliationOnlyRemovesModifier`, `RoomCharacterRepositoryTest.activeSevereInjuryAffectsSnapshotAndExpiryRestoresStatsWithoutHealingCurrentHp` | 위 `AppNavigationTest`와 `CharacterScreenTest.severeInjuryBadgeMergesSemanticsAndOpensViewModelOwnedDetails` |
| ordered lifecycle, 회전·재진입 비재생, UI 접근성 | `BattleAnimationControllerTest.lethalMonsterAttackConsumesTheEntireSevereInjuryLifecycleInOrder`, `refreshedSevereInjuryUsesRefreshingPhaseAndDoesNotDropLifecycleEvents`, `statusEffectRemovalIsConsumedOnceAndKeepsCurrentHpUnchanged`, `presentationCollectorResubscriptionOnlyRendersCurrentSequence`, `CalendarViewModelTest.statusRemovalEventPlaysOnceAndARecreatedControllerDoesNotReplayIt`, `CharacterViewModelTest.resumeAtExactExpirationReconcilesDatabaseStateAndRemovesTheEffect` | `BattleMapTest.severeInjuryBadgeUsesDedicatedGeometryAndAccessibleDetailsAtLargeFontScale`, 위 `CharacterScreenTest`와 `AppNavigationTest` |
| XP·재화 보상과 기존 완료·실패·monster attack 불변 | `RoomCombatRepositoryTest.lethalMonsterAttackPreservesMonsterProgressAndPlayerRewardSnapshot`, 기존 reward/failure/reconciliation transaction suite | 전체 `AppNavigationTest`, `CalendarScreenTest` 회귀 |
| Room 12→13 및 v1→13 chain | `TodoQuestDatabaseMigrationTest.migrationFromVersion12ToVersion13PreservesSourcesAndCreatesEmptyStatusEffectTables`, `migrationFromVersion1ThroughVersion13PreservesUserData` 등 migration chain | production v13 database 재개방 연결 테스트 |

최종 acceptance는 아래 명령을 순서대로 실행한다. Android SDK나 emulator가 없으면 임의 설치하지 않고 phase를 blocked로 기록한다.

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp-phase51
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

2026-08-10 phase 51 최종 검증은 Room v13 상태이상 원천·revision별 occurrence 회복 ledger, 중상 20% 유효 스탯 감소, `0 HP 전투 불능 → 중상 적용/갱신 → 50% 응급 회복`, 24시간 또는 서로 다른 완료 3회 회복과 replay 없는 UI lifecycle을 함께 대상으로 했다. debug·release JVM/Robolectric test 각 525개, `lint`, debug 앱·androidTest APK, Pixel 9 Android 17(API 37) AVD의 전체 connected test 121개, harness pytest 88개를 실패·오류·skip 없이 통과했다. phase manager는 52개 phase를 오류 없이 검증했고, 19개 Markdown 문서의 로컬 링크 181개와 `git diff --check`, 금지 legacy 표현 잔존 없음도 확인했다. 최초 두 장치 connected 실행에서는 Pixel 9 AVD 121개가 통과했지만 SM-A325N이 실행 중 `Dozing`으로 전환되어 Compose hierarchy가 사라진 뒤 69개가 연쇄 실패했으므로, 코드를 변경하거나 테스트를 완화하지 않고 안정적인 Pixel 9 AVD를 단일 connected gate로 다시 실행했다. 또한 기존 `.venv/pytest-tmp-phase51`의 외부 ACL 때문에 첫 pytest setup이 시작되지 못해 해당 폴더를 삭제하거나 권한 변경하지 않고 새 `.venv/pytest-tmp-phase51-final`을 사용했으며, 결과에 영향 없는 `.pytest_cache` 쓰기 warning 1건만 남았다.

2026-08-10 phase 52 최종 검증은 Room v14 난이도 source/version migration, 신규 completion snapshot과 legacy 중립 처리, EASY/MEDIUM/HARD `100/150/200%` 피해·XP 배율, staged exact alarm과 앱/채널 capability 분리, Calendar 실제 발화 시각·복구 CTA·compact action 및 EXP 양끝 정렬을 함께 대상으로 했다. debug·release JVM/Robolectric test 각 562개, `lint`, debug APK, Pixel 9 Android 17(API 37) AVD의 전체 connected test 125개를 실패·오류·skip 없이 통과했다. 별도 process-absent smoke는 30초 미래 alarm을 `cmd activity stop-app` 뒤 전달해 AlarmManager wakeup 증가, occurrence notification tag, target 오류·crash 0을 확인했고 `app/build/reminder-diagnostics/emulator-5554/`에 artifact를 남겼다. harness pytest는 88개가 통과했고 phase manager는 53개 phase를 오류 없이 검증했으며, 19개 Markdown 문서의 로컬 링크 181개와 `git diff --check`도 확인했다. 첫 lint 실행은 FIR analyzer 내부 오류였고 daemon 재시도에서 실제 `NonObservableLocale` 1건이 드러나 `LocalLocale.current.platformLocale`로 최소 수정했다. 기존 `.venv/pytest-tmp`의 외부 ACL 때문에 정확한 기본 basetemp 실행은 setup 단계에서 실패했으므로 폴더를 삭제하거나 권한 변경하지 않고 `.venv/pytest-tmp-phase52-final`에서 같은 전체 88개 suite를 재실행했으며, 결과에 영향 없는 `.pytest_cache` 쓰기 warning 1건만 남았다.

### Phase 53 Equipment Shop UX polish 구현·검증 경계

phase 53은 새 Room migration이나 장비 asset 없이 기존 Room v14 장비 source와 shared renderer 경계를 확장했다. 해제 transaction은 대상 `character_equipment` row만 제거해 `owned_equipment`를 보존하고, 활성 상태이상을 포함한 장착 전후 `MAX_HP` 비율과 사용자가 해제한 대상 appearance fallback의 기본 복장 전환을 함께 확정한다. 이미 빈 slot은 `AlreadyEmpty`로 write 없이 멱등 성공한다. Shop은 slot 관리 dialog, Inventory는 장착 중 item의 해제 action을 제공하며 처리 중 중복 command와 dismiss를 막는다. 상점 카드는 요구 레벨을 항상 표시하고 보유 상태를 색·2dp 테두리·체크 icon·text로 함께 구분하며, 일반 판매 장비의 중복 `판매 중` badge를 제거하고 판매 중지 예외만 남겼다. 프리뷰 slot 높이는 일반 `96dp`, 작은 폭 또는 큰 글꼴 compact layout은 `120dp`다.

2026-08-10 최종 검증은 Temurin JDK 17.0.19, Android SDK 37.0.0과 Pixel 9 Android 17(API 37) AVD를 단일 connected gate로 사용했다. debug·release JVM/Robolectric test 각 580개, `lint`, `assembleDebug`, connected test 134개를 실패·오류·skip 없이 통과했다. harness pytest는 phase 전용 `.venv/pytest-tmp-phase53-final`에서 88개가 통과했고, phase manager는 54개 phase를 오류 없이 검증했으며 19개 Markdown 문서의 로컬 링크 181개와 `git diff --check`도 확인했다. 최초 두 장치 connected 실행에서 Pixel 9 AVD의 134개는 통과했지만 SM-A325N Android 13 실기기는 화면 계층이 사라진 뒤 `No compose hierarchies found in the app`으로 124개가 연쇄 실패했으므로, 코드나 테스트를 완화하지 않고 안정적인 Pixel 9 AVD에서 전체 134개를 다시 실행했다. 정확한 기본 harness basetemp `.venv/pytest-tmp`도 기존 외부 ACL 때문에 setup 단계에서 실패했으며, 해당 폴더를 삭제하거나 권한을 변경하지 않고 새 phase 전용 경로에서 동일한 88개 suite를 재실행했다. 결과에는 영향 없는 `.pytest_cache` 쓰기 warning 1건만 남았다.

### Phase 54 Monster Compendium v1 구현·검증 경계

phase 54는 Room v14 schema와 migration chain을 변경하지 않고 기존 `monster_instances`의 현재·과거 이력을 발견 종족 집합으로 투영했다. `MonsterDiscoveryPolicy`는 저장된 Stage·encounter·grade·balance version을 결정적 종족 정책에 전달하고 HP 0 이력을 포함해 중복 종족을 제거한다. `RoomCombatRepository.observeDiscoveredMonsterSpecies()`는 첫 관찰에서 활성 몬스터 초기화를 보장하고 승리로 다음 인스턴스가 생성되면 Flow를 즉시 갱신한다. 네 번째 `도감` top-level destination은 현재 Monster category 하나와 5종 이름을 제공하며, 발견 종만 공용 최근접 sprite·한국어 외형 설명·상세 action을 노출한다. 펫·맵 placeholder와 종족별 고정 능력치·biome·스킬·전리품은 추가하지 않았다.

2026-08-11 최종 검증은 Temurin JDK 17.0.19, Android SDK Platform Tools 37.0.0과 `emulator-5554`의 Pixel 9 Android 17(API 37) 16 KB AVD를 단일 connected gate로 사용했다. `test lint assembleDebug assembleDebugAndroidTest`에서 debug·release JVM/Robolectric test 각 595개, `lint`, debug 앱·androidTest APK가 통과했고, 전체 connected test 140개도 실패·오류·skip 없이 통과했다. harness pytest는 phase 전용 `.venv/pytest-tmp-phase54-final`에서 88개가 통과했으며 결과에 영향 없는 기존 `.pytest_cache` 쓰기 권한 warning 1건만 남았다. phase manager는 55개 phase를 오류 없이 검증했고, 19개 Markdown 문서의 로컬 링크 181개와 `git diff --check`도 통과했다. Room database version과 `14.json` identity schema, `MIGRATION_1_2`부터 `MIGRATION_13_14`까지의 production chain은 변경하지 않았다.

### Phase 55 Blacksmith Shopkeeper NPC 구현·검증 경계

phase 55는 새 Room migration이나 경제 source 없이 Shop의 정적 presentation만 확장했다. `docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`와 schema v1 JSON은 `64×64`, 양 끝 포함 bounds `[14,13,50,58]`, 정확히 16색, 공통 외곽선 `#263B5A`와 binary alpha의 canonical 원천이다. Android `drawable-nodpi` resource는 SHA-256 `4361AC2FA94D14CC8A0EF9F5D4C22DCF01D779FF6A8B7C7FD3AFB21FA110F0F9`로 canonical과 byte-identical하다. Shop의 첫 스크롤 item은 이 logical canvas를 `104dp` frame과 `FilterQuality.None`으로 렌더링하고 `대장장이`·`어서 오게. 필요한 장비를 골라 보게.` 및 합쳐진 한국어 TalkBack을 제공한다. 일반 폭은 sprite 왼쪽·문구 오른쪽 Row, 폭 `360dp` 미만 또는 font scale `1.5` 이상은 sprite 위·문구 아래 Column이며 click action은 없다. decode 실패는 Material `Build` icon으로 격리한다. Room, Repository, UseCase, ViewModel, `ShopUiState`, application wiring, 장비 catalog와 구매·장착·해제 transaction은 변경하지 않았다.

2026-08-11 최종 검증은 Temurin JDK 17.0.19, Android SDK Platform Tools 37.0.0과 `emulator-5554`의 `sdk_gphone16k_x86_64` Android 17(API 37) 16 KB AVD를 단일 connected gate로 사용했다. NPC validator와 두 character sheet validator가 통과했고, NPC·monster·character·harness를 합친 Python test 311개(`scripts/test_execute.py` 88개 포함)가 실패·오류·skip 없이 통과했다. 결과에 영향 없는 기존 `.pytest_cache` 쓰기 권한 warning 1건만 남았다. debug·release JVM/Robolectric test 각 595개, `lint`, debug 앱·androidTest APK도 통과했다. 첫 전체 connected 실행에서는 새 첫 item으로 viewport가 이동한 뒤 기존 `AppNavigationTest` 2건이 offscreen character/category node를 바로 찾는 문제가 드러났다. 제품 코드는 바꾸지 않고 목적 node로 명시적으로 스크롤하도록 테스트를 보강해 두 테스트를 단독 통과시킨 뒤 전체 connected test 143개를 실패·오류·skip 없이 다시 통과했다. 1배율·8배율 canonical PNG와 실제 Shop 일반 Row·font scale `2.0` compact Column을 육안 확인했고 장치의 font scale과 회전 설정을 원래 값으로 복원했다. phase manager는 56개 phase를 오류 없이 검증했고 `Sync -Check` drift가 없었으며, 20개 Markdown 문서의 로컬 링크 190개와 `git diff --check`도 통과했다.

### Phase 56 Character Stat Allocation Guide v1 구현·검증 경계

phase 56은 Room schema와 능력치 transaction을 변경하지 않고 신규 설치 사용자의 첫 Character 진입에 능력치 배분 안내를 추가했다. `TodoQuestAppContainer.create()`는 Room builder를 만들기 전에 `todo-quest.db` 파일 존재 여부를 캡처하고, 파일이 없던 최초 초기화만 `SharedPreferencesCharacterGuideRepository`의 자동 표시 대상으로 고정한다. preference file은 `todo_quest_character_guides`, 분리된 key는 `stat_allocation_auto_eligible_v1`과 `stat_allocation_acknowledged_v1`이다. eligibility는 최초 commit 뒤 DB나 process 상태로 다시 계산하지 않으며, 자동 확인 전 process가 종료되면 다음 Character 진입에서 다시 표시한다. 기존 설치는 자동 대상에서 제외하지만 모든 사용자는 기본 능력치 도움말로 수동 재열람할 수 있고 수동 닫기는 automatic acknowledged를 변경하지 않는다.

canonical `docs/art/npc/todo-quest-fairy-guide-front-idle.png`와 schema v1 JSON은 `64×64`, 양 끝 포함 bounds `[6,22,58,52]`, 정확히 16색, 공통 외곽선 `#263B5A`와 binary alpha의 원천이다. Android `drawable-nodpi` resource는 SHA-256 `8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0`으로 canonical과 byte-identical하다. Character Dialog는 전체 canvas를 `96dp` frame과 `FilterQuality.None`으로 렌더링하고 decode 실패를 decorative `Info` icon으로 격리한다. 한국어 대사·포인트 상태를 합친 TalkBack, 독립 본문 scroll, 기본 능력치 이동과 `320dp×640dp`·font scale `2.0` 접근성은 Compose 연결 테스트가 담당한다.

최종 회귀는 저장소 루트에서 다음 순서로 실행한다. Android 도구나 연결 기기가 없으면 설치를 시도하지 않고 phase를 blocked로 기록한다.

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-56-fairy-final
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat test lint assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

2026-08-11 최종 검증은 Temurin JDK 17.0.19, Android SDK Platform Tools 37.0.0과 Pixel 9 Android 17(API 37) AVD를 connected gate로 사용했다. 요정 canonical validator와 SHA-256 `8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0` runtime byte identity가 통과했고, NPC·monster·character·harness Python test 314개도 실패·오류·skip 없이 통과했다. 결과에 영향 없는 기존 `.pytest_cache` 쓰기 권한 warning 1건만 남았다. debug·release JVM/Robolectric test 각 619개, `lint`, debug APK와 전체 connected test 147개도 실패·오류·skip 없이 통과했다. Room database version과 `14.json` identity schema, `MIGRATION_1_2`부터 `MIGRATION_13_14`까지의 production chain 및 기존 stat allocation transaction은 변경하지 않았다.

### Phase 60 Monster Compendium presentation v2 구현·검증 경계

phase 60은 ADR-021의 Room v14 `monster_instances` 발견 projection과 기존 사용자 소급 계약을 유지하면서 도감 presentation만 privacy-preserving 수집 UI로 교체했다. `MonsterCompendiumEntryUiModel.Undiscovered`와 호환 상세의 locked state에는 실제 이름·sprite·설명 resource가 없고, 검색은 `MonsterNameResolver`가 해석한 발견 종의 한국어 resource 이름만 대상으로 한다. `MonsterCompendiumViewModel`은 발견 수·전체 수·수집률, 앱바 검색, `전체/발견/미발견` filter, 선택 preview와 replay 없는 잠금 안내를 하나의 비영속 state/effect 경계로 관리한다. 고정 TopAppBar 아래 단일 `LazyVerticalGrid`는 수집 현황·filter·약 184dp preview·3~5열 card를 함께 스크롤하며 미발견 slot은 공통 `???`·`?`·잠금만 표시한다. 발견 preview는 공용 `ModalBottomSheet` 상세를 열고 기존 `compendium/monsters/{species}` route는 미발견 resource를 숨기는 호환 경계로 유지한다. phase 60 당시 네 bottom destination, canonical 64×64 monster PNG, Repository·Room schema와 발견 계산은 변경하지 않았으며 후속 Settings destination은 ADR-024가 추가한다.

2026-08-12 최종 검증은 Temurin JDK 17.0.19, Android SDK Platform Tools 37.0.0과 SM-A325N Android 13(API 33) 실기기를 connected gate로 사용했다. debug·release JVM/Robolectric test 각 638개와 `lint`, `assembleDebug`가 통과했고, 전체 connected test 156개도 실패·오류·skip 없이 통과했다. harness pytest는 `.venv/pytest-tmp-phase60-final`에서 88개가 통과했으며 `git diff --check`도 성공했다. phase 60의 Step 3 child는 문서 동기화와 JVM/lint 또는 connected 전체 검증을 각각 완료한 뒤 1,800초 child 제한에 도달했으므로 성공 산출물을 버리거나 suite를 축소하지 않고 부모 세션에서 `assembleDebug`, harness pytest, manager와 diff 검증을 마쳤다. Room database version, `14.json` identity schema, `MIGRATION_1_2`부터 `MIGRATION_13_14`까지의 production chain, occurrence·RewardLedger·공격 event 멱등성과 알림 권한 실패 독립성은 변경하지 않았다.

### Phase 61 Battle Sound Effects v1 구현·검증 경계

phase 61은 ADR-024에 따라 기존 replay 없는 전투 presentation을 효과음으로 확장한다. 현재 앱에는 전용 설정 화면이나 DataStore가 없으므로 application-scope SharedPreferences Repository를 새 설정 source로 사용하고 key가 없으면 `효과음`을 켜짐으로 읽는다. navigation은 `캘린더 → 캐릭터 → 상점 → 도감 → 설정` 다섯 top-level destination으로 확장하며 Settings는 Switch 하나만 제공한다. 설정 off는 음향만 억제하고 animation, damage text, HP bar, status text와 `전투 불능` 안내를 유지한다.

효과음 source는 `RoomCombatRepository.events`의 `MutableSharedFlow(replay = 0)` `CombatTransition`이고 조정 경계는 기존 `BattleAnimationController`의 단일 buffered actor다. Composable 또는 별도 HP collector가 체력 변화를 보고 효과음을 추론하지 않는다. 한 공격에서 공유 가능한 안정적 combat event id와 `BattleSfx`를 결합한 `eventId + BattleSfx`를 개별 소비 key로 사용한다. persisted `APPLIED` 재처리, 화면 회전·재구성·재진입과 다음 monster 생성은 과거 음향을 replay하지 않는다.

검증할 순서는 다음과 같다.

```text
player:  PLAYER_ATTACK → PLAYER_ATTACKING → MONSTER_HIT → MONSTER_HIT 표시
         → 치명 시 MONSTER_DEFEATED → MONSTER_DYING
monster: MONSTER_ATTACK → MONSTER_ATTACKING → PLAYER_HIT → PLAYER_HIT 표시
         → 치명 시 PLAYER_DEFEATED → PLAYER_DYING/전투 불능
```

defeat 음은 hit 음 뒤 death animation 시작에 재생한다. `PLAYER_DEFEATED`는 실제 `CombatLifecycleEvent.PlayerDefeated`가 있는 경우에만 허용하고 상태 적용·갱신, 응급 회복, 상태 제거와 spawn에는 death 또는 revive 음을 연결하지 않는다.

runtime player는 application-scope `SoundPool`을 process당 한 번 `USAGE_GAME`, `CONTENT_TYPE_SONIFICATION`, `maxStreams = 6`으로 생성하고 `PLAYER_ATTACK`, `MONSTER_ATTACK`, `MONSTER_HIT`, `PLAYER_HIT`, `MONSTER_DEFEATED`, `PLAYER_DEFEATED` 여섯 raw WAV를 preload한다. load 전·background·released 요청은 queue하지 않고 폐기하며 foreground 복귀 뒤 새 event만 재생한다. 시스템 media volume과 DND를 우회하거나 audio focus를 독점하지 않는다. WAV는 저장소의 결정론적 합성기로 생성하고 외부 음원을 다운로드하지 않는다. generator 재실행의 byte identity, PCM/WAV header와 여섯 output 존재를 Python test로 검증한다.

구현은 다음 계층으로 테스트를 먼저 추가한다.

| 검증 대상 | unit/Robolectric test | 연결 test |
|---|---|---|
| replay 없는 effect mapping | `eventId + BattleSfx` identity, player/monster attack·hit·defeat 순서, duplicate transition과 실제 lifecycle 없는 `PLAYER_DEFEATED` 거부 | 화면 회전·탭 재진입·다음 monster 생성에서 과거 소리 비재생 |
| 설정 source와 presentation 독립성 | SharedPreferences key 부재의 기본 켜짐, 저장·process 재생성 복원, off에서도 controller의 animation·damage·HP·status state 유지 | 다섯 top-level tab 순서와 Settings의 한국어 `효과음` Switch·최소 48dp semantics |
| application-scope audio lifecycle | `SoundPool` 단일 생성, 여섯 preload, audio attributes·stream 한도, load 전·background·released 요청 폐기와 복귀 후 신규 event만 허용 | foreground/background 왕복 뒤 과거 소리 비재생과 새 전투 effect 정상 처리 |
| 원본 asset | 결정론적 합성기 재실행 byte identity, 여섯 PCM/WAV header·duration·peak 범위 | APK raw resource에 여섯 WAV가 포함되고 외부 다운로드 의존이 없음 |
| 불변 회귀 | Room v14 migration chain과 피해·보상·spawn·중상 계산 기존 suite | Calendar 전투·Settings navigation·notification·Shop·Compendium 전체 회귀 |

Room database version은 v14를 유지하고 identity schema 또는 migration을 추가하지 않는다. 피해·보상·Stage·spawn·중상 수식과 occurrence·RewardLedger·양방향 공격 event key도 변경하지 않는다. Android 도구나 연결 기기가 없으면 설치를 시도하지 않고 해당 검증을 blocked로 기록한다.

2026-08-12 최종 검증은 Temurin JDK 17.0.19, compile/target SDK 35·Build Tools 35.0.0·Platform Tools 37.0.0과 SM-A325N Android 13(API 33) 실기기를 사용했다. Battle SFX·harness Python test 95개, debug·release JVM/Robolectric test 각 685개, `lint`, debug APK와 connected test 161개가 실패·오류·skip 없이 통과했다. 첫 Gradle 전체 실행의 test-source lint 분석기 내부 오류는 생성된 report가 `0 errors`임을 확인한 뒤 같은 전체 명령을 재실행해 통과했으며 제품 코드나 assertion을 완화하지 않았다. `build_battle_sfx.py --check`는 여섯 자산의 결정론적 byte identity와 manifest SHA-256, 44,100Hz·16-bit PCM·mono RIFF/WAVE, 150~650ms duration, frame·peak·RMS·선행/후행 무음 제한을 모두 통과했다. Python 실행에는 결과에 영향 없는 기존 `.pytest_cache` 쓰기 권한 warning 1건만 남았다. 연결 검증은 fake audio player를 사용했고 시스템 media volume·DND·notification permission과 사용자 설정을 변경하지 않았다. Room database version은 v14, identity schema는 `14.json`, production migration chain은 `MIGRATION_1_2`부터 `MIGRATION_13_14`까지 그대로이며 피해·보상·Stage·spawn·중상 lifecycle과 occurrence·RewardLedger·양방향 공격 event 멱등성도 변경하지 않았다.

### Phase 62 Gameplay UI·Equipment·Notification Permission Polish 구현·검증 경계

phase 62는 ADR-025에 따라 중상 status layout의 actor 좌표 불변, 중립 training fallback과 adventure 7부위 상품, Shop 공통 typed action·고정 stat/action layout, Settings 일반 알림 권한 관리를 구현한다. Room v15의 `MIGRATION_14_15`는 `character_equipped_items` appearance fallback만 빈 loadout으로 바꾸고 `owned_equipment`, `character_equipment`, HP·상태이상, 일정·완료·RewardLedger·전투·알림 source를 보존한다. 최신 identity schema는 `app/schemas/com.todoquest.data.local.TodoQuestDatabase/15.json`이고 production builder는 `MIGRATION_1_2`부터 `MIGRATION_14_15`까지 등록한다.

장비 seeder는 기존 18종에 고정 ID `1019..1025`의 `UNCOMMON`·요구 레벨 5 adventure 7부위를 더한 25종을 제공한다. schema v6 character asset 계약은 `top_default`·`bottom_default`·`shoes_default` 중립 fallback, `gloves_adventure`와 legacy 3분할 검 source를 포함한다. `EquipmentStoreSnapshot.ownedEquipmentByEquipmentId`와 Shop typed action은 catalog equipment id와 owned row id를 구분하며 card/detail의 `구매`·`구매 불가`·`장착`·`해제`를 공통 source로 만든다.

Settings의 일반 알림 권한 검증은 `ReminderScheduler.checkCapability(POST_NOTIFICATIONS) → SettingsViewModel → Compose launcher → AndroidReminderCapabilityAdapter` 경계만 대상으로 한다. Android 13 이상 runtime permission과 package 앱·채널 설정을 확인하되 exact-alarm capability·special access는 포함하지 않는다. 권한 거부·조회 실패는 효과음·navigation과 일정·보상·전투·구매·장착 회귀를 실패시키지 않아야 한다.

phase 62의 art와 전체 acceptance는 저장소 루트에서 다음 순서로 실행한다. 연결 기기나 Android 도구가 없으면 임의 설치하지 않고 해당 step을 blocked로 기록한다.

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py scripts\test_validate_character_sheet.py scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-62-art
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\.venv\Scripts\python.exe -m pytest scripts\test_execute.py --basetemp build\pytest-62-harness
.\scripts\run_phase_manager.ps1 -Command Validate
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
git diff --check
```

### Task Reminder v1 구현·정책·검증

Task Reminder v1은 Room v10에서 domain·Repository·Android delivery 경계, Calendar editor와 application wiring까지 구현했고 Delivery Reliability v2는 Room v14에서 staged plan과 채널 capability를 보강했다. 현재 identity schema는 `app/schemas/com.todoquest.data.local.TodoQuestDatabase/15.json`이며 production builder는 `MIGRATION_1_2`부터 `MIGRATION_14_15`까지 등록한다. v9→v10은 기존 row를 backfill하지 않고 FK cascade, mode/status index를 가진 빈 `task_reminders`만 추가하고, v10→v11은 `character_equipped_items`에 nullable `glovesId`를, v11→v12는 `equipment`에 nullable `weaponType`을 추가해 기존 WEAPON row만 `LONGSWORD`로 backfill하며, v12→v13은 reminder source를 바꾸지 않고 빈 상태이상 테이블만 추가한다. v13→v14는 기존 reminder schema를 변경하지 않고 player attack에 nullable completion 난이도와 version `0`만 추가하며, v14→v15도 reminder table·plan·alarm을 변경하지 않고 appearance fallback만 갱신한다. legacy task는 reminder row가 없으면 `NONE`·`DISABLED`로 읽고, 새 task 또는 수정된 task는 task row와 reminder setting을 하나의 Room transaction에서 저장한다.

- Android 공식 [alarm 예약 문서](https://developer.android.com/develop/background-work/services/alarms)는 일정 앱처럼 사용자에게 정확한 시각이 핵심인 기능에서 exact alarm을 허용하고, Android 12 이상 `SCHEDULE_EXACT_ALARM`, `canScheduleExactAlarms()` 확인, 권한 부여 broadcast 뒤 재등록을 요구한다. Todo Quest는 explicit immutable occurrence `PendingIntent`와 `RTC_WAKEUP` `setExactAndAllowWhileIdle()`을 사용하고 `USE_EXACT_ALARM`, listener 우회와 inexact fallback은 사용하지 않는다.
- Android 공식 [notification runtime permission 문서](https://developer.android.com/develop/ui/compose/notifications/notification-permission)는 Android 13 이상 `POST_NOTIFICATIONS`를 사용자 맥락에서 요청하도록 한다. Todo Quest는 첫 Calendar 진입의 한국어 안내에서 system dialog를 한 번만 요청하고, 거부·보류 뒤 reminder 저장 시 package `알림 설정` CTA를 제공한다. exact access는 notification capability가 확보된 뒤 `NONE`이 아닌 reminder 저장 흐름에서만 기존 한국어 rationale와 special-access 설정을 순차 제공한다.
- Android 공식 [notification 중요도 문서](https://developer.android.com/develop/ui/compose/notifications)는 높은 중요도의 channel이 heads-up 조건이 될 수 있고 사용자가 channel 중요도를 변경할 수 있음을 명시한다. API 26 이상은 기존 channel importance를 읽어 차단을 별도 상태로 노출하고, API 23~25는 notification 자체에 high priority와 기본 소리·진동을 지정한다. 기본 alert와 heads-up은 요청값이며 사용자 channel 설정과 방해 금지 모드를 우회하지 않는다.
- Android 공식 [alarm 재부팅 문서](https://developer.android.com/develop/background-work/services/alarms#boot)는 기기 종료 시 alarm이 취소되므로 `BOOT_COMPLETED` 뒤 다시 예약해야 함을 명시한다. manifest는 `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, 비exported alarm receiver와 restore receiver를 선언한다. 앱 시작, boot, package replace, 시스템 시각·시간대 변경, exact 권한 부여는 `reminder-reconciliation-startup` unique one-time WorkManager 진입점으로 모으고, WorkManager에는 재등록·상태 복구만 맡긴다.
- Android 공식 [Android 15 stopped-state 변경](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state-changes)과 [background restricted 정책](https://developer.android.com/topic/performance/background-optimization#user-initiated-restrictions)은 Force Stop이 PendingIntent를 취소하고 restricted 상태가 alarm·job·boot broadcast를 막을 수 있음을 설명한다. 이 환경에서 정확한 전달은 보장하지 않으며, 복귀 시 missed notification을 몰아서 게시하지 않는다.

구현 검증은 다음 계층으로 나눈다.

| 검증 대상 | JVM/Robolectric test | 연결 test |
|---|---|---|
| 네 mode·시간·DST·다음 occurrence | `ReminderPlannerTest`가 preset의 이전 날짜, 무시간 custom, 엄격한 미래, DST gap/overlap과 완료·실패 skip을 검증 | Calendar editor 연결 test가 실제 local time picker와 저장값을 검증 |
| Room v15·task transaction | `TodoQuestDatabaseMigrationTest`, `TaskReminderDaoTest`, `RoomTaskRepositoryTest`, `RoomReminderRepositoryTest`가 v1~v15 chain, v13→v14 null/version `0` legacy와 v14→v15 reminder source 보존, 전체 reminder Flow, 원자 저장·난이도 snapshot·반복 분할·조건부 key 갱신을 검증 | production container 재개방 테스트가 v15 file과 legacy `NONE` projection을 검증 |
| Repository→UseCase lifecycle | `ReminderUseCaseTest`가 앱 권한·채널 capability 순서, 다음 한 건의 PENDING stage, scheduler callback 선점 뒤 DELIVERED 보존, stale claim·publisher/scheduler 실패 격리와 전체 reconcile을 검증 | `ReminderIntegrationTest.productionGraphPersistsMutationAndUsesCurrentPlatformCapabilityWithoutLosingTask`와 `receiverClaimsStagedPendingPlanBeforeSchedulerFinalizesWithoutDuplicateDelivery`가 실제 Room과 AlarmManager 경계를 검증 |
| scheduler·publisher·receiver | `AndroidReminderSchedulerTest`, `NotificationPermissionEntryPointsTest`, `ReminderAlarmReceiverTest`가 immutable occurrence identity, API 31/33 capability, channel importance/settings intent, API 23~25 default alert, high-importance private notification, PENDING/SCHEDULED 발화 직전 재검증과 중복 게시 억제를 검증 | `ReminderIntegrationTest.receiverPublishesCurrentOccurrenceOnceAndIgnoresDuplicateCallback`가 manifest receiver 경계와 동일한 runtime graph를 검증 |
| restore worker·manifest action | `ReminderReconciliationWorkerTest`가 startup·boot·package replace·time/timezone·exact-grant action, unique work와 transient DB 제한 재시도를 검증 | 전체 connected suite가 merged manifest의 receiver와 application startup을 포함해 실행 |
| Calendar UI·권한·navigation | `CalendarViewModelTest`가 one-shot permission 순서, occurrence 기준 mode·당일/전날 trigger와 task별 typed 복구 event를 검증 | `CalendarScreenTest`가 selector·disabled preset·custom validation·앱/채널/exact CTA·compact 48dp action·한국어 semantics를, `AppNavigationTest.notificationPendingIntentOpensTheRequestedOccurrenceDate`가 notification tap과 Activity 재생성을 검증 |
| process-absent exact delivery | fixture가 capability·15~60초 trigger와 `SCHEDULED` source를 검증 | 표준 PowerShell 진입점이 target process 부재, AlarmManager delivery 증가, occurrence notification tag와 오류·crash 부재를 자동 검증 |

전체 connected suite와 별도로 disposable emulator에서 process-absent smoke를 실행한다. 먼저 debug·androidTest APK를 조립한 뒤 explicit emulator serial을 전달한다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
.\scripts\verify_task_reminder_delivery.ps1 -Serial emulator-5554
```

스크립트는 `-Serial`을 필수로 받고 기본적으로 serial prefix와 `ro.kernel.qemu=1`이 모두 확인된 emulator만 허용한다. 물리 기기는 `-AllowPhysicalDevice`를 명시하지 않으면 APK 설치, permission 변경과 app data reset 전에 `BLOCKED`로 종료한다. emulator에서는 debug/test APK를 설치하고 Android 13 이상 `POST_NOTIFICATIONS`와 Android 12 이상 exact capability를 준비한 뒤 기존 `ReminderBackgroundSmokeFixtureTest`로 15~60초 미래 custom reminder를 seed한다. 채널 importance와 DND는 읽기만 하며 차단된 channel을 삭제하거나 새 id로 우회하지 않는다. capability를 준비할 수 없으면 도구를 설치하거나 설정을 추측하지 않고 `BLOCKED`로 종료한다.

fixture가 끝나면 `cmd activity stop-app com.todoquest`로 process만 종료하고, 지정 trigger까지 process 부재를 확인하면서 최대 90초 polling한다. stopped package를 만드는 `am force-stop`은 사용하지 않는다. 통과하려면 fixture가 기록한 occurrence와 trigger epoch의 exact `RTC_WAKEUP`이 예약돼 있어야 하고, `dumpsys alarm`의 `com.todoquest.action.DELIVER_REMINDER` wakeup count가 증가하며, `dumpsys notification --noredact`에 `reminder:<taskId>:<occurrenceEpochDay>` tag의 active notification이 존재해야 한다. 같은 log window에 `TodoQuestReminder` error나 target crash가 있으면 실패다.

진단 artifact는 `app/build/reminder-diagnostics/<serial>/` 아래의 `summary.txt`, APK 설치·capability·fixture log, 예약/최종 alarm dump, notification dump, PID와 target error log로 남으며 Git에 추가하지 않는다. `summary.txt`의 `scheduledOccurrenceFound`, `processAbsentBeforeTrigger`, `alarmDeliveryHistoryFound`, `activeOccurrenceNotificationFound`가 모두 `true`이고 `targetErrorCount=0`일 때만 통과다. WorkManager restore test, 예약 row 또는 instrumentation 내부 fake publisher만으로 이 smoke를 대체하지 않는다.

2026-07-28 phase 39 최종 검증은 Pixel 9 Android 17(API 37) AVD를 단일 연결 gate로 사용했다. debug·release JVM/Robolectric test 각 388개, `lint`, debug 앱·androidTest APK, connected test 79개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크 96개와 `git diff --check`가 실패·오류·skip 없이 통과했다. process-absent smoke는 사용자의 명시적 승인 후 `pm grant` 및 `appops set`으로 notification과 exact access를 허용하고 31초 미래의 `RTC_WAKEUP` exact alarm을 seed했다. `cmd activity stop-app` 뒤 trigger 직전까지 target PID가 비어 있음을 확인했고, alarm callback이 process를 시작해 private high-importance notification을 게시했으며 notification service에서 `백그라운드 알림 스모크`·`2026년 7월 28일 일정 알림`을 확인했다. Force Stop은 사용하지 않았다.

2026-07-31 phase 40 최종 검증은 고블린·해골 canonical validator, 해골 canonical/runtime SHA-256 `64AA2C102D65DDCECBC6AE095D696C02569850F774B93F2A89FFC7E786CE53AF` 일치, Python 283개, debug·release JVM/Robolectric 각 395개, `lint`, debug APK와 SM-A325N Android 13 connected test 83개를 실패·오류·skip 없이 통과했다. 해골 8배율 최근접 확대는 source pixel별 `8×8` 블록 불일치 0과 alpha `0/255`를 확인했고, 실제 전투로 2번째 encounter를 연 뒤 portrait·landscape Battle Map에서 해골 sprite·HP·고정 map 경계를 확인했다. 18개 Markdown 문서의 로컬 링크 100개와 `git diff --check`도 통과했다.

2026-08-01 phase 41 최종 검증은 고블린·해골·타락한 나무 정령 canonical validator와 나무 정령 canonical/runtime SHA-256 `49BFC16525645EDC5EDA24039E78A8C0858C7D24E3FEEC35DEBC61B881158DAD` 일치, Python 284개, debug·release JVM/Robolectric 각 403개, `lint`, debug APK와 SM-A325N Android 13 connected test 86개를 실패·오류·skip 없이 통과했다. 나무 정령 1배율과 8배율 최근접 확대는 source pixel별 `8×8` 블록 불일치 0과 alpha `0/255`를 확인했고, fresh Stage 1 encounter 1을 연 portrait·landscape Battle Map에서 나무 정령 sprite·HP·발 anchor·고정 map 경계를 확인했다. 18개 Markdown 문서의 로컬 링크 104개와 `git diff --check`도 통과했다.

2026-08-01 phase 42 최종 검증은 고블린·해골·타락한 나무 정령·하피 canonical validator와 하피 canonical/runtime SHA-256 `817225DE2DFEE7449CAB4FEA34ABF8F49DE3A24CFD1F529DF8562E7CF5D1DBF4` 일치, Python 301개, debug·release JVM/Robolectric 각 407개, `lint`, debug APK와 SM-A325N Android 13 connected test 89개를 실패·오류·skip 없이 통과했다. 하피 1배율과 8배율 최근접 확대는 source pixel별 `8×8` 블록 불일치 0과 alpha `0/255`를 확인했고, `1080×499` portrait와 `2424×840` landscape Battle Map에서 하피 sprite·HP·공통 발 anchor·고정 map 경계를 확인했다. API 37 AVD와 SM-A325N의 하피 포함 `BattleMapTest` 26개도 각각 통과했으며, 18개 Markdown 문서의 로컬 링크 108개와 `git diff --check`도 통과했다.

2026-08-01 phase 45 최종 검증은 고블린·해골·타락한 나무 정령·하피·슬라임 canonical validator와 슬라임 canonical/runtime SHA-256 `83A9CE9BBF44403F63E1F54D58C17FDB519ABAC53EF6A85F6831BAC1151B6106` 일치, Python 383개, debug·release JVM/Robolectric 각 412개, `lint`와 debug APK를 실패·오류·skip 없이 통과했다. 슬라임 1배율·8배율과 `64×64` source frame의 최근접 확대, bounds `[17,35,47,58]`, alpha `0/255`, 공통 발 anchor를 확인했고, SM-A325N Android 13과 Pixel 9 Android 17의 `BattleMapTest` 각 29개 및 portrait·landscape Battle Map에서 슬라임 sprite·HP·고정 map 경계를 확인했다. 보존한 SM-A325N Gradle UTP XML은 `tests=92`, `failures=2`, `errors=0`, `skipped=0`이며 UTP가 exact-alarm app-op을 초기화해 `CalendarScreenTest.customReminderNullValidationAndCompactEditorScrollKeepActionsReachable`와 `ReminderBackgroundSmokeFixtureTest.seedCustomReminderFifteenToSixtySecondsAhead`만 실패했다. 나머지 비권한 90개 통과에 사용자가 명시 승인한 `SCHEDULE_EXACT_ALARM` app-op과 `POST_NOTIFICATIONS` 권한을 설정한 direct runner의 위 두 테스트 통과를 합쳐 92개 composite coverage를 확인했고, 종료 후 `stay_on_while_plugged_in=0`으로 복원했다.

2026-08-05 phase 46 최종 검증은 투구 layer validator와 character asset build check, Python 338개, debug·release JVM/Robolectric 각 425개, `lint`, debug APK와 SM-A325N Android 13의 `ShopScreenTest`·`InventoryScreenTest`·`CharacterScreenTest` 연결 테스트 29개를 실패·오류·skip 없이 통과했다. 가죽 모자 canonical/runtime SHA-256 `099BFC8868D9A2B4817A5D1443E80F95965A70D09486F8A0E659E7A955339E00`과 철 투구 `D09863E83ED4616F8666C37782C27769C02377114B2633F142578E03655D2329`의 byte equality를 확인했다. 1×/8× preview와 실기기 Shop 투구 filter에서 서로 다른 bounds-fit 이미지를 직접 확인했고, validator는 가죽 모자 `[19,4,45,22]`, 철 투구 `[18,4,46,29]`, 얼굴 보호 영역 `[23,20,41,28]`, alpha `0/255`와 8배율 최근접 확대를 검증했다. 연결 테스트는 실제 artwork·한국어 설명·unknown/decode fallback, Room 구매·바로 장착·철 투구 교체, 다른 slot 보존, open-face 전체 원점 합성, Inventory 상태와 `320dp×640dp`·font scale `2.0` 접근성을 포함했다. Room은 v10을 유지하고 가격·modifier·소유권·occurrence 보상·알림 권한 독립 계약은 변경하지 않았으며, 테스트 동안 임시 사용한 USB 화면 유지는 종료 후 `stay_on_while_plugged_in=0`으로 복원했다.

2026-08-05 phase 47 최종 검증은 투구와 상·하의 layer validator, character asset build check, Python 360개, debug·release JVM/Robolectric 각 430개, `lint`, debug APK와 Pixel 9 Android 17(API 37) AVD의 `ShopScreenTest`·`InventoryScreenTest`·`CharacterScreenTest` 연결 테스트 34개를 실패·오류·skip 없이 통과했다. Python 실행에는 결과에 영향을 주지 않은 `.pytest_cache` 쓰기 권한 warning 1건이 있었다. 상의 canonical/runtime SHA-256은 천 `3D4E8422B7FB206F0C1CF49EB1B7F96560C9A1F506D6491425C6CA5E4C180061`, 가죽 `2E4F827F0A41681D1E96274970F1F3E212D5D830744ED8B31E1B420F5317F610`, 철 `85150725CD0EA182E262918EA402C5EB558F9AB782665EB2FD07D83C9F5F2EEA`, 하의는 천 `D5560F6E75D4A5B8A17AB27A5BE17825956C5B0B61B90466486B9A55DD8CE8EE`, 가죽 `1E18B15B36AD71E87EA4B7287A7BCA91CB457B67E7E0BF7CEB76F284B2E7E7D4`, 강철 `8B0D1E506979E18BA93F0B7D4B11800109DF674AA155321EEA55D2367D4E0B6D`로 byte equality를 확인했다. 상의 bounds `[20,29,44,45]`, 하의 `[24,41,40,54]`, 허리·발목 interface, alpha `0/255`, 1×/8× 최근접 preview와 3×3 조합 matrix를 직접 확인했고 실제 Shop 상의 filter에서 bounds-fit 이미지·한국어 이름·가격을 확인했다. 연결 테스트는 6종의 서로 다른 목록·상세·slot·Inventory artwork와 한국어 설명, unknown/decode fallback, Room 구매·바로 장착·같은 slot 교체, matched 3세트와 cross-set 2조합, 다른 slot·얼굴·손·신발·투구·무기 pixel 보존, Character·Battle Map shared renderer, `320dp×640dp`·font scale `2.0` 접근성과 decode 실패 시 일정·보상·구매·장착 transaction 독립성을 포함했다. Room v10, 가격·modifier·소유권·occurrence 보상과 알림 권한 독립 계약은 변경하지 않았다.

2026-08-05 phase 48 최종 검증은 장갑·신발, 투구, 상·하의 layer validator와 character asset build check, Python 384개, debug·release JVM/Robolectric 각 442개, `lint`, debug APK와 Pixel 9 Android 17(API 37) AVD의 `ShopScreenTest`·`InventoryScreenTest`·`CharacterScreenTest`·`BattleMapTest` 연결 테스트 70개를 실패·오류·skip 없이 통과했다. Python 실행에는 결과에 영향을 주지 않은 `.pytest_cache` 쓰기 권한 warning 1건이 있었다. canonical/runtime SHA-256은 가죽 장갑 `592DF0366D81445015B13D3E6AA504E83499E9515743A79081E921F583B9A03A`, 강철 건틀릿 `F1CD19B0209CB6226C5E80D826BA395D57D08C0D7EF7B401DA3271C48B47948E`, 여행자의 장화 `3B378876EA5EF7B3ACDB214B9034831FD35B8386E8070B494B326622F9B02C32`, 바람걸음 장화 `3F936920A071FF8999A136E9C42917E3B0D929E98F3347EB5668229F6E4037E7`로 byte equality를 확인했다. 장갑 38픽셀 mask와 `weapon_held → hands_front → weapon_front` grip 순서, 신발 bounds `[23,53,41,58]`와 다섯 하의의 발목 interface·발바닥 `y=58`, alpha `0/255`, 장비별 1×/8× 최근접 preview와 2×2 혼합 matrix를 직접 확인했다. 연결 테스트는 네 장비의 정확한 이미지·한국어 이름·가격·등급을 Shop 목록·상세·slot·Inventory에 표시하고, 실제 Room 구매·바로 장착·같은 slot 교체·modifier 비교 갱신·Repository 재생성 복원, nullable gloves appearance fallback, weapon on/off와 다섯 하의 합성, Character·Battle Map shared renderer, `320dp×640dp`·font scale `2.0` 접근성, unknown/decode fallback의 일정·보상·구매·장착 transaction 독립성을 포함했다. Room v11의 nullable `glovesId`와 16종 catalog·12종 visual mapping을 유지했으며 가격·modifier·소유권, occurrence 보상과 알림 권한 독립 계약은 변경하지 않았다.

2026-08-05 phase 49 최종 검증은 무기, 장갑·신발, 투구, 상·하의 layer validator와 character asset build check, Python 438개, debug·release JVM/Robolectric 각 457개, `lint`, debug APK와 Pixel 9 Android 17(API 37) AVD의 `ShopScreenTest`·`InventoryScreenTest`·`CharacterScreenTest`·`BattleMapTest` 연결 테스트 75개를 실패·오류·skip 없이 통과했다. 무기 canonical/runtime SHA-256은 낡은 검 `7D0C103687AF8B97DC2D7368754985189BAE639485714B4851CEE9CCB9F60EAF`, 철 장검 `0801A9AD41CB8CCDDB6800898ED32E431D9585FE6F033F845AB25CF8C74C62B1`, 물푸레나무 창 `C37C62B25C5349A790D21F5BA5C11909AF338CE214A730A706FB59ABAE68696D`, 강철 철퇴 `64C25331107A4CFFB0EB8143807DAF6EF0E95AF3A855AF3D34647496941DBA2A`로 byte equality를 확인했다. schema v5의 31개 runtime path·7,200개 loadout, primary grip `(42,42)`, 얼굴 보호 영역과 최상단 weapon group, 장비별 1×/8× preview와 2×2 matrix를 검증했다. 연결 테스트는 18종 catalog·16종 visual mapping, 네 무기의 정확한 이미지·한국어 이름·subtype·가격·등급, 실제 Room 구매·바로 장착·단일 WEAPON slot 교체·modifier/공격력 갱신·Repository 재생성 복원, Shop·Inventory·Character·Battle Map shared renderer, `320dp×640dp`·font scale `2.0` 접근성, unknown/decode fallback의 일정·보상·구매·장착 transaction 독립성을 포함했다. Room v12는 기존 WEAPON만 `LONGSWORD`로 backfill하고 비무기와 일정·보상·소유·장착·전투·알림 상태를 보존했다.

### Room v15·Gameplay UI·장비·알림 전달·기존 기능 전체 acceptance

Room v8 자동 마감 실패 보정, Room v9 Combat Rewards, Room v10 Task Reminder, Room v11 nullable gloves appearance fallback, Room v12 weapon subtype, Room v13 Severe Injury, Room v14 난이도 공격 snapshot·staged reminder delivery와 Room v15 중립 appearance fallback을 adventure art·Shop typed action·Settings 일반 알림 권한과 함께 검증하는 전체 acceptance 명령은 저장소 루트에서 다음 순서로 실행한다. 연결 suite는 notification UI·manifest receiver·navigation 통합까지 포함한 필수 gate이며 기기가 없으면 Android 도구나 AVD를 설치하지 않고 해당 step을 blocked로 기록한다.

```powershell
$env:ANDROID_SERIAL='emulator-5554'
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe scripts\build_character_assets.py --check
.\.venv\Scripts\python.exe -m pytest scripts\test_build_character_assets.py scripts\test_validate_character_sheet.py scripts\test_validate_character_equipment_layers.py --basetemp build\pytest-character-art
.\gradlew.bat test lint assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\verify_task_reminder_delivery.ps1 -Serial emulator-5554
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
git diff --check
```

| 검증 대상 | unit/Robolectric test | 연결 test |
|---|---|---|
| Room v1~v15 migration과 legacy 보존 | `TodoQuestDatabaseMigrationTest`의 v1→v15 chain, v7→v8 backfill, v9→v10 reminder 무 backfill, v10→v11 gloves, v11→v12 weapon subtype, v12→v13 상태이상, v13→v14 nullable 난이도/version `0`, v14→v15 appearance-only fallback과 gameplay source 보존을 검증 | `TodoQuestDatabaseIsolationTest`가 production database의 v15 재개방과 legacy `NONE` reminder projection을 검증 |
| character·equipment art | `build_character_assets.py --check`와 세 Python suite가 schema v6 중립 training fallback, adventure 7부위, `gloves_adventure`, legacy 3분할 검, canonical/runtime byte identity와 preview/sheet drift를 검증 | `ShopScreenTest`·`CharacterScreenTest`·`BattleMapTest`가 실제 shared renderer와 빈/상품 loadout을 검증 |
| Shop typed action·고정 layout | `EquipmentDaoTest`, `RoomEquipmentRepositoryTest`, `ShopViewModelTest`가 `ownedEquipmentByEquipmentId`, 25종 catalog, Purchase/PurchaseUnavailable/Equip/Unequip 우선순위와 선택 장비 해제 cleanup을 검증 | `ShopScreenTest`가 card/detail 공통 `구매`·`구매 불가`·`장착`·`해제`, stat cell 고정 bounds와 우측 하단 `104dp × 48dp` action을 검증 |
| Settings 일반 알림 권한 | `SettingsViewModelTest`가 `POST_NOTIFICATIONS` 전용 typed state, 최신 refresh 우선·취소 격리와 효과음 독립성을 검증 | `SettingsScreenTest`·`AppNavigationTest`가 runtime·앱·채널 launcher, 복귀·`ON_RESUME` 재조회, exact-alarm action 제외와 navigation 독립성을 검증 |
| Task Reminder lifecycle·delivery | `ReminderPlannerTest`, `RoomTaskRepositoryTest`, `RoomReminderRepositoryTest`, `ReminderUseCaseTest`, `AndroidReminderSchedulerTest`, `ReminderAlarmReceiverTest`, `ReminderReconciliationWorkerTest`가 occurrence 시각·앱/채널 capability·PENDING stage·조건부 claim·Android 23~25 alert 경계를 검증 | `ReminderIntegrationTest`, `CalendarScreenTest`, `AppNavigationTest`가 production graph·editor·receiver·notification tap과 typed 복구 CTA를 검증하고 `verify_task_reminder_delivery.ps1`이 process-absent 실제 게시를 확인 |
| Combat Rewards v2·난이도 멱등 transaction | `TaskDifficultyCombatPolicyTest`, `CombatRewardPolicyTest`, `RoomTaskRepositoryTest`, `RoomCombatRepositoryTest`가 EASY/MEDIUM/HARD `100/150/200%`, MOMENTUM→난이도→critical→defense, hit/kill XP 배율, kill gold 불변, legacy 비소급과 rollback을 검증 | `BattleMapTest.combatRewardBadgeShowsHitRewardForSixHundredMillis`가 실제 snapshot 기반 600ms 배지를 검증 |
| 신규 자동 `FAILED`와 `MISSED_DEADLINE` 원자성 | `RoomCombatRepositoryTest.untimedOccurrenceBecomesFailedOnlyAfterNextDayStarts`, `recurringReconciliationFailsOnlyTheDueOccurrenceDate`, `reconciliationSortsNewDueEventsCapsDamageAtThreeAndIsIdempotent`, `cursorFailureRollsBackFailureEventCharacterHpAndCursorTogether` | `AppNavigationTest.automaticDeadlineFailureRestoresAfterRecreationAndUndoKeepsCombatResult`가 FAILED 표시·Activity 재생성·실패 취소 후 combat 비롤백을 검증 |
| HUD 좌측 level·우측 gold/EXP summary | `PlayerProgressHudTest`가 진행률 clamp와 한국어 축약·정확 접근성 숫자를 검증 | `BattleMapTest`가 320dp·font scale 2.0에서 EXP 최소 `104dp`·intrinsic 확장, label/value와 progress bar 양끝 정렬, merged TalkBack과 actor geometry 불변을 검증 |
| typed batch allocation과 Room transaction | `StatAllocationPolicyTest`, `RoomCharacterRepositoryTest.allocationRevalidatesLatestAvailablePointsBeforeWritingAnyStat`, `multiStatAllocationPersistsAllStatsAndPreservesHpRatioOnce`, `currentStateWriteFailureRollsBackEveryAllocatedStat`, `batchAllocationKeepsZeroHpAtZero` | `AppNavigationTest.stagedBatchStatsUpdateSharedRoomCharacterAndCombatOnlyAfterSingleSave`가 저장 전 비영속 상태와 저장 후 Character·Combat Flow·재생성 복원을 검증 |
| Character draft presentation | `CharacterViewModelTest.statIncreaseAndDecreaseOnlyChangeTheViewModelDraft`, `saveCallsBatchUseCaseOnceAndClearsDraftOnSuccessOrNoChanges`, `allocationValidationFailuresKeepDraftForAdjustmentAndRetry`, `pendingAllocationDisablesResetUntilDraftIsRemoved` | `CharacterScreenTest.draftControlsExposePendingValuesAndInvokeSeparateCommands`, `unspentPointsAppearImmediatelyAfterBaseStatsTitle`, `AppNavigationTest.stagedBatchStatsUpdateSharedRoomCharacterAndCombatOnlyAfterSingleSave`가 `-/+`, pending, 저장, 초기화 차단과 저장 전 HP·파생값 비preview를 검증 |

2026-07-28 phase 37 최종 검증은 JDK 17과 Android SDK가 준비된 상태에서 `SM-A325N` Android 13 실기기를 필수 연결 gate로 사용했다. debug·release JVM test 각 308개, `lint`, debug 앱·androidTest APK, 실기기 connected test 68개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크 89개와 `git diff --check`가 실패·오류·skip 없이 통과했다.

처음 연결된 두 대를 동시에 실행했을 때 실기기 68개는 모두 통과했지만 system locale이 비어 있는 API 37 `Pixel_9` AVD에서 기존 Material time picker의 한국어 content description을 찾는 Calendar test 3개가 locale 차이로 실패했다. Android 도구·AVD를 설치하거나 설정을 바꾸지 않고 `ANDROID_SERIAL=RF9R700HNNJ`로 실기기 전체 68개를 다시 실행해 최종 connected gate를 통과했다. 이번 phase가 추가한 자동 `FAILED` 복원, HUD bounds와 staged batch allocation의 application-scope 통합 test도 이 suite에 포함된다.

2026-07-28 phase 38 최종 검증은 Pixel 9 Android 17(API 37) AVD를 단일 연결 gate로 사용했다. debug·release JVM test 각 318개, `lint`, debug 앱·androidTest APK, connected test 72개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크 90개와 `git diff --check`가 실패·오류·skip 없이 통과했다. Material time picker 연결 테스트는 대상 기기의 Material 3 문자열 리소스를 조회하도록 보강해 한국어 고정 content description 의존을 제거했다.

### Calendar Battle Map과 플레이어 진행 HUD 검증 범위

Battle Map과 플레이어 진행 HUD는 다음 위치에서 상태, 경계값과 화면 폭을 나눠 검증한다.

| 상태·환경 | unit test | 연결 Compose test | Preview |
|---|---|---|---|
| Battle Map loading·unavailable | `CalendarViewModelTest.battleMapStartsLoadingBeforeCombatSnapshotArrives` | `BattleMapTest.loadingAndUnavailableExposeKoreanStatusWithoutHidingBackground` | 플레이어 HUD loading은 `BattleMapPlayerLoadingPreview`에서 함께 확인 |
| 플레이어 HUD loading | `CalendarViewModelTest.characterSummaryStartsLoadingBeforeCharacterSnapshotArrives` | `BattleMapTest.playerProgressHudLoadingDoesNotRenderPlaceholderValues`가 임의 level·XP·gold 부재와 한국어 설명을 확인 | `BattleMapPlayerLoadingPreview` |
| XP 0 | `PlayerProgressHudTest.progressClampsCurrentExperienceToValidRange` | `BattleMapTest.zeroExperienceStillRendersAVisibleBoundedTrack`이 `0/100` progress semantics와 4dp track·outline 존재를 확인 | 기본 sample 인자를 바꿀 수 있는 `BattleMapPreviewContent`를 `BattleMapSmallPhonePreview` 등에서 재사용 |
| 필요 XP 0·음수 | `PlayerProgressHudTest.progressIsZeroWhenRequiredExperienceIsNotPositive`가 0과 음수를 모두 확인 | `BattleMapTest.playerProgressHudSafelyDescribesBoundaryValues`가 필요 XP 0 표시를 확인 | 별도 경계 Preview 없이 unit·Compose test가 담당 |
| XP 초과 | `PlayerProgressHudTest.progressClampsCurrentExperienceToValidRange`가 진행률 1 clamp를 확인 | `BattleMapTest.playerProgressHudSafelyDescribesBoundaryValues`가 `120/100` 표시를 확인 | 별도 경계 Preview 없이 unit·Compose test가 담당 |
| level·gold·EXP summary와 bar 경계 | 숫자 format과 진행률 계산 test를 유지 | `BattleMapTest.playerProgressHudStaysInsideSmallMapWithoutTextOverlapAtLargeFontScale`가 좌측 level, 우측 gold/EXP summary, group 12dp, 내부 3dp 간격, EXP content와 bar 좌우 경계 일치 및 비중첩을 확인 | `BattleMapSmallPhonePreview` |
| 골드 0·큰 값 | 숫자 표시가 없는 순수 진행률 계산 범위 밖 | `BattleMapTest.playerProgressHudSafelyDescribesBoundaryValues`가 0과 `Long.MAX_VALUE`, `playerProgressHudStaysInsideSmallMapWithoutTextOverlapAtLargeFontScale`가 긴 값을 확인 | `BattleMapSmallPhonePreview`가 10자리 XP·gold sample을 사용 |
| HP·공격·피격 가시성 | `BattleMapLayoutTest`가 HP clamp와 placement를 확인 | `BattleMapTest.healthBarsClampLowHealthAndAnimateToTargetIn220Millis`가 커진 HP panel, `presentationPhasesRenderAttackHitDeathSpawnAndKoAnnouncementsInsideMap`가 양방향 `공격!`·damage badge와 map bounds를 확인 | phase별 `BattleMapPlayerAttackingPreview`, `BattleMapMonsterHitPreview`, `BattleMapPlayerHitPreview` |
| 320dp·큰 글꼴 | 진행률 경계 test는 폭과 독립적 | `BattleMapTest.smallWidthUsesMinimumHeightAndClampsEveryUnitInsideMapBounds`, `playerProgressHudStaysInsideSmallMapWithoutTextOverlapAtLargeFontScale`, `CalendarDayIndicatorTest.hudStaysAbovePlayerAndMonsterOnStandardAndCompactScreens`, `compactLayoutKeepsCalendarAndTaskActionsReachableFromSingleScrollOwner` | `BattleMapSmallPhonePreview` |
| 가로 화면 | `BattleMapLayoutTest`가 폭과 독립적인 정규화 배치를 확인 | 전용 가로 instrumentation은 없으며 연결 emulator 수동 QA로 보완 | `BattleMapLandscapePreview`의 800×360 sample |
| 밝은·어두운 배경과 dark mode | theme token 사용은 lint·code review 대상으로 둔다 | `BattleMapTest.playerProgressHudRendersInOverlayWithIntegratedSemantics`가 실제 opaque 초원 위 HUD를 확인 | `BattleMapOneMonsterPreview`의 밝은 초원과 `BattleMapDarkModePreview`의 dark mode·어두운 preview scrim을 확인. 앱은 별도 light theme을 지원하지 않는다. |
| 몬스터 0·1·다수 | `BattleMapUiModelTest`와 `BattleMapLayoutTest`가 0~4마리 불변식·slot을 확인 | `BattleMapTest.contentSupportsZeroOneThreeAndFourMonsterLayers`와 HUD overlay test, `CalendarViewModelTest.combatSnapshotMapsToSingleMonsterBattleMapContent`가 component 다수와 운영 1마리를 구분 | `BattleMapNoMonsterPreview`, `BattleMapOneMonsterPreview`, `BattleMapThreeMonstersPreview` |
| 배경 resource 누락 | 해당 없음 | `BattleMapTest.invalidBackgroundResourceUsesLayeredCanvasFallback`이 다층 Canvas fallback과 image 부재를 확인 | unavailable state도 runtime fallback을 사용 |

HUD 진행률은 필요 XP가 0 이하이거나 현재 XP가 0 이하이면 0, 현재 XP가 필요 XP 이상이면 1, 나머지는 비율을 `0f..1f`로 제한한다. level은 Row 왼쪽, gold와 EXP summary는 오른쪽에 둔다. gold와 EXP group 사이는 12dp, gold icon/value와 EXP label/value는 각각 3dp이며, EXP group은 `max(104dp, intrinsic content width)`를 사용한다. EXP의 4dp progress bar는 `EXP` label 왼쪽과 value 오른쪽의 실제 content 경계에 맞춘다. `surfaceVariant` track과 1dp outline은 진행률 0에서도 남는다. HUD는 `MaterialTheme`의 반투명 `surface`, `outline`, `onSurface`, `primary`, `secondary` 토큰을 사용하고 전체 level·XP·gold를 하나의 한국어 TalkBack semantics로 합친다. 골드 아이콘은 decorative다. 이 폭 변경은 actor placement와 map 높이를 변경하지 않는다.

`BattleMapPreview.kt`의 모든 Preview는 실제 ViewModel이나 database 없이 `BattleMapUiState.Content` sample과 같은 `PlayerProgressHud` component만 사용한다. `assembleDebug`가 Preview 소스를 컴파일하고, `connectedDebugAndroidTest`의 `BattleMapTest.playerProgressHudRendersInOverlayWithIntegratedSemantics`와 `CalendarScreenTest.battleMapIsFixedAboveScrollableCalendarWithHudAndLegacySummaryIsAbsent`가 같은 component의 독립 overlay 합성과 실제 Calendar 연결을 확인한다.

레이어 분리·fallback·monster 수·발 anchor와 화면 경계는 `BattleMapTest`, 정규화→pixel 식과 depth ordering은 `BattleMapLayoutTest`, presentation 불변식과 slot 범위는 `BattleMapUiModelTest`가 담당한다. Calendar의 `고정 map(HUD·HP·effect) → 독립 Calendar scroll → bottom navigation` 소유권, 기존 상단 정보 Header와 중복 날짜 부재, Sunday-first 요일·cell offset은 `CalendarDayIndicatorTest`와 `CalendarScreenTest`가 검증한다. `CalendarScreenTest.addButtonFromMainActivityOpensKoreanTaskEditor`는 제거된 Header 이후에도 실제 Activity에서 추가 modal이 열리는지 확인하며, `AppNavigationTest`와 `MainActivityLaunchSmokeTest`는 하단 navigation 충돌과 초기 render를 연결 기기에서 검증한다.

2026-07-21 phase 29 최종 수동 QA에서는 실제 Room 초기화 뒤 player와 단일 active goblin이 각 지면 그림자에 발을 맞추고 서로 겹치지 않는 portrait 화면, 위 콘텐츠 순서, task 영역까지의 실제 swipe와 bottom navigation 분리를 확인했다. 같은 emulator의 stable landscape app render에서도 `320dp` 최대 높이 맵, 두 unit과 navigation 분리를 확인했다. API 37 16 KB preview compositor는 회전 직후 raw system screenshot이 일시적으로 검게 나올 수 있으므로 안정화된 app render와 connected test 결과를 함께 판정한다.

2026-07-21 phase 30 최종 QA에서는 같은 emulator의 portrait app render에서 HUD가 player·monster 위에 겹치지 않고 `map(HUD) → Sunday-first calendar → 선택 날짜 일정 문맥`이 불필요한 Header 공간 없이 이어지는 것을 확인했다. landscape app render에서도 HUD와 두 actor, 하단 navigation이 독립 영역을 유지했다. 추가 modal의 UI hierarchy·connected semantics는 interaction 증거로만 사용하며, 최종 display 성공은 아래 Calendar modal 진단의 Window·Surface·raw screenshot 판정이 별도로 통과해야 한다.

### Calendar Combat Feedback v1 최종 검증

2026-07-22 phase 33 최종 검증은 JDK 17과 Android SDK가 준비된 상태에서 연결된 `SM-A325N` Android 13 실기기를 사용했다. `gradlew test`의 debug·release 각 241개, `lint`, `assembleDebug`, `connectedDebugAndroidTest` 45개와 harness pytest 69개가 실패·skip 없이 통과했고, 18개 문서의 로컬 링크 87개와 `git diff --check`도 확인했다.

실기기 최초 화면 캡처에서 animation modifier의 강제 offscreen 합성이 player와 monster bitmap을 투명하게 만드는 회귀를 발견했다. `BattleMapTest.playerAndMonsterLayersDrawOpaquePixelSpriteOutlines`를 먼저 추가해 실패를 재현한 뒤 불필요한 offscreen 전략을 제거했고, 전체 connected suite와 portrait·landscape raw screenshot으로 shared player sprite와 goblin drawable이 HP bar 아래에 실제 합성되는 것을 다시 확인했다. 최종 artifact는 `app/build/verification/calendar-combat-feedback-step8/portrait-fixed.png`와 `landscape-fixed.png`에 둔다.

`CalendarScreenTest`와 `CalendarDayIndicatorTest`는 고정 map·독립 scroll·bottom navigation, `TODO` 완료/실패와 `FAILED` 실패 취소를 검증하고, `AppNavigationTest.activityRecreationRestoresRoomCombatAndFailureWithoutReplayingEffect`는 재생 없는 복원을 검증한다. attack·hit·death·spawn/revive 순서는 screenshot으로 대체하지 않고 `BattleAnimationControllerTest`의 virtual-time 테스트를 근거로 판정한다.

Calendar reward snackbar의 최대 600ms 자동 종료는 `CalendarScreenTest.rewardSnackbarAutomaticallyDisappearsWithinShortFeedbackWindow`, 외부 pointer down이 snackbar를 닫으면서 월 이동 action도 소비하지 않는 계약은 `tappingCalendarOutsideSnackbarDismissesItWithoutConsumingMonthAction`이 연결 기기에서 검증한다. 이미 보상된 occurrence와 실패·실패 취소에는 기존 event·비표시 회귀를 유지한다.

phase 33 당시 최종 동기화 검토에서는 치명 monster event가 즉시 회복 HP를 `playerHpAfter`로 보존하던 계약과 presentation fixture의 0 HP 가정이 어긋난 것을 발견했다. 호환 테스트 식별자 `BattleAnimationControllerTest.lethalMonsterAttackShowsZeroHpDeathThenPersistedReviveHp`로 당시 동작을 검증했다. 이 흐름은 ADR-019와 phase 51에서 event의 `0 HP` 전투 불능 snapshot, 중상 적용/갱신과 50% 응급 회복 lifecycle로 교체했으며, 물리 컬럼 `revivedHp`만 migration 호환을 위해 유지한다.

### Equipment Shop and Inventory v1 최종 검증

2026-07-22 phase 34 최종 검증은 JDK 17과 Android SDK가 준비된 상태에서 연결된 `SM-A325N` Android 13 실기기를 사용했다. `gradlew test`의 debug·release 각 282개, `lint`, `assembleDebug`, `connectedDebugAndroidTest` 57개, harness pytest 69개, 18개 Markdown 문서의 로컬 링크와 `git diff --check`가 실패·오류·skip 없이 통과했다.

연결 검증은 Shop의 일곱 type filter와 `CHEST`·`LEGS` 분리, 같은 slot 상세 비교, 구매 확인·성공 세 action, Inventory 장착, Activity 재생성 뒤 Room 소유·장착 복원과 Character·Battle Map 능력치 갱신, 320dp·글꼴 배율 2.0 접근성을 포함한다. 최초 전체 connected 실행에서 장착 직후 Battle Map HP 갱신 누락을 재현한 뒤 `RoomCombatRepositoryTest.activeObservationSurvivesAtomicEquipmentAndHpUpdates`를 추가하고, 전투 관찰을 관련 table invalidation 뒤 단일 Room read transaction으로 구성해 중간 조합 상태를 제거했다. 화면 이동 직후 HP assertion도 실제 표시 상태를 기다리도록 보강한 뒤 전체 57개를 다시 실행했다.

Room v7 migration은 기존 `character_equipped_items.topId`·`bottomId`를 appearance fallback으로 보존하고 gameplay `CHEST`·`LEGS` 소유권으로 승격하지 않는다. v6에는 `ARMOR` row가 없으므로 migration 변환도 수행하지 않는다. seeded 장비에는 새 전용 bitmap이 없고 대부분 `layerKey`도 없어서 type별 접근 가능한 Material icon placeholder와 기존 appearance fallback을 유지한다.

### Shop 캐릭터·장비 프리뷰 최종 검증

2026-07-22 phase 35 최종 검증은 JDK 17과 Android SDK가 준비된 상태에서 연결된 `SM-A325N` Android 13 실기기를 사용했다. `gradlew test`의 debug·release 각 289개, `lint`, `assembleDebug`, `connectedDebugAndroidTest` 63개와 harness pytest 69개가 실패·오류·skip 없이 통과했다. 18개 Markdown 문서의 로컬 링크 89개와 `git diff --check`도 확인했다.

`ShopViewModelTest`는 `EquipmentStoreSnapshot`의 appearance fallback·실제 gameplay 장착·검증된 render loadout·공식 `DerivedStats`를 하나의 `StateFlow`로 매핑하고, slot/category 단일 선택 source와 구매·장착 commit 전후의 gold·소유·비교·프리뷰 갱신 및 `CHEST`/`LEGS` 독립성을 검증한다. `ShopScreenTest`는 app bar 다음 `캐릭터·장비 프리뷰 → 최종 공격력·최대 체력·방어력 → category → 목록` 순서, shared layered renderer, 일반 좌우 4/3 slot과 폭 `360dp` 미만 또는 font scale `1.5` 이상 compact `FlowRow`, 48dp 선택 target과 장착·빈 slot의 한국어 semantics를 검증한다. `AppNavigationTest`는 실제 application-scope `EquipmentRepository`와 Room Flow에서 구매·바로 장착 직후 같은 화면 갱신, 같은 slot 비교, 외형 fallback, Activity 재생성 복원과 기존 Calendar·Character·Inventory navigation 회귀를 확인한다.

이번 phase는 Room v7 schema·migration과 14종 seed를 변경하지 않았다. seed catalog에는 새 장비 bitmap이 없고 대부분 `layerKey`도 없으므로 type별 Material icon placeholder와 기존 appearance fallback을 유지하며, fallback은 gameplay 소유권이나 장착 source가 아니다.

### Calendar 전투 피드백 가시성 최종 검증

2026-07-22 phase 36 최종 검증은 JDK 17과 Android SDK가 준비된 상태에서 연결된 `SM-A325N` Android 13 실기기를 사용했다. `gradlew test`의 debug·release 각 289개, `lint`, `assembleDebug`, `connectedDebugAndroidTest` 65개와 harness pytest 69개가 실패·오류·skip 없이 통과했고 `git diff --check`도 확인했다. 첫 전체 connected 실행에서 기존 `AppNavigationTest` 한 건이 Activity 시작 전 Compose hierarchy를 찾지 못해 실패했지만 해당 테스트 단독 재실행과 전체 65개 재실행이 모두 통과해 일시적 기기 실행 플레이크임을 확인했다.

`BattleMapTest`는 작은 화면·font scale 2.0에서 EXP label/value의 3dp 간격과 content-width progress bar, 0 진행률 track, standard·compact map의 고대비 HP panel, player/monster 양방향 `공격!`·damage badge와 `600ms` reward badge를 검증한다. `CalendarScreenTest`는 새 combat completion에 legacy 보상 snackbar가 없고 완료 직후 월 이동 action도 유지되는지 검증한다.

### Room v9·Combat Rewards·능력치 설명 검증

- `CombatRewardPolicyTest`는 몬스터 level band, NORMAL/ELITE/BOSS 배율, 비치명 hit XP, 처치 추가 XP·gold와 `GOLD_GAIN_BONUS`의 정수 내림을 검증한다.
- `RoomTaskRepositoryTest`는 신규 완료의 direct award `0/0`, `COMBAT_ATTACK` ledger, 21번째 이후에도 player attack 생성, legacy repair version `0`을 검증한다.
- `RoomCombatRepositoryTest`는 hit/kill reward의 한 번 지급, legacy 무보상, 캐릭터 성장·HP·Stage와 attack snapshot의 transaction을 검증한다.
- `TodoQuestDatabaseMigrationTest`는 v1→v9 chain과 v8 row의 `TODO_COMPLETION`·reward version `0` 기본값을 검증하고 schema는 `app/schemas/com.todoquest.data.local.TodoQuestDatabase/9.json`에 고정한다.
- `CharacterViewModelTest`와 `CharacterScreenTest`는 설명 선택 state, 기본·파생 설명 dialog와 pending 전후 고정 control bounds를 검증한다.

## Windows Room KAPT 문제 해결

Android Studio에서 debug build 중 `:app:kaptDebugKotlin`이 실패하고 아래 메시지가 함께 나오면 Room compiler가 사용하는 SQLite JDBC 네이티브 DLL의 추출 실패 여부를 먼저 확인한다.

```text
No native library found for os.name=Windows, os.arch=x86_64
```

Android Studio JBR 21로 Gradle을 실행하면서 프로젝트의 Kotlin JDK 17 toolchain이 별도 KAPT process worker를 만들면, 해당 worker의 SQLite 임시 경로가 쓰기 불가능한 `C:\WINDOWS`로 결정될 수 있다. 이 경우 로그 앞부분에 `C:\WINDOWS\sqlite-*.dll.lck`에 대한 `AccessDeniedException`이 나타난다.

이 저장소는 Windows에서 KAPT task의 Java toolchain을 현재 Gradle JVM에 맞춘다. Android Studio JBR 21에서는 annotation processing이 별도 JDK 17 process worker 대신 Gradle worker의 `NONE` 격리 모드로 실행되므로 SQLite DLL 추출 경로 문제와 worker classpath 충돌을 함께 피한다. 앱의 Java/Kotlin 결과물은 계속 Java 17을 대상으로 하며 Room과 SQLite JDBC 의존성 버전도 변경하지 않는다.

Android Studio의 Gradle JDK는 프로젝트 기준인 Temurin JDK 17을 권장한다. 다만 저장소의 Windows KAPT 설정은 Android Studio JBR 21에서도 회귀 검증되어 있다. 사용자 또는 시스템의 `TEMP`, `TMP`, `java.io.tmpdir`을 변경하거나 Android Studio를 관리자 권한으로 실행할 필요가 없다.

문제를 다시 진단할 때는 사용 중인 Gradle JDK를 `JAVA_HOME`으로 지정하고 KAPT를 강제 재실행한다.

```powershell
.\gradlew.bat :app:kaptDebugKotlin --rerun-tasks --no-daemon --console=plain --stacktrace
```

정상 판정 기준은 `:app:kaptDebugKotlin`과 전체 build가 성공하고, 출력에 `C:\WINDOWS`의 SQLite lock 파일 접근 실패나 `No native library found`가 없는 것이다. 실행 방식까지 확인하려면 위 명령에 `--info`를 추가하고 JBR 21에서 `Using workers NONE isolation mode to run kapt`가 기록되는지 확인한다.

## Android launch 호환성 검증

앱 실행 후 검은 화면이 보이면 emulator 전용 문제로 단정하지 말고 실제 USB 디버깅 기기와 emulator 양쪽에서 같은 절차로 확인한다. 먼저 launch smoke test를 실행한다.

```powershell
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.todoquest.app.MainActivityLaunchSmokeTest"
```

launch 검증은 다음 세 판정을 분리한다.

- connected test의 app render: `ActivityScenario`와 decor view 상태를 확인하고 Compose root를 캡처한다. 앱이 실제 픽셀을 그렸는지 판정하며 device compositor 상태를 대신하지 않는다.
- diagnostics의 system baseline: Android Settings를 실행한 raw screenshot으로 앱과 무관한 device compositor 상태를 먼저 판정한다.
- diagnostics의 app screenshot: `am start -W` 이후 raw `adb screencap`으로 앱 window가 최종 display composition에 포함됐는지 판정한다.

수동 launch artifact가 필요하면 진단 스크립트를 실행한다. 이미 debug APK가 설치되어 있으면 `-SkipInstall`을 사용한다. 기존 debug APK를 다시 설치하되 Gradle build만 생략하려면 `-SkipBuild`를 사용하고, build와 설치까지 함께 확인하려면 두 옵션을 모두 생략한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_android_launch.ps1 -SkipInstall
```

성공 기준은 다음과 같다.

- `am start -W` 결과가 `Status: ok`로 끝난다.
- system baseline과 app raw screenshot이 모두 non-black이다.
- `MainActivityLaunchSmokeTest.launchRendersInitialCalendar`가 통과하고 app-render metadata가 `RESUMED`, `attached=true`, `shown=true`를 기록한다.
- SurfaceFlinger artifact의 HWC layer 수가 0보다 크고 MainActivity layer가 `hidden=false`이다.
- SurfaceFlinger 접근이 가능한 emulator 또는 debuggable 환경에서는 `VRI-Splash Screen com.todoquest`가 top visible layer로 남지 않는다.

실제 production device는 `dumpsys SurfaceFlinger --layers` 출력이 제한될 수 있다. 이 경우 SurfaceFlinger 실패만으로 launch 실패로 단정하지 말고 screenshot, `am start -W`, process/window 상태를 우선한다.

### Calendar task editor modal 합성 진단

Calendar의 실제 `추가` 버튼을 Android pointer로 탭한 뒤 task editor가 최종 display에 합성되는지 확인하려면 다음 진단을 실행한다. 이미 debug APK가 설치되어 있고 build도 끝났다면 두 skip 옵션을 함께 사용한다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\diagnose_calendar_modal.ps1 -SkipBuild -SkipInstall -LeaveRunning
```

진단기는 깨끗하게 앱을 다시 시작하고 필요할 때 최대 5회만 위로 swipe해 정확히 하나인 `추가` node의 가장 가까운 clickable 조상을 찾는다. 버튼 중앙에는 `adb shell input touchscreen -d 0 tap`을 보내며, `할 일 추가`와 `제목` hierarchy 생성, dialog Window·Surface 표시, SurfaceFlinger HWC layer와 raw screenshot 변화를 서로 독립적으로 판정한다. hierarchy나 connected semantics만 통과한 상태는 성공이 아니다.

기본 artifact 위치는 `app/build/launch-diagnostics/calendar-modal/<serial>`이다. `summary.txt`의 핵심 필드는 `result`, `failureDomain`, `failureReason`, `addButtonMatchCount`, `modalHierarchyFound`, `dialogWindowReady`, `dialogSurfaceShown`, `dialogLayerVisible`, `screenChangedPixelRatio`이며 클릭 전·후 screenshot, UI XML, `dumpsys window windows`, `dumpsys SurfaceFlinger --layers`, logcat 경로도 함께 기록한다. SurfaceFlinger 접근이 가능한 emulator에서는 visible dialog HWC layer가 필수이고, 출력이 제한된 production device에서도 표시된 Window와 raw screenshot 변화가 모두 필요하다.

`failureReason=DialogCompositionHidden`은 hierarchy가 생성됐더라도 dialog Surface·HWC layer 또는 최종 screenshot 변화가 확인되지 않았다는 뜻이다. 이때 진단기는 앱을 force-stop해 보이지 않는 dialog가 입력을 점유하지 않게 한다. 아래 modal-aware GPU 복구로 runtime backend를 바꿔 다시 확인하고, 모든 backend가 같은 이유로 실패하면 hierarchy 성공으로 대체 판정하지 말고 host emulator/system image 조합을 blocked로 기록한다. 열린 modal은 진단 성공과 `-LeaveRunning`이 모두 충족될 때만 유지된다.

Android preview 또는 16 KB page size AVD는 launch transition 회귀가 먼저 드러날 수 있는 고위험 호환성 검증 대상으로 취급한다. 해당 AVD에서 실패하면 안정 API AVD 또는 실제 device에서도 교차 확인하고, 같은 screenshot 검은 화면이 재현되는지 확인한다.

API 37/Android 17 16 KB preview AVD에서 system baseline부터 검거나 modal 합성이 숨겨지면 AVD를 삭제하거나 wipe하지 말고 snapshot loading을 끈 software backend로 교차 확인한다. 다음 명령은 AVD config를 수정하지 않고 현재 실행에만 `software`, `swangle`을 순서대로 적용하며, launch와 Calendar task editor modal이 모두 통과한 첫 인스턴스를 유지한다.

```powershell
.\scripts\recover_android_emulator_graphics.ps1 -AvdName Pixel_9 -GpuModes software,swangle -RestartRunningAvd -VerifyCalendarTaskEditor -LeaveRunning
```

기존 APK를 사용해 Gradle build만 생략하려면 `-SkipBuild`를 추가한다. 복구 요약은 `app/build/launch-diagnostics/graphics-recovery/<AVD>/summary.txt`, backend별 launch artifact는 그 아래에, modal artifact는 각 backend의 `calendar-modal/<serial>`에 기록된다. 모든 backend에서 system baseline부터 검거나 launch는 통과하지만 modal 합성이 모두 실패하면 앱 기능 성공으로 분류하지 말고 host emulator/system image 조합을 blocked로 기록한다.

API 37/Android 17 16 KB preview AVD에서는 AndroidX SplashScreen compat launch transition이 stale `Splash Screen com.todoquest` 또는 `starting_reveal` layer를 남길 수 있다. MVP launch 경로는 `Theme.TodoQuest`를 직접 사용하는 splashless theme를 기본값으로 두고, 위의 app render와 system/app raw screenshot 판정을 함께 사용한다.

## 의존성 버전 정책

- 구현 phase에서 Android Gradle Plugin, Kotlin, Compose BOM, Room, WorkManager 버전을 고정한다.
- 버전 고정 전에는 공식 Android와 Kotlin 문서를 확인한다.
- preview, alpha, beta 버전은 명확한 이유가 없으면 사용하지 않는다.
- JDK는 Android Gradle Plugin 호환성을 기준으로 17을 사용한다.

## Python 테스트 환경

현재 저장소의 harness 스크립트 테스트는 프로젝트 로컬 가상환경을 사용한다. `.venv/`는 커밋하지 않고, `requirements-dev.txt`로 개발 의존성을 재설치한다.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
```

Windows 기본 인코딩과 임시 디렉터리 권한 문제를 피하기 위해 테스트는 다음 방식으로 실행한다.

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## Harness 실행

### Phase manager와 catalog

새 phase 생성의 표준 진입점은 `scripts/run_phase_manager.ps1`이다. 설계 초안의 slug·step·의도된 경로를 `Suggest`로 분류하고 승인 후 `Create`로 registry와 phase index를 생성한 다음 `step{N}.md`를 작성한다.

```powershell
.\scripts\run_phase_manager.ps1 -Command Suggest -Slug <phase-slug> -Step <step-name> -Path <intended-path>
.\scripts\run_phase_manager.ps1 -Command Create -Slug <phase-slug> -Step <step-name> -Path <intended-path>
.\scripts\run_phase_manager.ps1 -Command List
.\scripts\run_phase_manager.ps1 -Command Show -Selector <phase-selector>
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
.\scripts\run_phase_manager.ps1 -Command MigrateLegacy -DryRun
```

`Create`는 다음 숫자 id를 할당하고 `000-009`, `010-019`처럼 10개 단위 bucket 아래에 timestamp 없는 phase index를 생성한다. `Suggest`가 모호하면 임의 확정하지 않고 `-Area`, `-Kind`, `-Tag` override를 명시한다. `List`는 status·area·kind·tag filter를 제공하고, `Show`와 harness 실행은 숫자 id, 기존 basename, registry 상대 `dir` selector를 지원하므로 migration 전 평면 phase도 계속 사용할 수 있다.

`Sync`는 각 phase `index.json`의 step 상태를 실행 원천으로 삼아 `phases/index.json`의 표시 상태와 생성되는 `phases/README.md`를 결정론적으로 갱신한다. `Sync -Check`는 파일을 쓰지 않고 drift만 확인한다. catalog의 outgoing·incoming은 실제 `step*.md`의 명시적 phase 참조에서 계산하는 읽기 전용 catalog 관계이며 registry metadata나 child prompt의 실행 의존성으로 저장하지 않는다. bootstrap-compatible `Validate`는 legacy·신규 항목의 혼합을 허용하고 `-Strict`는 metadata와 10개 단위 bucket migration 완료를 요구한다. 실제 migration은 별도 승인 phase에서만 수행하며 평소에는 `MigrateLegacy -DryRun`의 hash·참조 manifest만 확인한다.

phase 파일 생성 후 같은 요청에서 즉시 구현까지 지시받으면 승인 후 `step{N}.md` 작성을 마치고 아래 harness 래퍼를 추가 입력 없이 호출한다. phase 생성만 요청받으면 실행하지 않으며, `-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다.

### Phase 실행

Harness phase 실행은 `scripts/execute.py`를 직접 호출하지 않고 프로젝트 래퍼를 사용한다. 래퍼는 저장소 루트를 script 위치 기준으로 계산하고, `.venv\Scripts\python.exe` 존재 여부를 확인한 뒤 `PYTHONUTF8=1`을 설정해서 `scripts\execute.py`를 호출한다.

```powershell
.\scripts\run_harness.ps1 -Phase <phase-dir>
```

`-Push`는 사용자가 명시적으로 요청한 경우에만 사용한다.

```powershell
.\scripts\run_harness.ps1 -Phase <phase-dir> -Push
```

phase 파일 생성 후 같은 요청에서 즉시 구현까지 지시받으면 사용자에게 추가 입력을 요구하지 않고 다음 작업으로 `.\scripts\run_harness.ps1 -Phase <phase-dir>`를 호출한다. phase 생성만 요청받으면 래퍼를 실행하지 않는다.

래퍼가 호출하는 `execute.py`는 각 pending step을 별도 `codex exec` child 세션에서 실행하고, 완료 step의 `summary`를 다음 step에 전달한다. 이 구조가 step별 컨텍스트를 격리하므로 부모 Codex 세션을 별도로 정리할 필요가 없다. child step이 `blocked` 또는 `error`로 종료되면 다음 step으로 진행하지 않는다.

Codex 승인 프롬프트는 래퍼 실행 권한을 확인하는 절차이며, 사용자가 별도의 컨텍스트 정리 명령을 입력하도록 요구하는 절차와는 별개다.

`.codex/rules/harness.rules`는 위 래퍼 실행만 `prompt` 대상으로 등록한다. `git`, `python`, `powershell` 전체를 허용하지 않는다. rule을 새로 추가하거나 수정한 뒤에는 Codex를 재시작해야 하며, project-local `.codex` layer가 trusted 상태여야 rule이 로드된다.

`execute.py`가 step 실행을 위해 띄우는 child Codex는 `--dangerously-bypass-approvals-and-sandbox`와 `--dangerously-bypass-hook-trust`로 실행한다. 이는 사용자가 `run_harness.ps1` 실행을 승인한 뒤 Gradle wrapper 배포 zip 다운로드나 Gradle 의존성 접근이 child sandbox 네트워크 제한에 반복 차단되지 않게 하기 위한 harness 내부 정책이다.

### Stop hook 정책

Stop hook은 turn 단위로 실행된다. matcher로 phase 실행만 선별할 수 없으므로 repository runner인 `scripts/stop_hook.py`가 hook event context와 Git changed path를 판별한다.

Harness child Codex에는 `TODO_QUEST_HARNESS_CHILD=1`이 전달되며, runner는 `TODO_QUEST_HARNESS_CHILD=1`이면 Stop 검증을 건너뛴다. 각 child step은 step 파일의 Acceptance Criteria를 직접 실행하고 status를 갱신해야 하며, Acceptance Criteria 실행과 step status가 harness 완료 판정의 기준이다. Phase 완료 후 부모 Stop은 이미 커밋된 phase 전체를 다시 검증하지 않는다. 부모 Stop을 두 번째 phase acceptance gate로 사용하지 않는다.

일반 Codex turn에서도 runner는 Plan, 관련 변경 없음, 동일 fingerprint 성공 상태에서는 즉시 종료한다. 관련 미커밋 변경이 있을 때만 harness 또는 Android 검증군을 선택하며, 두 검증군에 모두 관련된 변경이면 각각 실행한다.

성공 cache는 `build/codex-stop-hook/cache.json`에 검증군별 fingerprint로 저장한다. 검증 출력은 `build/codex-stop-hook/latest-harness.log`와 `build/codex-stop-hook/latest-android.log`에 저장하고, harness pytest 임시 산출물도 `build/codex-stop-hook/pytest-<turn>` 아래에 둔다. Android 검증에서 Gradle은 `test`, `lint`, `assembleDebug`를 한 번의 offline invocation으로 실행한다.

검증 실패 또는 필수 도구 부재 시 runner는 `continue: false`, `stopReason`, `systemMessage`가 있는 continuation JSON을 반환하여 종료를 중단한다. 검증 명령이 실행된 경우 `stopReason`에는 전체 출력 로그 경로와 크기가 제한된 출력 끝부분을 포함한다. Codex는 실패를 수정하거나 필요한 도구가 없으면 blocked로 보고해야 한다.

`.codex/hooks.json` 또는 runner처럼 hook 정의에 영향을 주는 변경 후에는 `/hooks`에서 새 hash를 검토하고 신뢰한 다음 Codex를 재시작해야 한다.
