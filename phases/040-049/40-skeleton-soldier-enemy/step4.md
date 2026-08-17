# Step 4: register-skeleton-runtime-resources

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle.png`
- `/docs/art/monster/todo-quest-skeleton-soldier-front-idle-spec.json`
- `/app/src/main/res/drawable-nodpi/todo_quest_goblin_scout_front_idle.png`
- `/app/src/main/res/values/strings.xml`
- `/scripts/validate_monster_sprite.py`
- `/phases/040-049/40-skeleton-soldier-enemy/index.json`

## 작업

canonical 해골 PNG의 byte-identical runtime 사본을 `/app/src/main/res/drawable-nodpi/todo_quest_skeleton_soldier_front_idle.png`로 추가한다. 이미지 픽셀을 다시 저장하거나 Android density 변환을 적용하지 않는다.

`/app/src/main/res/values/strings.xml`에 다음 한국어 문자열 리소스를 추가한다.

```xml
<string name="battle_monster_skeleton_soldier_name">해골 병사</string>
<string name="battle_monster_skeleton_soldier_death_announcement">해골 병사가 쓰러졌습니다.</string>
```

기존 `battle_monster_goblin_scout_name`과 고블린 쓰러짐 안내는 유지한다. 새 bitmap 안에는 이름, 체력바, UI, 그림자와 배경을 합성하지 않는다.

canonical/runtime SHA-256과 byte equality를 검증하는 Python 또는 기존 battle-map validator 테스트를 먼저 추가한다. 새 resource 이름이 Android resource 처리와 APK packaging을 통과하는지 검증한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-skeleton-soldier-front-idle.png --spec docs\art\monster\todo-quest-skeleton-soldier-front-idle-spec.json
$canonical = (Get-FileHash -Algorithm SHA256 docs\art\monster\todo-quest-skeleton-soldier-front-idle.png).Hash
$runtime = (Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_skeleton_soldier_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime skeleton sprite differs from canonical art' }
.\gradlew.bat :app:assembleDebug
git diff --check
```

## 검증 절차

1. equality 테스트를 먼저 작성하고 runtime resource 추가 전 실패를 확인한다.
2. PNG를 byte-for-byte 복사하고 문자열을 추가한 뒤 AC 명령을 실행한다.
3. PNG가 64×64 RGBA이고 Android resource optimizer에 의해 canonical 파일과 달라지지 않았는지 확인한다.
4. 새 사용자 노출 문구가 한국어 strings resource에만 존재하는지 확인한다.
5. task index의 step 4를 `completed`로 변경하고 canonical/runtime 경로와 hash equality를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- runtime PNG를 리사이즈하거나 다시 인코딩하지 마라. 이유: canonical과 byte-identical한 픽셀 계약을 유지해야 한다.
- Compose 또는 ViewModel에 `해골 병사` 문장을 하드코딩하지 마라. 이유: AGENTS.md의 한국어 문자열 resource 규칙을 지켜야 한다.
- 고블린 resource를 덮어쓰지 마라. 이유: 두 종족은 독립 resource로 공존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
