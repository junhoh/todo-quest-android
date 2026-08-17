# Step 8: synchronize-and-validate

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/`
- `/app/schemas/com.todoquest.data.local.TodoQuestDatabase/7.json`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

구현과 테스트의 실제 상태를 canonical 문서에 최종 동기화한다. PRD/ADR에는 구매·장착 승인 범위, 중복 금지와 원자성, Room v7, CHEST/LEGS 독립성과 외형 fallback을 기록한다. Architecture에는 EquipmentRepository, seeder, 네 테이블과 Character/Task/Combat calculator data flow, Shop/Inventory navigation을 기록한다. UI Guide에는 filter 순서, 카드/상세/확인/성공 UX, gold 인접 배치, rarity와 placeholder 접근성, 큰 글꼴 계약을 기록한다. game design 문서에는 기존 네 기본 스탯을 사용한 modifier source, 장착 시 HP 비율 보존과 실제 테스트 위치를 기록한다.

전체 검증을 실행한다. connected device가 있으면 모든 instrumentation test를 실행하고 Shop 목록·CHEST/LEGS filter·상세 same-slot 비교·구매 확인/성공·Inventory 장착·Character/Battle stat 갱신과 작은 화면/큰 글꼴을 test evidence로 확인한다. connected device가 없거나 Android 도구가 사라졌으면 임의 설치하지 말고 step과 phase를 `blocked`로 기록한다. 기존 JVM test output lock이 재현되면 관련 사용자 프로세스를 임의 종료하거나 tracked 파일을 삭제하지 말고 정상 재시도 후 정확한 원인을 기록한다.

실패가 있으면 이번 phase 회귀를 테스트 우선으로 수정하고 AC를 다시 실행한다. 최종 summary에는 변경/추가 파일군, TOP/BOTTOM 외형과 CHEST/LEGS gameplay 분리, ARMOR migration 적용 여부, 구매·장착·재계산 data flow, 추가 테스트, 실제 명령 결과와 남은 image/asset 제한을 포함한다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; assert not bad,bad; print(f'{len(files)} markdown files checked')"
git diff --check
```

## 검증 절차

1. 전체 unit·lint·assemble·connected·harness·문서 링크 AC를 실행하고 실패/skip을 성공으로 기록하지 않는다.
2. AGENTS.md CRITICAL 규칙, Room v7 비파괴 migration, 구매/장착 transaction, CHEST/LEGS 독립성과 기존 occurrence·전투 멱등성을 최종 확인한다.
3. task index의 step 8을 `completed`로 바꾸고 실제 검증 수치와 제한사항을 한국어 `summary` 한 줄로 기록한다.
4. 모든 step이 completed면 task index와 `/phases/index.json`의 phase 34를 `completed`로 바꾸고 완료 시각과 한국어 summary를 기록한다.

## 금지사항

- 수행하지 못한 connected test나 잠금으로 실패한 JVM test를 성공으로 기록하지 마라. 이유: phase status는 실제 Acceptance Criteria 근거여야 한다.
- 기존 외형 row를 ARMOR gameplay migration 결과라고 문서화하지 마라. 이유: v6에는 저장된 ARMOR slot이 없다.
- Google Calendar, equipment quantity, pet slot이나 새 bitmap 제작을 후속 구현 없이 완료 범위에 포함하지 마라. 이유: 승인된 phase 밖의 기능이다.
- 기존 테스트를 깨뜨리지 마라.
