# Step 0: 핵심 스탯 및 전투 계산 모델 정의

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/app/src/main/java/com/todoquest/domain/model/CharacterProfile.kt`
- `/app/src/main/java/com/todoquest/domain/usecase/RewardPolicy.kt`
- `/phases/020-029/23-character-stats-design/index.json`

## 작업

`/docs/game-design/character-stats-design.md`를 새로 작성한다. 이 문서는 전투와 장비 능력치를 도입하는 후속 확장 설계임을 서두에 명시하고 다음 현재 상태와 충돌을 표로 설명한다.

- PRD의 MVP는 아이템 능력치와 전투를 제외하고 아이템을 외형·수집 전용으로 정의한다.
- 현재 `CharacterProfile`은 레벨, XP, 골드만 저장하고 `totalXp / 100 + 1`로 제한 없이 레벨을 계산한다.
- 현재 `RewardPolicy`는 난이도별 XP·골드만 다루고 실패, 정시 완료, 연속 기록과 전투 event를 다루지 않는다.
- 현재 장비 관련 구현은 픽셀 아트 합성 규칙뿐이고 인벤토리, 무기, 펫 모델은 없다.
- 본 문서만으로 MVP UI나 앱 동작을 바꾸지 않으며 구현 전에 PRD·ADR 갱신과 별도 migration phase가 필요하다.

기본 스탯을 정확히 4개로 확정하고 한글명, 코드, 설명, 최소 1, 초기 5, 투자 상한 60, 장비·효과 포함 절대 상한 99, 투자 가능 여부, 영향 능력치, 차이와 과투자 위험을 표로 작성한다.

- `STRENGTH`(힘): 안정적인 공격력과 치명타 피해. 과투자 시 공격 빌드를 독점할 수 있다.
- `VITALITY`(체력): 최대 HP와 방어력. 과투자 시 전투가 지연되고 실패 의미가 약해질 수 있다.
- `FOCUS`(집중): 공격력 보조와 치명타 확률. 치명타 상한 이후 가치 급락을 주의한다.
- `WILLPOWER`(의지): 상태 저항과 HP 회복. 과투자 시 상태 이상과 소모전이 무의미해질 수 있다.

행운과 민첩은 채택하지 않는다. 행운의 전투·보상 동시 최적화와 자동 전투에 불필요한 회피·속도 확장을 피하기 위한 결정이다.

파생 능력치는 정확히 8개다. `L`은 1~50 레벨이고 표의 기본 스탯은 캐릭터 투자값이다. 각 항목에 역할, 입력, 공식, 최소·최대, 백분율 여부, 반올림, UI/내부 값, 상한 이유를 정의한다.

| 코드 | 장비 적용 전 공식 | 최종 범위 |
|---|---|---|
| `MAX_HP` | `60 + 6 × (L - 1) + 10 × VITALITY` | 1~9,999 |
| `ATTACK` | `5 + (L - 1) + 2 × STRENGTH + FOCUS` | 1~2,000 |
| `DEFENSE` | `3 + floor((L - 1) / 2) + VITALITY` | 0~500 |
| `CRITICAL_CHANCE` | `500 + 50 × FOCUS` bp | 0~5,000bp |
| `CRITICAL_DAMAGE` | `15,000 + 50 × STRENGTH` bp | 10,000~25,000bp |
| `STATUS_RESISTANCE` | `75 × WILLPOWER` bp | 0~7,500bp |
| `HP_RECOVERY` | `2 + floor((L - 1) / 5) + WILLPOWER` | 0~`min(999, floor(MAX_HP × 30%))` |
| `GOLD_GAIN_BONUS` | 기본 0bp, 기본 스탯 기여 없음 | 0~5,000bp |

고정값은 최종 단계에서 `floor`해 `Int`로 표시한다. 확률·배율은 `10,000bp = 100%`인 `Int` 고정소수점이며 기본 UI는 half-up 소수점 한 자리, 상세 진단은 원시 bp를 사용한다. `currentHp`만 저장 상태이고 8개 파생값은 항상 계산한다.

장비 기본 스탯은 해당 파생 능력치의 장비 고정 기여로 환산한다. 고정형은 전체 분자를 `Long`으로 계산하고 마지막에 한 번만 내림한다.

```text
finalValue = clamp(floor(
    (levelBase + characterStatContribution + equipmentFlatContribution)
    × (1 + summedEquipmentPercent)
    × (1 + summedPassivePercent)
    × (1 + summedTemporaryPercent)
), minValue, maxValue)
```

- 장비 % 버킷은 0~+50%, 패시브·세트와 일시 효과 버킷은 각각 -50%~+30%다.
- 같은 버킷은 먼저 합산하고 효과별 연쇄 곱셈을 금지한다.
- v1 패시브·세트·일시 효과는 고정형에 비율 modifier, 확률형에 bp 고정 증감만 준다.
- 치명타·치명타 피해·저항·골드는 모든 bp를 합산한 후 clamp한다. 초과값은 전환하지 않지만 디버프가 clamp 전에 적용되므로 상한 버퍼가 될 수 있다.

난수 `0..9999`가 최종 bp보다 작으면 확률 판정에 성공한다. 상태 적용은 코어 파생 능력치로 추가하지 않고 `clamp(effectBase + sourceEquipmentBonus + sourcePassiveBonus + sourceTemporaryBonus - targetResistance, 500, 9500)`을 사용하며 명시적 면역만 0%다. 기본 스탯은 상태 적용률에 직접 기여하지 않는다. 향후 아이템 드롭도 전리품 표, 장비·펫, pity로 다루고 기본 스탯과 연결하지 않는다.

선형 방어를 비교한 뒤 점감식 `damageReduction = defense / (defense + 100)`을 채택한다. `reducedDamage = floor(rawDamage × 100 / (defense + 100))`, `minimumDamage = max(1, floor(rawDamage × 10 / 100))`, `finalDamage = max(minimumDamage, reducedDamage)`다. 일반 raw damage는 `ATTACK`, 치명타 raw damage는 `floor(ATTACK × CRITICAL_DAMAGE_BP / 10,000)`이며 각 단계에서 내림한다.

최대 HP 변경은 현재 HP 비율 유지로 결정한다. 0 HP는 0을 유지하고 그 외에는 `floor(oldHp × newMax / oldMax)`를 1~새 최대값으로 clamp한다. 증가량만큼 회복은 장비 교체 악용, HP 고정은 증가 장비의 즉시 불이익 때문에 채택하지 않는다.

## Acceptance Criteria

```powershell
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\character-stats-design.md'
@('STRENGTH','VITALITY','FOCUS','WILLPOWER','MAX_HP','ATTACK','DEFENSE','CRITICAL_CHANCE','CRITICAL_DAMAGE','STATUS_RESISTANCE','HP_RECOVERY','GOLD_GAIN_BONUS','defense / (defense + 100)','currentHp') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Missing contract: $_" } }
git diff --check
```

## 검증 절차

1. AC를 실행한다.
2. 기본 4개, 파생 8개와 확률 단위·반올림·피해 내림 순서를 확인한다.
3. PRD, ARCHITECTURE, ADR, UI_GUIDE, AGENTS.md의 CRITICAL 규칙을 확인한다.
4. task index step 0을 `completed`로 변경하고 문서 경로와 핵심 공식을 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 앱 Kotlin, Room schema, PRD, ADR, UI 가이드를 수정하지 마라. 이유: 후속 설계 문서만 작성한다.
- 전투 계산 권장 타입으로 `Float`나 `Double`을 사용하지 마라. 이유: 재현 가능한 반올림이 필요하다.
- 행운을 다섯 번째 기본 스탯으로 추가하지 마라. 이유: 4개 제한과 역할 분리를 지켜야 한다.
- 기존 테스트를 깨뜨리지 마라.
