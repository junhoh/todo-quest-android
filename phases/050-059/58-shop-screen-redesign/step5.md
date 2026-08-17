# Step 5: synchronize-and-verify-shop-redesign

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/app/src/main/java/com/todoquest/domain/model/Equipment.kt`
- `/app/src/main/java/com/todoquest/data/repository/RoomEquipmentRepository.kt`
- `/app/src/main/java/com/todoquest/feature/shop/`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/phases/050-059/58-shop-screen-redesign/step4.md`
- `/phases/050-059/58-shop-screen-redesign/index.json`

## 작업

canonical 문서를 실제 구현과 동기화한다. UI Guide의 기존 104dp 대장장이 카드, 96/120dp 대형 slot, 분리된 stat 영역, Shop back과 카드 tap 즉시 상세 계약을 새 압축 배너·통합 preview/stat card·소형 일곱 slot·별도 상세 action·top-level navigation 계약으로 교체한다. gold gap은 4dp로 기록한다.

Architecture와 ADR에는 `RoomEquipmentRepository snapshot → 순수 구매 전 projection → ShopViewModel 선택 UI state → 캐릭터/최종 stat delta` 흐름을 기록한다. projection은 비영속이며 실제 구매/장착 transaction, current HP, gameplay source를 변경하지 않고 장착 commit 이후에만 실제 shared Flow가 갱신된다는 경계를 명시한다. Room schema, 장비 catalog와 asset이 변경되지 않았음을 기록한다.

전체 검증을 실행한다. connected device나 Android 도구가 없으면 설치하지 말고 step과 phase를 `blocked`로 기록한다. 수행하지 못한 검증을 성공으로 기록하지 않는다. 표준 및 small/large-font Compose Preview와 connected layout 결과를 최종 시각 검토하고 첫 화면 밀도, 캐릭터 중심성, 상품 상태와 하단 navigation 경계를 확인한다.

성공 후 step 5와 phase index를 `completed`로 갱신하고 phase summary에는 변경 파일, 주요 UI 구조, 유지한 골드·filter·선택·구매·소유·장착·preview·navigation 기능과 실제 test 수/명령 결과를 한국어로 기록한다. manager `Sync`로 root index와 README를 갱신한 뒤 `Validate`와 `Sync -Check`를 통과시킨다.

## Acceptance Criteria

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. 전체 unit·lint·assemble·connected·harness AC를 실행하고 failure 또는 skip을 성공으로 기록하지 않는다.
2. 상품 선택 전후 외형/stat delta, 구매 가능·골드 부족·보유·장착 상태와 마지막 card/inset을 실제 connected test로 확인한다.
3. AGENTS.md CRITICAL 규칙, ADR의 transaction/source 분리, occurrence 보상 멱등성과 반복 일정 분리를 최종 확인한다.
4. step 5와 phase를 `completed`로 갱신한 뒤 `Sync`, `Validate`, `Sync -Check`를 실행해 registry drift가 없는지 확인한다.

## 금지사항

- 수행하지 못한 connected test나 Preview 검토를 성공으로 기록하지 마라. 이유: UI 리디자인 완료에는 실제 레이아웃 검증 근거가 필요하다.
- Room migration, 새 장비 데이터나 asset을 구현했다고 문서화하지 마라. 이유: 이번 phase 범위가 아니다.
- 상품 preview projection을 gameplay 장착 source로 문서화하지 마라. 이유: 비영속 선택 상태와 transaction 결과를 분리해야 한다.
- `scripts/execute.py`를 직접 호출하지 마라. 이유: Windows/Codex 표준 진입점은 `run_harness.ps1`이다.
- 관련 없는 화면과 기능을 수정하지 마라.
- 기존 테스트를 깨뜨리지 마라.
