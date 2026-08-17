# Step 3: generate-original-battle-sfx-assets

## 읽어야 할 파일

- `/AGENTS.md`
- `/docs/ADR.md`
- `/docs/DEVELOPMENT.md`
- `/app/build.gradle.kts`
- `/scripts/validate_battle_map.py`
- `/scripts/test_validate_battle_map.py`
- `/phases/060-069/61-battle-sound-effects/step0.md`
- `/phases/060-069/61-battle-sound-effects/index.json`

## 작업

Python standard library만 사용하는 결정론적 `scripts/build_battle_sfx.py`와 `scripts/test_build_battle_sfx.py`를 테스트 우선으로 추가한다. 외부 음원·network·음성·음악을 사용하지 않고 seeded waveform synthesis로 다음 mono PCM WAV를 만든다.

- `app/src/main/res/raw/sfx_player_attack.wav`: 약 180ms, 짧은 검/바람 sweep.
- `app/src/main/res/raw/sfx_monster_hit.wav`: 약 150ms, 선명한 짧은 impact.
- `app/src/main/res/raw/sfx_monster_attack.wav`: 약 280ms, 낮고 둔탁한 claw/swing.
- `app/src/main/res/raw/sfx_player_hit.wav`: 약 220ms, monster hit보다 낮은 armor/body impact.
- `app/src/main/res/raw/sfx_monster_defeated.wav`: 약 520ms, 짧은 falling/dissolve 처치음.
- `app/src/main/res/raw/sfx_player_defeated.wav`: 약 650ms, 처치음과 구분되는 무겁고 불쾌하지 않은 전투 불능음.

모든 파일은 44,100Hz, 16-bit PCM, mono, loop 없는 RIFF/WAVE로 생성한다. 짧은 attack envelope, 끝 fade-out, peak headroom과 유사한 RMS 목표를 적용해 clipping, 긴 선행/후행 무음과 과도한 잔향을 방지한다. defeat 두 종류는 waveform·주파수 범위·duration으로 명확히 구분한다.

script는 기본 write와 `--check` 모드를 제공한다. `--check`는 repository WAV를 다시 생성한 expected bytes와 비교하고 format, frame count, duration, channel, sample width, sample rate, peak, RMS와 leading/trailing silence 제한을 검사한다. `docs/audio/README.md` 또는 동등한 manifest에 각 파일의 의도·duration·SHA-256, 결정론적 seed, 프로젝트에서 직접 합성한 원본이라 외부 license/source가 없다는 사실을 기록한다.

## Acceptance Criteria

```powershell
$env:PYTHONUTF8='1'
.\.venv\Scripts\python.exe -m pytest scripts/test_build_battle_sfx.py --basetemp .\.venv\pytest-tmp-battle-sfx
.\.venv\Scripts\python.exe scripts/build_battle_sfx.py --check
git diff --check
```

## 검증 절차

1. format·duration·distinct hash·clipping·silence·determinism 테스트를 먼저 작성한다.
2. generator로 여섯 WAV를 만든 뒤 AC 명령을 실행한다.
3. 파일명이 Android raw resource 규칙과 사용자 요구에 정확히 일치하는지 확인한다.
4. task index의 step 3을 `completed`로 바꾸고 생성 script, asset과 검증 결과를 한국어 `summary` 한 줄로 기록한다.

## 금지사항

- 외부 음원을 다운로드하거나 포함하지 마라. 이유: 라이선스와 재배포 출처가 불명확해진다.
- mp3/ogg encoder나 새 Python dependency를 설치하지 마라. 이유: PCM WAV로 요구 품질과 재현성을 충족할 수 있다.
- clipping을 normalization으로 숨기거나 긴 silence를 남기지 마라. 이유: 연속 효과음의 체감 품질이 저하된다.
- 기존 이미지 asset을 수정하지 마라. 이유: 이번 step은 audio asset만 다룬다.
- 기존 테스트를 깨뜨리지 마라.
