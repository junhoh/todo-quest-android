# Step 9: document-and-validate-reward-polish

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/monster-stats-and-growth.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/todo-combat-rewards.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomTaskRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/phases/030-039/38-hud-stat-descriptions-combat-rewards/index.json`

## 작업

canonical 문서와 검증표를 실제 구현 결과에 맞게 최종 동기화한다.

- PRD·ADR-014에 모든 신규 완료의 combat reward 대체, 무제한 player attack outbox, legacy 비소급과 best-effort 완료 격리를 기록한다.
- ARCHITECTURE에 Room v9 `rewardMode`·combat reward snapshot, TaskRepository→PENDING event→CombatRepository 단일 transaction과 Character Flow/HUD data flow를 반영한다.
- monster·character reward 문서에 level/grade 공식, gold bonus, hit/kill 구분, level-up HP ratio와 처치 recovery 순서를 기록한다.
- UI_GUIDE에 실제 EXP text-width bar·0 track, 600ms reward badge, Calendar combat reward 안내, 고정 stat cluster와 12개 설명 dialog를 기록한다.
- DEVELOPMENT에 Room v9 migration, reward policy/repository/HUD/Character test matrix와 실행 결과를 반영한다.

전체 검증을 실행한다. 연결 test는 필수 acceptance gate다. 모든 step이 `completed`인지 확인한 뒤 task index의 step 9와 root `phases/index.json`의 phase를 `completed`, `completed_at`, 한국어 `summary`로 갱신한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
git diff --check
```

## 검증 절차

1. production 시그니처·Room schema·실제 UI 문자열과 step 0의 승인 계약을 대조해 문서를 갱신한다.
2. AC를 순서대로 실행하고 JVM debug/release, lint, APK·androidTest APK, 전체 connected suite, harness pytest, 문서 링크가 모두 성공하는지 확인한다.
3. AGENTS.md의 occurrence 멱등성, 반복 원본 분리, UI data boundary, 한국어 resource와 테스트 우선 규칙을 최종 검토한다.
4. 연결 기기가 없거나 필수 도구가 사라지면 설치하지 말고 step과 phase를 `blocked`로 기록한다.
5. 성공하면 step과 root phase를 완료 처리하고 Room v9·경제 변경·세 UI 개선·전체 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 실제 구현과 다른 미래 보상·전리품을 완료로 문서화하지 마라. 이유: canonical 문서와 코드가 불일치하게 된다.
- 연결 test 실패나 미실행을 성공으로 기록하지 마라. 이유: 세 UI 변경과 application Flow 연동은 실기 검증이 필요하다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows 표준 진입점은 `run_harness.ps1`이다.
- Android 도구를 임의 설치하지 마라. 이유: 사용자 환경 변경은 별도 승인 phase에서만 허용된다.
- 기존 테스트를 깨뜨리지 마라.
