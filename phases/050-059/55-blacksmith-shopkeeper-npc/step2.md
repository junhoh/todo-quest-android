# Step 2: publish-blacksmith-shopkeeper-resource

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json`
- `/scripts/test_validate_npc_sprite.py`
- `/app/src/main/res/drawable-nodpi/`
- `/app/build.gradle.kts`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/step1.md`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/index.json`

## 작업

canonical NPC PNG를 Android runtime resource로 게시한다. 먼저 `/scripts/test_validate_npc_sprite.py`에 아래 회귀 테스트를 추가해 runtime 파일 부재로 실패하는 것을 확인한다.

- canonical PNG가 JSON 계약을 계속 통과한다.
- `/app/src/main/res/drawable-nodpi/todo_quest_blacksmith_shopkeeper_front_idle.png`가 canonical PNG와 byte-identical하다.
- canonical과 runtime PNG의 SHA-256이 같다.

그 뒤 canonical PNG를 재인코딩하거나 리사이즈하지 않고 byte-for-byte 그대로 runtime 경로에 복사한다. `/docs/art/npc/README.md`의 runtime source 설명을 실제 경로와 검증 명령으로 갱신한다. Kotlin 코드, 문자열 리소스와 Shop UI는 이 step에서 수정하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py --basetemp build\pytest-55-blacksmith-resource
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png --spec docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle-spec.json
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_blacksmith_shopkeeper_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime blacksmith sprite differs from canonical art' }
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. runtime byte identity 테스트를 먼저 추가해 파일 부재 실패를 확인한다.
2. canonical을 byte-for-byte 복사한 뒤 AC를 실행한다.
3. Android resource name이 lowercase underscore 규칙을 지키고 `drawable-nodpi`에서 밀도 재샘플링되지 않는지 확인한다.
4. Android/JDK 도구가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
5. phase index의 step 2를 `completed`로 변경하고 runtime 경로·SHA-256 일치·assemble 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- runtime PNG를 별도로 편집하거나 재인코딩하지 마라. 이유: canonical과 runtime은 byte-identical이어야 한다.
- `drawable` 또는 density별 폴더에 추가 사본을 만들지 마라. 이유: 최근접 논리 픽셀을 Android 밀도 처리와 분리한다.
- Shop UI나 ViewModel을 수정하지 마라. 이유: 이 step은 runtime resource 게시 경계만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
