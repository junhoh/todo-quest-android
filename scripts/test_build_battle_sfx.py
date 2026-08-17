import hashlib
import io
import math
import struct
import sys
import wave
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS_DIR))

from build_battle_sfx import (  # noqa: E402
    ASSET_SPECS,
    CHANNELS,
    MAX_LEADING_SILENCE_MS,
    MAX_PEAK_RATIO,
    MAX_TRAILING_SILENCE_MS,
    MIN_PEAK_RATIO,
    RMS_TOLERANCE,
    RMS_TARGET,
    SAMPLE_RATE,
    SAMPLE_WIDTH_BYTES,
    check_assets,
    generate_asset_bytes,
    inspect_wave_bytes,
    write_assets,
)


EXPECTED_DURATIONS_MS = {
    "sfx_player_attack.wav": 180,
    "sfx_monster_hit.wav": 150,
    "sfx_monster_attack.wav": 280,
    "sfx_player_hit.wav": 220,
    "sfx_monster_defeated.wav": 520,
    "sfx_player_defeated.wav": 650,
}


def _samples(wav_bytes: bytes) -> tuple[int, ...]:
    with wave.open(io.BytesIO(wav_bytes), "rb") as source:
        frames = source.readframes(source.getnframes())
    return struct.unpack(f"<{len(frames) // 2}h", frames)


def _rms(samples: tuple[int, ...]) -> float:
    if not samples:
        return 0.0
    return math.sqrt(sum(sample * sample for sample in samples) / len(samples))


def test_specs_use_exact_android_raw_resource_names_and_durations() -> None:
    assert {spec.filename: spec.duration_ms for spec in ASSET_SPECS} == (
        EXPECTED_DURATIONS_MS
    )
    assert len({spec.seed for spec in ASSET_SPECS}) == len(ASSET_SPECS)


def test_generated_assets_are_deterministic_and_have_distinct_hashes() -> None:
    first = {spec.filename: generate_asset_bytes(spec) for spec in ASSET_SPECS}
    second = {spec.filename: generate_asset_bytes(spec) for spec in ASSET_SPECS}

    assert first == second
    assert len({hashlib.sha256(data).hexdigest() for data in first.values()}) == 6


def test_generated_assets_are_loopless_mono_pcm_wav_with_exact_frame_counts() -> None:
    for spec in ASSET_SPECS:
        metrics = inspect_wave_bytes(generate_asset_bytes(spec))

        assert metrics.riff_format == b"WAVE"
        assert metrics.audio_format == 1
        assert metrics.channels == CHANNELS == 1
        assert metrics.sample_width == SAMPLE_WIDTH_BYTES == 2
        assert metrics.sample_rate == SAMPLE_RATE == 44_100
        assert metrics.frame_count == round(SAMPLE_RATE * spec.duration_ms / 1_000)
        assert metrics.duration_ms == spec.duration_ms
        assert metrics.chunk_ids == (b"fmt ", b"data")


def test_generated_assets_keep_headroom_similar_rms_and_short_silence() -> None:
    rms_ratios = []
    for spec in ASSET_SPECS:
        metrics = inspect_wave_bytes(generate_asset_bytes(spec))
        peak_ratio = metrics.peak / 32_767
        rms_ratio = metrics.rms / 32_767
        rms_ratios.append(rms_ratio)

        assert MIN_PEAK_RATIO <= peak_ratio <= MAX_PEAK_RATIO
        assert abs(rms_ratio - RMS_TARGET) <= RMS_TOLERANCE
        assert metrics.peak < 32_767
        assert metrics.leading_silence_ms <= MAX_LEADING_SILENCE_MS
        assert metrics.trailing_silence_ms <= MAX_TRAILING_SILENCE_MS

        samples = _samples(generate_asset_bytes(spec))
        final_window = samples[-round(SAMPLE_RATE * 0.010) :]
        preceding_window = samples[
            -round(SAMPLE_RATE * 0.050) : -round(SAMPLE_RATE * 0.020)
        ]
        assert _rms(final_window) < _rms(preceding_window) * 0.45

    assert max(rms_ratios) - min(rms_ratios) <= RMS_TOLERANCE


def test_defeat_assets_are_distinct_in_duration_and_frequency_character() -> None:
    by_name = {spec.filename: spec for spec in ASSET_SPECS}
    monster = inspect_wave_bytes(
        generate_asset_bytes(by_name["sfx_monster_defeated.wav"])
    )
    player = inspect_wave_bytes(
        generate_asset_bytes(by_name["sfx_player_defeated.wav"])
    )

    assert monster.duration_ms == 520
    assert player.duration_ms == 650
    assert monster.zero_crossings_per_second > player.zero_crossings_per_second * 1.5


def test_write_assets_creates_wavs_and_manifest_with_seed_hash_and_provenance(
    tmp_path: Path,
) -> None:
    write_assets(tmp_path)

    raw_dir = tmp_path / "app" / "src" / "main" / "res" / "raw"
    manifest = (tmp_path / "docs" / "audio" / "README.md").read_text(
        encoding="utf-8"
    )
    assert {path.name for path in raw_dir.glob("*.wav")} == set(
        EXPECTED_DURATIONS_MS
    )
    assert "외부 음원" in manifest
    assert "사용하지" in manifest

    for spec in ASSET_SPECS:
        wav_path = raw_dir / spec.filename
        digest = hashlib.sha256(wav_path.read_bytes()).hexdigest()
        assert spec.filename in manifest
        assert f"{spec.duration_ms} ms" in manifest
        assert str(spec.seed) in manifest
        assert digest in manifest


def test_check_assets_detects_byte_format_and_manifest_drift(tmp_path: Path) -> None:
    write_assets(tmp_path)
    raw_dir = tmp_path / "app" / "src" / "main" / "res" / "raw"

    errors = check_assets(tmp_path)
    assert errors == []

    with wave.open(str(raw_dir / "sfx_player_attack.wav"), "wb") as target:
        target.setnchannels(2)
        target.setsampwidth(2)
        target.setframerate(SAMPLE_RATE)
        target.writeframes(b"\x00\x00\x00\x00" * 20)
    manifest_path = tmp_path / "docs" / "audio" / "README.md"
    manifest_path.write_text("drift", encoding="utf-8")

    errors = check_assets(tmp_path)

    assert any("sfx_player_attack.wav" in error and "bytes" in error for error in errors)
    assert any("sfx_player_attack.wav" in error and "channel" in error for error in errors)
    assert any("README.md" in error for error in errors)
