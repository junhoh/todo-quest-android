# Step 1: 게임 설계 및 아트 문서 인덱스 추가

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/*.md`
- `/docs/art/character/character-base-spec.json`
- `/docs/art/character/character-modular-sheet-spec.json`
- `/docs/art/monster/todo-quest-goblin-scout-front-idle-spec.json`
- `/scripts/validate_character_sheet.py`
- `/scripts/validate_monster_sprite.py`
- `/phases/020-029/24-doc-indexing-and-cross-references/index.json`

## 작업

문서와 아트 자산을 처음 찾는 개발자가 디렉터리만 보고 기준 문서와 직접 명세를 선택할 수 있도록 다음 인덱스를 작성한다.

- `/docs/README.md`
- `/docs/game-design/README.md`
- `/docs/art/character/README.md`
- `/docs/art/monster/README.md`

`docs/README.md`에는 PRD, ARCHITECTURE, ADR, UI_GUIDE, DEVELOPMENT와 세 하위 인덱스를 연결하고 문서 책임과 참조 규칙을 정의한다.

- 제품 범위와 MVP 여부는 PRD를 따른다.
- 아키텍처 및 불변 규칙은 AGENTS.md, ARCHITECTURE, ADR을 따른다.
- 수치와 기능별 상세 규칙은 game-design 문서를 따른다. 단, PRD가 후속 기능으로 분류한 설계를 현재 구현으로 표현하지 않는다.
- 화면 표시와 접근성은 UI_GUIDE를 따르고, 픽셀 좌표·팔레트·레이어·검증 계약은 JSON 명세를 따른다.
- 일반적인 기능 또는 디렉터리 안내는 `README.md`, 구체적인 규칙은 해당 Markdown heading, 기계 검증 계약은 JSON, 시각 예시는 PNG를 직접 참조한다.
- Markdown 링크 대상에는 현재 파일 기준 상대 경로와 `/`를 사용하며 Windows `\`, `C:\...` 절대 경로나 저장소 외부 로컬 경로를 쓰지 않는다.

`game-design/README.md`에는 `character-stats-design.md`를 정식 진입점으로 등록하고 `후속 확장 설계 / MVP 미구현` 상태를 표시한다. 분할 하위 파일은 개별 목록으로 복제하지 않고 진입 인덱스를 통해 탐색하게 한다.

`art/character/README.md`에는 다음 역할을 구분한다.

- 현재 대표 및 런타임 합성 기준: `todo-quest-character-modular-sheet.png`, `character-modular-sheet-spec.json`
- 최초 외형 원본 기준: `todo-quest-character-base-sheet.png`, `character-base-spec.json`
- 모듈 합성용 외부 base-body: `todo-quest-character-base-body.png`
- 정확한 좌표와 합성 규칙은 JSON이 canonical이며 PNG는 시각 결과다.
- `validate_character_sheet.py`의 현재 두 검증 명령을 제공한다.

`art/monster/README.md`에는 고블린 PNG와 JSON 명세를 현재 일반 등급 적 기준으로 등록하고, JSON 내부의 캐릭터 base-body 참조는 상대 크기와 기준선 비교용이지 몬스터 전투 스탯의 저장 장소가 아님을 명시한다. 몬스터 능력치가 추가되면 game-design 문서에서 관리한다. `validate_monster_sprite.py`의 검증 명령을 제공한다.

## Acceptance Criteria

```powershell
@('docs\README.md','docs\game-design\README.md','docs\art\character\README.md','docs\art\monster\README.md') | ForEach-Object { if (-not (Test-Path -LiteralPath $_)) { throw "missing index: $_" } }
```

```powershell
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; files=list(pathlib.Path('docs').rglob('*.md')); [(bad.append((str(p),t))) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8')) if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; assert not bad,bad; print(f'{len(files)} markdown files checked')"
```

```powershell
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-base-sheet.png --spec docs\art\character\character-base-spec.json
.\.venv\Scripts\python.exe scripts\validate_character_sheet.py --image docs\art\character\todo-quest-character-modular-sheet.png --spec docs\art\character\character-modular-sheet-spec.json
.\.venv\Scripts\python.exe scripts\validate_monster_sprite.py --image docs\art\monster\todo-quest-goblin-scout-front-idle.png --spec docs\art\monster\todo-quest-goblin-scout-front-idle-spec.json
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 네 인덱스의 모든 링크가 저장소 안의 현재 파일을 가리키는지 확인한다.
3. 캐릭터 modular spec과 몬스터 spec의 canonical 역할이 UI_GUIDE 및 실제 JSON 참조와 일치하는지 확인한다.
4. task index에서 step 1을 `completed`로 바꾸고, 생성한 네 인덱스와 참조 규칙을 설명하는 한국어 `summary` 및 `completed_at`을 기록한다.

## 금지사항

- JSON 명세의 내용을 Markdown으로 전부 복제하지 마라. 이유: JSON을 기계 검증 가능한 단일 계약으로 유지해야 한다.
- PNG를 수정하거나 다시 생성하지 마라. 이유: 이 step은 아트 제작이 아니라 탐색 인덱스 작성이다.
- 전투 스탯을 아트 명세에 추가하지 마라. 이유: 게임 수치와 픽셀 자산 계약의 책임을 분리해야 한다.
- 모든 문서에 모든 인덱스를 무조건 연결하지 마라. 이유: 문서 책임과 직접 관련된 링크만 유지해야 탐색성이 좋아진다.
- 기존 테스트를 깨뜨리지 마라.
