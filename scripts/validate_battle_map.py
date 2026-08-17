"""Validate the canonical Todo Quest battle-map background and runtime copy."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
from collections.abc import Sequence

from PIL import Image


def load_spec(path: pathlib.Path) -> dict:
    """Load a battle-map contract from a UTF-8 JSON file."""
    spec = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(spec, dict):
        raise ValueError("battle-map specification must be a JSON object")
    return spec


def _validate_loaded_image(image: Image.Image, spec: dict) -> list[str]:
    asset = spec["asset"]
    expected_size = asset["width"], asset["height"]
    allowed_modes = asset["allowedModes"]
    ratio = asset["aspectRatio"]
    errors: list[str] = []

    if image.format != asset["format"]:
        errors.append(
            f"image format must be {asset['format']}; got {image.format or 'unknown'}"
        )
    if image.size != expected_size:
        errors.append(f"image size must be {expected_size}; got {image.size}")
    if image.width * ratio["height"] != image.height * ratio["width"]:
        actual_ratio = image.width / image.height
        errors.append(
            f"aspect ratio must be {ratio['decimal']}:1; got {actual_ratio:.6f}:1"
        )
    if image.mode not in allowed_modes:
        errors.append(f"image mode must be one of {allowed_modes}; got {image.mode}")
    elif image.mode == "RGBA" and asset["rgbaAlpha"] == "fullyOpaque":
        alpha_minimum, alpha_maximum = image.getchannel("A").getextrema()
        if alpha_minimum != 255 or alpha_maximum != 255:
            errors.append(
                "RGBA image must be fully opaque; "
                f"alpha range is {alpha_minimum}..{alpha_maximum}"
            )
    return errors


def _validate_sha256(image_path: pathlib.Path, spec: dict) -> list[str]:
    expected = spec["asset"]["sha256"].lower()
    actual = hashlib.sha256(image_path.read_bytes()).hexdigest()
    if actual == expected:
        return []
    return [f"image SHA-256 must be {expected}; got {actual}"]


def _validate_runtime_copy(
    image_path: pathlib.Path,
    runtime_path: pathlib.Path,
    spec: dict,
) -> list[str]:
    try:
        runtime_bytes = runtime_path.read_bytes()
    except OSError as error:
        return [f"runtime image could not read {runtime_path}: {error}"]

    if not spec["runtime"].get("mustBeByteIdentical", False):
        return []
    if runtime_bytes == image_path.read_bytes():
        return []
    return [
        "runtime image must be byte-for-byte identical to canonical image: "
        f"{runtime_path}"
    ]


def validate_battle_map(
    image_path: pathlib.Path,
    spec_path: pathlib.Path,
    runtime_path: pathlib.Path,
) -> list[str]:
    """Return every machine-verifiable battle-map contract violation."""
    try:
        spec = load_spec(spec_path)
        if spec["schemaVersion"] != 1:
            raise ValueError(f"unsupported schemaVersion: {spec['schemaVersion']}")
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
        return [f"could not load specification {spec_path}: {error}"]

    try:
        with Image.open(image_path) as image:
            image.load()
            errors = _validate_loaded_image(image, spec)
        errors.extend(_validate_sha256(image_path, spec))
        errors.extend(_validate_runtime_copy(image_path, runtime_path, spec))
        return errors
    except OSError as error:
        return [f"could not load image {image_path}: {error}"]
    except (KeyError, TypeError, ValueError) as error:
        return [f"invalid battle-map specification: {error}"]


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate the Todo Quest battle-map PNG and runtime copy."
    )
    parser.add_argument("--image", required=True, type=pathlib.Path, help="canonical PNG")
    parser.add_argument("--spec", required=True, type=pathlib.Path, help="contract JSON")
    parser.add_argument(
        "--runtime",
        required=True,
        type=pathlib.Path,
        help="runtime PNG resource",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    errors = validate_battle_map(args.image, args.spec, args.runtime)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"Battle-map validation passed: {args.image}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
