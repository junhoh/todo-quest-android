"""Build and validate Todo Quest's original deterministic battle sound effects."""

from __future__ import annotations

import argparse
import hashlib
import io
import math
import pathlib
import random
import struct
import sys
import wave
from collections.abc import Callable, Sequence
from dataclasses import dataclass


ROOT = pathlib.Path(__file__).resolve().parent.parent
RAW_RESOURCE_DIR = pathlib.Path("app/src/main/res/raw")
MANIFEST_PATH = pathlib.Path("docs/audio/README.md")

SAMPLE_RATE = 44_100
CHANNELS = 1
SAMPLE_WIDTH_BYTES = 2
PCM_MAX = 32_767
RMS_TARGET = 0.14
RMS_TOLERANCE = 0.012
MIN_PEAK_RATIO = 0.27
MAX_PEAK_RATIO = 0.82
SILENCE_THRESHOLD_RATIO = 0.01
MAX_LEADING_SILENCE_MS = 12.0
MAX_TRAILING_SILENCE_MS = 24.0


@dataclass(frozen=True)
class BattleSfxSpec:
    filename: str
    duration_ms: int
    seed: int
    intent: str
    synthesis: str


@dataclass(frozen=True)
class WaveMetrics:
    riff_format: bytes
    chunk_ids: tuple[bytes, ...]
    audio_format: int
    channels: int
    sample_width: int
    sample_rate: int
    frame_count: int
    duration_ms: int
    peak: int
    rms: float
    leading_silence_ms: float
    trailing_silence_ms: float
    zero_crossings_per_second: float


ASSET_SPECS = (
    BattleSfxSpec(
        filename="sfx_player_attack.wav",
        duration_ms=180,
        seed=61_301,
        intent="짧은 검날과 바람이 함께 스치는 플레이어 공격 sweep",
        synthesis="player_attack",
    ),
    BattleSfxSpec(
        filename="sfx_monster_hit.wav",
        duration_ms=150,
        seed=61_302,
        intent="몬스터 타격을 즉시 구분하는 선명한 짧은 impact",
        synthesis="monster_hit",
    ),
    BattleSfxSpec(
        filename="sfx_monster_attack.wav",
        duration_ms=280,
        seed=61_303,
        intent="낮고 둔탁하게 지나가는 몬스터 claw/swing",
        synthesis="monster_attack",
    ),
    BattleSfxSpec(
        filename="sfx_player_hit.wav",
        duration_ms=220,
        seed=61_304,
        intent="몬스터 타격음보다 낮은 갑옷과 몸의 impact",
        synthesis="player_hit",
    ),
    BattleSfxSpec(
        filename="sfx_monster_defeated.wav",
        duration_ms=520,
        seed=61_305,
        intent="밝은 고주파 입자가 아래로 흩어지는 몬스터 처치음",
        synthesis="monster_defeated",
    ),
    BattleSfxSpec(
        filename="sfx_player_defeated.wav",
        duration_ms=650,
        seed=61_306,
        intent="무겁지만 불쾌하지 않게 가라앉는 플레이어 전투 불능음",
        synthesis="player_defeated",
    ),
)


def _smoothstep(value: float) -> float:
    clamped = min(max(value, 0.0), 1.0)
    return clamped * clamped * (3.0 - 2.0 * clamped)


def _envelope(
    time_seconds: float,
    duration_seconds: float,
    attack_ms: float,
    release_ms: float,
    decay: float,
) -> float:
    attack = _smoothstep(time_seconds / (attack_ms / 1_000.0))
    remaining = duration_seconds - time_seconds
    release = _smoothstep(remaining / (release_ms / 1_000.0))
    return attack * release * math.exp(-decay * time_seconds / duration_seconds)


def _linear_chirp_phase(
    time_seconds: float,
    duration_seconds: float,
    start_hz: float,
    end_hz: float,
) -> float:
    rate = (end_hz - start_hz) / duration_seconds
    return math.tau * (
        start_hz * time_seconds + 0.5 * rate * time_seconds * time_seconds
    )


def _player_attack(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    previous_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        progress = time_seconds / duration_seconds
        noise = rng.uniform(-1.0, 1.0)
        high_noise = noise - 0.72 * previous_noise
        previous_noise = noise
        phase = _linear_chirp_phase(
            time_seconds, duration_seconds, 2_100.0, 430.0
        )
        blade = 0.70 * math.sin(phase) + 0.20 * math.sin(1.83 * phase + 0.4)
        whoosh = 0.48 * high_noise * math.sin(math.pi * progress)
        envelope = _envelope(time_seconds, duration_seconds, 5.0, 52.0, 0.55)
        values.append(envelope * (blade + whoosh))
    return values


def _monster_hit(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    previous_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        noise = rng.uniform(-1.0, 1.0)
        high_noise = noise - 0.55 * previous_noise
        previous_noise = noise
        phase = _linear_chirp_phase(time_seconds, duration_seconds, 920.0, 260.0)
        crack = 0.64 * high_noise + 0.60 * math.sin(phase)
        envelope = _envelope(time_seconds, duration_seconds, 2.5, 55.0, 2.5)
        values.append(envelope * crack)
    return values


def _monster_attack(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    low_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        progress = time_seconds / duration_seconds
        low_noise += 0.055 * (rng.uniform(-1.0, 1.0) - low_noise)
        phase = _linear_chirp_phase(time_seconds, duration_seconds, 210.0, 72.0)
        claw_pulse = math.sin(math.pi * min(progress * 1.45, 1.0))
        swing = (
            0.78 * math.sin(phase)
            + 0.28 * math.sin(0.52 * phase + 0.8)
            + 0.48 * low_noise * claw_pulse
        )
        envelope = _envelope(time_seconds, duration_seconds, 8.0, 75.0, 0.65)
        values.append(envelope * swing)
    return values


def _player_hit(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    low_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        low_noise += 0.075 * (rng.uniform(-1.0, 1.0) - low_noise)
        phase = _linear_chirp_phase(time_seconds, duration_seconds, 250.0, 82.0)
        impact = (
            0.82 * math.sin(phase)
            + 0.34 * math.sin(0.61 * phase + 0.25)
            + 0.38 * low_noise
        )
        envelope = _envelope(time_seconds, duration_seconds, 3.5, 72.0, 1.65)
        values.append(envelope * impact)
    return values


def _monster_defeated(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    previous_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        progress = time_seconds / duration_seconds
        noise = rng.uniform(-1.0, 1.0)
        bright_noise = noise - 0.62 * previous_noise
        previous_noise = noise
        phase = _linear_chirp_phase(time_seconds, duration_seconds, 980.0, 125.0)
        dissolve_gate = 0.58 + 0.42 * math.sin(math.tau * (7.0 * progress**1.3)) ** 2
        falling = 0.62 * math.sin(phase) + 0.24 * math.sin(2.31 * phase + 0.6)
        dissolve = 0.48 * bright_noise * dissolve_gate
        envelope = _envelope(time_seconds, duration_seconds, 9.0, 118.0, 0.75)
        values.append(envelope * (falling + dissolve))
    return values


def _player_defeated(
    rng: random.Random,
    frame_count: int,
    duration_seconds: float,
) -> list[float]:
    values: list[float] = []
    low_noise = 0.0
    for index in range(frame_count):
        time_seconds = index / SAMPLE_RATE
        low_noise += 0.025 * (rng.uniform(-1.0, 1.0) - low_noise)
        phase = _linear_chirp_phase(time_seconds, duration_seconds, 190.0, 52.0)
        heavy_fall = (
            0.88 * math.sin(phase)
            + 0.34 * math.sin(0.51 * phase + 0.55)
            + 0.16 * math.sin(1.49 * phase + 0.2)
            + 0.25 * low_noise
        )
        envelope = _envelope(time_seconds, duration_seconds, 13.0, 150.0, 0.55)
        values.append(envelope * heavy_fall)
    return values


_SYNTHESIZERS: dict[
    str,
    Callable[[random.Random, int, float], list[float]],
] = {
    "player_attack": _player_attack,
    "monster_hit": _monster_hit,
    "monster_attack": _monster_attack,
    "player_hit": _player_hit,
    "monster_defeated": _monster_defeated,
    "player_defeated": _player_defeated,
}


def _to_pcm_samples(values: list[float]) -> tuple[int, ...]:
    mean = sum(values) / len(values)
    centered = [math.tanh((value - mean) * 1.15) for value in values]
    current_rms = math.sqrt(sum(value * value for value in centered) / len(centered))
    if current_rms <= 0.0:
        raise ValueError("synthesis produced silence")
    peak = max(abs(value) for value in centered)
    gain = min(RMS_TARGET / current_rms, MAX_PEAK_RATIO / peak)
    samples = tuple(round(value * gain * PCM_MAX) for value in centered)
    if max(abs(sample) for sample in samples) >= PCM_MAX:
        raise ValueError("synthesis exceeded PCM peak headroom")
    return samples


def generate_asset_bytes(spec: BattleSfxSpec) -> bytes:
    """Return deterministic mono PCM WAV bytes for one battle sound effect."""
    frame_count = round(SAMPLE_RATE * spec.duration_ms / 1_000)
    duration_seconds = frame_count / SAMPLE_RATE
    rng = random.Random(spec.seed)
    try:
        values = _SYNTHESIZERS[spec.synthesis](rng, frame_count, duration_seconds)
    except KeyError as error:
        raise ValueError(f"unknown synthesis: {spec.synthesis}") from error
    samples = _to_pcm_samples(values)
    frames = struct.pack(f"<{len(samples)}h", *samples)

    output = io.BytesIO()
    with wave.open(output, "wb") as target:
        target.setnchannels(CHANNELS)
        target.setsampwidth(SAMPLE_WIDTH_BYTES)
        target.setframerate(SAMPLE_RATE)
        target.writeframes(frames)
    return output.getvalue()


def _riff_chunks(wav_bytes: bytes) -> tuple[bytes, tuple[bytes, ...], int]:
    if len(wav_bytes) < 12 or wav_bytes[:4] != b"RIFF":
        raise ValueError("missing RIFF header")
    riff_format = wav_bytes[8:12]
    chunk_ids: list[bytes] = []
    audio_format = -1
    offset = 12
    while offset + 8 <= len(wav_bytes):
        chunk_id = wav_bytes[offset : offset + 4]
        chunk_size = struct.unpack_from("<I", wav_bytes, offset + 4)[0]
        payload_start = offset + 8
        payload_end = payload_start + chunk_size
        if payload_end > len(wav_bytes):
            raise ValueError(f"truncated {chunk_id!r} chunk")
        chunk_ids.append(chunk_id)
        if chunk_id == b"fmt ":
            if chunk_size < 2:
                raise ValueError("fmt chunk is too short")
            audio_format = struct.unpack_from("<H", wav_bytes, payload_start)[0]
        offset = payload_end + (chunk_size % 2)
    return riff_format, tuple(chunk_ids), audio_format


def _edge_silence_ms(samples: tuple[int, ...], from_start: bool) -> float:
    threshold = round(PCM_MAX * SILENCE_THRESHOLD_RATIO)
    iterable = samples if from_start else reversed(samples)
    silent_frames = 0
    for sample in iterable:
        if abs(sample) > threshold:
            break
        silent_frames += 1
    return silent_frames * 1_000.0 / SAMPLE_RATE


def _zero_crossings_per_second(samples: tuple[int, ...], duration_seconds: float) -> float:
    crossings = 0
    previous_sign = 0
    for sample in samples:
        sign = 1 if sample > 0 else -1 if sample < 0 else 0
        if sign and previous_sign and sign != previous_sign:
            crossings += 1
        if sign:
            previous_sign = sign
    return crossings / duration_seconds


def inspect_wave_bytes(wav_bytes: bytes) -> WaveMetrics:
    """Read the format and measurable loudness/silence properties of WAV bytes."""
    riff_format, chunk_ids, audio_format = _riff_chunks(wav_bytes)
    try:
        with wave.open(io.BytesIO(wav_bytes), "rb") as source:
            channels = source.getnchannels()
            sample_width = source.getsampwidth()
            sample_rate = source.getframerate()
            frame_count = source.getnframes()
            compression = source.getcomptype()
            frames = source.readframes(frame_count)
    except (EOFError, wave.Error) as error:
        raise ValueError(f"invalid WAV: {error}") from error
    if compression != "NONE":
        raise ValueError(f"WAV must be uncompressed PCM; got {compression}")
    if sample_width != SAMPLE_WIDTH_BYTES:
        raise ValueError(f"unsupported sample width for inspection: {sample_width}")
    sample_count = len(frames) // SAMPLE_WIDTH_BYTES
    samples = struct.unpack(f"<{sample_count}h", frames)
    if not samples:
        raise ValueError("WAV contains no samples")
    peak = max(abs(sample) for sample in samples)
    rms = math.sqrt(sum(sample * sample for sample in samples) / len(samples))
    duration_seconds = frame_count / sample_rate
    return WaveMetrics(
        riff_format=riff_format,
        chunk_ids=chunk_ids,
        audio_format=audio_format,
        channels=channels,
        sample_width=sample_width,
        sample_rate=sample_rate,
        frame_count=frame_count,
        duration_ms=round(duration_seconds * 1_000),
        peak=peak,
        rms=rms,
        leading_silence_ms=_edge_silence_ms(samples, from_start=True),
        trailing_silence_ms=_edge_silence_ms(samples, from_start=False),
        zero_crossings_per_second=_zero_crossings_per_second(
            samples, duration_seconds
        ),
    )


def _validate_metrics(metrics: WaveMetrics, spec: BattleSfxSpec) -> list[str]:
    expected_frames = round(SAMPLE_RATE * spec.duration_ms / 1_000)
    errors: list[str] = []
    expected_values = (
        (metrics.riff_format == b"WAVE", "RIFF format must be WAVE"),
        (metrics.chunk_ids == (b"fmt ", b"data"), "WAV must not contain loop chunks"),
        (metrics.audio_format == 1, "audio format must be PCM 1"),
        (metrics.channels == CHANNELS, f"channel count must be {CHANNELS}"),
        (
            metrics.sample_width == SAMPLE_WIDTH_BYTES,
            f"sample width must be {SAMPLE_WIDTH_BYTES} bytes",
        ),
        (metrics.sample_rate == SAMPLE_RATE, f"sample rate must be {SAMPLE_RATE}"),
        (
            metrics.frame_count == expected_frames,
            f"frame count must be {expected_frames}",
        ),
        (
            metrics.duration_ms == spec.duration_ms,
            f"duration must be {spec.duration_ms} ms",
        ),
    )
    errors.extend(message for valid, message in expected_values if not valid)
    peak_ratio = metrics.peak / PCM_MAX
    rms_ratio = metrics.rms / PCM_MAX
    if not MIN_PEAK_RATIO <= peak_ratio <= MAX_PEAK_RATIO:
        errors.append(
            f"peak ratio must be {MIN_PEAK_RATIO:.3f}..{MAX_PEAK_RATIO:.3f}; "
            f"got {peak_ratio:.6f}"
        )
    if abs(rms_ratio - RMS_TARGET) > RMS_TOLERANCE:
        errors.append(
            f"RMS ratio must be {RMS_TARGET:.3f} +/- {RMS_TOLERANCE:.3f}; "
            f"got {rms_ratio:.6f}"
        )
    if metrics.leading_silence_ms > MAX_LEADING_SILENCE_MS:
        errors.append(
            f"leading silence must be <= {MAX_LEADING_SILENCE_MS:.1f} ms; "
            f"got {metrics.leading_silence_ms:.3f} ms"
        )
    if metrics.trailing_silence_ms > MAX_TRAILING_SILENCE_MS:
        errors.append(
            f"trailing silence must be <= {MAX_TRAILING_SILENCE_MS:.1f} ms; "
            f"got {metrics.trailing_silence_ms:.3f} ms"
        )
    return errors


def _render_manifest(generated: dict[str, bytes]) -> str:
    lines = [
        "# Todo Quest 전투 효과음 원본",
        "",
        "이 디렉터리는 Battle Sound Effects v1의 결정론적 원본 계약을 기록한다. "
        "여섯 WAV는 `scripts/build_battle_sfx.py`가 Python 표준 라이브러리만으로 "
        "seeded waveform synthesis해 만든 프로젝트 자체 원본이다.",
        "",
        "외부 음원, 음성, 음악, network source를 다운로드하거나 사용하지 않았으므로 "
        "가져온 외부 license/source가 없다.",
        "",
        "모든 파일은 44,100 Hz, 16-bit PCM, mono, loop 없는 RIFF/WAVE다. "
        "기본 실행은 runtime raw resource와 이 manifest를 다시 쓰고, `--check`는 "
        "결정론적 expected bytes와 format·frame·duration·peak·RMS·무음 제한을 검증한다.",
        "",
        "| 파일 | 의도 | 길이 | 결정론적 seed | SHA-256 |",
        "|---|---|---:|---:|---|",
    ]
    for spec in ASSET_SPECS:
        digest = hashlib.sha256(generated[spec.filename]).hexdigest()
        lines.append(
            f"| `{spec.filename}` | {spec.intent} | {spec.duration_ms} ms | "
            f"{spec.seed} | `{digest}` |"
        )
    lines.extend(
        [
            "",
            "재생 순서와 application-scope SoundPool 수명 계약은 "
            "[ADR-024](../ADR.md#adr-024-battle-sound-effects는-replay-없는-domain-effect와-application-scope-audio로-처리한다)를 따른다.",
            "",
        ]
    )
    return "\n".join(lines)


def _generated_assets() -> dict[str, bytes]:
    return {spec.filename: generate_asset_bytes(spec) for spec in ASSET_SPECS}


def write_assets(root: pathlib.Path = ROOT) -> None:
    """Write all deterministic WAV resources and their manifest below root."""
    generated = _generated_assets()
    raw_dir = root / RAW_RESOURCE_DIR
    raw_dir.mkdir(parents=True, exist_ok=True)
    for filename, wav_bytes in generated.items():
        (raw_dir / filename).write_bytes(wav_bytes)
    manifest_path = root / MANIFEST_PATH
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(_render_manifest(generated), encoding="utf-8", newline="\n")


def check_assets(root: pathlib.Path = ROOT) -> list[str]:
    """Return deterministic-byte and WAV-contract violations below root."""
    generated = _generated_assets()
    specs_by_name = {spec.filename: spec for spec in ASSET_SPECS}
    raw_dir = root / RAW_RESOURCE_DIR
    errors: list[str] = []
    for filename, expected_bytes in generated.items():
        path = raw_dir / filename
        try:
            actual_bytes = path.read_bytes()
        except OSError as error:
            errors.append(f"{path}: could not read WAV: {error}")
            continue
        if actual_bytes != expected_bytes:
            errors.append(f"{path}: bytes differ from deterministic synthesis")
        try:
            metrics = inspect_wave_bytes(actual_bytes)
        except (ValueError, struct.error) as error:
            errors.append(f"{path}: {error}")
            continue
        errors.extend(
            f"{path}: {error}"
            for error in _validate_metrics(metrics, specs_by_name[filename])
        )

    manifest_path = root / MANIFEST_PATH
    expected_manifest = _render_manifest(generated)
    try:
        actual_manifest = manifest_path.read_text(encoding="utf-8")
    except OSError as error:
        errors.append(f"{manifest_path}: could not read manifest: {error}")
    else:
        if actual_manifest != expected_manifest:
            errors.append(f"{manifest_path}: manifest differs from generated contract")
    return errors


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build deterministic original Todo Quest battle PCM WAV assets."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate repository WAV bytes and manifest without writing",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    if args.check:
        errors = check_assets()
        if errors:
            for error in errors:
                print(error, file=sys.stderr)
            return 1
        print("Battle SFX validation passed: 6 deterministic PCM WAV assets")
        return 0

    write_assets()
    print("Battle SFX assets written: 6 WAV files and docs/audio/README.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
