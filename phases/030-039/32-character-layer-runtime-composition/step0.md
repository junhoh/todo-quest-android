# Step 0: audit-character-geometry

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/character/README.md`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/docs/art/character/todo-quest-character-modular-sheet.png`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/character/todo-quest-character-base-sheet.png`
- `/docs/art/character/character-base-spec.json`
- `/scripts/validate_character_sheet.py`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/feature/battle/BattleMap.kt`
- `/app/src/main/java/com/todoquest/feature/calendar/CalendarViewModel.kt`

## 작업

`/docs/art/character/character-layer-migration-report.md`를 생성해 구현 전 사실을 기록한다. 세 PNG의 실제 경로, 크기, mode, 파일 SHA-256, raw RGBA SHA-256, alpha-mask SHA-256, inclusive opaque bounds와 모든 타일 raw RGBA 해시를 계산한다. 번호가 붙은 사본을 저장소 전체에서 검색하고 없으면 없다고 기록한다.

다음을 측정 근거와 함께 명시한다.

- base body는 64×64 RGBA, opaque bounds `[20,7,44,58]`, centerX 32, soleY 58이다.
- head bounds는 `[20,7,44,28]`, neck transition은 y=29..30, shoulder band는 y=30..35이며 shoulder anchors는 `[22,35]`, `[42,35]`다.
- torso `[24,30,40,43]`, waist overlap `[24,41,40,43]`, ankle overlap left `[24,53,31,54]`, right `[33,53,40,54]`다.
- hand protected regions는 left `[20,39,24,45]`, right `[40,39,44,45]`이고 base-body 피부 픽셀 좌표 38개를 계산한다.
- 현재 default/adventure top의 face region 침범, default bottom의 hand 침범, legacy와 current composite의 픽셀 차이를 수치로 기록한다.
- 현재 Character 화면과 Battle Map이 sheet 첫 타일을 직접 자르는 코드 경로와 Android sheet resource가 docs PNG와 byte-identical임을 기록한다.
- 실제 base-body RGBA hash와 spec hash 불일치, alpha hash 일치, 존재하지 않는 `body-base` tile 참조, row0/column1의 실제 `default-outfit`, 65,536 대 32,768 픽셀 오류, stale targeted baseline을 충돌 목록에 기록한다.

이미지를 수정하거나 해시를 맞추지 않는다.

## Acceptance Criteria

```powershell
Test-Path docs\art\character\character-layer-migration-report.md
Select-String -Path docs\art\character\character-layer-migration-report.md -Pattern 'bodyOpaqueBounds','65,536','CharacterScreen.kt','BattleMap.kt','originalBodyBaseReference'
git diff --check
```

## 검증 절차

1. 보고서 수치를 Pillow로 다시 계산해 표와 일치하는지 확인한다.
2. AGENTS.md의 파괴적 명령 금지와 base body 불변 규칙을 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 보고서 경로와 핵심 충돌을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- PNG, JSON 또는 Android 코드를 수정하지 마라. 이유: 이 step은 기준 상태 감사만 담당한다.
- 기존 spec hash에 실제 base body를 맞추지 마라. 이유: 외부 base body가 geometry canonical reference다.
- 색상만으로 guide pixel을 분류하지 마라. 이유: `#E05252`와 teal은 실제 캐릭터 색으로 사용된다.
- 기존 테스트를 깨뜨리지 마라.
