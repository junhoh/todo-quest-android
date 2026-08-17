# Step 2: publish-fairy-guide-resource

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/scripts/test_validate_npc_sprite.py`
- `/app/src/main/res/drawable-nodpi/`
- `/app/build.gradle.kts`
- `/phases/050-059/56-fairy-character-stat-guide/step1.md`
- `/phases/050-059/56-fairy-character-stat-guide/index.json`

## 작업

canonical fairy PNG를 Android runtime resource로 게시한다. 먼저 `/scripts/test_validate_npc_sprite.py`에 다음 회귀 테스트를 추가해 runtime 파일 부재 실패를 확인한다.

- fairy canonical PNG가 JSON 계약을 계속 통과한다.
- `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`가 canonical PNG와 byte-identical하다.
- canonical과 runtime PNG의 SHA-256이 같다.

그 뒤 canonical PNG를 재인코딩하거나 리사이즈하지 않고 byte-for-byte 그대로 runtime 경로에 복사한다. Kotlin·문자열·UI는 이 step에서 수정하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py --basetemp build\pytest-56-fairy-resource
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. runtime 테스트를 먼저 추가해 파일 부재 실패를 확인한다.
2. byte-for-byte 복사 후 AC를 실행한다.
3. resource 이름과 `drawable-nodpi` 배치를 확인한다.
4. Android/JDK 도구가 없으면 설치하지 말고 `blocked`로 기록한다.
5. phase index step 2를 완료 처리하고 경로·SHA 일치·assemble 결과를 한국어로 요약한다.

## 금지사항

- runtime PNG를 별도로 편집하거나 재인코딩하지 마라. 이유: canonical과 byte-identical이어야 한다.
- density별 drawable 폴더에 추가 사본을 만들지 마라. 이유: Android 재샘플링과 분리해야 한다.
- UI를 연결하지 마라. 이유: 이 step은 Android resource 게시만 담당한다.
- 기존 테스트를 깨뜨리지 마라.

