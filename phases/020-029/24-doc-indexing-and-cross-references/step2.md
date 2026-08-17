# Step 2: 핵심 문서 교차 참조 갱신

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/README.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/*.md`
- `/docs/art/character/README.md`
- `/docs/art/monster/README.md`
- `/phases/020-029/24-doc-indexing-and-cross-references/index.json`

## 작업

`docs/`의 기존 핵심 Markdown 문서가 새 인덱스와 상세 문서를 책임에 맞게 참조하도록 갱신한다. 기존 제품 범위와 아키텍처 결정을 바꾸지 않는다.

### `/docs/PRD.md`

- `RPG 성장`에서 `[캐릭터 스탯 및 전투 계산 설계](game-design/character-stats-design.md)`를 후속 확장 설계로 연결한다.
- 아이템 능력치와 전투는 여전히 `MVP 제외 사항`이며 링크된 문서가 현재 MVP 범위를 확장하지 않는다고 명시한다.
- 정확한 픽셀 자산 링크를 PRD 본문에 나열하지 않는다. PRD는 제품 범위 문서다.

### `/docs/ARCHITECTURE.md`

- 현재 `주요 도메인 모델` 목록과 분리된 `후속 전투·스탯 확장 계약` 단락을 추가한다.
- `[구현 및 검증 계약](game-design/character-stats/implementation-and-validation.md)`을 연결하고 저장 원본과 계산 가능한 파생값을 분리한다는 목표를 요약한다.
- 이 계약은 MVP에 아직 구현되지 않은 후속 목표이며 현재 Room 스키마 설명으로 오인하지 않게 한다.

### `/docs/ADR.md`

- ADR-005에 `[TODO 보상과 자동 전투 연동](game-design/character-stats/todo-combat-rewards.md)`을 관련 후속 문서로 연결한다. RewardLedger occurrence 멱등 결정이 우선임을 함께 적는다.
- ADR-007에 `[캐릭터 아트 인덱스](art/character/README.md)`와 `[몬스터 아트 인덱스](art/monster/README.md)`를 관련 문서로 연결한다.
- 새 ADR 번호나 새로운 결정은 만들지 않는다.

### `/docs/UI_GUIDE.md`

- 캐릭터 패널에서 `[스탯 설계 인덱스](game-design/character-stats-design.md)`를 후속 수치 표시 기준으로 연결하고 내부 bp/고정소수점이 아니라 문서가 정한 UI 표시값을 보여준다는 원칙을 추가한다.
- 캐릭터와 몬스터 절에 각 아트 인덱스 링크를 추가한다.
- 기존 PNG와 JSON 직접 링크는 정확한 현재 에셋 및 규칙 참조이므로 유지한다.
- 후속 전투 UI를 현재 MVP 구현으로 표현하지 않는다.

### `/docs/DEVELOPMENT.md`

- 문서 작업 진입점으로 `[문서 인덱스](README.md)`를 연결한다.
- Markdown은 상대 경로와 `/`를 사용하고, 디렉터리 안내는 README, 기계 계약은 JSON을 직접 연결한다는 간단한 작성 규칙을 추가한다.
- 아래의 로컬 링크 검증 명령을 문서 검증 절차로 제공한다. 별도 외부 패키지를 도입하지 않는다.

모든 변경 후 전체 `docs/**/*.md` 링크를 검사한다. 링크 목적지에 Windows 경로 구분자나 로컬 절대 경로가 없어야 한다. 문서만 변경하므로 Android Gradle 빌드나 Room migration은 수행하지 않는다.

## Acceptance Criteria

```powershell
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; backslashes=[]; absolute=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; [(backslashes.append((str(p),t))) for p,t in links if '\\' in t]; [(absolute.append((str(p),t))) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
```

```powershell
.\.venv\Scripts\python.exe -c "import pathlib; checks={'docs/PRD.md':['game-design/character-stats-design.md','MVP'],'docs/ARCHITECTURE.md':['game-design/character-stats/implementation-and-validation.md','후속'],'docs/ADR.md':['game-design/character-stats/todo-combat-rewards.md','art/character/README.md','art/monster/README.md'],'docs/UI_GUIDE.md':['game-design/character-stats-design.md','art/character/README.md','art/monster/README.md'],'docs/DEVELOPMENT.md':['README.md']}; missing=[(p,s) for p,needles in checks.items() for s in needles if s not in pathlib.Path(p).read_text(encoding='utf-8')]; assert not missing,missing"
```

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. 각 링크가 문서 책임에 필요한 경우에만 추가됐는지 검토한다.
3. PRD의 MVP 제외 사항, AGENTS.md의 occurrence 멱등성, ARCHITECTURE와 ADR의 기존 결정을 변경하지 않았는지 확인한다.
4. task index에서 step 2를 `completed`로 바꾸고, 갱신한 핵심 문서와 전체 링크 검증 결과를 설명하는 한국어 `summary` 및 `completed_at`을 기록한다.
5. 모든 step이 완료되면 phase index와 `/phases/index.json`의 phase 상태를 `completed`로 갱신한다.

## 금지사항

- 후속 스탯·전투 설계를 MVP 구현 완료 상태로 표현하지 마라. 이유: PRD가 전투와 장비 능력치를 MVP에서 제외한다.
- 기존 ADR의 결정문을 새로운 의미로 바꾸지 마라. 이유: 이번 작업은 관련 상세 문서를 연결하는 문서 탐색 개선이다.
- UI_GUIDE의 직접 PNG·JSON 링크를 인덱스 링크로 대체해 제거하지 마라. 이유: 정확한 자산과 기계 계약으로 바로 이동할 수 있어야 한다.
- 외부 Markdown link checker나 새 의존성을 설치하지 마라. 이유: 로컬 표준 도구만으로 재현 가능해야 한다.
- 앱 Kotlin 코드, Room 스키마, JSON 명세와 PNG를 수정하지 마라. 이유: 이 step은 핵심 Markdown 교차 참조만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
