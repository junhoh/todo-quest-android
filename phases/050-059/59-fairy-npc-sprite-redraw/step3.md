# Step 3: publish-fairy-runtime-resource

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/scripts/test_validate_npc_sprite.py`
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/androidTest/java/com/todoquest/feature/character/CharacterScreenTest.kt`
- `/phases/050-059/59-fairy-npc-sprite-redraw/step2.md`
- `/phases/050-059/59-fairy-npc-sprite-redraw/index.json`

## 작업

검증된 canonical 요정 PNG를 Android runtime resource로 게시한다. 이 step은 `app/src/main/res/drawable-nodpi/`의 요정 PNG만 수정한다.

먼저 기존 runtime byte identity 테스트를 실행해 canonical은 새 파일이고 runtime은 이전 파일이라 실패하는 것을 확인한다. 그 뒤 canonical bytes를 재인코딩·리사이즈·palette 변환하지 않고 `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`에 그대로 복사한다.

`CharacterScreen.kt`의 기존 `FairyGuideSprite`가 다음 계약을 유지하는지 읽기 전용으로 확인한다.

- `96dp × 96dp` 정사각형 frame.
- 전체 `64×64` canvas를 `ContentScale.Fit`으로 균등 표시.
- `FilterQuality.None`으로 보간색 방지.
- decode 실패 시 같은 frame의 decorative `Info` icon fallback.

Kotlin, 문자열, ViewModel, Repository와 Room은 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py --basetemp build\pytest-59-runtime
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat test lint assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. runtime copy 전에 byte identity 테스트 실패를 확인한다.
2. canonical을 runtime 경로에 byte-for-byte 복사한다.
3. Python 검증과 Gradle test·lint·assembleDebug를 실행한다.
4. `drawable-nodpi` 이외에 density별 사본이 생기지 않았는지 확인한다.
5. Android/JDK 도구가 없으면 설치하지 말고 `blocked`로 기록한다.
6. phase index의 step 3을 `completed`로 바꾸고 runtime SHA-256과 Gradle 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- runtime PNG를 별도로 편집하거나 재인코딩하지 마라. 이유: canonical과 byte-identical해야 한다.
- Kotlin UI를 수정하지 마라. 이유: 현재 정사각형 Fit·무보간 계약이 이미 요구사항을 충족한다.
- 다른 drawable이나 density resource를 변경하지 마라. 이유: 이번 범위는 요정 한 장뿐이다.
- Android 도구를 임의 설치하지 마라. 이유: AGENTS.md의 구현 phase 도구 정책을 따른다.
- 기존 테스트를 깨뜨리지 마라.
