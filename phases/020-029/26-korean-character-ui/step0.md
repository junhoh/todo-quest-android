# Step 0: 한국어 기본 UI 정책 정의

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/feature/character/CharacterScreen.kt`
- `/app/src/main/java/com/todoquest/app/TodoQuestApp.kt`
- `/phases/020-029/26-korean-character-ui/index.json`

## 작업

앱의 사용자 노출 언어 기본값을 한국어로 고정하는 문서 계약을 먼저 작성한다.

- `/AGENTS.md`에 새 화면과 새 사용자 노출 문구는 `/app/src/main/res/values/strings.xml`의 한국어 문자열 리소스를 기본으로 사용하고 Compose 및 ViewModel에 표시용 영문 문장을 하드코딩하지 않는 CRITICAL 규칙을 추가한다.
- `/docs/UI_GUIDE.md`에 한국어 기본 언어 정책을 추가한다. `values/strings.xml`은 한국어 기본값이고, 영어 번역을 실제로 지원할 때만 `values-en`을 추가하며 한국어 기본 리소스를 제거하지 않는다고 명시한다.
- 하단 navigation 표시명은 `캘린더`, `캐릭터`로, Character 화면의 표시 용어는 다음과 같이 고정한다.
  - `캐릭터`, `성장`, `기본 능력치`, `파생 능력치`
  - `체력`, `경험치`, `최대 레벨`, `골드`, `연속 달성`, `기세`, `미배분 능력치 포인트`
  - `힘`, `활력`, `집중`, `의지`
  - `최대 체력`, `공격력`, `방어력`, `치명타 확률`, `치명타 피해`, `상태 이상 저항`, `체력 회복`, `골드 획득 보너스`
- `Todo Quest` 브랜드명과 route, enum, test tag, resource key 같은 내부 식별자는 영문을 유지할 수 있음을 명시한다.
- 현재 Calendar 화면 내부의 기존 영문 문구 번역은 이번 phase 범위 밖이며 별도 후속 작업임을 명시한다.

## Acceptance Criteria

```powershell
$agents = Get-Content -Raw -Encoding utf8 -LiteralPath 'AGENTS.md'
$guide = Get-Content -Raw -Encoding utf8 -LiteralPath 'docs\UI_GUIDE.md'
@('한국어', 'strings.xml', 'CRITICAL') | ForEach-Object { if (-not $agents.Contains($_)) { throw "Missing AGENTS policy: $_" } }
@('한국어', '캘린더', '캐릭터', '기본 능력치', '파생 능력치', '기세') | ForEach-Object { if (-not $guide.Contains($_)) { throw "Missing UI guide term: $_" } }
git diff --check
```

## 검증 절차

1. AC 명령을 실행한다.
2. PRD, ARCHITECTURE, ADR의 제품 범위와 AGENTS.md의 기존 CRITICAL 규칙을 변경하지 않았는지 확인한다.
3. `/phases/020-029/26-korean-character-ui/index.json`의 step 0을 `completed`로 변경하고 정책과 용어 결정을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- Calendar 화면 내부의 기존 영문 문구를 이 step에서 수정하지 마라. 이유: 이번 phase의 구현 범위는 Character 화면과 공통 navigation이다.
- 도메인 타입, route, enum과 test tag를 한국어 식별자로 바꾸지 마라. 이유: 사용자 표시 언어와 코드·테스트 호환성 계약은 분리해야 한다.
- 한국어 기본 문자열을 `values-ko`에만 두지 마라. 이유: 지원하지 않는 locale에서도 기본 `values/strings.xml`의 한국어가 사용되어야 한다.
- 기존 테스트를 깨뜨리지 마라.
