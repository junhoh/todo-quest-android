# Step 7: document-and-validate-corrections

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCharacterRepository.kt`
- `/app/src/main/java/com/todoquest/feature/battle/PlayerProgressHud.kt`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/phases/030-039/37-overdue-hud-stat-allocation-fixes/index.json`

## 작업

canonical 문서와 검증표를 실제 구현에 맞게 갱신한다.

- PRD와 ADR에 자동 `MISSED_DEADLINE` attack과 occurrence `FAILED` source가 같은 transaction에서 확정되고 v8 migration이 기존 자동 event를 일회성 backfill한다는 계약을 기록한다.
- ARCHITECTURE에 UI→ViewModel→일괄 UseCase→Repository transaction 경계와 비영속 stat draft, 파생값 비저장 원칙을 반영한다.
- UI_GUIDE에 좌측 level/우측 gold+EXP HUD, 12dp group 간격, EXP content-width bar, Base Stats 안의 미배분 포인트와 `-/+/저장`, pending 표시, 저장 전 파생값·HP 비preview를 기록한다.
- 캐릭터 구현 문서에 typed batch allocation과 최신 Room 상태 재검증, HP 비율 1회 보존을 반영한다.
- DEVELOPMENT의 Room migration, 자동 실패, HUD, Character unit/connected test matrix와 실제 명령을 갱신한다.

전체 검증을 실행한다. 연결 기기 검증은 필수 acceptance gate이며 현재 기기가 없으면 Android 도구나 AVD를 임의 설치하지 않고 이 step을 `blocked`로 기록한다. 모든 명령 성공 뒤 phase index의 모든 step이 `completed`인지 확인하고 root `phases/index.json`의 phase 상태를 `completed`, `completed_at`, 한국어 `summary`와 함께 갱신한다.

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

1. 문서 변경 전에 production code와 test에서 확정된 실제 시그니처·상태·간격을 다시 확인한다.
2. AC를 순서대로 실행하고 JVM debug/release, lint, APK와 androidTest APK, 연결 suite, harness pytest, 문서 링크가 모두 성공하는지 확인한다.
3. AGENTS.md의 occurrence 멱등성, 반복 원본 분리, UI data boundary, 한국어 문자열과 테스트 우선 규칙을 최종 검토한다.
4. 연결 기기나 emulator가 없으면 설치하지 말고 step과 phase를 `blocked`로 기록하며 필요한 사용자 조치를 명시한다.
5. 성공하면 task index step 7과 root phase를 `completed`로 갱신하고 전체 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 실제 구현과 다른 미래 계약을 완료 상태로 문서화하지 마라. 이유: canonical 문서와 코드가 다시 불일치하게 된다.
- 연결 테스트 실패나 미실행을 성공으로 기록하지 마라. 이유: 이번 phase의 시각·상호작용 변경은 연결 검증이 필수다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows/Codex 표준 진입점은 `run_harness.ps1`이다.
- Android 도구, SDK package 또는 AVD를 임의 설치하지 마라. 이유: 사용자 환경 변경은 별도 승인 phase에서만 허용된다.
- 기존 테스트를 깨뜨리지 마라.
