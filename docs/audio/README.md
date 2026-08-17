# Todo Quest 전투 효과음 원본

이 디렉터리는 Battle Sound Effects v1의 결정론적 원본 계약을 기록한다. 여섯 WAV는 `scripts/build_battle_sfx.py`가 Python 표준 라이브러리만으로 seeded waveform synthesis해 만든 프로젝트 자체 원본이다.

외부 음원, 음성, 음악, network source를 다운로드하거나 사용하지 않았으므로 가져온 외부 license/source가 없다.

모든 파일은 44,100 Hz, 16-bit PCM, mono, loop 없는 RIFF/WAVE다. 기본 실행은 runtime raw resource와 이 manifest를 다시 쓰고, `--check`는 결정론적 expected bytes와 format·frame·duration·peak·RMS·무음 제한을 검증한다.

| 파일 | 의도 | 길이 | 결정론적 seed | SHA-256 |
|---|---|---:|---:|---|
| `sfx_player_attack.wav` | 짧은 검날과 바람이 함께 스치는 플레이어 공격 sweep | 180 ms | 61301 | `f02a633e0027a19817e094646c8d7f3f465cecb127be97aa8f6e094a061ffb37` |
| `sfx_monster_hit.wav` | 몬스터 타격을 즉시 구분하는 선명한 짧은 impact | 150 ms | 61302 | `363de6f924791571179fba97deb03e5e8bd77e465ddb50c14cb83de2d84375d6` |
| `sfx_monster_attack.wav` | 낮고 둔탁하게 지나가는 몬스터 claw/swing | 280 ms | 61303 | `cda216d6456933a600fdf1a7bf7265d3ac091388d1d8c9cd849855a48ed5524e` |
| `sfx_player_hit.wav` | 몬스터 타격음보다 낮은 갑옷과 몸의 impact | 220 ms | 61304 | `7eecfc866bbc895f076f2a2ffc35ce125e2077a05363232c4d01e5b7a6127055` |
| `sfx_monster_defeated.wav` | 밝은 고주파 입자가 아래로 흩어지는 몬스터 처치음 | 520 ms | 61305 | `5e30a943472d61b5d47281eb241acf3ff0dfa3beafe1b1ac1808bf9eea607b3f` |
| `sfx_player_defeated.wav` | 무겁지만 불쾌하지 않게 가라앉는 플레이어 전투 불능음 | 650 ms | 61306 | `100c618b8f676caac598acaa825d03cee6df603434ce43b8c9679ae65affafa4` |

재생 순서와 application-scope SoundPool 수명 계약은 [ADR-024](../ADR.md#adr-024-battle-sound-effects는-replay-없는-domain-effect와-application-scope-audio로-처리한다)를 따른다.
