# Step 4: synchronize-blacksmith-shopkeeper-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle-spec.json`
- `/scripts/test_validate_npc_sprite.py`
- `/app/src/main/java/com/todoquest/feature/shop/ShopScreen.kt`
- `/app/src/main/res/values/strings.xml`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/step3.md`
- `/phases/050-059/55-blacksmith-shopkeeper-npc/index.json`

## 작업

canonical 문서와 실제 대장장이 NPC·Shop 구현을 동기화하고 전체 regression을 검증한다.

- PRD의 Shop 콘텐츠 순서를 `대장장이 인사 → 캐릭터·장비 프리뷰 → 최종 능력치 → category filter → 판매 목록`으로 갱신한다. NPC는 정적 presentation이며 경제·소유·장착·보상 source가 아니라는 범위를 기록한다.
- ARCHITECTURE의 Shop presentation 설명에 canonical `docs/art/npc` PNG/JSON, byte-identical `drawable-nodpi`, `FilterQuality.None`, decode 실패 Material fallback을 추가한다. Room, Repository, ViewModel과 application wiring은 변경되지 않았음을 명시한다.
- UI_GUIDE의 상점 순서, 일반/compact 인사 카드, 정확한 한국어 호칭·인사·TalkBack, 104dp 동일 logical canvas와 비대화형 동작을 기록한다.
- `docs/art/npc/README.md`에 canonical/runtime 역할, 64×64·bounds·팔레트·외곽선·alpha 계약, 플레이어 reference의 비편집 역할, validator·SHA 검증 명령을 최종화한다.
- `docs/README.md`에서 NPC 아트 인덱스로 탐색 가능하게 한다.
- 새 아키텍처 결정이 없으므로 ADR을 추가하거나 기존 ADR 번호를 변경하지 않는다.
- DEVELOPMENT에 phase 55의 실제 도구·unit/lint/assemble/connected/harness 결과와 검증 경계를 기록한다. 실행하지 않은 수치나 성공을 추정하지 않는다.

전체 검증 성공 후 step 4를 `completed`로 변경하고 phase 완료 summary를 한국어로 기록한다. 이어 manager `Sync`로 root index와 README를 동기화한 뒤 `Validate`, `Sync -Check`를 통과시킨다. 최종 summary에는 canonical/runtime 경로, Shop 인사 카드, exact 한국어 문구, art/Compose/full regression 실제 결과를 포함한다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle.png --spec docs\art\npc\todo-quest-blacksmith-shopkeeper-front-idle-spec.json
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py scripts\test_validate_monster_sprite.py scripts\test_validate_character_sheet.py scripts\test_execute.py --basetemp build\pytest-55-blacksmith-final
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest --console=plain
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. AC를 순서대로 실행하고 실패·오류·skip을 성공으로 기록하지 않는다.
2. 1배율·8배율 PNG와 Shop 일반/compact 화면을 최종 육안 확인한다.
3. AGENTS.md CRITICAL 규칙, occurrence 보상 멱등성, 반복 일정 분리와 권한 실패 독립성이 변경되지 않았는지 확인한다.
4. Android SDK나 안정적인 emulator가 없으면 임의 설치하지 말고 step과 phase를 `blocked`로 기록한다.
5. step 4와 phase index를 `completed`로 갱신한 뒤 `.\scripts\run_phase_manager.ps1 -Command Sync`와 `-Command Sync -Check`를 실행한다.
6. task index, `/phases/index.json`, `/phases/README.md`의 상태와 summary가 일치하는지 확인한다.

## 금지사항

- Room schema, DAO, Repository, ViewModel, 장비 catalog나 구매 transaction을 변경하지 마라. 이유: NPC는 정적 Shop presentation이다.
- 새 ADR을 만들지 마라. 이유: 기존 presentation·resource 경계 안의 기능이며 새 아키텍처 결정을 요구하지 않는다.
- 수행하지 못한 connected test나 art 검증을 성공으로 기록하지 마라. 이유: phase 완료는 실제 검증 근거가 필요하다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows/Codex 표준 진입점은 `run_harness.ps1`이다.
- 기존 테스트를 깨뜨리지 마라.
