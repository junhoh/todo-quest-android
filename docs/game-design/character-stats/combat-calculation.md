# 전투 계산

[캐릭터 스탯 설계 인덱스로 돌아가기](../character-stats-design.md)

> 문서 지위: PRD의 MVP 제외 범위를 바꾸지 않는 후속 확장 설계다.

## 확률 판정, 상태 적용 및 전리품

모든 확률 판정은 균등 난수 정수 `0..9999`를 하나 뽑고 그 값이 최종 bp보다 작으면 성공한다. 예를 들어 `500bp`는 난수 `0..499`에서 성공한다.

내부 확률 범위 `0..10,000bp`는 실수 `0.0..1.0`을 정수 고정소수점으로 표현한 것이다. UI에서는 `0..100%`로 변환해 표시하며 전투 계산에는 부동소수점을 사용하지 않는다. 일반 상태 이상의 초기 권장 `effectBase`는 `3,000bp`(30%)이고, 보스 전용·희귀 상태처럼 다른 값이 필요한 효과는 versioned balance config에서 개별 정의한다. 상태 적용률에 대한 기본 스탯 1점당 증가는 `0bp`이며, 대신 대상의 `WILLPOWER` 1점이 저항을 `75bp` 높인다.

상태 적용률은 코어 파생 능력치로 추가하지 않는다. 각 상태 시도는 다음 식으로 별도 계산한다.

```text
statusChanceBp = clamp(
    effectBase
    + sourceEquipmentBonus
    + sourcePassiveBonus
    + sourceTemporaryBonus
    - targetResistance,
    500,
    9500
)
```

각 항은 bp이며 `targetResistance`는 대상의 최종 `STATUS_RESISTANCE`다. 명시적 면역만 이 식을 우회하여 `0bp`로 판정한다. 기본 스탯은 상태 적용률에 직접 기여하지 않는다.

향후 아이템 드롭은 전리품 표, 장비·펫, pity 규칙으로 다룬다. 드롭률이나 pity를 기본 스탯에 연결하지 않으며, 특히 채택하지 않은 행운을 우회적으로 도입하지 않는다.

## 방어와 피해 계산

선형 방어는 `rawDamage - defense`처럼 낮은 공격을 쉽게 최소 피해로 고정하고, 수치가 커질수록 방어 1점의 상대 가치가 급변한다. 고정 비율 선형식도 상한 직전과 직후의 효율 차이가 커진다. 따라서 방어가 늘수록 추가 효율이 완만해지고 공격 규모에도 일관되게 적용되는 다음 점감식을 채택한다.

```text
damageReduction = defense / (defense + 100)
reducedDamage = floor(rawDamage × 100 / (defense + 100))
minimumDamage = max(1, floor(rawDamage × 10 / 100))
finalDamage = max(minimumDamage, reducedDamage)
```

`damageReduction`은 설계 설명용 비율이고 실제 피해 계산은 재현 가능한 두 번째 정수식으로 수행한다. 곱셈은 `Long` 분자로 수행한 뒤 각 식에 표시된 단계에서 내림한다.

- 일반 공격의 `rawDamage`는 최종 `ATTACK`이다.
- 치명타 공격의 `rawDamage`는 `floor(ATTACK × CRITICAL_DAMAGE_BP / 10,000)`이며 이 단계에서 먼저 내림한다.
- 이어서 `reducedDamage`를 계산하며 내리고, 별도로 `minimumDamage`를 계산하며 내린 다음 둘의 최댓값을 `finalDamage`로 사용한다.
- 따라서 치명타 raw damage 내림 → 방어 적용 내림과 최소 피해 내림 → 최댓값 선택 순서를 바꾸지 않는다.

## 최대 HP 변경 시 현재 HP

`MAX_HP`가 바뀌면 현재 HP 비율을 유지한다. `oldMax`와 `newMax`는 1 이상이라는 도메인 불변식을 전제로 한다.

```text
if oldHp == 0:
    newHp = 0
else:
    newHp = clamp(floor(oldHp × newMax / oldMax), 1, newMax)
```

곱셈은 `Long`으로 수행하고 나눗셈 단계에서 내린다. 전투 불능인 `0 HP`는 장비 변경만으로 전투 가능 상태가 되지 않도록 `0`을 유지한다. 그 외에는 반올림 결과를 `1..newMax`로 clamp한다.

최대 HP 증가량만큼 즉시 회복하는 방식은 장비를 반복 교체해 회복하는 악용을 만들 수 있어 채택하지 않는다. 현재 HP 수치를 그대로 고정하는 방식은 최대 HP 증가 장비를 착용하는 순간 체력 비율이 낮아지는 즉시 불이익을 주므로 채택하지 않는다.
