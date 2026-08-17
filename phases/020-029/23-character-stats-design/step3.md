# Step 3: 데이터 모델, 재계산 이벤트와 수치 예시 검증

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/character-stats-design.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterProfile.kt`
- `/app/src/main/java/com/todoquest/data/local/CharacterProfileEntity.kt`
- `/app/src/main/java/com/todoquest/data/local/TodoQuestDatabase.kt`
- `/app/src/main/java/com/todoquest/data/local/RewardLedgerEntity.kt`
- `/phases/020-029/23-character-stats-design/index.json`

## 작업

`/docs/game-design/character-stats-design.md`에 구현용 데이터 계약, 저장 범위, 재계산 이벤트, 설정 분리와 골든 수치 검증을 추가해 문서를 완성한다. 앱 코드는 수정하지 않는다.

### 데이터 계약

다음 Kotlin 시그니처 수준의 모델을 제안한다.

- `PlayerCharacter`: id, `totalXp: Long`, `currentGold: Long`, base stats, 미배분 포인트. level은 XP와 config에서 계산한다.
- `CharacterBaseStats`: strength/vitality/focus/willpower `Int`, 각 1~60 검증.
- `CharacterCurrentState`: characterId, `currentHp: Int`, balanceVersion, 갱신 시각. 앱 종료 후 보존한다.
- `DerivedStats`: 8개 immutable 계산 결과. 확률·배율 필드는 `Bp` 접미사 `Int`다.
- `EquipmentStatModifier`: item id, `StatTarget`, `ModifierType`, amount.
- `TemporaryStatEffect`: effect id, target, type, amount, stackingKey, 시작 시각, 종료 시각 또는 남은 trigger 수. duration 둘 중 하나만 허용하며 gameplay 효과는 영속한다.
- `StatType { STRENGTH, VITALITY, FOCUS, WILLPOWER }`.
- `DerivedStatType { MAX_HP, ATTACK, DEFENSE, CRITICAL_CHANCE, CRITICAL_DAMAGE, STATUS_RESISTANCE, HP_RECOVERY, GOLD_GAIN_BONUS }`.
- `ModifierType { FLAT, PERCENT_ADD }`. 기본 스탯은 FLAT만, 고정형 파생값은 둘 다, 확률·비율은 bp FLAT만 허용한다.
- `DerivedStatsCalculator.calculate(input: StatCalculationInput, config: CharacterStatBalanceConfig): DerivedStats`는 Android·Room 비의존 순수 Kotlin 계약이다.

Room 영속, 매번 계산, 세션 전용, 앱 종료 후 보존 값을 표로 구분한다.

- 영속: XP·골드, 기본 스탯, 미배분 포인트, current HP, 장착 id, 아이템 modifier, 활성 지속 효과, 완료·보상·전투 ledger와 balance version.
- 계산: level, 파생 8개, 장비·세트·버프 집계, 감소율과 예상 피해.
- 세션: 계산 cache, UI 문자열, 현재 roll·animation. 재현 seed와 확정 event는 ledger에 저장한다.
- 앱 종료 후 보존: current HP와 끝나지 않은 시간·trigger 효과. 프레젠테이션 효과는 저장하지 않는다.

XP·골드·epoch millis·seed는 `Long`, 스탯·고정 파생값·bp는 `Int`, 곱셈 중간값은 `Long`이고 전투 계산은 `Float`/`Double`을 쓰지 않는다. 현재 DB의 `Int` XP·골드와 저장 level은 후속 migration 대상이다.

### 재계산 이벤트

- 레벨업: HP·공격·방어·회복, HP 비율 유지.
- 포인트·초기화: 힘→공격/치명타 피해, 체력→HP/방어, 집중→공격/치명타 확률, 의지→저항/회복.
- 장착·해제: modifier target과 기본 스탯 transitive dependency, HP 비율 유지.
- 세트·버프·디버프 시작/종료: target과 dependency. 같은 stackingKey는 가장 높은 하나와 기간 갱신을 기본으로 한다.
- HP 회복: 파생값 재계산 없이 current HP만 `0..MAX_HP` clamp·저장.
- 전투 종료: trigger/종료 효과 제거 후 관련 파생값을 재계산하고 `HP_RECOVERY`를 current HP에 적용.

source와 HP가 함께 바뀌면 변경 전·후 MAX_HP를 같은 transaction에서 계산해 비율을 유지한다. config 변경은 versioned migration에서 같은 절차를 쓴다. 파생값 snapshot은 DB에 저장하지 않는다.

모든 계수, 상한, 등급 범위, 방어 상수, 보상 multiplier와 일일 효율을 versioned `CharacterStatBalanceConfig`에 모아 calculator에 주입한다. 확정 ledger는 기록된 version과 결과를 신뢰해 업데이트 후 소급 변경하지 않는다.

### 골든 수치

예상 공격은 같은 레벨 표준 몬스터의 `benchmarkDefense = 5 + 2 × level`을 사용한다. 이 값은 공식 검증용 초기 기준이라고 명시한다.

1. Lv1, 무장 없음, `5/5/5/5`: HP 110, 공격 20, 방어 8, 치명타 750bp(7.5%), 피해 15,250bp(152.5%), 저항 375bp(3.75%), 회복 7. 방어 7 상대 일반 18, 치명타 28, 기대 18.75.
2. Lv10, `10/10/9/9`와 일반 장비: HP 243, 공격 51, 방어 20, 치명타 1,100bp(11.0%), 피해 15,750bp(157.5%), 저항 900bp(9.0%), 회복 14. 방어 25 상대 일반 40, 치명타 64, 기대 42.64.
3. Lv30, `35/13/21/9`와 희귀 장비: HP 484, 공격 166, 방어 45, 치명타 2,050bp(20.5%), 피해 17,700bp(177.0%), 저항 1,400bp(14.0%), 회복 23. 방어 65 상대 일반 100, 치명타 177, 기대 115.785.

Lv10은 다음 단계를 문서에 그대로 풀어쓴다.

- HP `floor((114 + 100 + 10 + 12) × 1.03) = 243`.
- 공격 `floor((14 + 20 + 9 + 2 + 1 + 4) × 1.03) = 51`.
- 방어 `floor((7 + 10 + 1 + 2) × 1.03) = 20`.
- 치명타 `500 + 450 + 50 + 100 = 1,100bp`.
- 치명타 피해 `15,000 + 500 + 50 + 200 = 15,750bp`.
- 저항 `675 + 75 + 150 = 900bp`.
- 일반 피해 `floor(51 × 100 / 125) = 40`.
- 치명타 raw `floor(51 × 15,750 / 10,000) = 80`, 방어 적용 `floor(80 × 100 / 125) = 64`.
- 기대 `40 × 0.89 + 64 × 0.11 = 42.64`.

Lv50 균형 무장 없음 `30/30/29/29` 참고값은 HP 654, 공격 143, 방어 57, 치명타 19.5%, 치명타 피해 165.0%, 저항 21.75%, 회복 40이다.

문서 끝에 순수 Kotlin unit test 우선 checklist를 둔다: 공식·버킷·내림, 상한/디버프, 방어/최소 피해/seed, HP 비율, 레벨 cap·포인트·초기화, 장비 validation, occurrence 전 이벤트 멱등성, 효율·정시·연속 상한, config version과 파생값 비저장.

## Acceptance Criteria

```powershell
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\character-stats-design.md'
@('PlayerCharacter','CharacterBaseStats','CharacterCurrentState','DerivedStats','EquipmentStatModifier','TemporaryStatEffect','StatType','DerivedStatType','ModifierType','CharacterStatBalanceConfig','18.75','42.64','115.785','HP 654') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Missing data or golden contract: $_" } }
$hp = [math]::Floor((114 + 100 + 10 + 12) * 1.03); if ($hp -ne 243) { throw 'Lv10 HP mismatch' }
$atk = [math]::Floor((14 + 20 + 9 + 2 + 1 + 4) * 1.03); if ($atk -ne 51) { throw 'Lv10 attack mismatch' }
$expected10 = 40 * 0.89 + 64 * 0.11; if ([math]::Abs($expected10 - 42.64) -gt 0.0001) { throw 'Lv10 damage mismatch' }
$expected30 = 100 * 0.795 + 177 * 0.205; if ([math]::Abs($expected30 - 115.785) -gt 0.0001) { throw 'Lv30 damage mismatch' }
git diff --check
$env:PYTHONUTF8='1'; .\.venv\Scripts\python.exe -m pytest scripts/test_execute.py --basetemp .\.venv\pytest-tmp
```

## 검증 절차

1. 문서 검사, 산술 assertion, harness pytest와 `git diff --check`를 실행한다.
2. 세 골든 예시를 독립 재계산한다.
3. 저장값과 계산값이 중복되지 않는지 확인한다.
4. PRD·ADR의 MVP 제외, ARCHITECTURE 레이어, AGENTS occurrence 멱등성을 대조한다.
5. task index step 3을 `completed`로 변경하고 데이터·재계산·골든 검증을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 파생 능력치를 Room에 저장하도록 권장하지 마라. 이유: source 변경 후 불일치가 생긴다.
- Room schema나 Android 코드를 수정하지 마라. 이유: 설계 문서 phase다.
- 전투에 플랫폼 기본 난수와 부동소수점만 의존하지 마라. 이유: 재현성이 필요하다.
- 골든 수치를 설명 없이 바꾸지 마라. 이유: 공식 실행 가능성의 기준이다.
- 기존 테스트를 깨뜨리지 마라.
