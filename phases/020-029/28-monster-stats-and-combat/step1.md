# Step 1: 몬스터 밸런스 명세

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/game-design/README.md`
- `/docs/game-design/character-stats/stats-and-progression.md`
- `/docs/game-design/character-stats/combat-calculation.md`
- `/docs/game-design/character-stats/implementation-and-validation.md`
- `/phases/020-029/28-monster-stats-and-combat/index.json`

## 작업

`/docs/game-design/monster-stats-and-growth.md`를 canonical 설계 문서로 만들고 게임 설계 인덱스에서 연결한다. 다음 계약을 표·공식·골든 예시와 함께 정확히 기록한다.

### 능력치와 숫자 계약

- `MAX_HP/maxHp`: 최대 체력, `Int 1..9,999`, 계산값이라 DB에 저장하지 않고 전투 중 바뀌지 않는다.
- `DAMAGE/damage`: 데미지, `Int 1..2,000`, 계산값이라 DB에 저장하지 않고 전투 중 바뀌지 않는다.
- `DEFENSE/defense`: 방어력, `Int 0..200`, 계산값이라 DB에 저장하지 않고 전투 중 바뀌지 않는다.
- `currentHp`: `Int 0..maxHp`, 인스턴스 원천 상태로 Room에 저장하고 피해로 변경된다.
- `isDefeated`: `currentHp == 0`인 파생 `Boolean`이며 별도 저장하지 않는다.

기준 선형 성장은 다음이다.

```text
MAX_HP  = 75 + 5 × (level - 1)
DAMAGE  = 12 + 2 × (level - 1)
DEFENSE = 7  + 2 × (level - 1)
```

레벨 `1/10/30/50/55` 기준값은 각각 HP `75/120/220/320/345`, 데미지 `12/30/70/110/120`, 방어 `7/25/65/105/115`다. 현재 실제 Repository가 장비를 적용하지 않으므로 무장비 균형 플레이어의 Lv1 `HP110/ATK20/DEF8`, Lv10 `214/43/17`, Lv30 `434/93/37`, Lv50 `654/143/57`을 전투 시간 기준으로 사용한다.

선형·완만한 지수·구간별 성장을 비교하고 현재 플레이어 공식과 정수 재현성 때문에 선형을 채택한다. 지수는 플레이어 성장과 어긋나므로 제외하고 구간별은 플레이 테스트 후 새 balance version에만 허용한다.

### 유형과 등급

유형 배율은 다음이다.

| 유형 | HP | DAMAGE | DEFENSE |
|---|---:|---:|---:|
| `BALANCED` | 1.00 | 1.00 | 1.00 |
| `ATTACK` | 0.90 | 1.25 | 0.85 |
| `DEFENSE` | 1.10 | 0.85 | 1.15 |
| `BOSS` | 1.30 | 1.15 | 1.15 |

등급 배율은 NORMAL `1.00/1.00/1.00`, ELITE `1.75/1.25/1.05`, BOSS `2.75/1.40/1.10`이다. 권장 처치 공격 수·빈도·향후 보상 배율·난이도는 NORMAL `3~5, Stage당 8, 1.0, 표준`, ELITE `6~9, Stage당 1, 2.0, 도전적`, BOSS `12~18, Stage당 1, 4.0, 장기·고난도`다. 보상 배율은 설정에 두되 이번 phase에서는 추가 경제 보상에 적용하지 않는다.

combined 배율 상한은 HP `4.0`, DAMAGE `1.75`, DEFENSE `1.5`이고 절대 상한을 마지막에 적용한다. bp `Int`와 중간 `Long`을 사용해 `floor(levelStat × min(typeBp × gradeBp, combinedCapBp × 10,000) / 100,000,000)`을 한 번만 내린다. `BOSS` 유형+BOSS 등급의 결합값은 `3.575/1.61/1.265`다.

### 피해와 Stage

단순 차감과 비율 감소를 사용자 이해, 저·고레벨, 방어가 공격보다 클 때, 최소 피해, 장비 체감, 구현·테스트 난이도로 비교한다. 방식 B를 채택하고 기존 공식을 양방향으로 재사용한다.

```text
reducedDamage = floor(rawDamage × 100 / (defense + 100))
minimumDamage = max(1, floor(rawDamage × 1,000 / 10,000))
finalDamage = max(minimumDamage, reducedDamage)
```

몬스터 `DAMAGE`는 별도 공격력·스킬·치명타 없이 raw damage다. 런타임에 `Float`와 `Double`을 사용하지 않는다. 몬스터 방어 상한 200으로 방어 감소율을 약 66.7% 이하로 제한한다.

Stage는 `NORMAL 1~4 → ELITE 5 → NORMAL 6~9 → BOSS 10`이다. NORMAL 유형은 `[BALANCED, ATTACK, DEFENSE]`를 `(stageNumber - 1 + normalOrdinal) % 3`으로 순환하고 ELITE도 Stage 번호로 세 일반 유형을 순환한다. 10번째는 항상 BOSS 유형·등급이다. Stage 시작 시 플레이어 레벨 `1..50`을 `stageLevel`로 잠그고 NORMAL `+0`, ELITE `+1`, BOSS `+2`, 몬스터 절대 상한 `55`를 적용한다.

치명 피해는 `max(1, floor(maxHp × 2,500 / 10,000))`으로 즉시 부활하고 `wasLethal` event를 후속 디버프 근거로 저장한다. 몬스터 처치 시 기존 `HP_RECOVERY`를 1회 적용한다.

### 데이터 경계

`MonsterDefinition`은 id/nameKey/type와 base/growth만 갖고 grade는 중복 definition을 피하기 위해 `MonsterInstance`에 둔다. definition은 versioned catalog에서 읽고 Room에는 instance id, definition id, grade, Stage/encounter, level, current HP, balance version만 저장한다. 최종 `MonsterStats`와 `isDefeated`는 계산한다. 공격·Stage·cursor persistence와 앱 종료 후 보존 범위를 명시한다.

## Acceptance Criteria

```powershell
$doc = Get-Content -Raw -Encoding UTF8 -LiteralPath 'docs\game-design\monster-stats-and-growth.md'
@('MAX_HP','DAMAGE','DEFENSE','currentHp','isDefeated','75 + 5','12 + 2','7  + 2','BALANCED','ATTACK','BOSS','3.575','66.7%','Stage','25%','MonsterDefinition','MonsterInstance','MonsterStats') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Monster design contract missing: $_" } }
@('75/120/220/320/345','12/30/70/110/120','7/25/65/105/115') | ForEach-Object { if (-not $doc.Contains($_)) { throw "Golden progression missing: $_" } }
git diff --check
```

## 검증 절차

1. AC를 실행하고 레벨별 기준값과 보스 결합 배율을 독립 재계산한다.
2. 플레이어 기존 공식과 피해 내림 순서가 일치하는지 확인한다.
3. phase index의 step 1을 완료 처리하고 문서와 핵심 수치를 한국어 `summary`로 기록한다.

## 금지사항

- 전투 UI나 몬스터 처치 경제 보상을 구현 완료로 표시하지 마라. 이유: 이번 범위 밖이다.
- `Float`나 `Double`을 런타임 공식으로 권장하지 마라. 이유: 재현 가능한 정수 내림이 필요하다.
- 최종 능력치를 Room에 저장하지 마라. 이유: definition/config와 불일치가 생긴다.
- 기존 테스트를 깨뜨리지 마라.
