# Step 1: publish-fairy-guide-runtime-resource

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
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/step0.md`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`

## 작업

수정된 canonical 페어리 PNG를 Android runtime resource로 게시한다.

먼저 `/scripts/test_validate_npc_sprite.py`의 runtime byte identity와 SHA-256 테스트를 실행해 canonical은 새 파일이고 runtime은 이전 파일이므로 실패하는 것을 확인한다. 그 뒤 canonical PNG를 재인코딩·리사이즈·팔레트 변환하지 않고 `/app/src/main/res/drawable-nodpi/todo_quest_fairy_guide_front_idle.png`에 byte-for-byte 복사한다.

현재 `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`의 `FairyGuideSprite`는 이미 다음 계약을 만족하므로 읽기 전용으로 확인하고 수정하지 않는다.

- `96dp × 96dp` 정사각형 frame.
- 전체 `64×64` canvas를 `ContentScale.Fit`으로 균등 표시.
- `FilterQuality.None`으로 팔레트 외 보간색 방지.
- decode 실패 시 같은 frame 안의 decorative `Info` icon fallback.

기존 `CharacterScreenTest`의 frame 크기와 무보간 팔레트 검증을 유지한다. 새 문자열, UI state, ViewModel, Repository, Room, WorkManager와 AlarmManager 변경은 만들지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py --basetemp build\pytest-57-fairy-runtime
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-fairy-guide-front-idle.png --spec docs\art\npc\todo-quest-fairy-guide-front-idle-spec.json
$canonical=(Get-FileHash -Algorithm SHA256 docs\art\npc\todo-quest-fairy-guide-front-idle.png).Hash
$runtime=(Get-FileHash -Algorithm SHA256 app\src\main\res\drawable-nodpi\todo_quest_fairy_guide_front_idle.png).Hash
if ($canonical -ne $runtime) { throw 'Runtime fairy sprite differs from canonical art' }
.\gradlew.bat :app:assembleDebug --console=plain
git diff --check
```

## 검증 절차

1. runtime byte identity 테스트가 복사 전 실패하는 것을 확인한다.
2. canonical bytes를 runtime 경로에 그대로 복사한다.
3. AC를 실행하고 두 SHA-256이 같은지 확인한다.
4. `drawable-nodpi` 외에 density별 사본이 생기지 않았는지 확인한다.
5. Android/JDK 도구가 없으면 설치하지 말고 `blocked`로 기록한다.
6. phase index step 1을 `completed`로 바꾸고 runtime 경로·최종 SHA-256·assemble 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- runtime PNG를 별도로 편집하거나 재인코딩하지 마라. 이유: canonical과 byte-identical 배포본이어야 한다.
- `FillBounds`나 비정사각형 frame으로 UI를 변경하지 마라. 이유: 현재 구현이 이미 균등 Fit 계약을 충족한다.
- 다른 캐릭터·NPC drawable을 변경하지 마라. 이유: 이번 수정은 페어리 한 장만 대상으로 한다.
- Kotlin, 문자열, ViewModel과 데이터 계층을 변경하지 마라. 이유: 이미지 교체만으로 앱 표시가 갱신된다.
- 기존 테스트를 깨뜨리지 마라.

