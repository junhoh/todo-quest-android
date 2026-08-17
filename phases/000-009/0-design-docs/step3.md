# Step 3: ui-guide

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/phases/000-009/0-design-docs/index.json`

## 작업

`docs/UI_GUIDE.md`를 Todo Quest의 픽셀 RPG 스타일과 Android 사용성 기준으로 작성한다.

반드시 포함할 UI 기준은 다음과 같다.

- 월간 캘린더와 일간 퀘스트 목록은 생산성 도구처럼 빠르게 읽혀야 한다.
- 픽셀 RPG 감성은 캐릭터, XP, 골드, 보상, 상점, 배지 영역에 집중한다.
- 일정 제목, 날짜, 시간, 오류 메시지는 가독성 좋은 시스템 폰트를 사용한다.
- 터치 대상은 최소 48dp를 유지한다.
- 색만으로 완료, 실패, 선택 상태를 구분하지 않는다.
- TalkBack에서 일정 제목, 날짜, 시간, 완료 상태, 보상 정보를 읽을 수 있어야 한다.
- 완료 보상 애니메이션은 짧고 핵심 조작을 오래 막지 않는다.
- 알림 권한 요청은 첫 실행 즉시가 아니라 사용자 행동 이후에 보여준다.
- 상점 구매 불가 상태는 골드 부족 등 이유를 명확히 표시한다.

색상, 화면 구조, 컴포넌트 규칙, 애니메이션 제한, 접근성 규칙, 금지사항을 모두 작성한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
python -m json.tool phases/index.json
python -m json.tool phases/000-009/0-design-docs/index.json
git diff --check
$matches = rg -n "\{[^}]+\}|Next\.js|Tailwind|npm run|app/api" AGENTS.md docs
if ($LASTEXITCODE -eq 0) { $matches; exit 1 }
if ($LASTEXITCODE -gt 1) { exit $LASTEXITCODE }
exit 0
```

검색 결과가 있으면 실패로 처리한다.

## 검증 절차

1. Acceptance Criteria 명령을 실행한다.
2. `docs/UI_GUIDE.md`가 PRD의 픽셀 RPG 감성과 캘린더 사용성을 동시에 만족하는지 확인한다.
3. `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`의 CRITICAL 규칙과 충돌하지 않는지 확인한다.
4. `/phases/000-009/0-design-docs/index.json`의 step 3 상태와 결과 필드를 업데이트한다.

## 금지사항

- 픽셀 폰트를 모든 텍스트에 강제하지 마라. 이유: 일정 앱의 핵심 정보 가독성이 떨어진다.
- 게임 장식을 날짜 숫자나 완료 버튼 위에 겹치지 마라. 이유: 반복 사용성이 나빠진다.
- 기존 harness 테스트를 깨뜨리지 마라.
