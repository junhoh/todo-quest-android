# Step 0: document-equipment-shop-contract

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/game-design/character-stats-design.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/phases/030-039/32-character-layer-runtime-composition/index.json`
- `/phases/030-039/33-calendar-combat-feedback/index.json`
- `/phases/030-039/34-equipment-shop-and-inventory/index.json`

## 작업

Post-MVP Equipment Shop and Inventory v1 계약을 canonical 문서에 먼저 승인한다. 기존 문서에서 inventory·ownership·획득·사용자 장착·장비 능력치가 후속 범위라고 한 부분을 이번 phase에서 명시적으로 승인하되 일정 occurrence 멱등 보상, 독립 전투 event, 권한 비의존 핵심 기능과 Google Calendar 제외 범위는 바꾸지 않는다. ADR-013을 추가하고 PRD, Architecture, UI Guide와 캐릭터 장비 설계의 상태를 동기화한다.

게임플레이 장비 type과 slot은 각각 `WEAPON`, `HELMET`, `CHEST`, `LEGS`, `GLOVES`, `SHOES`, `ACCESSORY` 일곱 값으로 고정한다. 현재 순수 검증용 `EquipmentSlot.HEAD/TOP/BOTTOM/SHOES/ACCESSORY/WEAPON/PET`는 새 일곱 슬롯으로 교체한다. 기존 Room v6에는 `ARMOR` slot 값이 없고 `character_equipped_items.topId`와 `bottomId`가 이미 외형 레이어로 분리돼 있음을 기록한다. 이 외형 테이블은 사용자 진행을 보존하는 fallback으로 유지하고 실제 소유·장착·능력치 source는 새 정규화 테이블로 분리한다. 저장 호환 mapping에서 `ARMOR`와 `TOP`은 `CHEST`, `BOTTOM`은 `LEGS`, `HEAD`는 `HELMET`으로 해석하며 구분 불가능한 `ARMOR`를 `CHEST`로 두는 근거를 명시한다.

캐릭터의 기존 기본 스탯 `STRENGTH`, `VITALITY`, `FOCUS`, `WILLPOWER`와 8개 `DerivedStats`를 유지한다. 요청 예시의 민첩 계열은 `FOCUS`, 지능 계열은 `WILLPOWER`에 맞춰 초기 장비 효과를 설계하며 새 캐릭터 원천 스탯이나 별도 agility/intelligence 컬럼을 추가하지 않는다. 장비 구매만으로 스탯이 오르지 않고 `character_equipment`에 실제 장착된 modifier만 계산에 포함한다고 고정한다. `CHEST`와 `LEGS`는 동시 장착 가능하고 교체는 대상 slot 하나만 바꾼다.

구매는 캐릭터별 동일 equipment 중복을 금지하고 quantity를 도입하지 않는다. 골드 재확인·판매 상태·요구 레벨·중복·type/slot mapping 검증, 골드 차감과 owned row 추가를 하나의 Room transaction으로 처리한다. 장착도 소유권·slot 일치 검증, 대상 slot 교체와 MAX_HP 변화에 따른 HP 비율 보존을 하나의 transaction으로 처리한다. Shop은 세 번째 하단 destination, Inventory는 Shop에서 여는 nested destination으로 승인한다. 구매 성공 UX는 바로 장착·인벤토리로 이동·계속 쇼핑 세 선택을 제공한다.

## Acceptance Criteria

```powershell
rg -n "Equipment Shop|장비 상점|ADR-013|CHEST|LEGS|GLOVES|owned_equipment|character_equipment|외형 fallback" docs\PRD.md docs\ARCHITECTURE.md docs\ADR.md docs\UI_GUIDE.md docs\game-design\character-stats-design.md docs\game-design\character-stats\modifiers-and-equipment.md docs\game-design\character-stats\implementation-and-validation.md
.\.venv\Scripts\python.exe -c "import pathlib,re,urllib.parse; bad=[]; files=list(pathlib.Path('docs').rglob('*.md')); links=[(p,t) for p in files for t in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text(encoding='utf-8'))]; [(bad.append((str(p),t))) for p,t in links if not (t.startswith(('http://','https://','mailto:','#')) or (p.parent / urllib.parse.unquote(t.split('#',1)[0])).exists())]; assert not bad,bad; print(f'{len(files)} markdown files checked')"
git diff --check
```

## 검증 절차

1. AC 명령을 실행하고 기존 제외 범위와 이번 승인 범위가 문서에서 모순되지 않는지 확인한다.
2. AGENTS.md의 occurrence 멱등성, UI→Repository/UseCase 경계, 한국어 문자열과 destructive migration 금지 규칙을 확인한다.
3. task index의 step 0을 `completed`로 바꾸고 승인 계약을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 기존 occurrence 보상이나 전투 event의 멱등 키를 바꾸지 마라. 이유: 상점 경제와 일정 보상은 독립된 transaction 경계다.
- 민첩·지능 원천 스탯을 새로 추가하지 마라. 이유: 승인된 결정은 기존 네 기본 스탯과 계산 공식을 재사용한다.
- 기존 `character_equipped_items`를 삭제하거나 새 gameplay 소유권으로 간주하지 마라. 이유: 기존 사용자 외형 진행을 보존해야 한다.
- 기존 테스트를 깨뜨리지 마라.
