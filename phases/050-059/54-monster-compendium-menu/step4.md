# Step 4: document-monster-compendium

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/domain/repository/CombatRepository.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomCombatRepository.kt`
- `/app/src/main/java/com/todoquest/feature/compendium/`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/phases/050-059/54-monster-compendium-menu/step0.md`
- `/phases/050-059/54-monster-compendium-menu/step1.md`
- `/phases/050-059/54-monster-compendium-menu/step2.md`
- `/phases/050-059/54-monster-compendium-menu/step3.md`
- `/phases/050-059/54-monster-compendium-menu/index.json`

## 작업

완성된 구현과 테스트를 먼저 확인한 뒤 canonical 문서를 실제 동작과 동기화한다.

- `docs/PRD.md`에 승인된 후속 범위 `Monster Compendium v1`을 추가한다. 네 번째 도감 탭, Monster-only category, 5종 이름 상시 표시, 활성 등장 즉시 발견, 기존 기록 소급, 발견 시 image/detail 공개와 미발견 name-only 계약을 기록한다.
- `docs/ARCHITECTURE.md`의 composition root와 navigation을 Calendar, Character, Shop, Compendium 네 top-level destination으로 갱신한다. `monster_instances → CombatDao Flow → RoomCombatRepository.observeDiscoveredMonsterSpecies() → Compendium ViewModel` projection, nested list/detail route와 Database v14 무 migration 결정을 기록한다.
- `docs/ADR.md`에 다음 번호인 ADR-021을 추가해 발견 상태를 별도 ledger가 아닌 versioned 전투 이력의 결정적 projection으로 선택한 이유, 기존 사용자 소급과 향후 policy version routing 책임을 기록한다.
- `docs/UI_GUIDE.md`의 고정 하단 navigation 표시명을 `캘린더`, `캐릭터`, `상점`, `도감`으로 갱신하고 root/category/list/detail, 발견·미발견, 한국어·TalkBack·48dp·최근접 sprite 계약을 기록한다.
- `docs/DEVELOPMENT.md`에 실제 실행한 debug/release JVM, lint, APK, connected, harness, phase manager, Markdown link와 diff 검증 결과 및 사용한 장치/AVD를 과장 없이 기록한다.

문서에 펫·맵이 향후 동일 category/route 구조로 확장 가능하다고만 기록하고 placeholder가 현재 구현되었다고 쓰지 않는다. 몬스터 상세에 종족별 고정 능력치, biome, 스킬 또는 전리품이 있다고 암시하지 않는다.

전체 기능 검증을 통과한 뒤 task index의 step 4를 `completed`와 한국어 `summary`로 갱신한다. 그 다음 phase manager `Sync`와 `Sync -Check`를 실행해 step 상태를 top-level registry와 생성 catalog에 반영한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp-phase54-final
.\scripts\run_phase_manager.ps1 -Command Validate
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
.\scripts\run_phase_manager.ps1 -Command Sync
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. Step 0~3의 completed summary와 실제 변경 파일을 읽고 문서가 구현보다 앞서거나 뒤처지지 않게 수정한다.
2. 전체 Android·connected·harness·문서 link·phase manager 검증을 실행한다.
3. Android 도구나 connected device가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
4. 기존 `.venv/pytest-tmp` ACL 문제가 있으면 삭제·권한 변경하지 말고 위 phase 전용 basetemp를 사용한다.
5. 모든 기능 검증 성공 후 task index의 step 4를 `completed`로 바꾸고 최종 수치와 핵심 결정을 한국어 `summary` 한 줄로 기록한다.
6. `Sync`와 `Sync -Check`로 registry/catalog drift가 없음을 확인한다.

## 금지사항

- 통과하지 않은 테스트를 성공으로 문서화하지 마라. 이유: DEVELOPMENT는 재현 가능한 검증 기록이어야 한다.
- 펫·맵 placeholder나 빈 화면을 구현 완료로 기록하지 마라. 이유: 이번 phase 범위가 아니다.
- Room database version이나 schema export를 변경하지 마라. 이유: 발견 상태는 기존 `monster_instances` projection이다.
- 외부 Google Calendar, 계정 또는 서버 동기화를 추가하지 마라. 이유: MVP의 로컬 전용 범위를 유지해야 한다.
- 파괴적 명령이나 Android 도구 자동 설치를 실행하지 마라. 이유: AGENTS.md CRITICAL 개발 프로세스를 준수해야 한다.
- 기존 테스트를 깨뜨리지 마라.
