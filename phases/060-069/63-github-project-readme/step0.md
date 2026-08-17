# Step 0: GitHub 프로젝트 README 작성

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/settings.gradle.kts`
- `/build.gradle.kts`
- `/app/build.gradle.kts`
- `/phases/060-069/63-github-project-readme/index.json`

## 작업

저장소 루트에 GitHub 방문자를 위한 한국어 중심의 `/README.md`를 생성한다. 상세 제품·아키텍처 계약을 복제하지 말고, 처음 방문한 사용자가 프로젝트의 목적과 현재 구현 상태, 기술 구조, 로컬 실행 방법을 빠르게 이해한 뒤 `/docs/`의 canonical 문서로 이동할 수 있는 균형형 랜딩 페이지로 작성한다.

README는 다음 순서와 책임을 사용한다.

1. `# Todo Quest`
   - 개인 일정과 할 일을 RPG 퀘스트와 성장으로 연결하는 로컬 우선 Android 앱이라는 한 줄 소개를 작성한다.
   - 현재 앱 버전 `0.1.0`과 Android 전용 개발 버전이라는 상태를 과장 없이 표시한다.
   - 원격 CI 또는 라이선스 상태를 암시하는 badge는 사용하지 않는다.
2. 대표 비주얼
   - `docs/art/battle/todo-quest-battle-map-grassland.png`를 Markdown 이미지로 표시한다.
   - 실제 앱 스크린샷이 아니라 게임 비주얼/아트 미리보기라는 짧은 설명과 의미 있는 한국어 대체 텍스트를 제공한다.
3. `## 프로젝트 소개`
   - 빠르고 명확한 일정 관리, 완료 행동을 강화하는 RPG 피드백, 서버 없이 로컬에서 동작한다는 세 핵심 가치를 요약한다.
4. `## 주요 기능`
   - 월간·일간 일정과 TODO 생성·수정·삭제·완료
   - 없음·매일·매주·매월 반복과 날짜별 occurrence 완료
   - 일정별 알림, 권한·채널·exact alarm 실패와 핵심 기능의 분리
   - XP·골드·레벨·능력치, occurrence 단위 멱등 보상
   - Battle Map, 몬스터 전투, 중상 상태, 전투 효과음
   - 장비 상점·인벤토리·구매·장착과 캐릭터 layer 합성
   - 발견 이력 기반 몬스터 도감
   현재 소스와 PRD에서 구현된 기능만 현재형으로 설명하고, 설계만 존재하는 기능을 완료된 기능처럼 표현하지 않는다.
5. `## 비주얼 미리보기`
   - `docs/art/character/previews/adventure-equipped@8x.png`와 `docs/art/equipment/previews/weapon-combination-matrix@4x.png`를 두 열 Markdown 표에 배치한다.
   - 두 이미지 모두 실제 화면 캡처가 아니라 캐릭터·장비 아트 미리보기라고 명시한다.
6. `## 기술 스택`
   - Kotlin, Jetpack Compose, Material 3, ViewModel, Coroutines, Flow, Room, WorkManager, AlarmManager를 역할과 함께 간결하게 정리한다.
   - JDK 17, compile/target SDK 35, min SDK 23을 현재 Gradle 설정과 일치하게 표시한다.
7. `## 아키텍처`
   - `UI → ViewModel → UseCase → Repository → Room / Android platform` 데이터 흐름을 짧은 text code block으로 제시한다.
   - UI가 DAO·AlarmManager·WorkManager를 직접 호출하지 않는 경계, occurrence 단위 반복 일정과 멱등 보상, 권한 거부 시 핵심 기능 유지 원칙을 요약한다.
8. `## 시작하기`
   - Android Studio 최신 안정 버전, JDK 17, Android SDK 35를 준비 사항으로 안내한다.
   - 공개 origin인 `https://github.com/junhoh/todo-quest-android.git` clone, 저장소 이동, Android Studio 열기와 Gradle sync 순서를 제공한다.
   - 저장소 루트의 Windows PowerShell 기준으로 아래 명령을 그대로 제공한다.

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat lint --console=plain
.\gradlew.bat assembleDebug --console=plain
```

   - emulator 또는 실제 기기가 준비된 경우에만 `connectedDebugAndroidTest`를 실행한다고 구분한다.
9. `## 문서 안내`
   - `docs/README.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md`, `docs/ADR.md`, `docs/UI_GUIDE.md`, `docs/DEVELOPMENT.md`, `docs/game-design/README.md`를 상대 링크 표로 연결하고 각 책임을 한 줄로 설명한다.
10. `## 현재 범위`
    - 로컬 저장만 사용하며 외부 Google Calendar 연동, 계정·서버 동기화, 결제·광고, iOS·웹 버전은 현재 범위가 아님을 명시한다.

모든 Markdown 링크와 이미지 경로는 `/README.md` 기준 `/` 구분자의 상대 경로를 사용한다. 기존 저장소 자산만 참조하고 PNG를 복사하거나 새 이미지를 생성하지 않는다. 앱 코드, Room schema, Gradle 설정, canonical 문서, 공개 API·인터페이스·타입은 변경하지 않는다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; files=[pathlib.Path('README.md'),*pathlib.Path('docs').rglob('*.md')]; body=files[0].read_text(encoding='utf-8'); required=['# Todo Quest','## 프로젝트 소개','## 주요 기능','## 비주얼 미리보기','## 기술 스택','## 아키텍처','## 시작하기','## 문서 안내','## 현재 범위','docs/art/battle/todo-quest-battle-map-grassland.png','docs/art/character/previews/adventure-equipped@8x.png','docs/art/equipment/previews/weapon-combination-matrix@4x.png']; missing=[value for value in required if value not in body]; links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; bad=[(str(p),t) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent/urllib.parse.unquote(t.split('#',1)[0])).exists())]; backslashes=[(str(p),t) for p,t in links if '\\' in t]; absolute=[(str(p),t) for p,t in links if re.match(r'^[A-Za-z]:',t)]; assert not missing,missing; assert not bad,bad; assert not backslashes,backslashes; assert not absolute,absolute; print(f'{len(files)} markdown files and {len(links)} links checked')"
```

```powershell
.\scripts\run_phase_manager.ps1 -Command Validate
git diff --check
```

## 검증 절차

1. 모든 Acceptance Criteria 명령을 실행한다.
2. README의 기능 설명과 버전·SDK 값이 PRD, ARCHITECTURE, Gradle 설정 및 실제 앱 소스와 일치하는지 diff로 확인한다.
3. 대표 이미지 세 개가 저장소에 존재하고 GitHub 상대 경로로 열리며, 앱 스크린샷이라고 잘못 표기되지 않았는지 확인한다.
4. AGENTS.md의 occurrence 멱등성, 반복 원본·발생분 분리, Google Calendar 제외, 권한 실패 시 핵심 기능 유지 규칙을 약화하지 않았는지 확인한다.
5. task index에서 step 0을 `completed`로 바꾸고 `/README.md` 생성과 핵심 구성 내용을 설명하는 한국어 `summary` 및 `completed_at`을 기록한다.

## 금지사항

- 앱 Kotlin 코드, Room schema, Gradle 설정 또는 기존 canonical 문서를 수정하지 마라. 이유: 이 step은 GitHub 루트 문서만 추가하는 documentation 작업이다.
- 설계 문서에만 존재하거나 현재 범위에서 제외된 기능을 구현 완료로 표현하지 마라. 이유: 공개 README는 실제 저장소 상태를 정확히 설명해야 한다.
- 기존 아트 파일을 수정·복사하거나 새 이미지를 생성하지 마라. 이유: 승인된 저장소 자산을 비파괴적으로 재사용하는 범위다.
- 대표 아트 이미지를 실제 앱 스크린샷이라고 부르지 마라. 이유: GitHub 방문자가 현재 UI 캡처로 오인할 수 있다.
- CI, 배포, Play Store 공개 또는 라이선스 badge를 추가하지 마라. 이유: 이를 뒷받침하는 workflow, 배포 링크와 LICENSE 파일이 없다.
- Markdown 링크에 Windows 절대 경로나 `\` 구분자를 사용하지 마라. 이유: GitHub 상대 링크 호환성을 보장해야 한다.
- 기존 테스트를 깨뜨리지 마라.
