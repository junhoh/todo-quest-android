from __future__ import annotations

import copy
import hashlib
import json
import sys
from pathlib import Path

import pytest
from PIL import Image


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "equipment"
    / "todo-quest-helmet-layers-spec.json"
)
OUTFIT_SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "equipment"
    / "todo-quest-top-bottom-layers-spec.json"
)
GLOVES_SHOES_SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "equipment"
    / "todo-quest-gloves-shoes-layers-spec.json"
)
WEAPON_SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "equipment"
    / "todo-quest-weapon-layers-spec.json"
)
CHARACTER_SPEC_PATH = (
    ROOT / "docs" / "art" / "character" / "character-modular-sheet-spec.json"
)

sys.path.insert(0, str(SCRIPTS_DIR))

from validate_character_equipment_layers import (  # noqa: E402
    main,
    validate_all,
    validate_contract,
    validate_sources,
)


Rgba = tuple[int, int, int, int]
Point = tuple[int, int]


def _rgba(spec: dict, palette_name: str) -> Rgba:
    value = spec["productionPalette"]["colors"][palette_name].removeprefix("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4)) + (255,)


def _opaque_points(item_key: str) -> set[Point]:
    if item_key == "headgear_leather_hat":
        left, right, full_bottom, guard_bottom = 19, 45, 19, 22
    else:
        left, right, full_bottom, guard_bottom = 18, 46, 19, 29

    points: set[Point] = set()
    for y in range(4, full_bottom + 1):
        radius = min(y - 4 + 1, right - 32)
        points.update((x, y) for x in range(32 - radius, 32 + radius + 1))
    for y in range(full_bottom + 1, guard_bottom + 1):
        points.update((x, y) for x in range(left, 23))
        points.update((x, y) for x in range(42, right + 1))
    return points


def _layer_image(spec: dict, item_key: str) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    points = _opaque_points(item_key)
    outline = _rgba(spec, "outlineDarkNavy")
    fill_name = "skinShadow" if item_key == "headgear_leather_hat" else "underMid"
    fill = _rgba(spec, fill_name)
    for point in points:
        x, y = point
        boundary = any(
            (x + dx, y + dy) not in points
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
        )
        image.putpixel(point, outline if boundary else fill)
    return image


def _save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG")


def _hashes(path: Path, image: Image.Image) -> dict[str, str]:
    rgba = image.tobytes("raw", "RGBA")
    alpha = image.getchannel("A").tobytes()
    return {
        "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "rawRgbaSha256": hashlib.sha256(rgba).hexdigest(),
        "alphaMaskSha256": hashlib.sha256(alpha).hexdigest(),
    }


def _metadata(path: Path, image: Image.Image, bounds: list[int]) -> dict:
    return {
        "status": "available",
        "opaqueBounds": bounds,
        "opaquePixelCount": sum(
            pixel[3] != 0 for pixel in image.get_flattened_data()
        ),
        "fileByteCount": path.stat().st_size,
        "hashes": _hashes(path, image),
    }


def _pending_metadata(bounds: list[int]) -> dict:
    return {
        "status": "pendingGeneration",
        "opaqueBounds": bounds,
        "opaquePixelCount": None,
        "fileByteCount": None,
        "hashes": {
            "fileSha256": None,
            "rawRgbaSha256": None,
            "alphaMaskSha256": None,
        },
    }


def _write_spec(path: Path, spec: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(spec, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


@pytest.fixture
def contract() -> dict:
    return json.loads(SPEC_PATH.read_text(encoding="utf-8"))


@pytest.fixture
def source_fixture(tmp_path: Path, contract: dict) -> dict[str, object]:
    spec = copy.deepcopy(contract)
    spec_path = (
        tmp_path
        / "docs"
        / "art"
        / "equipment"
        / "todo-quest-helmet-layers-spec.json"
    )
    runtime_root = tmp_path / "app" / "src" / "main" / "assets"

    for item in spec["items"]:
        source_path = spec_path.parent / item["sourcePath"]
        image = _layer_image(spec, item["layerKey"])
        _save(image, source_path)
        item["sourceArtifact"] = _metadata(
            source_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )

        runtime_path = runtime_root / item["runtimePath"]
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source_path.read_bytes())
        item["runtimeArtifact"] = _metadata(
            runtime_path,
            image,
            item["runtimeArtifact"]["opaqueBounds"],
        )

        one_key = item["previewKeys"]["equipped1x"]
        eight_key = item["previewKeys"]["equipped8x"]
        one_contract = spec["previews"]["artifacts"][one_key]
        eight_contract = spec["previews"]["artifacts"][eight_key]
        one_path = spec_path.parent / one_contract["path"]
        eight_path = spec_path.parent / eight_contract["path"]
        _save(image, one_path)
        enlarged = image.resize((512, 512), Image.Resampling.NEAREST)
        _save(enlarged, eight_path)
        one_contract["artifact"] = _metadata(
            one_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )
        eight_contract["artifact"] = _metadata(
            eight_path,
            enlarged,
            [value * 8 if index < 2 else value * 8 + 7 for index, value in enumerate(item["sourceArtifact"]["opaqueBounds"])],
        )

    _write_spec(spec_path, spec)
    return {
        "spec": spec,
        "spec_path": spec_path,
        "runtime_root": runtime_root,
    }


def _fixture_spec(fixture: dict[str, object]) -> dict:
    return fixture["spec"]  # type: ignore[return-value]


def _fixture_spec_path(fixture: dict[str, object]) -> Path:
    return fixture["spec_path"]  # type: ignore[return-value]


def _item(spec: dict, key: str = "headgear_leather_hat") -> dict:
    return next(item for item in spec["items"] if item["layerKey"] == key)


def _source_path(fixture: dict[str, object], key: str = "headgear_leather_hat") -> Path:
    spec = _fixture_spec(fixture)
    return _fixture_spec_path(fixture).parent / _item(spec, key)["sourcePath"]


def _save_fixture_spec(fixture: dict[str, object]) -> None:
    _write_spec(_fixture_spec_path(fixture), _fixture_spec(fixture))


def test_repository_contract_declares_exact_common_and_item_values(contract: dict) -> None:
    assert contract["schemaVersion"] == 1
    assert contract["contractKind"] == "character-equipment-layer-variants"
    assert contract["canvas"] == {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "boundsInclusive": [0, 0, 63, 63],
        "centerX": 32,
        "characterSoleY": 58,
        "anchorProfile": "canvas-64-center-x-32-sole-y-58-schema-v5",
    }
    layer = contract["layerContract"]
    assert layer["slot"] == "headgear_front"
    assert layer["allowedAlphaValues"] == [0, 255]
    assert layer["transparentPixelRgba"] == [0, 0, 0, 0]
    assert layer["alphaCompositing"] == "source-over"
    assert layer["interpolation"] == "nearest-neighbor"
    assert layer["faceProtectedRegion"] == [23, 20, 41, 28]
    assert layer["connectivity"] == {
        "opaqueNeighborMode": 8,
        "opaqueComponentCount": 1,
        "allowIsolatedOpaquePixels": False,
    }
    assert layer["outline"]["color"] == "#263B5A"
    assert layer["outline"]["widthLogicalPixels"] == 1
    assert layer["coordinatePreservation"] == {
        "singleCanvasOrigin": [0, 0],
        "translationAllowed": False,
        "croppingAllowed": False,
        "scalingAllowed": False,
        "thumbnailOpaqueBoundsReadOnlyScalingAllowed": True,
    }

    palette = contract["productionPalette"]
    assert palette["colorCount"] == 16
    assert list(palette["colors"].values()) == [
        "#11151C", "#1D3557", "#263B5A", "#35445C",
        "#3A3F45", "#2853A6", "#737982", "#B7B0A3",
        "#D99872", "#FFD3AE", "#F4EFE3", "#4F86E8",
        "#7FB3FF", "#5CC8A7", "#F2C14E", "#E05252",
    ]

    leather = _item(contract)
    iron = _item(contract, "headgear_iron_helmet")
    assert (leather["equipmentId"], leather["imageKey"], leather["layerKey"]) == (
        1003,
        "headgear_leather_hat",
        "headgear_leather_hat",
    )
    assert leather["sourcePath"] == "layers/headgear_leather_hat.png"
    assert leather["runtimePath"] == "character/layers/headgear_leather_hat.png"
    assert leather["sourceArtifact"]["opaqueBounds"] == [19, 4, 45, 22]
    assert (iron["equipmentId"], iron["imageKey"], iron["layerKey"]) == (
        1004,
        "headgear_iron_helmet",
        "headgear_iron_helmet",
    )
    assert iron["sourcePath"] == "layers/headgear_iron_helmet.png"
    assert iron["runtimePath"] == "character/layers/headgear_iron_helmet.png"
    assert iron["sourceArtifact"]["opaqueBounds"] == [18, 4, 46, 29]
    assert iron["design"]["mirrorSymmetryRequired"] is True

    assert {
        preview["path"] for preview in contract["previews"]["artifacts"].values()
    } == {
        "previews/leather-hat-equipped.png",
        "previews/leather-hat-equipped@8x.png",
        "previews/iron-helmet-equipped.png",
        "previews/iron-helmet-equipped@8x.png",
    }


def test_pending_contract_passes_without_png_files() -> None:
    assert validate_contract(SPEC_PATH) == []


@pytest.mark.parametrize(
    ("target", "unsafe_path"),
    [
        ("source", "../headgear.png"),
        ("source", "C:/outside/headgear.png"),
        ("runtime", "../headgear.png"),
        ("preview", "../../headgear.png"),
    ],
)
def test_contract_rejects_unsafe_artifact_paths(
    contract: dict,
    tmp_path: Path,
    target: str,
    unsafe_path: str,
) -> None:
    invalid = copy.deepcopy(contract)
    if target == "source":
        invalid["items"][0]["sourcePath"] = unsafe_path
    elif target == "runtime":
        invalid["items"][0]["runtimePath"] = unsafe_path
    else:
        next(iter(invalid["previews"]["artifacts"].values()))["path"] = unsafe_path
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)

    errors = validate_contract(spec_path)

    assert any("safe relative path" in error for error in errors)


@pytest.mark.parametrize("field", ["opaquePixelCount", "fileByteCount"])
def test_pending_artifact_metadata_cannot_invent_values(
    contract: dict,
    tmp_path: Path,
    field: str,
) -> None:
    invalid = copy.deepcopy(contract)
    invalid["items"][0]["sourceArtifact"] = _pending_metadata(
        invalid["items"][0]["sourceArtifact"]["opaqueBounds"]
    )
    invalid["items"][0]["sourceArtifact"][field] = 1
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)

    assert any(field in error and "pendingGeneration" in error for error in validate_contract(spec_path))


def test_pending_artifact_metadata_cannot_invent_hashes(contract: dict, tmp_path: Path) -> None:
    invalid = copy.deepcopy(contract)
    invalid["items"][0]["sourceArtifact"] = _pending_metadata(
        invalid["items"][0]["sourceArtifact"]["opaqueBounds"]
    )
    invalid["items"][0]["sourceArtifact"]["hashes"]["fileSha256"] = "0" * 64
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)

    assert any("pendingGeneration" in error and "fileSha256" in error for error in validate_contract(spec_path))


def test_valid_canonical_sources_and_previews_pass_without_runtime(
    source_fixture: dict[str, object],
) -> None:
    runtime_root: Path = source_fixture["runtime_root"]  # type: ignore[assignment]
    for path in runtime_root.rglob("*.png"):
        path.unlink()

    assert validate_sources(_fixture_spec_path(source_fixture)) == []


@pytest.mark.parametrize(
    ("invalid_kind", "expected"),
    [("size", "size"), ("mode", "mode")],
)
def test_source_size_and_mode_are_enforced(
    source_fixture: dict[str, object],
    invalid_kind: str,
    expected: str,
) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image = image.crop((0, 0, 63, 64)) if invalid_kind == "size" else image.convert("RGB")
    _save(image, path)

    assert any(str(path) in error and expected in error for error in validate_sources(_fixture_spec_path(source_fixture)))


@pytest.mark.parametrize(
    ("pixel", "expected"),
    [
        ((38, 63, 69, 128), "alpha"),
        ((1, 2, 3, 255), "palette"),
        ((255, 0, 255, 255), "chroma key"),
        ((1, 2, 3, 0), "transparent pixel RGBA"),
    ],
)
def test_source_alpha_palette_chroma_key_and_transparent_rgba_are_enforced(
    source_fixture: dict[str, object],
    pixel: Rgba,
    expected: str,
) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    point = (32, 10) if pixel[3] != 0 else (0, 0)
    image.putpixel(point, pixel)
    _save(image, path)

    errors = validate_sources(_fixture_spec_path(source_fixture))

    assert any(str(path) in error and expected in error and str(point) in error for error in errors)


def test_source_exact_opaque_bounds_are_enforced(source_fixture: dict[str, object]) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((18, 10), _rgba(_fixture_spec(source_fixture), "outlineDarkNavy"))
    _save(image, path)

    assert any("opaque bounds" in error and "(18, 10)" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_source_must_not_cover_the_face_region(source_fixture: dict[str, object]) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((23, 20), _rgba(_fixture_spec(source_fixture), "outlineDarkNavy"))
    _save(image, path)

    assert any("face protected region" in error and "(23, 20)" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_source_opaque_pixels_must_be_one_connected_component(
    source_fixture: dict[str, object],
) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((3, 3), _rgba(_fixture_spec(source_fixture), "outlineDarkNavy"))
    image.putpixel((4, 3), _rgba(_fixture_spec(source_fixture), "outlineDarkNavy"))
    _save(image, path)

    errors = validate_sources(_fixture_spec_path(source_fixture))

    assert any("8-connected" in error and "(3, 3)" in error for error in errors)


def test_source_isolated_opaque_pixels_are_rejected(source_fixture: dict[str, object]) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((3, 3), _rgba(_fixture_spec(source_fixture), "outlineDarkNavy"))
    _save(image, path)

    assert any("isolated opaque pixel" in error and "(3, 3)" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_external_boundary_must_use_one_pixel_outline(source_fixture: dict[str, object]) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((32, 4), _rgba(_fixture_spec(source_fixture), "skinShadow"))
    _save(image, path)

    assert any("external boundary" in error and "(32, 4)" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_external_outline_rejects_two_by_two_blocks(source_fixture: dict[str, object]) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    outline = _rgba(_fixture_spec(source_fixture), "outlineDarkNavy")
    for y in (9, 10):
        for x in (23, 24):
            image.putpixel((x, y), outline)
    _save(image, path)

    assert any("2x2" in error and "(23, 9)" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


@pytest.mark.parametrize(
    ("field", "value", "expected"),
    [
        ("opaquePixelCount", 1, "opaquePixelCount"),
        ("fileByteCount", 1, "fileByteCount"),
        ("fileSha256", "0" * 64, "fileSha256"),
        ("rawRgbaSha256", "0" * 64, "rawRgbaSha256"),
        ("alphaMaskSha256", "0" * 64, "alphaMaskSha256"),
    ],
)
def test_source_metadata_counts_and_hashes_are_enforced(
    source_fixture: dict[str, object],
    field: str,
    value: object,
    expected: str,
) -> None:
    spec = _fixture_spec(source_fixture)
    metadata = _item(spec)["sourceArtifact"]
    if field.endswith("Sha256"):
        metadata["hashes"][field] = value
    else:
        metadata[field] = value
    _save_fixture_spec(source_fixture)

    assert any(expected in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_missing_preview_is_reported(source_fixture: dict[str, object]) -> None:
    spec = _fixture_spec(source_fixture)
    preview = spec["previews"]["artifacts"][_item(spec)["previewKeys"]["equipped1x"]]
    path = _fixture_spec_path(source_fixture).parent / preview["path"]
    path.unlink()

    assert any(str(path) in error and "missing" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_preview_metadata_hash_is_enforced(source_fixture: dict[str, object]) -> None:
    spec = _fixture_spec(source_fixture)
    preview = spec["previews"]["artifacts"][_item(spec)["previewKeys"]["equipped1x"]]
    preview["artifact"]["hashes"]["fileSha256"] = "0" * 64
    _save_fixture_spec(source_fixture)

    assert any("preview" in error and "fileSha256" in error for error in validate_sources(_fixture_spec_path(source_fixture)))


def test_eight_times_preview_must_be_nearest_neighbor_copy(
    source_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(source_fixture)
    preview = spec["previews"]["artifacts"][_item(spec)["previewKeys"]["equipped8x"]]
    path = _fixture_spec_path(source_fixture).parent / preview["path"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((256, 80), _rgba(spec, "redAccent"))
    _save(image, path)
    preview["artifact"] = _metadata(
        path,
        image,
        preview["artifact"]["opaqueBounds"],
    )
    _save_fixture_spec(source_fixture)

    errors = validate_sources(_fixture_spec_path(source_fixture))

    assert any("nearest-neighbor" in error and "(256, 80)" in error for error in errors)


def test_full_check_passes_with_byte_identical_runtime(source_fixture: dict[str, object]) -> None:
    assert validate_all(_fixture_spec_path(source_fixture)) == []


def test_full_check_requires_runtime_copy(source_fixture: dict[str, object]) -> None:
    spec = _fixture_spec(source_fixture)
    runtime_root: Path = source_fixture["runtime_root"]  # type: ignore[assignment]
    path = runtime_root / _item(spec)["runtimePath"]
    path.unlink()

    assert any(str(path) in error and "missing" in error for error in validate_all(_fixture_spec_path(source_fixture)))


def test_full_check_requires_canonical_runtime_byte_equality(
    source_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(source_fixture)
    runtime_root: Path = source_fixture["runtime_root"]  # type: ignore[assignment]
    path = runtime_root / _item(spec)["runtimePath"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((32, 10), _rgba(spec, "bluePrimary"))
    _save(image, path)
    _item(spec)["runtimeArtifact"] = _metadata(
        path,
        image,
        _item(spec)["runtimeArtifact"]["opaqueBounds"],
    )
    _save_fixture_spec(source_fixture)

    errors = validate_all(_fixture_spec_path(source_fixture))

    assert any("byte-identical" in error and str(path) in error for error in errors)


@pytest.mark.parametrize(
    ("mode", "validator"),
    [
        ("--check-contract", validate_contract),
        ("--check-sources", validate_sources),
        ("--check", validate_all),
    ],
)
def test_cli_public_modes_match_python_validation_boundaries(
    source_fixture: dict[str, object],
    mode: str,
    validator,
    capsys: pytest.CaptureFixture[str],
) -> None:
    spec_path = _fixture_spec_path(source_fixture)
    assert validator(spec_path) == []

    result = main(["--spec", str(spec_path), mode])

    captured = capsys.readouterr()
    assert result == 0
    assert mode.removeprefix("--") in captured.out
    assert captured.err == ""


def test_cli_writes_file_and_first_coordinate_to_stderr(
    source_fixture: dict[str, object],
    capsys: pytest.CaptureFixture[str],
) -> None:
    path = _source_path(source_fixture)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((32, 10), (1, 2, 3, 128))
    _save(image, path)

    result = main(
        [
            "--spec",
            str(_fixture_spec_path(source_fixture)),
            "--check-sources",
        ]
    )

    captured = capsys.readouterr()
    assert result != 0
    assert captured.out == ""
    assert str(path) in captured.err
    assert "(32, 10)" in captured.err


@pytest.fixture
def outfit_contract() -> dict:
    return json.loads(OUTFIT_SPEC_PATH.read_text(encoding="utf-8"))


def _outfit_layer_image(spec: dict, item: dict) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    left, top, right, bottom = item["sourceArtifact"]["opaqueBounds"]
    outline = _rgba(spec, "outlineDarkNavy")
    fill = _rgba(
        spec,
        "bluePrimary" if item["slot"] == "top" else "underMid",
    )
    hidden_waist = {
        (x, y)
        for x in range(24, 41)
        for y in range(41, 44)
    } if item["slot"] == "bottom" else set()
    for y in range(top, bottom + 1):
        for x in range(left, right + 1):
            boundary = x in (left, right) or y in (top, bottom)
            image.putpixel(
                (x, y),
                fill if (x, y) in hidden_waist else outline if boundary else fill,
            )
    return image


@pytest.fixture
def outfit_source_fixture(tmp_path: Path, outfit_contract: dict) -> dict[str, object]:
    spec = copy.deepcopy(outfit_contract)
    spec_path = (
        tmp_path
        / "docs"
        / "art"
        / "equipment"
        / "todo-quest-top-bottom-layers-spec.json"
    )
    runtime_root = tmp_path / "app" / "src" / "main" / "assets"
    item_images: dict[int, Image.Image] = {}

    for item in spec["items"]:
        source_path = spec_path.parent / item["sourcePath"]
        image = _outfit_layer_image(spec, item)
        item_images[item["equipmentId"]] = image
        _save(image, source_path)
        item["sourceArtifact"] = _metadata(
            source_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )

        runtime_path = runtime_root / item["runtimePath"]
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source_path.read_bytes())
        item["runtimeArtifact"] = _metadata(
            runtime_path,
            image,
            item["runtimeArtifact"]["opaqueBounds"],
        )

        one_key = item["previewKeys"]["equipped1x"]
        eight_key = item["previewKeys"]["equipped8x"]
        one_contract = spec["previews"]["artifacts"][one_key]
        eight_contract = spec["previews"]["artifacts"][eight_key]
        one_path = spec_path.parent / one_contract["path"]
        eight_path = spec_path.parent / eight_contract["path"]
        _save(image, one_path)
        enlarged = image.resize((512, 512), Image.Resampling.NEAREST)
        _save(enlarged, eight_path)
        one_contract["artifact"] = _metadata(
            one_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )
        eight_contract["artifact"] = _metadata(
            eight_path,
            enlarged,
            [
                value * 8 if index < 2 else value * 8 + 7
                for index, value in enumerate(item["sourceArtifact"]["opaqueBounds"])
            ],
        )

    matrix = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
    matrix_contract = spec["previews"]["combinationMatrix"]
    for row, top_id in enumerate(matrix_contract["topEquipmentIds"]):
        for column, bottom_id in enumerate(matrix_contract["bottomEquipmentIds"]):
            cell = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            cell.alpha_composite(item_images[bottom_id])
            cell.alpha_composite(item_images[top_id])
            matrix.alpha_composite(cell, (column * 64, row * 64))

    one_key = matrix_contract["previewKeys"]["matrix1x"]
    four_key = matrix_contract["previewKeys"]["matrix4x"]
    one_contract = spec["previews"]["artifacts"][one_key]
    four_contract = spec["previews"]["artifacts"][four_key]
    one_path = spec_path.parent / one_contract["path"]
    four_path = spec_path.parent / four_contract["path"]
    _save(matrix, one_path)
    enlarged_matrix = matrix.resize((768, 768), Image.Resampling.NEAREST)
    _save(enlarged_matrix, four_path)
    matrix_bounds = [20, 29, 172, 182]
    one_contract["artifact"] = _metadata(one_path, matrix, matrix_bounds)
    four_contract["artifact"] = _metadata(
        four_path,
        enlarged_matrix,
        [80, 116, 691, 731],
    )

    _write_spec(spec_path, spec)
    return {
        "spec": spec,
        "spec_path": spec_path,
        "runtime_root": runtime_root,
    }


def test_repository_outfit_contract_declares_available_byte_identical_sources_runtime_and_previews(
    outfit_contract: dict,
) -> None:
    assert outfit_contract["schemaVersion"] == 1
    assert outfit_contract["contractKind"] == "character-equipment-layer-variants"
    assert outfit_contract["baseCharacterContract"]["schemaVersion"] == 5
    assert outfit_contract["baseCharacterContract"]["canonicalSourceCount"] == 15
    assert outfit_contract["canvas"] == {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "boundsInclusive": [0, 0, 63, 63],
        "centerX": 32,
        "characterSoleY": 58,
        "anchorProfile": "canvas-64-center-x-32-sole-y-58-schema-v5",
    }
    assert outfit_contract["productionPalette"]["colorCount"] == 16
    assert list(outfit_contract["productionPalette"]["colors"].values()) == [
        "#11151C", "#1D3557", "#263B5A", "#35445C",
        "#3A3F45", "#2853A6", "#737982", "#B7B0A3",
        "#D99872", "#FFD3AE", "#F4EFE3", "#4F86E8",
        "#7FB3FF", "#5CC8A7", "#F2C14E", "#E05252",
    ]

    expected = {
        1005: ("천 상의", "top_cloth", "top", [20, 29, 44, 45]),
        1006: ("가죽 갑옷", "top_leather_armor", "top", [20, 29, 44, 45]),
        1007: ("철 흉갑", "top_iron_breastplate", "top", [20, 29, 44, 45]),
        1008: ("천 바지", "bottom_cloth_pants", "bottom", [24, 41, 40, 54]),
        1009: ("가죽 바지", "bottom_leather_pants", "bottom", [24, 41, 40, 54]),
        1010: ("강철 각반", "bottom_steel_greaves", "bottom", [24, 41, 40, 54]),
    }
    assert {item["equipmentId"] for item in outfit_contract["items"]} == set(expected)
    for item in outfit_contract["items"]:
        display_name, key, slot, bounds = expected[item["equipmentId"]]
        assert item["displayNameKorean"] == display_name
        assert item["imageKey"] == item["layerKey"] == key
        assert item["slot"] == slot
        assert item["sourcePath"] == f"layers/{key}.png"
        assert item["runtimePath"] == f"character/layers/{key}.png"
        source_path = OUTFIT_SPEC_PATH.parent / item["sourcePath"]
        with Image.open(source_path) as stored:
            source_image = stored.convert("RGBA")
        assert item["sourceArtifact"] == _metadata(source_path, source_image, bounds)
        runtime_path = ROOT / "app" / "src" / "main" / "assets" / item["runtimePath"]
        with Image.open(runtime_path) as stored:
            runtime_image = stored.convert("RGBA")
        assert item["runtimeArtifact"] == _metadata(runtime_path, runtime_image, bounds)
        assert runtime_path.read_bytes() == source_path.read_bytes()

    assert outfit_contract["layerContracts"]["top"]["slot"] == "top"
    assert outfit_contract["layerContracts"]["bottom"]["slot"] == "bottom"
    assert outfit_contract["compositionContract"] == {
        "slotsBackToFront": ["shoes", "bottom", "top"],
        "waistOverlap": [24, 41, 40, 43],
        "ankleOverlapBands": {
            "left": [24, 53, 31, 54],
            "right": [33, 53, 40, 54],
        },
        "sharedOpaqueCoverageRequired": True,
        "transparentGapsAllowed": False,
        "hiddenHorizontalJunctionDoubleOutlineAllowed": False,
    }

    artifacts = outfit_contract["previews"]["artifacts"]
    assert len(artifacts) == 14
    assert sum(preview["scale"] == 1 and preview["width"] == 64 for preview in artifacts.values()) == 6
    assert sum(preview["scale"] == 8 and preview["width"] == 512 for preview in artifacts.values()) == 6
    matrix = outfit_contract["previews"]["combinationMatrix"]
    assert matrix["topEquipmentIds"] == [1005, 1006, 1007]
    assert matrix["bottomEquipmentIds"] == [1008, 1009, 1010]
    assert (matrix["columns"], matrix["rows"], matrix["cellWidth"], matrix["cellHeight"]) == (3, 3, 64, 64)
    assert artifacts[matrix["previewKeys"]["matrix1x"]]["width"] == 192
    assert artifacts[matrix["previewKeys"]["matrix4x"]]["width"] == 768
    for preview in artifacts.values():
        preview_path = OUTFIT_SPEC_PATH.parent / preview["path"]
        with Image.open(preview_path) as stored:
            preview_image = stored.convert("RGBA")
        assert preview["artifact"] == _metadata(
            preview_path,
            preview_image,
            preview["artifact"]["opaqueBounds"],
        )


def test_validator_selects_both_known_contract_branches(
    outfit_contract: dict,
    tmp_path: Path,
) -> None:
    assert validate_contract(SPEC_PATH) == []
    assert validate_contract(OUTFIT_SPEC_PATH) == []

    invalid = copy.deepcopy(outfit_contract)
    invalid["items"][0]["equipmentId"] = 9999
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("supported" in error for error in validate_contract(spec_path))


@pytest.mark.parametrize(
    ("target", "unsafe_path"),
    [
        ("source", "../top.png"),
        ("runtime", "C:/outside/top.png"),
        ("preview", "../../matrix.png"),
    ],
)
def test_outfit_contract_rejects_unsafe_relative_paths(
    outfit_contract: dict,
    tmp_path: Path,
    target: str,
    unsafe_path: str,
) -> None:
    invalid = copy.deepcopy(outfit_contract)
    if target == "source":
        invalid["items"][0]["sourcePath"] = unsafe_path
    elif target == "runtime":
        invalid["items"][0]["runtimePath"] = unsafe_path
    else:
        matrix_key = invalid["previews"]["combinationMatrix"]["previewKeys"]["matrix1x"]
        invalid["previews"]["artifacts"][matrix_key]["path"] = unsafe_path
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("safe relative path" in error for error in validate_contract(spec_path))


@pytest.mark.parametrize(
    ("mutation", "expected"),
    [
        (lambda spec: spec["items"][0].__setitem__("slot", "bottom"), "slot"),
        (lambda spec: spec["layerContracts"]["top"].__setitem__("slot", "chest"), "layerContracts.top.slot"),
        (lambda spec: spec["previews"]["artifacts"]["top-cloth-equipped@8x"].__setitem__("width", 511), "width"),
    ],
)
def test_outfit_contract_rejects_wrong_slot_and_preview_dimensions(
    outfit_contract: dict,
    tmp_path: Path,
    mutation,
    expected: str,
) -> None:
    invalid = copy.deepcopy(outfit_contract)
    mutation(invalid)
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any(expected in error for error in validate_contract(spec_path))


@pytest.mark.parametrize(
    ("item_key", "point", "expected"),
    [
        ("top_cloth", (24, 41), "waist overlap"),
        ("bottom_cloth_pants", (40, 43), "waist overlap"),
        ("bottom_cloth_pants", (24, 53), "left ankle overlap"),
        ("bottom_cloth_pants", (40, 54), "right ankle overlap"),
    ],
)
def test_outfit_layers_must_fill_waist_and_ankle_overlap_regions(
    outfit_source_fixture: dict[str, object],
    item_key: str,
    point: Point,
    expected: str,
) -> None:
    path = _source_path(outfit_source_fixture, item_key)
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel(point, (0, 0, 0, 0))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(outfit_source_fixture))
    assert any(expected in error and str(point) in error for error in errors)


def test_outfit_hidden_waist_overlap_forbids_outline_color(
    outfit_source_fixture: dict[str, object],
) -> None:
    path = _source_path(outfit_source_fixture, "bottom_cloth_pants")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((24, 41), _rgba(_fixture_spec(outfit_source_fixture), "outlineDarkNavy"))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(outfit_source_fixture))
    assert any("hidden waist seam" in error and "(24, 41)" in error for error in errors)


def test_outfit_ankle_overlap_rejects_two_pixel_horizontal_outline(
    outfit_source_fixture: dict[str, object],
) -> None:
    path = _source_path(outfit_source_fixture, "bottom_cloth_pants")
    with Image.open(path) as stored:
        image = stored.copy()
    outline = _rgba(_fixture_spec(outfit_source_fixture), "outlineDarkNavy")
    for y in (53, 54):
        for start, end in ((24, 31), (33, 40)):
            for x in range(start, end + 1):
                image.putpixel((x, y), outline)
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(outfit_source_fixture))
    assert any("2-pixel horizontal outline" in error and "(24, 53)" in error for error in errors)


def test_outfit_layer_connectivity_is_enforced(
    outfit_source_fixture: dict[str, object],
) -> None:
    path = _source_path(outfit_source_fixture, "top_cloth")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((3, 3), _rgba(_fixture_spec(outfit_source_fixture), "outlineDarkNavy"))
    image.putpixel((4, 3), _rgba(_fixture_spec(outfit_source_fixture), "outlineDarkNavy"))
    _save(image, path)
    assert any("8-connected" in error for error in validate_sources(_fixture_spec_path(outfit_source_fixture)))


def test_outfit_source_metadata_hash_is_enforced(
    outfit_source_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(outfit_source_fixture)
    _item(spec, "top_cloth")["sourceArtifact"]["hashes"]["rawRgbaSha256"] = "0" * 64
    _save_fixture_spec(outfit_source_fixture)
    assert any("rawRgbaSha256" in error for error in validate_sources(_fixture_spec_path(outfit_source_fixture)))


@pytest.mark.parametrize(
    ("preview_key", "point", "expected"),
    [
        ("top-cloth-equipped@8x", (256, 240), "nearest-neighbor"),
        ("top-bottom-combination-matrix@4x", (300, 300), "nearest-neighbor"),
    ],
)
def test_outfit_preview_pixel_enlargements_are_enforced(
    outfit_source_fixture: dict[str, object],
    preview_key: str,
    point: Point,
    expected: str,
) -> None:
    spec = _fixture_spec(outfit_source_fixture)
    preview = spec["previews"]["artifacts"][preview_key]
    path = _fixture_spec_path(outfit_source_fixture).parent / preview["path"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel(point, _rgba(spec, "redAccent"))
    _save(image, path)
    preview["artifact"] = _metadata(path, image, preview["artifact"]["opaqueBounds"])
    _save_fixture_spec(outfit_source_fixture)
    assert any(expected in error and str(point) in error for error in validate_sources(_fixture_spec_path(outfit_source_fixture)))


def test_outfit_full_check_passes_then_rejects_runtime_mismatch(
    outfit_source_fixture: dict[str, object],
) -> None:
    spec_path = _fixture_spec_path(outfit_source_fixture)
    assert validate_all(spec_path) == []

    spec = _fixture_spec(outfit_source_fixture)
    runtime_root: Path = outfit_source_fixture["runtime_root"]  # type: ignore[assignment]
    item = _item(spec, "top_cloth")
    path = runtime_root / item["runtimePath"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((32, 35), _rgba(spec, "goldAccent"))
    _save(image, path)
    item["runtimeArtifact"] = _metadata(
        path,
        image,
        item["runtimeArtifact"]["opaqueBounds"],
    )
    _save_fixture_spec(outfit_source_fixture)
    assert any("byte-identical" in error for error in validate_all(spec_path))


@pytest.mark.parametrize(
    ("mode", "validator"),
    [
        ("--check-contract", validate_contract),
        ("--check-sources", validate_sources),
        ("--check", validate_all),
    ],
)
def test_outfit_cli_preserves_all_public_validation_boundaries(
    outfit_source_fixture: dict[str, object],
    mode: str,
    validator,
    capsys: pytest.CaptureFixture[str],
) -> None:
    spec_path = _fixture_spec_path(outfit_source_fixture)
    assert validator(spec_path) == []
    assert main(["--spec", str(spec_path), mode]) == 0
    captured = capsys.readouterr()
    assert mode.removeprefix("--") in captured.out
    assert captured.err == ""


@pytest.fixture
def gloves_shoes_contract() -> dict:
    return json.loads(GLOVES_SHOES_SPEC_PATH.read_text(encoding="utf-8"))


def _gloves_shoes_points(render_slot: str) -> set[Point]:
    if render_slot == "hands_front":
        rows = {
            39: [22, 23, 24, 40, 41, 42],
            40: [22, 23, 24, 40, 41, 42],
            41: [22, 23, 41, 42],
            42: [21, 22, 23, 41, 42, 43],
            43: [21, 22, 23, 41, 42, 43],
            44: [21, 22, 23, 41, 42, 43],
            45: [22, 23, 41, 42],
        }
    else:
        rows = {
            53: [*range(24, 32), *range(33, 41)],
            54: [*range(24, 32), *range(33, 41)],
            55: [*range(23, 32), *range(33, 42)],
            56: [*range(23, 32), *range(33, 42)],
            57: [*range(23, 32), *range(33, 42)],
            58: [*range(23, 32), *range(33, 42)],
        }
    return {(x, y) for y, xs in rows.items() for x in xs}


def _gloves_shoes_layer_image(spec: dict, item: dict) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    points = _gloves_shoes_points(item["renderSlot"])
    fill_name = (
        "skinShadow"
        if item["equipmentId"] in (1011, 1012)
        else "underMid"
    )
    fill = _rgba(spec, fill_name)
    highlight = _rgba(spec, "lightCream")
    for index, point in enumerate(sorted(points, key=lambda value: (value[1], value[0]))):
        image.putpixel(point, highlight if index % 7 == 0 else fill)
    return image


@pytest.fixture
def gloves_shoes_source_fixture(
    tmp_path: Path,
    gloves_shoes_contract: dict,
) -> dict[str, object]:
    spec = copy.deepcopy(gloves_shoes_contract)
    spec_path = (
        tmp_path
        / "docs"
        / "art"
        / "equipment"
        / "todo-quest-gloves-shoes-layers-spec.json"
    )
    runtime_root = tmp_path / "app" / "src" / "main" / "assets"
    item_images: dict[int, Image.Image] = {}

    for render_slot, layer in spec["layerContracts"].items():
        reference_path = spec_path.parent / layer["referenceAlphaMask"]["path"]
        reference_image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for point in _gloves_shoes_points(render_slot):
            reference_image.putpixel(point, _rgba(spec, "skinLight"))
        _save(reference_image, reference_path)

    for item in spec["items"]:
        source_path = spec_path.parent / item["sourcePath"]
        image = _gloves_shoes_layer_image(spec, item)
        item_images[item["equipmentId"]] = image
        _save(image, source_path)
        item["sourceArtifact"] = _metadata(
            source_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )

        runtime_path = runtime_root / item["runtimePath"]
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source_path.read_bytes())
        item["runtimeArtifact"] = _metadata(
            runtime_path,
            image,
            item["runtimeArtifact"]["opaqueBounds"],
        )

        one_key = item["previewKeys"]["equipped1x"]
        eight_key = item["previewKeys"]["equipped8x"]
        one_contract = spec["previews"]["artifacts"][one_key]
        eight_contract = spec["previews"]["artifacts"][eight_key]
        one_path = spec_path.parent / one_contract["path"]
        eight_path = spec_path.parent / eight_contract["path"]
        _save(image, one_path)
        enlarged = image.resize((512, 512), Image.Resampling.NEAREST)
        _save(enlarged, eight_path)
        one_contract["artifact"] = _metadata(
            one_path,
            image,
            item["sourceArtifact"]["opaqueBounds"],
        )
        eight_contract["artifact"] = _metadata(
            eight_path,
            enlarged,
            [
                value * 8 if index < 2 else value * 8 + 7
                for index, value in enumerate(item["sourceArtifact"]["opaqueBounds"])
            ],
        )

    matrix = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    matrix_contract = spec["previews"]["combinationMatrix"]
    for row, glove_id in enumerate(matrix_contract["gloveEquipmentIds"]):
        for column, shoe_id in enumerate(matrix_contract["shoeEquipmentIds"]):
            cell = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
            cell.alpha_composite(item_images[shoe_id])
            cell.alpha_composite(item_images[glove_id])
            matrix.alpha_composite(cell, (column * 64, row * 64))

    one_key = matrix_contract["previewKeys"]["matrix1x"]
    four_key = matrix_contract["previewKeys"]["matrix4x"]
    one_contract = spec["previews"]["artifacts"][one_key]
    four_contract = spec["previews"]["artifacts"][four_key]
    one_path = spec_path.parent / one_contract["path"]
    four_path = spec_path.parent / four_contract["path"]
    _save(matrix, one_path)
    enlarged_matrix = matrix.resize((512, 512), Image.Resampling.NEAREST)
    _save(enlarged_matrix, four_path)
    one_contract["artifact"] = _metadata(one_path, matrix, [21, 39, 107, 122])
    four_contract["artifact"] = _metadata(
        four_path,
        enlarged_matrix,
        [84, 156, 431, 491],
    )

    _write_spec(spec_path, spec)
    return {
        "spec": spec,
        "spec_path": spec_path,
        "runtime_root": runtime_root,
    }


def test_repository_gloves_shoes_contract_declares_byte_identical_runtime_and_previews(
    gloves_shoes_contract: dict,
) -> None:
    assert gloves_shoes_contract["schemaVersion"] == 1
    assert gloves_shoes_contract["contractKind"] == "character-equipment-layer-variants"
    assert gloves_shoes_contract["baseCharacterContract"]["schemaVersion"] == 5
    assert gloves_shoes_contract["baseCharacterContract"]["canonicalSourceCount"] == 15
    assert gloves_shoes_contract["canvas"] == {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "boundsInclusive": [0, 0, 63, 63],
        "centerX": 32,
        "characterSoleY": 58,
        "anchorProfile": "canvas-64-center-x-32-sole-y-58-schema-v5",
    }
    assert list(gloves_shoes_contract["productionPalette"]["colors"].values()) == [
        "#11151C", "#1D3557", "#263B5A", "#35445C",
        "#3A3F45", "#2853A6", "#737982", "#B7B0A3",
        "#D99872", "#FFD3AE", "#F4EFE3", "#4F86E8",
        "#7FB3FF", "#5CC8A7", "#F2C14E", "#E05252",
    ]

    expected = {
        1011: ("가죽 장갑", "leather_gloves", "gloves_leather", "hands_front", "GLOVES", [21, 39, 43, 45]),
        1015: ("강철 건틀릿", "steel_gauntlets", "gloves_steel_gauntlets", "hands_front", "GLOVES", [21, 39, 43, 45]),
        1012: ("여행자의 장화", "travelers_boots", "shoes_travelers_boots", "shoes", "SHOES", [23, 53, 41, 58]),
        1016: ("바람걸음 장화", "windwalker_boots", "shoes_windwalker_boots", "shoes", "SHOES", [23, 53, 41, 58]),
    }
    assert {item["equipmentId"] for item in gloves_shoes_contract["items"]} == set(expected)
    for item in gloves_shoes_contract["items"]:
        display_name, equipment_key, layer_key, render_slot, equipment_slot, bounds = expected[item["equipmentId"]]
        assert item["displayNameKorean"] == display_name
        assert item["equipmentKey"] == equipment_key
        assert item["imageKey"] == item["layerKey"] == layer_key
        assert item["renderSlot"] == render_slot
        assert item["equipmentSlot"] == equipment_slot
        assert item["sourcePath"] == f"layers/{layer_key}.png"
        assert item["runtimePath"] == f"character/layers/{layer_key}.png"
        source_path = GLOVES_SHOES_SPEC_PATH.parent / item["sourcePath"]
        with Image.open(source_path) as stored:
            source_image = stored.convert("RGBA")
        assert item["sourceArtifact"] == _metadata(source_path, source_image, bounds)
        runtime_path = (
            GLOVES_SHOES_SPEC_PATH.parent
            / gloves_shoes_contract["pathContract"]["runtimePathRootRelativeToSpec"]
            / item["runtimePath"]
        ).resolve()
        with Image.open(runtime_path) as stored:
            runtime_image = stored.convert("RGBA")
        assert runtime_path.read_bytes() == source_path.read_bytes()
        assert item["runtimeArtifact"] == _metadata(runtime_path, runtime_image, bounds)

    hands = gloves_shoes_contract["layerContracts"]["hands_front"]
    assert hands["referenceAlphaMask"] == {
        "baseCharacterSourceId": "hands_front",
        "path": "../character/layers/hands_front.png",
        "opaqueBounds": [21, 39, 43, 45],
        "opaquePixelCount": 38,
        "alphaMaskSha256": "115452260a8f6d94e7dd000bda02875d52ec02a0516be5b547b82da8fc3169e3",
    }
    assert hands["connectivity"]["components"] == [
        {"name": "leftHand", "opaqueBounds": [21, 39, 24, 45], "opaquePixelCount": 19},
        {"name": "rightHand", "opaqueBounds": [40, 39, 43, 45], "opaquePixelCount": 19},
    ]
    shoes = gloves_shoes_contract["layerContracts"]["shoes"]
    assert shoes["referenceAlphaMask"] == {
        "baseCharacterSourceId": "shoes_adventure",
        "path": "../character/layers/shoes_adventure.png",
        "opaqueBounds": [23, 53, 41, 58],
        "opaquePixelCount": 104,
        "alphaMaskSha256": "9d42064b05d8ede2c5ca9ce1cb0dc7c0551ed11f2e9f855e6e25fe0e69f0f104",
    }
    assert shoes["connectivity"]["components"] == [
        {"name": "leftFoot", "opaqueBounds": [23, 53, 31, 58], "opaquePixelCount": 52},
        {"name": "rightFoot", "opaqueBounds": [33, 53, 41, 58], "opaquePixelCount": 52},
    ]
    assert gloves_shoes_contract["compositionContract"]["gloveGripOrder"] == [
        "weapon_held", "hands_front", "weapon_front",
    ]

    artifacts = gloves_shoes_contract["previews"]["artifacts"]
    assert len(artifacts) == 10
    assert sum(preview["scale"] == 1 and preview["width"] == 64 for preview in artifacts.values()) == 4
    assert sum(preview["scale"] == 8 and preview["width"] == 512 for preview in artifacts.values()) == 4
    assert artifacts["gloves-shoes-combination-matrix@1x"]["width"] == 128
    assert artifacts["gloves-shoes-combination-matrix@4x"]["width"] == 512
    for preview in artifacts.values():
        preview_path = GLOVES_SHOES_SPEC_PATH.parent / preview["path"]
        with Image.open(preview_path) as stored:
            preview_image = stored.convert("RGBA")
        assert preview["artifact"] == _metadata(
            preview_path,
            preview_image,
            preview["artifact"]["opaqueBounds"],
        )


def test_gloves_shoes_contract_mirrors_character_loadout_art_contract() -> None:
    character_spec = json.loads(CHARACTER_SPEC_PATH.read_text(encoding="utf-8"))
    equipment_spec = json.loads(GLOVES_SHOES_SPEC_PATH.read_text(encoding="utf-8"))

    assert equipment_spec["loadoutArtContract"] == character_spec["loadoutArtContract"]
    contract = equipment_spec["loadoutArtContract"]
    assert contract["emptyGameplaySlots"] == {
        "HELMET": {"representation": "transparent-overlay", "sourceIds": []},
        "CHEST": {"representation": "neutral-training-fallback", "sourceIds": ["top_default"]},
        "LEGS": {"representation": "neutral-training-fallback", "sourceIds": ["bottom_default"]},
        "GLOVES": {"representation": "transparent-overlay", "sourceIds": []},
        "SHOES": {"representation": "neutral-training-fallback", "sourceIds": ["shoes_default"]},
        "ACCESSORY": {"representation": "transparent-overlay", "sourceIds": []},
        "WEAPON": {"representation": "transparent-overlay", "sourceIds": []},
    }
    assert contract["alwaysPresentSourceIds"] == [
        "body_base",
        "hair_back_default",
        "hair_front_default",
        "hands_front",
    ]
    assert contract["adventureShopSet"]["slots"]["GLOVES"] == {
        "layerKey": "gloves_adventure",
        "sourceIds": ["gloves_adventure"],
    }
    assert contract["adventureShopSet"]["slots"]["WEAPON"] == {
        "layerKey": "weapon_default_sword",
        "sourceIds": [
            "weapon_back_default_sword",
            "weapon_held_default_sword",
            "weapon_front_default_sword",
        ],
        "mergedRuntimePngAllowed": False,
    }
    planned = contract["plannedCanonicalLayer"]
    assert planned["sourceArtifact"]["status"] == "available"
    assert planned["runtimeArtifact"]["status"] == "available"
    assert planned["sourceArtifact"] == planned["runtimeArtifact"]
    source_path = ROOT / planned["sourcePath"]
    runtime_path = ROOT / planned["runtimePath"]
    with Image.open(source_path) as stored:
        source = stored.convert("RGBA")
    with Image.open(runtime_path) as stored:
        runtime = stored.convert("RGBA")
    assert source_path.read_bytes() == runtime_path.read_bytes()
    assert source.getchannel("A").tobytes() == runtime.getchannel("A").tobytes()
    assert planned["sourceArtifact"] == _metadata(
        source_path, source, [21, 39, 43, 45]
    )


@pytest.mark.parametrize(
    ("mutator", "expected"),
    [
        (
            lambda contract: contract["neutralTrainingFallback"]["sourceIds"].remove(
                "shoes_default"
            ),
            "neutral training fallback",
        ),
        (
            lambda contract: contract["plannedCanonicalLayer"].update(
                id="gloves_missing"
            ),
            "gloves_adventure",
        ),
        (
            lambda contract: contract["regenerationManifest"]
            ["generatedPreviewTileNames"].remove("runtime-equipped-reference"),
            "preview regeneration",
        ),
    ],
)
def test_gloves_shoes_contract_rejects_incomplete_loadout_art_manifest(
    gloves_shoes_contract: dict,
    tmp_path: Path,
    mutator,
    expected: str,
) -> None:
    invalid = copy.deepcopy(gloves_shoes_contract)
    mutator(invalid["loadoutArtContract"])
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)

    errors = validate_contract(spec_path)

    assert any(expected in error for error in errors)


def test_validator_selects_all_three_known_contract_branches(
    gloves_shoes_contract: dict,
    tmp_path: Path,
) -> None:
    assert validate_contract(SPEC_PATH) == []
    assert validate_contract(OUTFIT_SPEC_PATH) == []
    assert validate_contract(GLOVES_SHOES_SPEC_PATH) == []

    invalid = copy.deepcopy(gloves_shoes_contract)
    invalid["items"][0]["equipmentId"] = 9999
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("supported" in error for error in validate_contract(spec_path))


@pytest.mark.parametrize(
    ("target", "unsafe_path"),
    [
        ("source", "../gloves.png"),
        ("runtime", "C:/outside/shoes.png"),
        ("preview", "../../matrix.png"),
    ],
)
def test_gloves_shoes_contract_rejects_unsafe_relative_paths(
    gloves_shoes_contract: dict,
    tmp_path: Path,
    target: str,
    unsafe_path: str,
) -> None:
    invalid = copy.deepcopy(gloves_shoes_contract)
    if target == "source":
        invalid["items"][0]["sourcePath"] = unsafe_path
    elif target == "runtime":
        invalid["items"][0]["runtimePath"] = unsafe_path
    else:
        matrix_key = invalid["previews"]["combinationMatrix"]["previewKeys"]["matrix1x"]
        invalid["previews"]["artifacts"][matrix_key]["path"] = unsafe_path
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("safe relative path" in error for error in validate_contract(spec_path))


@pytest.mark.parametrize("invalid_kind", ["size", "mode"])
def test_gloves_shoes_source_size_and_mode_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
    invalid_kind: str,
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "gloves_leather")
    with Image.open(path) as stored:
        image = stored.copy()
    image = image.crop((0, 0, 63, 64)) if invalid_kind == "size" else image.convert("RGB")
    _save(image, path)
    assert any(invalid_kind in error for error in validate_sources(_fixture_spec_path(gloves_shoes_source_fixture)))


@pytest.mark.parametrize(
    ("pixel", "expected"),
    [
        ((38, 63, 69, 128), "alpha"),
        ((1, 2, 3, 255), "palette"),
    ],
)
def test_gloves_shoes_source_alpha_and_palette_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
    pixel: Rgba,
    expected: str,
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "gloves_leather")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((22, 39), pixel)
    _save(image, path)
    assert any(expected in error for error in validate_sources(_fixture_spec_path(gloves_shoes_source_fixture)))


def test_glove_bounds_and_reference_alpha_mask_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "gloves_leather")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((20, 39), _rgba(_fixture_spec(gloves_shoes_source_fixture), "underMid"))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(gloves_shoes_source_fixture))
    assert any("opaque bounds" in error and "(20, 39)" in error for error in errors)
    assert any("alpha mask" in error for error in errors)


def test_glove_components_and_weapon_grip_mask_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "gloves_steel_gauntlets")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((42, 42), (0, 0, 0, 0))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(gloves_shoes_source_fixture))
    assert any("glove grip mask" in error and "(42, 42)" in error for error in errors)
    assert any("rightHand" in error or "alpha mask" in error for error in errors)


def test_glove_left_and_right_masks_must_remain_two_components(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "gloves_leather")
    with Image.open(path) as stored:
        image = stored.copy()
    fill = _rgba(_fixture_spec(gloves_shoes_source_fixture), "skinShadow")
    for x in range(24, 41):
        image.putpixel((x, 42), fill)
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(gloves_shoes_source_fixture))
    assert any("exactly 2 8-connected" in error for error in errors)


@pytest.mark.parametrize(
    ("point", "expected"),
    [
        ((24, 53), "left ankle overlap"),
        ((40, 54), "right ankle overlap"),
        ((23, 58), "sole row"),
    ],
)
def test_shoe_components_ankles_and_sole_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
    point: Point,
    expected: str,
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "shoes_travelers_boots")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel(point, (0, 0, 0, 0))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(gloves_shoes_source_fixture))
    assert any(expected in error and str(point) in error for error in errors)
    assert any("leftFoot" in error or "rightFoot" in error or "alpha mask" in error for error in errors)


def test_shoe_left_and_right_masks_must_remain_two_components(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    path = _source_path(gloves_shoes_source_fixture, "shoes_windwalker_boots")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((32, 55), _rgba(_fixture_spec(gloves_shoes_source_fixture), "underMid"))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(gloves_shoes_source_fixture))
    assert any("exactly 2 8-connected" in error for error in errors)


def test_gloves_shoes_source_metadata_hash_is_enforced(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(gloves_shoes_source_fixture)
    _item(spec, "shoes_windwalker_boots")["sourceArtifact"]["hashes"]["alphaMaskSha256"] = "0" * 64
    _save_fixture_spec(gloves_shoes_source_fixture)
    assert any("alphaMaskSha256" in error for error in validate_sources(_fixture_spec_path(gloves_shoes_source_fixture)))


@pytest.mark.parametrize(
    ("preview_key", "point", "expected"),
    [
        ("leather-gloves-equipped@8x", (176, 312), "nearest-neighbor"),
        ("gloves-shoes-combination-matrix@4x", (300, 220), "nearest-neighbor"),
    ],
)
def test_gloves_shoes_preview_sizes_and_nearest_enlargements_are_enforced(
    gloves_shoes_source_fixture: dict[str, object],
    preview_key: str,
    point: Point,
    expected: str,
) -> None:
    spec = _fixture_spec(gloves_shoes_source_fixture)
    preview = spec["previews"]["artifacts"][preview_key]
    path = _fixture_spec_path(gloves_shoes_source_fixture).parent / preview["path"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel(point, _rgba(spec, "redAccent"))
    _save(image, path)
    preview["artifact"] = _metadata(path, image, preview["artifact"]["opaqueBounds"])
    _save_fixture_spec(gloves_shoes_source_fixture)
    assert any(expected in error and str(point) in error for error in validate_sources(_fixture_spec_path(gloves_shoes_source_fixture)))


def test_gloves_shoes_preview_contract_rejects_wrong_size(
    gloves_shoes_contract: dict,
    tmp_path: Path,
) -> None:
    invalid = copy.deepcopy(gloves_shoes_contract)
    invalid["previews"]["artifacts"]["travelers-boots-equipped@8x"]["height"] = 511
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("height" in error for error in validate_contract(spec_path))


def test_gloves_shoes_full_check_passes_then_rejects_runtime_mismatch(
    gloves_shoes_source_fixture: dict[str, object],
) -> None:
    spec_path = _fixture_spec_path(gloves_shoes_source_fixture)
    assert validate_all(spec_path) == []

    spec = _fixture_spec(gloves_shoes_source_fixture)
    runtime_root: Path = gloves_shoes_source_fixture["runtime_root"]  # type: ignore[assignment]
    item = _item(spec, "shoes_windwalker_boots")
    path = runtime_root / item["runtimePath"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((24, 53), _rgba(spec, "goldAccent"))
    _save(image, path)
    item["runtimeArtifact"] = _metadata(
        path,
        image,
        item["runtimeArtifact"]["opaqueBounds"],
    )
    _save_fixture_spec(gloves_shoes_source_fixture)
    assert any("byte-identical" in error for error in validate_all(spec_path))


@pytest.mark.parametrize(
    ("mode", "validator"),
    [
        ("--check-contract", validate_contract),
        ("--check-sources", validate_sources),
        ("--check", validate_all),
    ],
)
def test_gloves_shoes_cli_preserves_all_public_validation_boundaries(
    gloves_shoes_source_fixture: dict[str, object],
    mode: str,
    validator,
    capsys: pytest.CaptureFixture[str],
) -> None:
    spec_path = _fixture_spec_path(gloves_shoes_source_fixture)
    assert validator(spec_path) == []
    assert main(["--spec", str(spec_path), mode]) == 0
    captured = capsys.readouterr()
    assert mode.removeprefix("--") in captured.out
    assert captured.err == ""


@pytest.fixture
def weapon_contract() -> dict:
    return json.loads(WEAPON_SPEC_PATH.read_text(encoding="utf-8"))


def _line_points(start: Point, end: Point) -> set[Point]:
    x, y = start
    end_x, end_y = end
    dx = abs(end_x - x)
    step_x = 1 if x < end_x else -1
    dy = -abs(end_y - y)
    step_y = 1 if y < end_y else -1
    error = dx + dy
    points: set[Point] = set()
    while True:
        points.add((x, y))
        if (x, y) == (end_x, end_y):
            return points
        doubled = 2 * error
        if doubled >= dy:
            error += dy
            x += step_x
        if doubled <= dx:
            error += dx
            y += step_y


def _weapon_points(equipment_id: int) -> set[Point]:
    points = _line_points((42, 58), (42, 42))
    points.update(_line_points((42, 42), (46, 28)))
    if equipment_id == 1001:
        points.update(_line_points((46, 28), (52, 4)))
        points.update(_line_points((47, 28), (53, 4)))
    elif equipment_id == 1002:
        points.update(_line_points((46, 28), (55, 4)))
        points.update(_line_points((47, 28), (56, 4)))
    elif equipment_id == 1017:
        points.update(_line_points((46, 28), (50, 8)))
        points.update((50, y) for y in range(4, 9))
        points.update({(49, 6), (49, 7), (51, 6), (51, 7)})
    else:
        points.update(_line_points((46, 28), (48, 12)))
        points.update(
            (x, y)
            for y in range(4, 13)
            for x in range(46, 59)
        )
    return points


def _points_bounds(points: set[Point]) -> list[int]:
    return [
        min(x for x, _ in points),
        min(y for _, y in points),
        max(x for x, _ in points),
        max(y for _, y in points),
    ]


def _weapon_layer_image(spec: dict, item: dict) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    fill_names = {
        1001: "underMid",
        1002: "lightCream",
        1017: "skinShadow",
        1018: "underLight",
    }
    fill = _rgba(spec, fill_names[item["equipmentId"]])
    for point in _weapon_points(item["equipmentId"]):
        image.putpixel(point, fill)
    return image


@pytest.fixture
def weapon_source_fixture(
    tmp_path: Path,
    weapon_contract: dict,
) -> dict[str, object]:
    spec = copy.deepcopy(weapon_contract)
    spec_path = (
        tmp_path
        / "docs"
        / "art"
        / "equipment"
        / "todo-quest-weapon-layers-spec.json"
    )
    runtime_root = tmp_path / "app" / "src" / "main" / "assets"
    item_images: dict[int, Image.Image] = {}

    for item in spec["items"]:
        source_path = spec_path.parent / item["sourcePath"]
        image = _weapon_layer_image(spec, item)
        item_images[item["equipmentId"]] = image
        bounds = _points_bounds(_weapon_points(item["equipmentId"]))
        _save(image, source_path)
        item["sourceArtifact"] = _metadata(source_path, image, bounds)

        runtime_path = runtime_root / item["runtimePath"]
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source_path.read_bytes())
        item["runtimeArtifact"] = _metadata(runtime_path, image, bounds)

        one_key = item["previewKeys"]["equipped1x"]
        eight_key = item["previewKeys"]["equipped8x"]
        one_contract = spec["previews"]["artifacts"][one_key]
        eight_contract = spec["previews"]["artifacts"][eight_key]
        one_path = spec_path.parent / one_contract["path"]
        eight_path = spec_path.parent / eight_contract["path"]
        _save(image, one_path)
        enlarged = image.resize((512, 512), Image.Resampling.NEAREST)
        _save(enlarged, eight_path)
        one_contract["artifact"] = _metadata(one_path, image, bounds)
        enlarged_bounds = [
            value * 8 if index < 2 else value * 8 + 7
            for index, value in enumerate(bounds)
        ]
        eight_contract["artifact"] = _metadata(
            eight_path,
            enlarged,
            enlarged_bounds,
        )

    matrix = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    matrix_contract = spec["previews"]["combinationMatrix"]
    for index, equipment_id in enumerate(matrix_contract["weaponEquipmentIds"]):
        column = index % matrix_contract["columns"]
        row = index // matrix_contract["columns"]
        matrix.alpha_composite(
            item_images[equipment_id],
            (column * 64, row * 64),
        )

    one_key = matrix_contract["previewKeys"]["matrix1x"]
    four_key = matrix_contract["previewKeys"]["matrix4x"]
    one_contract = spec["previews"]["artifacts"][one_key]
    four_contract = spec["previews"]["artifacts"][four_key]
    one_path = spec_path.parent / one_contract["path"]
    four_path = spec_path.parent / four_contract["path"]
    _save(matrix, one_path)
    enlarged_matrix = matrix.resize((512, 512), Image.Resampling.NEAREST)
    _save(enlarged_matrix, four_path)
    matrix_points = {
        (x + (index % 2) * 64, y + (index // 2) * 64)
        for index, equipment_id in enumerate(matrix_contract["weaponEquipmentIds"])
        for x, y in _weapon_points(equipment_id)
    }
    matrix_bounds = _points_bounds(matrix_points)
    one_contract["artifact"] = _metadata(one_path, matrix, matrix_bounds)
    four_contract["artifact"] = _metadata(
        four_path,
        enlarged_matrix,
        [
            value * 4 if index < 2 else value * 4 + 3
            for index, value in enumerate(matrix_bounds)
        ],
    )

    _write_spec(spec_path, spec)
    return {
        "spec": spec,
        "spec_path": spec_path,
        "runtime_root": runtime_root,
    }


def test_repository_weapon_contract_declares_four_available_sources_and_runtime(
    weapon_contract: dict,
) -> None:
    assert weapon_contract["schemaVersion"] == 1
    assert weapon_contract["contractKind"] == "character-equipment-layer-variants"
    assert weapon_contract["baseCharacterContract"] == {
        "specPath": "../character/character-modular-sheet-spec.json",
        "schemaVersion": 5,
        "canonicalSourceCount": 15,
        "relationship": (
            "independent gameplay weapon variants for the schema-v5 topmost weapon group"
        ),
    }
    assert weapon_contract["canvas"] == {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "boundsInclusive": [0, 0, 63, 63],
        "centerX": 32,
        "characterSoleY": 58,
        "anchorProfile": "canvas-64-center-x-32-sole-y-58-schema-v5",
    }
    assert list(weapon_contract["productionPalette"]["colors"].values()) == [
        "#11151C", "#1D3557", "#263B5A", "#35445C",
        "#3A3F45", "#2853A6", "#737982", "#B7B0A3",
        "#D99872", "#FFD3AE", "#F4EFE3", "#4F86E8",
        "#7FB3FF", "#5CC8A7", "#F2C14E", "#E05252",
    ]

    expected = {
        1001: ("낡은 검", "worn_sword", "LONGSWORD", "weapon_worn_sword"),
        1002: ("철 장검", "iron_longsword", "LONGSWORD", "weapon_iron_longsword"),
        1017: ("물푸레나무 창", "ash_spear", "SPEAR", "weapon_ash_spear"),
        1018: ("강철 철퇴", "steel_mace", "BLUNT", "weapon_steel_mace"),
    }
    assert {item["equipmentId"] for item in weapon_contract["items"]} == set(expected)
    for item in weapon_contract["items"]:
        display_name, equipment_key, weapon_type, layer_key = expected[item["equipmentId"]]
        assert item["displayNameKorean"] == display_name
        assert item["equipmentKey"] == equipment_key
        assert item["weaponType"] == weapon_type
        assert item["imageKey"] == item["layerKey"] == layer_key
        assert item["renderSlot"] == "weapon_front"
        assert item["equipmentSlot"] == "WEAPON"
        assert item["sourcePath"] == f"layers/{layer_key}.png"
        assert item["runtimePath"] == f"character/layers/{layer_key}.png"
        source_artifact = item["sourceArtifact"]
        assert source_artifact["status"] == "available"
        assert source_artifact["opaqueBounds"] == [40, 4, 58, 58]
        assert source_artifact["opaquePixelCount"] > 0
        assert source_artifact["fileByteCount"] > 0
        assert all(
            isinstance(value, str) and len(value) == 64
            for value in source_artifact["hashes"].values()
        )

        runtime_artifact = item["runtimeArtifact"]
        assert runtime_artifact == source_artifact

    layer = weapon_contract["layerContracts"]["weapon_front"]
    assert layer["opaqueEnvelope"] == [40, 4, 58, 58]
    assert layer["faceProtectedRegion"] == [20, 7, 44, 28]
    assert layer["requiredOpaquePoints"] == {"primaryGripAnchor": [42, 42]}
    assert layer["connectivity"] == {
        "opaqueNeighborMode": 8,
        "opaqueComponentCount": 1,
        "allowIsolatedOpaquePixels": False,
    }
    assert layer["handOverlapContract"] == {
        "region": [40, 39, 44, 45],
        "allowedOpaqueEnvelope": [41, 39, 44, 45],
        "allowedPart": "handle",
        "forbiddenParts": ["blade", "spearhead", "maceHead"],
    }
    assert weapon_contract["compositionContract"]["weaponGroup"] == {
        "groupId": "weapon",
        "zOrder": "topmost",
        "drawnAfterAllCharacterGroups": True,
        "containsAllGameplayWeaponSources": True,
    }
    assert weapon_contract["compositionContract"]["characterSchemaVersion"] == 5
    assert "plannedCharacterSchemaVersion" not in weapon_contract["compositionContract"]

    artifacts = weapon_contract["previews"]["artifacts"]
    assert len(artifacts) == 10
    assert sum(value["scale"] == 1 and value["width"] == 64 for value in artifacts.values()) == 4
    assert sum(value["scale"] == 8 and value["width"] == 512 for value in artifacts.values()) == 4
    assert artifacts["weapon-combination-matrix@1x"]["width"] == 128
    assert artifacts["weapon-combination-matrix@4x"]["width"] == 512
    for preview in artifacts.values():
        artifact = preview["artifact"]
        assert artifact["status"] == "available"
        assert artifact["opaqueBounds"] is not None
        assert artifact["opaquePixelCount"] > 0
        assert artifact["fileByteCount"] > 0
        assert all(
            isinstance(value, str) and len(value) == 64
            for value in artifact["hashes"].values()
        )


def test_validator_selects_all_four_known_contract_branches(
    weapon_contract: dict,
    tmp_path: Path,
) -> None:
    for path in (SPEC_PATH, OUTFIT_SPEC_PATH, GLOVES_SHOES_SPEC_PATH, WEAPON_SPEC_PATH):
        assert validate_contract(path) == []

    invalid = copy.deepcopy(weapon_contract)
    invalid["items"][0]["equipmentId"] = 9999
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("supported" in error for error in validate_contract(spec_path))


@pytest.mark.parametrize(
    ("target", "unsafe_path"),
    [
        ("source", "../weapon.png"),
        ("runtime", "C:/outside/weapon.png"),
        ("preview", "../../weapon-matrix.png"),
    ],
)
def test_weapon_contract_rejects_unsafe_relative_paths(
    weapon_contract: dict,
    tmp_path: Path,
    target: str,
    unsafe_path: str,
) -> None:
    invalid = copy.deepcopy(weapon_contract)
    if target == "source":
        invalid["items"][0]["sourcePath"] = unsafe_path
    elif target == "runtime":
        invalid["items"][0]["runtimePath"] = unsafe_path
    else:
        key = invalid["previews"]["combinationMatrix"]["previewKeys"]["matrix1x"]
        invalid["previews"]["artifacts"][key]["path"] = unsafe_path
    spec_path = tmp_path / "invalid.json"
    _write_spec(spec_path, invalid)
    assert any("safe relative path" in error for error in validate_contract(spec_path))


def test_generated_weapon_sources_and_previews_pass_without_runtime(
    weapon_source_fixture: dict[str, object],
) -> None:
    runtime_root: Path = weapon_source_fixture["runtime_root"]  # type: ignore[assignment]
    for path in runtime_root.rglob("*.png"):
        path.unlink()
    assert validate_sources(_fixture_spec_path(weapon_source_fixture)) == []


@pytest.mark.parametrize("invalid_kind", ["size", "mode"])
def test_weapon_source_size_and_mode_are_enforced(
    weapon_source_fixture: dict[str, object],
    invalid_kind: str,
) -> None:
    path = _source_path(weapon_source_fixture, "weapon_worn_sword")
    with Image.open(path) as stored:
        image = stored.copy()
    image = image.crop((0, 0, 63, 64)) if invalid_kind == "size" else image.convert("RGB")
    _save(image, path)
    assert any(invalid_kind in error for error in validate_sources(_fixture_spec_path(weapon_source_fixture)))


@pytest.mark.parametrize(
    ("pixel", "expected"),
    [
        ((38, 63, 69, 128), "alpha"),
        ((1, 2, 3, 255), "palette"),
        ((1, 2, 3, 0), "transparent pixel RGBA"),
    ],
)
def test_weapon_source_alpha_palette_and_transparent_rgba_are_enforced(
    weapon_source_fixture: dict[str, object],
    pixel: Rgba,
    expected: str,
) -> None:
    path = _source_path(weapon_source_fixture, "weapon_worn_sword")
    with Image.open(path) as stored:
        image = stored.copy()
    point = (42, 42) if pixel[3] else (0, 0)
    image.putpixel(point, pixel)
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(weapon_source_fixture))
    assert any(expected in error and str(point) in error for error in errors)


def test_weapon_opaque_envelope_and_connected_silhouette_are_enforced(
    weapon_source_fixture: dict[str, object],
) -> None:
    path = _source_path(weapon_source_fixture, "weapon_iron_longsword")
    with Image.open(path) as stored:
        image = stored.copy()
    fill = _rgba(_fixture_spec(weapon_source_fixture), "underMid")
    image.putpixel((39, 42), fill)
    image.putpixel((58, 58), fill)
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(weapon_source_fixture))
    assert any("opaque envelope" in error and "(39, 42)" in error for error in errors)
    assert any("8-connected" in error for error in errors)


def test_weapon_primary_grip_and_hand_handle_only_overlap_are_enforced(
    weapon_source_fixture: dict[str, object],
) -> None:
    path = _source_path(weapon_source_fixture, "weapon_ash_spear")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((42, 42), (0, 0, 0, 0))
    image.putpixel((40, 42), _rgba(_fixture_spec(weapon_source_fixture), "skinShadow"))
    image.putpixel((41, 42), _rgba(_fixture_spec(weapon_source_fixture), "skinShadow"))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(weapon_source_fixture))
    assert any("primary grip anchor" in error and "(42, 42)" in error for error in errors)
    assert any("hand region" in error and "(40, 42)" in error for error in errors)


def test_weapon_face_exclusion_is_enforced(
    weapon_source_fixture: dict[str, object],
) -> None:
    path = _source_path(weapon_source_fixture, "weapon_steel_mace")
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((44, 28), _rgba(_fixture_spec(weapon_source_fixture), "underLight"))
    _save(image, path)
    errors = validate_sources(_fixture_spec_path(weapon_source_fixture))
    assert any("face protected region" in error and "(44, 28)" in error for error in errors)


def test_weapon_source_metadata_hash_is_enforced(
    weapon_source_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(weapon_source_fixture)
    _item(spec, "weapon_steel_mace")["sourceArtifact"]["hashes"]["rawRgbaSha256"] = "0" * 64
    _save_fixture_spec(weapon_source_fixture)
    assert any("rawRgbaSha256" in error for error in validate_sources(_fixture_spec_path(weapon_source_fixture)))


@pytest.mark.parametrize(
    ("preview_key", "point"),
    [
        ("worn-sword-equipped@8x", (344, 336)),
        ("weapon-combination-matrix@4x", (300, 220)),
    ],
)
def test_weapon_preview_nearest_neighbor_enlargements_are_enforced(
    weapon_source_fixture: dict[str, object],
    preview_key: str,
    point: Point,
) -> None:
    spec = _fixture_spec(weapon_source_fixture)
    preview = spec["previews"]["artifacts"][preview_key]
    path = _fixture_spec_path(weapon_source_fixture).parent / preview["path"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel(point, _rgba(spec, "redAccent"))
    _save(image, path)
    preview["artifact"] = _metadata(path, image, preview["artifact"]["opaqueBounds"])
    _save_fixture_spec(weapon_source_fixture)
    errors = validate_sources(_fixture_spec_path(weapon_source_fixture))
    assert any("nearest-neighbor" in error and str(point) in error for error in errors)


def test_weapon_full_check_passes_then_rejects_runtime_byte_mismatch(
    weapon_source_fixture: dict[str, object],
) -> None:
    spec_path = _fixture_spec_path(weapon_source_fixture)
    assert validate_all(spec_path) == []

    spec = _fixture_spec(weapon_source_fixture)
    runtime_root: Path = weapon_source_fixture["runtime_root"]  # type: ignore[assignment]
    item = _item(spec, "weapon_worn_sword")
    path = runtime_root / item["runtimePath"]
    with Image.open(path) as stored:
        image = stored.copy()
    image.putpixel((42, 42), _rgba(spec, "goldAccent"))
    _save(image, path)
    item["runtimeArtifact"] = _metadata(
        path,
        image,
        item["runtimeArtifact"]["opaqueBounds"],
    )
    _save_fixture_spec(weapon_source_fixture)
    assert any("byte-identical" in error for error in validate_all(spec_path))


@pytest.mark.parametrize(
    ("mode", "validator"),
    [
        ("--check-contract", validate_contract),
        ("--check-sources", validate_sources),
        ("--check", validate_all),
    ],
)
def test_weapon_cli_preserves_all_public_validation_boundaries(
    weapon_source_fixture: dict[str, object],
    mode: str,
    validator,
    capsys: pytest.CaptureFixture[str],
) -> None:
    spec_path = _fixture_spec_path(weapon_source_fixture)
    assert validator(spec_path) == []
    assert main(["--spec", str(spec_path), mode]) == 0
    captured = capsys.readouterr()
    assert mode.removeprefix("--") in captured.out
    assert captured.err == ""
