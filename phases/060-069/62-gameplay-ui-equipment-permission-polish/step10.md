# Step 10: synchronize-canonical-docs

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/README.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/DEVELOPMENT.md`
- `/docs/game-design/character-stats/modifiers-and-equipment.md`
- `/docs/art/character/README.md`
- `/docs/art/equipment/README.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step0.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step4.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step6.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/step9.md`
- `/phases/060-069/62-gameplay-ui-equipment-permission-polish/index.json`

## 작업

구현 결과를 canonical 문서에 동기화하고 ADR-025로 호환성 결정을 기록한다.

PRD/UI guide에는 다음 사용자 계약을 반영한다: 중상 badge가 actor 좌표를 바꾸지 않음, stat summary 고정 크기, card/detail 공통 `구매`·`구매 불가`·`장착`·`해제` action 및 우측 하단 고정, 선택 장비 해제 시 선택/preview 해제, 빈 slot 중립 훈련복, 기존 adventure 7부위 상점 세트, 설정의 일반 알림 권한 관리.

ARCHITECTURE에는 `EquipmentStoreSnapshot.ownedEquipmentByEquipmentId`, repository→ViewModel typed action 흐름, `ReminderScheduler` capability state→SettingsViewModel→Compose launcher/Android adapter 경계를 기록한다. UI가 DAO/AlarmManager/WorkManager를 직접 호출하지 않는다는 기존 원칙을 유지한다.

ADR-025에는 Room v15의 비파괴 `MIGRATION_14_15`, appearance fallback만 빈 loadout으로 갱신하고 실제 ownership/`character_equipment`를 보존하는 결정, 고정 ID 1019~1025 adventure catalog, 빈 slot fallback과 legacy layer-key 호환을 기록한다. 알림은 POST_NOTIFICATIONS/app/channel settings만 포함하고 exact alarm은 제외하며 권한 거부가 핵심 기능을 막지 않는다고 명시한다.

game design 문서에는 7개 UNCOMMON level 5 item의 ID·가격·두 modifier를 step 4 표와 동일하게 기록한다. art README에는 default training fallback과 adventure 상품 layer, `gloves_adventure`, 3분할 sword 조합을 기록한다. DEVELOPMENT에는 Room v15 migration chain 및 art/전체 검증 명령을 최신화한다. 과거 ADR의 당시 결정 문장을 현재 사실처럼 덮어쓰지 말고 superseded 범위를 새 ADR에서 명시한다.

## Acceptance Criteria

```powershell
rg -n "ADR-025|MIGRATION_14_15|1019|1025|ownedEquipmentByEquipmentId|구매 불가|알림 권한|gloves_adventure" docs\PRD.md docs\ARCHITECTURE.md docs\ADR.md docs\UI_GUIDE.md docs\DEVELOPMENT.md docs\game-design\character-stats\modifiers-and-equipment.md docs\art\character\README.md docs\art\equipment\README.md
git diff --check
```

## 검증 절차

1. 문서의 item ID/가격/modifier가 step 4와 정확히 같은지 대조한다.
2. Room v15 migration의 변경/보존 table 경계가 코드와 같은지 확인한다.
3. shop action 우선순위와 notification scope가 UI/ViewModel 구현과 같은지 확인한다.
4. 과거 ADR-021~024의 역사적 범위를 직접 수정하지 않고 ADR-025에서 후속 결정을 설명했는지 확인한다.
5. 성공 시 task index의 step 10을 `completed`로 바꾸고 동기화한 문서 계약을 한국어 `summary` 두 줄로 기록한다.

## 금지사항

- 과거 ADR을 삭제하거나 당시 결정을 현재 구현에 맞춰 소급 수정하지 마라. 이유: 결정 이력을 보존해야 한다.
- migration이 gameplay ownership/equipment를 초기화한다고 문서화하지 마라. 이유: 실제 보존 계약과 반대다.
- 알림 설정 범위에 exact alarm을 포함하지 마라. 이유: 이번 phase에서 승인되지 않은 범위다.
- 코드와 다른 item 수치나 UI label을 문서에 기록하지 마라. 이유: canonical 문서 drift가 발생한다.
- 기존 테스트를 삭제하거나 완화하지 마라.
