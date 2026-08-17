import hashlib
import json
import sys
from pathlib import Path

import pytest
from PIL import Image


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
SPEC_PATH = ROOT / "docs" / "art" / "battle" / "battle-map-grassland-spec.json"
sys.path.insert(0, str(SCRIPTS_DIR))

from validate_battle_map import load_spec, main, validate_battle_map  # noqa: E402


def _write_png(path: Path, mode: str = "RGB", alpha: int = 255) -> None:
    color = (112, 143, 104) if mode == "RGB" else (112, 143, 104, alpha)
    Image.new(mode, (1200, 500), color).save(path, format="PNG")


def _write_spec(path: Path, image_path: Path, **asset_changes: object) -> None:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    spec["asset"]["sha256"] = hashlib.sha256(image_path.read_bytes()).hexdigest()
    spec["asset"].update(asset_changes)
    path.write_text(json.dumps(spec), encoding="utf-8")


@pytest.fixture
def valid_contract(tmp_path: Path) -> tuple[Path, Path, Path]:
    image_path = tmp_path / "battle-map.png"
    runtime_path = tmp_path / "runtime.png"
    spec_path = tmp_path / "spec.json"
    _write_png(image_path)
    runtime_path.write_bytes(image_path.read_bytes())
    _write_spec(spec_path, image_path)
    return image_path, spec_path, runtime_path


def test_load_spec_reads_json_contract() -> None:
    assert load_spec(SPEC_PATH) == json.loads(SPEC_PATH.read_text(encoding="utf-8"))


def test_valid_rgb_png_with_identical_runtime_passes(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    assert validate_battle_map(*valid_contract) == []


def test_fully_opaque_rgba_png_passes(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    _write_png(image_path, mode="RGBA")
    runtime_path.write_bytes(image_path.read_bytes())
    _write_spec(spec_path, image_path)

    assert validate_battle_map(image_path, spec_path, runtime_path) == []


@pytest.mark.parametrize(
    ("size", "message"),
    [
        ((1199, 500), "size"),
        ((1200, 501), "aspect ratio"),
    ],
)
def test_dimensions_and_aspect_ratio_are_enforced(
    valid_contract: tuple[Path, Path, Path],
    size: tuple[int, int],
    message: str,
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    Image.new("RGB", size, (112, 143, 104)).save(image_path, format="PNG")
    runtime_path.write_bytes(image_path.read_bytes())
    _write_spec(spec_path, image_path)

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any(message in error for error in errors)


def test_non_png_image_is_rejected(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    Image.new("RGB", (1200, 500), (112, 143, 104)).save(image_path, format="BMP")
    runtime_path.write_bytes(image_path.read_bytes())
    _write_spec(spec_path, image_path)

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any("format" in error for error in errors)


@pytest.mark.parametrize(
    ("mode", "alpha", "message"),
    [
        ("L", 255, "mode"),
        ("RGBA", 254, "fully opaque"),
    ],
)
def test_mode_and_rgba_opacity_are_enforced(
    valid_contract: tuple[Path, Path, Path],
    mode: str,
    alpha: int,
    message: str,
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    if mode == "L":
        Image.new(mode, (1200, 500), 112).save(image_path, format="PNG")
    else:
        _write_png(image_path, mode=mode, alpha=alpha)
    runtime_path.write_bytes(image_path.read_bytes())
    _write_spec(spec_path, image_path)

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any(message in error for error in errors)


def test_sha256_must_match_spec(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    spec["asset"]["sha256"] = "f" * 64
    spec_path.write_text(json.dumps(spec), encoding="utf-8")

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any("SHA-256" in error for error in errors)


def test_runtime_png_must_be_byte_identical(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    runtime_path.write_bytes(runtime_path.read_bytes() + b"different")

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any("byte-for-byte" in error for error in errors)


def test_missing_runtime_png_is_reported(
    valid_contract: tuple[Path, Path, Path],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    runtime_path.unlink()

    errors = validate_battle_map(image_path, spec_path, runtime_path)

    assert any("runtime" in error and "could not read" in error for error in errors)


def test_cli_reports_success_to_stdout(
    valid_contract: tuple[Path, Path, Path],
    capsys: pytest.CaptureFixture[str],
) -> None:
    image_path, spec_path, runtime_path = valid_contract

    result = main(
        [
            "--image",
            str(image_path),
            "--spec",
            str(spec_path),
            "--runtime",
            str(runtime_path),
        ]
    )

    captured = capsys.readouterr()
    assert result == 0
    assert "passed" in captured.out
    assert captured.err == ""


def test_cli_reports_all_violations_to_stderr(
    valid_contract: tuple[Path, Path, Path],
    capsys: pytest.CaptureFixture[str],
) -> None:
    image_path, spec_path, runtime_path = valid_contract
    spec = json.loads(spec_path.read_text(encoding="utf-8"))
    spec["asset"]["sha256"] = "f" * 64
    spec_path.write_text(json.dumps(spec), encoding="utf-8")
    runtime_path.write_bytes(b"different")

    result = main(
        [
            "--image",
            str(image_path),
            "--spec",
            str(spec_path),
            "--runtime",
            str(runtime_path),
        ]
    )

    captured = capsys.readouterr()
    assert result == 1
    assert captured.out == ""
    assert "SHA-256" in captured.err
    assert "byte-for-byte" in captured.err
