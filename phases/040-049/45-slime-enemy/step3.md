# Step 3: register-slime-runtime-resources

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/todo-quest-slime-front-idle.png`
- `/docs/art/monster/todo-quest-slime-front-idle-spec.json`
- `/app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png`
- `/app/src/main/res/drawable-nodpi/todo_quest_skeleton_soldier_front_idle.png`
- `/app/src/main/res/drawable-nodpi/todo_quest_corrupted_tree_spirit_front_idle.png`
- `/app/src/main/res/drawable-nodpi/todo_quest_harpy_front_idle.png`
- `/app/src/main/res/values/strings.xml`
- `/scripts/validate_monster_sprite.py`
- `/scripts/test_validate_monster_sprite.py`
- `/app/src/main/java/com/todoquest/domain/model/Monster.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/MonsterSpeciesPolicy.kt`
- `/phases/040-049/45-slime-enemy/index.json`

## 작업

테스트를 먼저 작성해 runtime resource 부재로 실패하는 것을 확인한 뒤 canonical 슬라임 PNG의 byte-identical 사본을 `/app/src/main/res/drawable-nodpi/todo_quest_slime_front_idle.png`로 추가한다. 이미지를 다시 저장하거나 Android density 변환을 적용하지 않는다.

`/app/src/main/res/values/strings.xml`에 다음 한국어 문자열 리소스를 추가한다.

```xml
<string name="battle_monster_slime_name">슬라임</string>
<string name="battle_monster_slime_death_announcement">슬라임이 쓰러졌습니다.</string>
```

`scripts/test_validate_monster_sprite.py`에 canonical/runtime byte equality와 SHA-256 equality 테스트를 추가한다. 기존 네 몬스터 resource와 문자열은 유지한다. 새 bitmap 안에는 이름, 체력바, UI, 그림자, 물웅덩이, 이펙트와 배경을 합성하지 않는다.

이 step에서는 step 2가 추가한 `SLIME` enum과 결정적 정책을 읽기만 하며 Kotlin source나 compile-only presentation 분기를 수정하지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-slime-front-idle.png --spec docs\art\monster\todo-quest-slime-front-idle-spec.json
$canonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-slime-front-idle.png).Hash
$runtime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_slime_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime slime sprite differs from canonical art' }
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_monster_sprite.py --basetemp build\pytest-45-slime-resource
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. equality 테스트를 먼저 작성하고 runtime resource 추가 전 예상된 실패를 확인한다.
2. PNG를 byte-for-byte 복사하고 문자열을 추가한 뒤 AC 명령을 실행한다.
3. PNG가 64×64 RGBA이고 canonical/runtime 파일이 완전히 같은지 확인한다.
4. 새 사용자 노출 문구가 한국어 strings resource에만 존재하는지 확인한다.
5. task index의 step 3을 `completed`로 변경하고 canonical/runtime 경로, hash equality와 문자열 resource를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- runtime PNG를 리사이즈하거나 다시 인코딩하지 마라. 이유: canonical과 byte-identical한 픽셀 계약을 유지해야 한다.
- Compose 또는 ViewModel에 `슬라임` 문장을 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 기존 네 몬스터 resource를 덮어쓰지 마라. 이유: 다섯 종족은 독립 resource로 공존해야 한다.
- PNG에 HP, 그림자, 웅덩이 또는 효과를 합성하지 마라. 이유: Battle Map의 독립 레이어 계약을 유지해야 한다.
- 테스트보다 resource를 먼저 추가하지 마라. 이유: AGENTS.md의 CRITICAL 테스트 우선 규칙을 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
