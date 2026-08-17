# Step 6: synchronize-and-validate

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/`
- `/phases/050-059/53-equipment-shop-ux-polish/step5.md`
- `/phases/050-059/53-equipment-shop-ux-polish/index.json`

## 작업

canonical 문서와 실제 구현을 동기화한다. ADR-013에는 해제가 `character_equipment` 대상 row만 제거하고 소유권을 보존하며, 같은 transaction에서 명시적으로 해제한 slot의 appearance fallback을 기본 복장으로 바꾸고 MAX_HP 비율을 보존한다는 후속 계약을 기록한다. gameplay source와 appearance source를 소유권 관점에서 합치지 않으며, 해제 command가 사용자 의도를 shared renderer에 반영하기 위해 대상 fallback만 원자 갱신한다는 경계를 명확히 한다.

Architecture에는 `UnequipEquipmentUseCase → RoomEquipmentRepository transaction → store/inventory/character Flow → Shop·Character·Battle shared renderer` data flow와 `AlreadyEmpty` 멱등성을 기록한다. UI Guide에는 Shop slot 관리 popup, Inventory 해제, 요구 레벨 상시 표시, owned card의 색+테두리+icon+text, 판매 중 badge 제거/판매 중지 예외, 일반 96dp·compact 120dp preview slot을 기록한다. 게임 설계 문서에는 소유 보존, modifier 제거와 HP 비율, 기본 복장 slot mapping을 기록한다. PRD 구현 상태와 DEVELOPMENT의 실제 검증 환경·수치를 갱신한다.

전체 검증을 실행한다. connected device가 없거나 Android 도구가 없으면 설치하지 말고 step과 phase를 `blocked`로 기록한다. 수행하지 못한 검증을 성공으로 기록하지 않는다. 성공 후 step 6과 phase index를 `completed`로 갱신하고 한국어 summary를 기록한 다음 manager `Sync`로 root index와 README를 동기화하고 `Validate`, `Sync -Check`를 통과시킨다.

최종 summary에는 해제 transaction/멱등성, 기본 복장 shared renderer, 상점 카드·preview UX, unit/connected/harness 실제 수치를 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; assert not bad,bad; print(f'{len(files)} markdown files checked')"
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. 전체 unit·lint·assemble·connected·harness·문서 링크 AC를 실행하고 실패나 skip을 성공으로 기록하지 않는다.
2. AGENTS.md CRITICAL 규칙, ADR-013 source 분리, occurrence 보상 멱등성과 반복 일정 분리를 최종 확인한다.
3. step 6과 phase를 `completed`로 갱신한 뒤 `.\scripts\run_phase_manager.ps1 -Command Sync`와 `-Command Sync -Check`를 실행하고 drift가 없는지 확인한다.
4. task index와 `/phases/index.json`, `/phases/README.md`에 실제 결과가 일치하는지 확인한다.

## 금지사항

- 수행하지 못한 connected test를 성공으로 기록하지 마라. 이유: phase 완료는 실제 connected 검증 근거가 필요하다.
- 새 Room migration이나 장비 asset을 구현했다고 문서화하지 마라. 이유: 이번 phase 범위가 아니다.
- gameplay 소유권과 appearance fallback을 같은 source라고 문서화하지 마라. 이유: ADR-013의 분리 원칙은 유지된다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows/Codex 표준 진입점은 `run_harness.ps1`이다.
- 기존 테스트를 깨뜨리지 마라.
