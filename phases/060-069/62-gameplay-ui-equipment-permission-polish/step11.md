# Step 11: verify-gameplay-ui-polish

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/DEVELOPMENT.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/scripts/test_execute.py`
- `/scripts/test_validate_character_sheet.py`
- `/scripts/test_validate_character_equipment_layers.py`
- `/scripts/test_build_character_assets.py`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step0.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step1.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step2.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step3.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step4.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step5.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step6.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step7.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step8.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step9.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step10.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

phase 전체를 통합 검증하고 요구사항별 수동 확인 결과를 남긴다. 이 step은 새로운 기능 범위를 추가하지 않고 앞선 step의 결함만 수정한다.

새 터미널 기준으로 Java/Javac/Android SDK/adb 노출을 확인한다. Python art/harness test, Android unit test, lint, debug APK, test APK, connected instrumentation test, phase registry validation을 모두 실행한다. connected device가 없다면 설치나 환경 변경을 시도하지 말고 정확한 누락 도구/장치를 `blocked_reason`으로 기록한다.

수동 검증은 다음 여섯 시나리오를 포함한다.

1. player death→중상 적용 전후 Battle Map player 좌표가 동일하다.
2. Shop item 선택 전후 공격력/최대 체력/방어력 영역 크기가 동일하다.
3. 카드와 상세 action이 우측 하단 고정 크기이며 상태에 따라 `구매`, `구매 불가`, `장착`, `해제`만 표시한다.
4. 선택된 장착 item을 해제하면 선택/상세가 닫히고 preview에서 해당 layer가 제거된다.
5. clean/migrated 저장에서 빈 slot은 중립 훈련복이고 기존 adventure 7부위는 상점 상품이며 실제 owned/equipped 데이터는 보존된다.
6. Settings에서 runtime/app/channel 일반 알림 설정으로 진입하고 복귀 상태를 갱신하며 권한 거부 중에도 핵심 기능이 동작한다.

## Acceptance Criteria

```powershell
java -version
javac -version
$env:ANDROID_HOME
$env:ANDROID_SDK_ROOT
adb version
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_validate_character_sheet.py scripts/test_validate_character_equipment_layers.py scripts/test_build_character_assets.py scripts/test_execute.py --basetemp build/pytest-phase62-final
.\gradlew.bat test lint assembleDebug assembleDebugAndroidTest --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\scripts\run_phase_manager.ps1 -Command Validate
.\scripts\run_phase_manager.ps1 -Command Sync -Check
git diff --check
```

## 검증 절차

1. 모든 AC 명령을 순서대로 실행하고 실패한 명령의 원인을 수정한 뒤 처음부터 다시 검증한다.
2. 요구사항 여섯 시나리오의 test 또는 수동 확인 근거를 phase summary에 기록한다.
3. Room migration에서 일정 occurrence, RewardLedger, HP/status, combat event, gold, ownership/equipment가 보존되는지 최종 확인한다.
4. AGENTS.md의 UI/data 경계, occurrence 멱등성, 한국어 문자열, 권한 실패 독립성을 위반하지 않는지 확인한다.
5. 연결된 Android device가 없거나 필수 Android 도구가 없으면 임의 설치하지 말고 step을 `blocked`로 기록한다.
6. 성공 시 task index의 step 11을 `completed`로 바꾸고 전체 검증 결과를 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 검증 실패를 무시하고 phase를 `completed`로 표시하지 마라. 이유: 이 step은 최종 acceptance gate다.
- Android 도구나 emulator가 없을 때 임의 설치하거나 사용자 환경 변수를 변경하지 마라. 이유: 별도 승인된 준비 phase가 필요하다.
- 테스트를 통과시키기 위해 assertion, migration 보존 범위 또는 접근성 조건을 완화하지 마라. 이유: 회귀를 숨기게 된다.
- 새 기능, 새 monster, 외부 Calendar 연동 또는 exact alarm 설정을 추가하지 마라. 이유: 확정된 phase 범위를 벗어난다.
- `rm -rf`, `git reset --hard`, `DROP TABLE` 같은 파괴적 명령을 실행하지 마라.
- 기존 테스트를 삭제하거나 완화하지 마라.
