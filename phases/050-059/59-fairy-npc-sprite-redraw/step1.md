# Step 1: redraw-fairy-guide-pixel-art

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/PRD.md`
- `/docs/ARCHITECTURE.md`
- `/docs/ADR.md`
- `/docs/UI_GUIDE.md`
- `/docs/art/npc/README.md`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-spec.json`
- `/docs/art/npc/todo-quest-blacksmith-shopkeeper-front-idle.png`
- `/docs/art/character/todo-quest-character-base-body.png`
- `/scripts/test_validate_npc_sprite.py`
- `/phases/050-059/57-fairy-guide-humanoid-proportion-fix/index.json`
- `/phases/050-059/59-fairy-npc-sprite-redraw/step0.md`
- `/phases/050-059/59-fairy-npc-sprite-redraw/index.json`
- `~/.codex/skills/.system/imagegen/SKILL.md`
- `~/.codex/skills/.system/imagegen/references/prompting.md`
- `~/.codex/skills/.system/imagegen/references/sample-prompts.md`

## 작업

현재 canonical 요정의 정체성과 분위기를 시각적 참조로 사용하되 기존 pixel을 늘리거나 재배치하지 않고 새 비율의 정면 안내 NPC를 처음부터 다시 그린다. 이 step은 regenerated 비교 산출물만 만들며 canonical PNG, spec JSON, runtime resource와 문서는 수정하지 않는다.

테스트 우선 순서는 이미 step 0에서 regenerated 계약이 작성된 상태다. 산출물을 만들기 전 해당 테스트가 skip되는 것을 확인하고, 파일 생성 후 실제 검증으로 전환되어 통과하게 한다.

먼저 `view_image`로 현재 원본과 8배 nearest-neighbor 확대를 검사한다. `git show 304840a:docs/art/npc/todo-quest-fairy-guide-front-idle.png`의 되돌린 phase 57 수정본은 `build/fairy-npc-sprite-redraw/rejected-phase57.png`에만 추출해 실패 비교 기준으로 검사한다. 원본 SHA-256은 `8418CD89EE396BADB197A81117DE9E6CAF1960EA3BC4A05C5BD5E5AB417DE2E0`, 거부된 수정본은 `FD753FA1C52CB16BA4586DD972AC9146C87396BA7936EF09F11DC08FF3942375`여야 한다.

built-in `image_gen`을 사용해 디자인 참조 시안을 한 번에 하나씩 최대 3개 만든다. CLI/API와 `gpt-image-1.5` fallback은 사용하지 않는다. 각 호출에서 `/docs/art/npc/todo-quest-fairy-guide-front-idle.png`를 `Image 1: identity, palette, outfit and pixel-art style reference only; not an edit target`로 전달하고 다음 정규화된 prompt를 사용한다.

```text
Use case: stylized-concept
Asset type: front-idle guide fairy NPC design reference for a 64×64 Android pixel RPG sprite
Input images: Image 1 is visual reference only for character identity, palette, outfit and friendly mood; do not transform, stretch, rearrange or preserve its pixel mask
Primary request: redraw from scratch one friendly humanoid fairy guide facing forward in a calm standing idle pose, keeping the original blonde jaw-length hair, one small gold star, bright eyes, gentle smile, pale green and cream tunic, small brown waist pouch, soft brown boots and paired mint wings
Scene/backdrop: perfectly flat solid #FF00FF chroma-key background with no shadow, gradient, texture, reflection or floor plane
Subject: a natural 2.5-to-3-head-tall fairy; keep the original head approximately the same visual size and distribute extra height through a readable neck, torso, waist and longer separated legs; one hand makes a small guide gesture and the other rests near the pouch
Wings: elongated rounded mint wings beginning behind the shoulders, nearly symmetric, angled outward and upward about 20–30 degrees, visually separated from face and arms, with only one or two compact interior pixel clusters per wing
Style/medium: authentic hand-placed hard-edged logical pixel art, one-pixel dark navy outer contour, two or three flat shade steps per material, readable at 64×64
Composition/framing: one full body character centered on x=32; target opaque extent near [6,14,58,54]; feet near y=54; front-facing upright idle; both legs and boots clearly separate
Color palette: #263B5A #11151C #D99872 #FFD3AE #A9824B #D7B86D #B7B0A3 #F4EFE3 #4F6B55 #789B6E #AFC79A #4A3225 #765238 #A9D9D4 #D9F1E8 #F2C14E
Constraints: preserve the original character identity and head presence; arms must not merge visually with wings; no isolated noisy pixels; no text or watermark
Avoid: vertical stretching, narrow stick-like body, tiny redesigned head, copied pixel mask, high-resolution painted illustration, antialiasing, blur, gradients, dithering, semitransparent edges, attack, spell casting, walking, hovering, extra characters, animation frames, background, ground shadow, light effects or particles
```

생성 결과는 디자인 참조일 뿐 최종 PNG의 pixel source가 아니다. 선택한 참조를 고해상도에서 축소하거나 리샘플링하지 않는다. 새 `64×64` RGBA canvas에서 alpha 0의 검정 투명 배경을 만들고, Pillow 등의 정수 좌표 pixel 작업으로 외곽선과 내부 면을 직접 배치한다. 자동 trace, 원본 mask 확대, 비균등 resize와 보간을 사용하지 않는다.

최종 후보의 조형 기준은 다음과 같다.

- 목표 inclusive opaque bounds는 약 `[6,14,58,54]`, 중심축은 `x=32`, 발끝은 `y=54` 부근이다. 좌표별 1~2px 차이는 허용한다.
- 원본의 큰 머리 존재감을 유지한다. 별은 최고점 근처, 머리·머리카락은 대략 `x=20..44`, `y=15..30`에서 읽히고 phase 57처럼 작고 가는 머리로 바꾸지 않는다.
- 짧지만 분명한 목·어깨, 튜닉 몸통, 허리, 길어진 양다리와 분리된 양부츠가 순서대로 읽힌다. 길이는 머리가 아니라 몸통과 다리에 분배한다.
- 날개는 어깨 뒤에서 시작해 바깥 위로 기울고 몸과 작은 투명 간격을 두며, 팔과 같은 수평선으로 보이지 않는다. 전체 폭은 원본과 비슷하게 유지한다.
- viewer-right 손은 작게 안내하고 반대 손은 주머니 가까이에 둔다. 좌우를 기계적으로 복사하지 않는다.
- 정확히 지정된 16색만 사용하고 부위별 2~3단계만 사용한다. alpha는 0/255, 투명 RGB는 0, 외부 4-neighbor 경계는 `#263B5A` 1px다.

직접 재구성한 논리 pixel 후보도 최대 3개까지만 만든다. 원본, phase 57 거부본과 후보들을 1배율 및 nearest-neighbor 8배율 side-by-side로 `build/fairy-npc-sprite-redraw/`에서 비교한다. 다음 조건을 모두 만족하는 가장 자연스러운 한 개만 아래 경로로 저장한다.

- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png`
- `/docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated-8x.png`

8배 미리보기는 `Image.Resampling.NEAREST`만 사용한다. built-in source와 탈락 후보·비교 strip은 `build/`에만 두고 커밋하지 않는다. 세 후보 안에 원본보다 명확히 좋은 결과가 없거나 built-in tool을 사용할 수 없으면 canonical을 수정하지 말고 step을 `blocked`로 기록한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts\test_validate_npc_sprite.py -k "regenerated" --basetemp build\pytest-59-regenerated
.\.venv\Scripts\python.exe -c "from PIL import Image; a=Image.open('docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated.png'); b=Image.open('docs/art/npc/todo-quest-fairy-guide-front-idle-regenerated-8x.png'); assert a.size==(64,64) and a.mode=='RGBA'; assert b.size==(512,512) and b.mode=='RGBA'; assert set(a.getchannel('A').getdata()) <= {0,255}; assert (255,0,255,255) not in set(a.getdata())"
git diff --check
```

## 검증 절차

1. built-in 생성 시안과 직접 재구성 후보를 각각 `view_image`로 검사한다.
2. 원본·거부본·최대 3개 후보를 1배율과 8배율로 나란히 비교한다.
3. 단순 세로 확대가 아니고 머리·몸통·허리·양다리·양부츠 및 팔·날개가 분리되는지 확인한다.
4. AC를 실행하고 regenerated 테스트가 skip 없이 통과하는지 확인한다.
5. phase index의 step 1을 `completed`로 바꾸고 built-in 모드, 선택 후보, 최종 bounds와 두 산출물 경로를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 원본이나 phase 57 수정본의 pixel을 늘리거나 재배치하지 마라. 이유: 새 비율로 처음부터 재드로잉해야 한다.
- image_gen 결과를 축소해 최종 PNG로 사용하지 마라. 이유: 최종 외곽선과 명암은 논리 64×64에서 직접 정리해야 한다.
- CLI/API fallback으로 전환하지 마라. 이유: 사용자가 fallback을 승인하지 않았다.
- canonical PNG, spec JSON, runtime resource 또는 문서를 수정하지 마라. 이유: 이 step은 비교 가능한 regenerated 산출물만 담당한다.
- 3개를 초과하는 시안을 만들지 마라. 이유: 사용자가 최대 3개 비교를 명시했다.
- 기존 테스트를 깨뜨리지 마라.
