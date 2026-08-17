# Step 4: synchronize-and-validate

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
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/`
- `/app/src/main/res/values/strings.xml`
- `/app/src/test/java/com/todoquest/feature/shop/ShopViewModelTest.kt`
- `/app/src/androidTest/java/com/todoquest/feature/shop/ShopScreenTest.kt`
- `/app/src/androidTest/java/com/todoquest/app/AppNavigationTest.kt`
- `/phases/030-039/35-shop-character-equipment-preview/step3.md`
- `/phases/030-039/35-shop-character-equipment-preview/index.json`

## 작업

구현과 테스트의 실제 상태를 canonical 문서에 동기화한다. PRD 구현 상태에는 기존 Shop 위에 캐릭터·7 slot·최종 3개 능력치 프리뷰가 추가됐음을 기록한다. Architecture에는 `EquipmentStoreSnapshot`이 appearance fallback, gameplay 장착, 렌더 loadout과 `DerivedStats`를 하나의 Repository projection으로 제공하고 ShopViewModel이 이를 단일 StateFlow로 매핑하는 data flow를 기록한다. UI Guide에는 app bar 이후 프리뷰→능력치→category→목록 순서, 일반 양옆 배치와 360dp/font scale 1.5 기준 compact FlowRow, slot 선택 연동, 장착/빈 slot 접근성, shared character renderer를 기록한다. DEVELOPMENT에는 실제 검증 기기와 명령 결과를 기록한다.

새 ADR을 만들거나 기존 ADR-013의 gameplay/appearance 분리 결정을 변경하지 않는다. Room schema version, migration과 seeded asset 제한도 실제 구현대로 유지한다. 문서에서 캐릭터 외형 fallback을 gameplay 소유 장비로 표현하지 않는다.

전체 검증을 실행한다. connected device가 있으면 모든 instrumentation test를 실행하고 Shop 프리뷰, slot/category, 구매·바로 장착 실시간 갱신, CHEST/LEGS 독립성, compact 접근성과 기존 Calendar·Character·Inventory 회귀를 확인한다. Android 도구나 connected device가 없으면 임의 설치하지 말고 step과 phase를 `blocked`로 기록한다. 실패가 있으면 이번 phase 회귀를 테스트 우선으로 수정하고 AC를 다시 실행한다.

최종 summary에는 변경 파일군, aggregate source와 계산 재사용, 반응형 UI, 추가 테스트, 실제 unit/connected/harness 수치, 남은 장비 bitmap/layer 제한을 포함한다.

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

1. 전체 unit·lint·assemble·connected·harness·문서 링크 AC를 실행하고 실패나 skip을 성공으로 기록하지 않는다.
2. AGENTS.md CRITICAL 규칙, ADR-013의 source 분리, CHEST/LEGS 독립성과 기존 occurrence·보상·전투 멱등성을 최종 확인한다.
3. task index의 step 4를 `completed`로 바꾸고 실제 검증 결과를 한국어 `summary` 한 줄로 기록한다.
4. 모든 step이 completed면 task index와 `/phases/index.json`의 phase 35를 `completed`로 바꾸고 완료 시각과 한국어 summary를 기록한다.

## 금지사항

- 수행하지 못한 connected test를 성공으로 기록하지 마라. 이유: phase 완료는 실제 기기 검증 근거가 필요하다.
- 새 Room migration이나 장비 bitmap이 구현됐다고 문서화하지 마라. 이유: 이번 phase 범위에 포함되지 않는다.
- 외형 fallback과 gameplay 장착 source를 하나로 합쳤다고 문서화하지 마라. 이유: ADR-013의 분리 원칙을 위반한다.
- 기존 테스트를 깨뜨리지 마라.
