# Todo Quest 문서 인덱스

이 디렉터리는 Todo Quest의 제품, 아키텍처, UI, 개발 환경, 게임 설계와 아트 계약을 찾기 위한 시작점이다.

## 핵심 문서

| 문서 | 책임 |
|---|---|
| [PRD](PRD.md) | 제품 목표, MVP 기능과 제외 범위, 성공 기준 |
| [아키텍처](ARCHITECTURE.md) | 레이어 구조, 데이터 흐름, 도메인 모델과 구현 경계 |
| [ADR](ADR.md) | 주요 기술·제품 결정과 트레이드오프 |
| [UI 디자인 가이드](UI_GUIDE.md) | 화면 구성, 표시 원칙, 접근성과 픽셀 아트 사용 방식 |
| [개발 환경](DEVELOPMENT.md) | 로컬 도구, 빌드·테스트·진단과 harness 실행 방법 |
| [프로젝트 규칙](../AGENTS.md) | 저장소 전체에 적용되는 필수 아키텍처·개발 규칙 |

## 하위 인덱스

- [게임 설계](game-design/README.md): 구현된 캐릭터 성장과 Monster Combat v1 backend, 미구현 UI·장비 후속 설계의 진입점
- [몬스터 능력치와 성장](game-design/monster-stats-and-growth.md): `MAX_HP`·`DAMAGE`·`DEFENSE`, 유형·등급, 10칸 Stage, Room v4 전투 상태의 canonical 계약
- [Battle Map 아트](art/battle/README.md): Calendar Battle Map 배경 자산, runtime 교체와 시각·기계 검증 계약
- [캐릭터 아트](art/character/README.md): 캐릭터 시트, 합성 레이어, 원본과 검증 계약
- [장비 아트](art/equipment/README.md): gameplay 장비 layer, 빈 slot 훈련복과 모험가 상점 세트의 원본·검증 계약
- [몬스터 아트](art/monster/README.md): 몬스터 스프라이트, 기준 명세와 검증 방법
- [NPC 아트](art/npc/README.md): 대장장이 상점 주인 등 NPC의 canonical·runtime 자산, 비편집 참조와 검증 계약

## 문서 책임과 참조 규칙

- 제품 범위와 MVP 포함 여부는 [PRD](PRD.md)를 따른다.
- 아키텍처와 불변 규칙은 [프로젝트 규칙](../AGENTS.md), [아키텍처](ARCHITECTURE.md), [ADR](ADR.md)을 따른다.
- 수치와 기능별 상세 규칙은 [게임 설계 인덱스](game-design/README.md)에서 찾는다. 다만 PRD가 후속 기능으로 분류한 설계를 현재 구현된 기능으로 표현하지 않는다.
- 화면 표시와 접근성은 [UI 디자인 가이드](UI_GUIDE.md)를 따르고, 픽셀 좌표·팔레트·레이어·검증 계약은 각 아트 인덱스가 가리키는 JSON 명세를 따른다.
- 일반적인 기능 또는 디렉터리 안내는 해당 `README.md`, 구체적인 규칙은 해당 Markdown heading, 기계 검증 계약은 JSON, 시각 예시는 PNG를 직접 참조한다.
- Markdown 링크 대상은 현재 파일 기준 상대 경로와 `/`를 사용한다. Windows `\`, `C:\...` 절대 경로나 저장소 외부 로컬 경로는 문서 링크에 사용하지 않는다.
