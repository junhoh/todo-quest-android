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
    / "monster"
    / "todo-quest-goblin-scout-front-idle-spec.json"
)
SKELETON_SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "monster"
    / "todo-quest-skeleton-soldier-front-idle-spec.json"
)
SKELETON_CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "monster"
    / "todo-quest-skeleton-soldier-front-idle.png"
)
SKELETON_RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_skeleton_soldier_front_idle.png"
)
CORRUPTED_TREE_SPIRIT_CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "monster"
    / "todo-quest-corrupted-tree-spirit-front-idle.png"
)
CORRUPTED_TREE_SPIRIT_RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_corrupted_tree_spirit_front_idle.png"
)
HARPY_CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "monster"
    / "todo-quest-harpy-front-idle.png"
)
HARPY_RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_harpy_front_idle.png"
)
SLIME_CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "monster"
    / "todo-quest-slime-front-idle.png"
)
SLIME_RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_slime_front_idle.png"
)
sys.path.insert(0, str(SCRIPTS_DIR))

from validate_monster_sprite import load_spec, main, validate_sprite  # noqa: E402


Point = tuple[int, int]
Rgba = tuple[int, int, int, int]


def _rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.removeprefix("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4))


def _rgba(spec: dict, palette_name: str) -> Rgba:
    return (*_rgb(spec["palette"][palette_name]), 255)


def _rectangle(
    points: set[Point],
    left: int,
    top: int,
    right: int,
    bottom: int,
) -> None:
    points.update(
        (x, y)
        for y in range(top, bottom + 1)
        for x in range(left, right + 1)
    )


def _minimal_goblin_points() -> set[Point]:
    points: set[Point] = set()
    _rectangle(points, 30, 13, 34, 14)
    _rectangle(points, 27, 15, 37, 16)
    _rectangle(points, 23, 17, 41, 19)
    _rectangle(points, 16, 20, 48, 23)
    _rectangle(points, 21, 24, 43, 29)
    _rectangle(points, 24, 30, 40, 48)
    _rectangle(points, 21, 33, 43, 43)
    _rectangle(points, 24, 49, 31, 58)
    _rectangle(points, 33, 49, 40, 58)
    _rectangle(points, 41, 35, 48, 46)
    return points


def _minimal_skeleton_points() -> set[Point]:
    points = _minimal_goblin_points()
    _rectangle(points, 44, 34, 48, 50)
    for point in (
        (30, 37),
        (32, 37),
        (34, 37),
        (30, 40),
        (32, 40),
        (34, 40),
    ):
        points.remove(point)
    return points


def _external_boundary(points: set[Point], width: int, height: int) -> set[Point]:
    offsets = ((-1, 0), (1, 0), (0, -1), (0, 1))
    return {
        (x, y)
        for x, y in points
        if any(
            not (0 <= x + dx < width and 0 <= y + dy < height)
            or (x + dx, y + dy) not in points
            for dx, dy in offsets
        )
    }


def _build_valid_sprite(spec: dict) -> Image.Image:
    asset = spec["asset"]
    image = Image.new(
        asset["mode"],
        (asset["width"], asset["height"]),
        (0, 0, 0, 0),
    )
    points = _minimal_goblin_points()
    skin_base = _rgba(spec, "skinBase")
    outline = _rgba(spec, "outlineDarkNavy")

    for point in points:
        image.putpixel(point, skin_base)
    for point in _external_boundary(points, image.width, image.height):
        image.putpixel(point, outline)

    named_pixels = {
        (25, 25): "skinShadow",
        (39, 25): "skinHighlight",
        (28, 36): "leatherShadow",
        (31, 38): "leatherBase",
        (35, 40): "leatherHighlight",
        (29, 22): "dangerRed",
        (35, 22): "dangerRed",
        (44, 36): "metalDark",
        (44, 40): "metalMid",
        (44, 44): "metalLight",
        (45, 45): "poisonAccent",
    }
    for point, palette_name in named_pixels.items():
        assert point not in _external_boundary(points, image.width, image.height)
        image.putpixel(point, _rgba(spec, palette_name))

    outline_points = {
        point
        for point in points
        if image.getpixel(point) == outline
    }
    assert not any(
        {
            (x, y),
            (x + 1, y),
            (x, y + 1),
            (x + 1, y + 1),
        }
        <= outline_points
        for y in range(image.height - 1)
        for x in range(image.width - 1)
    )
    return image


def _build_valid_skeleton_sprite(spec: dict) -> Image.Image:
    asset = spec["asset"]
    image = Image.new(
        asset["mode"],
        (asset["width"], asset["height"]),
        (0, 0, 0, 0),
    )
    points = _minimal_skeleton_points()
    bone_base = _rgba(spec, "boneBase")
    outline = _rgba(spec, "outlineDarkNavy")
    boundary = _external_boundary(points, image.width, image.height)

    for point in points:
        image.putpixel(point, bone_base)
    for point in boundary:
        image.putpixel(point, outline)

    named_pixels = {
        (25, 25): "inkDeep",
        (27, 36): "boneShadow",
        (37, 36): "boneHighlight",
        (27, 39): "ironShadow",
        (37, 39): "ironBase",
        (27, 42): "rustAccent",
        (37, 42): "leatherShadow",
        (27, 45): "leatherBase",
        (37, 45): "clothDark",
        (27, 47): "clothMid",
        (29, 22): "dangerRed",
        (35, 22): "dangerRed",
        (45, 35): "bladeDark",
        (45, 49): "bladeLight",
    }
    for point, palette_name in named_pixels.items():
        assert point in points
        assert point not in boundary
        image.putpixel(point, _rgba(spec, palette_name))

    outline_points = {
        point
        for point in points
        if image.getpixel(point) == outline
    }
    assert not any(
        {
            (x, y),
            (x + 1, y),
            (x, y + 1),
            (x + 1, y + 1),
        }
        <= outline_points
        for y in range(image.height - 1)
        for x in range(image.width - 1)
    )
    return image


def _open_image(path: Path) -> Image.Image:
    with Image.open(path) as image:
        image.load()
        return image.copy()


def _save(image: Image.Image, path: Path) -> None:
    image.save(path)


def _replace_color(image: Image.Image, old: Rgba, new: Rgba) -> None:
    for y in range(image.height):
        for x in range(image.width):
            if image.getpixel((x, y)) == old:
                image.putpixel((x, y), new)


def _write_spec(tmp_path: Path, spec: dict, name: str = "spec.json") -> Path:
    path = tmp_path / name
    path.write_text(json.dumps(spec, ensure_ascii=False), encoding="utf-8")
    return path


def _with_mirrored_wings(spec: dict) -> dict:
    mirrored = json.loads(json.dumps(spec))
    mirrored["mirroredRegions"] = {
        "wings": {
            "axisX": 32,
            "leftRegion": [26, 24, 27, 25],
            "rightRegion": [37, 24, 38, 25],
            "regionInclusive": True,
            "comparison": "rgba",
        }
    }
    return mirrored


@pytest.fixture
def spec() -> dict:
    return json.loads(SPEC_PATH.read_text(encoding="utf-8"))


@pytest.fixture
def skeleton_spec() -> dict:
    return json.loads(SKELETON_SPEC_PATH.read_text(encoding="utf-8"))


@pytest.fixture
def valid_sprite(tmp_path: Path, spec: dict) -> Path:
    path = tmp_path / spec["asset"]["fileName"]
    _build_valid_sprite(spec).save(path)
    return path


@pytest.fixture
def valid_skeleton_sprite(tmp_path: Path, skeleton_spec: dict) -> Path:
    path = tmp_path / skeleton_spec["asset"]["fileName"]
    _build_valid_skeleton_sprite(skeleton_spec).save(path)
    return path


def test_load_spec_reads_json_contract(spec: dict) -> None:
    assert load_spec(SPEC_PATH) == spec


def test_skeleton_runtime_resource_is_byte_identical_to_canonical_art() -> None:
    canonical_bytes = SKELETON_CANONICAL_PATH.read_bytes()
    runtime_bytes = SKELETON_RUNTIME_PATH.read_bytes()

    assert hashlib.sha256(runtime_bytes).hexdigest() == hashlib.sha256(
        canonical_bytes
    ).hexdigest()
    assert runtime_bytes == canonical_bytes


def test_corrupted_tree_spirit_runtime_resource_is_byte_identical_to_canonical_art() -> None:
    canonical_bytes = CORRUPTED_TREE_SPIRIT_CANONICAL_PATH.read_bytes()
    runtime_bytes = CORRUPTED_TREE_SPIRIT_RUNTIME_PATH.read_bytes()

    assert hashlib.sha256(runtime_bytes).hexdigest() == hashlib.sha256(
        canonical_bytes
    ).hexdigest()
    assert runtime_bytes == canonical_bytes


def test_harpy_runtime_resource_is_byte_identical_to_canonical_art() -> None:
    canonical_bytes = HARPY_CANONICAL_PATH.read_bytes()
    runtime_bytes = HARPY_RUNTIME_PATH.read_bytes()

    assert hashlib.sha256(runtime_bytes).hexdigest() == hashlib.sha256(
        canonical_bytes
    ).hexdigest()
    assert runtime_bytes == canonical_bytes


def test_slime_runtime_resource_is_byte_identical_to_canonical_art() -> None:
    canonical_bytes = SLIME_CANONICAL_PATH.read_bytes()
    runtime_bytes = SLIME_RUNTIME_PATH.read_bytes()

    assert hashlib.sha256(runtime_bytes).hexdigest() == hashlib.sha256(
        canonical_bytes
    ).hexdigest()
    assert runtime_bytes == canonical_bytes


def test_valid_sprite_passes(valid_sprite: Path) -> None:
    assert validate_sprite(valid_sprite, SPEC_PATH) == []


def test_existing_goblin_semantics_pass_without_new_optional_contracts(
    valid_sprite: Path,
    spec: dict,
) -> None:
    assert set(spec["semanticRegions"]) == {"redEyes", "daggerMetal", "poison"}
    assert "groundContacts" not in spec
    assert "transparentRegions" not in spec

    assert validate_sprite(valid_sprite, SPEC_PATH) == []


def test_mirrored_regions_are_optional_for_schema_v1(
    valid_sprite: Path,
    spec: dict,
) -> None:
    assert "mirroredRegions" not in spec
    assert validate_sprite(valid_sprite, SPEC_PATH) == []


def test_exact_rgba_mirrored_region_passes(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
) -> None:
    mirrored_path = _write_spec(
        tmp_path,
        _with_mirrored_wings(spec),
        "mirrored.json",
    )

    assert validate_sprite(valid_sprite, mirrored_path) == []


@pytest.mark.parametrize(
    ("invalid_kind", "right_rgba"),
    [
        ("opaque-mask", (0, 0, 0, 0)),
        ("color", (118, 82, 56, 255)),
        ("alpha", (101, 122, 75, 128)),
    ],
)
def test_mirrored_region_reports_first_rgba_mismatch(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
    invalid_kind: str,
    right_rgba: Rgba,
) -> None:
    mirrored = _with_mirrored_wings(spec)
    if invalid_kind == "alpha":
        mirrored["allowedAlphaValues"].append(128)
    mirrored_path = _write_spec(tmp_path, mirrored, f"{invalid_kind}.json")
    image = _open_image(valid_sprite)
    left_rgba = image.getpixel((26, 24))
    image.putpixel((38, 24), right_rgba)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, mirrored_path)

    assert any(
        "mirroredRegions.wings" in error
        and "left (26, 24)" in error
        and str(left_rgba) in error
        and "right (38, 24)" in error
        and str(right_rgba) in error
        for error in errors
    )


def test_mirrored_region_compares_rgb_of_fully_transparent_pixels(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
) -> None:
    mirrored_path = _write_spec(
        tmp_path,
        _with_mirrored_wings(spec),
        "transparent-rgb.json",
    )
    image = _open_image(valid_sprite)
    left_rgba = (1, 2, 3, 0)
    right_rgba = (4, 5, 6, 0)
    image.putpixel((26, 24), left_rgba)
    image.putpixel((38, 24), right_rgba)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, mirrored_path)

    assert any(
        "mirroredRegions.wings" in error
        and "left (26, 24)" in error
        and str(left_rgba) in error
        and "right (38, 24)" in error
        and str(right_rgba) in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("invalid_kind", "message"),
    [
        ("size", "same width and height"),
        ("axis", "exact reflections around axisX=31"),
        ("comparison", "comparison must be rgba"),
        ("bounds", "within image bounds"),
        ("overlap", "must not overlap"),
        ("center", "must stay strictly left of axisX=32"),
        ("vertical-offset", "exact reflections around axisX=32"),
    ],
)
def test_invalid_mirrored_region_geometry_is_a_spec_error(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
    invalid_kind: str,
    message: str,
) -> None:
    invalid = _with_mirrored_wings(spec)
    contract = invalid["mirroredRegions"]["wings"]
    if invalid_kind == "size":
        contract["rightRegion"] = [37, 24, 39, 25]
    elif invalid_kind == "axis":
        contract["axisX"] = 31
    elif invalid_kind == "comparison":
        contract["comparison"] = "rgb"
    elif invalid_kind == "bounds":
        contract["leftRegion"] = [-1, 24, 0, 25]
    elif invalid_kind == "overlap":
        contract["rightRegion"] = [27, 24, 28, 25]
    elif invalid_kind == "center":
        contract["leftRegion"] = [31, 24, 32, 25]
        contract["rightRegion"] = [33, 24, 34, 25]
    else:
        contract["rightRegion"] = [37, 25, 38, 26]
    invalid_path = _write_spec(tmp_path, invalid, f"invalid-{invalid_kind}.json")

    errors = validate_sprite(valid_sprite, invalid_path)

    assert len(errors) == 1
    assert errors[0].startswith("invalid monster sprite specification:")
    assert message in errors[0]


@pytest.mark.parametrize(
    ("invalid_kind", "message"),
    [
        ("axis-type", "mirroredRegions.wings.axisX must be an integer"),
        ("coordinate-type", "mirroredRegions.wings.leftRegion[2] must be an integer"),
        (
            "inclusive-type",
            "mirroredRegions.wings.regionInclusive must be a boolean",
        ),
    ],
)
def test_invalid_mirrored_region_field_types_are_spec_errors(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
    invalid_kind: str,
    message: str,
) -> None:
    invalid = _with_mirrored_wings(spec)
    contract = invalid["mirroredRegions"]["wings"]
    if invalid_kind == "axis-type":
        contract["axisX"] = "32"
    elif invalid_kind == "coordinate-type":
        contract["leftRegion"][2] = True
    else:
        contract["regionInclusive"] = "true"
    invalid_path = _write_spec(tmp_path, invalid, f"invalid-{invalid_kind}.json")

    errors = validate_sprite(valid_sprite, invalid_path)

    assert len(errors) == 1
    assert errors[0] == f"invalid monster sprite specification: {message}"


def test_arbitrary_semantic_region_names_support_single_and_combined_palettes(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
) -> None:
    renamed = json.loads(json.dumps(spec))
    renamed["semanticRegions"] = {
        "hostileGaze": renamed["semanticRegions"]["redEyes"],
        "sideBlade": renamed["semanticRegions"]["daggerMetal"],
        "venomResidue": renamed["semanticRegions"]["poison"],
    }
    renamed_path = _write_spec(tmp_path, renamed, "renamed-semantics.json")

    assert validate_sprite(valid_sprite, renamed_path) == []


@pytest.mark.parametrize(
    ("invalid_kind", "semantic_name", "message"),
    [
        ("pixel-count", "hostileGaze", "pixel count"),
        ("outside-region", "hostileGaze", "outside hostileGaze region"),
        ("height", "sideBlade", "opaque bounding box height"),
        ("x-range", "sideBlade", "center body x-range"),
    ],
)
def test_arbitrary_semantic_regions_apply_every_declared_rule(
    valid_sprite: Path,
    spec: dict,
    tmp_path: Path,
    invalid_kind: str,
    semantic_name: str,
    message: str,
) -> None:
    renamed = json.loads(json.dumps(spec))
    renamed["semanticRegions"] = {
        "hostileGaze": renamed["semanticRegions"]["redEyes"],
        "sideBlade": renamed["semanticRegions"]["daggerMetal"],
        "venomResidue": renamed["semanticRegions"]["poison"],
    }
    image = _open_image(valid_sprite)
    if invalid_kind == "pixel-count":
        _replace_color(image, _rgba(spec, "dangerRed"), _rgba(spec, "skinBase"))
    elif invalid_kind == "outside-region":
        image.putpixel((25, 25), _rgba(spec, "dangerRed"))
    elif invalid_kind == "height":
        for palette_name in renamed["semanticRegions"]["sideBlade"]["paletteNames"]:
            _replace_color(image, _rgba(spec, palette_name), _rgba(spec, "skinBase"))
        for palette_name, y in zip(
            ("metalDark", "metalMid", "metalLight", "poisonAccent"),
            (39, 40, 41, 42),
        ):
            image.putpixel((45, y), _rgba(spec, palette_name))
    else:
        image.putpixel((32, 36), _rgba(spec, "metalDark"))
    _save(image, valid_sprite)
    renamed_path = _write_spec(tmp_path, renamed, "renamed-invalid.json")

    errors = validate_sprite(valid_sprite, renamed_path)

    assert any(
        semantic_name in error and message in error
        for error in errors
    )


def test_valid_skeleton_declarative_regions_contacts_and_gaps_pass(
    valid_skeleton_sprite: Path,
    capsys: pytest.CaptureFixture[str],
) -> None:
    assert validate_sprite(valid_skeleton_sprite, SKELETON_SPEC_PATH) == []

    result = main(
        [
            "--image",
            str(valid_skeleton_sprite),
            "--spec",
            str(SKELETON_SPEC_PATH),
        ]
    )

    captured = capsys.readouterr()
    assert result == 0
    assert "passed" in captured.out
    assert captured.err == ""


@pytest.mark.parametrize(
    ("invalid_kind", "contract_name", "message"),
    [
        ("semantic-color", "eyeGlow", "pixel count"),
        ("sword-height", "swordMetal", "opaque bounding box height"),
        ("center-overlap", "swordMetal", "center body x-range"),
        ("left-contact", "leftFoot", "ground contact"),
        ("rib-gap", "ribCageGaps", "transparent pixel count"),
        ("leg-gap", "legGap", "transparent pixel count"),
        ("sword-gap", "swordBodyGap", "transparent pixel count"),
    ],
)
def test_skeleton_contract_reports_distinct_declarative_errors(
    valid_skeleton_sprite: Path,
    skeleton_spec: dict,
    invalid_kind: str,
    contract_name: str,
    message: str,
) -> None:
    image = _open_image(valid_skeleton_sprite)
    if invalid_kind == "semantic-color":
        _replace_color(
            image,
            _rgba(skeleton_spec, "dangerRed"),
            _rgba(skeleton_spec, "boneBase"),
        )
    elif invalid_kind == "sword-height":
        for palette_name in ("bladeDark", "bladeLight"):
            _replace_color(
                image,
                _rgba(skeleton_spec, palette_name),
                _rgba(skeleton_spec, "boneBase"),
            )
        image.putpixel((45, 40), _rgba(skeleton_spec, "bladeDark"))
        image.putpixel((45, 42), _rgba(skeleton_spec, "bladeLight"))
    elif invalid_kind == "center-overlap":
        image.putpixel((32, 36), _rgba(skeleton_spec, "bladeDark"))
    elif invalid_kind == "left-contact":
        for x in range(24, 32):
            image.putpixel((x, 58), (0, 0, 0, 0))
    else:
        region_name = {
            "rib-gap": "ribCageGaps",
            "leg-gap": "legGap",
            "sword-gap": "swordBodyGap",
        }[invalid_kind]
        left, top, right, bottom = skeleton_spec["transparentRegions"][
            region_name
        ]["region"]
        for y in range(top, bottom + 1):
            for x in range(left, right + 1):
                if image.getpixel((x, y))[3] == 0:
                    image.putpixel((x, y), _rgba(skeleton_spec, "boneBase"))
    _save(image, valid_skeleton_sprite)

    errors = validate_sprite(valid_skeleton_sprite, SKELETON_SPEC_PATH)

    assert any(
        contract_name in error and message in error
        for error in errors
    )


def test_transparent_region_optional_maximum_is_enforced(
    valid_skeleton_sprite: Path,
    skeleton_spec: dict,
    tmp_path: Path,
) -> None:
    limited = json.loads(json.dumps(skeleton_spec))
    limited["transparentRegions"]["ribCageGaps"]["pixelCount"]["max"] = 4
    limited_path = _write_spec(tmp_path, limited, "limited-gap.json")

    errors = validate_sprite(valid_skeleton_sprite, limited_path)

    assert any(
        "ribCageGaps" in error
        and "transparent pixel count" in error
        and "maximum 4" in error
        for error in errors
    )


@pytest.mark.parametrize(
    "mutate",
    [
        lambda value: value.update(semanticRegions=[]),
        lambda value: value["semanticRegions"]["eyeGlow"].update(paletteName=7),
        lambda value: value["semanticRegions"]["swordMetal"].update(
            paletteNames="bladeDark"
        ),
        lambda value: value["semanticRegions"]["eyeGlow"].update(
            paletteName="missingPaletteName"
        ),
        lambda value: value.update(groundContacts=[]),
        lambda value: value["transparentRegions"]["ribCageGaps"].update(
            region="28,35,36,43"
        ),
    ],
)
def test_invalid_declarative_field_types_and_palette_names_are_spec_errors(
    valid_skeleton_sprite: Path,
    skeleton_spec: dict,
    tmp_path: Path,
    mutate,
) -> None:
    invalid = json.loads(json.dumps(skeleton_spec))
    mutate(invalid)
    invalid_path = _write_spec(tmp_path, invalid, "invalid-spec.json")

    errors = validate_sprite(valid_skeleton_sprite, invalid_path)

    assert len(errors) == 1
    assert errors[0].startswith("invalid monster sprite specification:")


@pytest.mark.parametrize("invalid_kind", ["size", "mode"])
def test_image_size_and_rgba_mode_are_enforced(
    valid_sprite: Path,
    invalid_kind: str,
) -> None:
    image = _open_image(valid_sprite)
    if invalid_kind == "size":
        image = image.crop((0, 0, image.width - 1, image.height))
    else:
        image = image.convert("RGB")
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(invalid_kind in error for error in errors)


@pytest.mark.parametrize(
    ("pixel", "message"),
    [
        ((101, 122, 75, 128), "alpha"),
        ((1, 2, 3, 255), "outside contract palette"),
        ((255, 0, 255, 255), "chroma key"),
    ],
)
def test_alpha_palette_and_chroma_key_are_enforced(
    valid_sprite: Path,
    pixel: Rgba,
    message: str,
) -> None:
    image = _open_image(valid_sprite)
    image.putpixel((32, 32), pixel)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(message in error for error in errors)


@pytest.mark.parametrize(
    ("invalid_kind", "message"),
    [
        ("bounding-box", "opaque bounding box"),
        ("center", "center axis"),
        ("margin", "minimum margin"),
        ("sole", "sole"),
    ],
)
def test_opaque_geometry_contract_is_enforced(
    valid_sprite: Path,
    spec: dict,
    invalid_kind: str,
    message: str,
) -> None:
    image = _open_image(valid_sprite)
    if invalid_kind == "bounding-box":
        for x in range(image.width):
            image.putpixel((x, 13), (0, 0, 0, 0))
    elif invalid_kind == "center":
        image.putpixel((15, 21), _rgba(spec, "outlineDarkNavy"))
    elif invalid_kind == "margin":
        image.putpixel((2, 21), _rgba(spec, "outlineDarkNavy"))
    else:
        for x in range(image.width):
            image.putpixel((x, 58), (0, 0, 0, 0))
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(message in error for error in errors)


def test_every_required_palette_color_must_be_present(
    valid_sprite: Path,
    spec: dict,
) -> None:
    image = _open_image(valid_sprite)
    _replace_color(
        image,
        _rgba(spec, "leatherHighlight"),
        _rgba(spec, "leatherBase"),
    )
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(
        "required palette" in error and "leatherHighlight" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("palette_name", "invalid_kind"),
    [
        ("dangerRed", "missing"),
        ("dangerRed", "overflow"),
        ("dangerRed", "region"),
        ("poisonAccent", "missing"),
        ("poisonAccent", "overflow"),
        ("poisonAccent", "region"),
    ],
)
def test_eye_and_poison_pixel_counts_and_regions_are_enforced(
    valid_sprite: Path,
    spec: dict,
    palette_name: str,
    invalid_kind: str,
) -> None:
    image = _open_image(valid_sprite)
    color = _rgba(spec, palette_name)
    if invalid_kind == "missing":
        _replace_color(image, color, _rgba(spec, "skinBase"))
    elif invalid_kind == "overflow" and palette_name == "dangerRed":
        for point in ((30, 22), (31, 22), (32, 22)):
            image.putpixel(point, color)
    elif invalid_kind == "overflow":
        for point in ((45, 36), (45, 37), (45, 38), (45, 39), (45, 40), (45, 41)):
            image.putpixel(point, color)
    elif palette_name == "dangerRed":
        image.putpixel((25, 25), color)
    else:
        image.putpixel((39, 25), color)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    expected = "pixel count" if invalid_kind != "region" else "outside"
    assert any(
        palette_name in error and expected in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("point", "message"),
    [
        ((20, 21), "outside daggerMetal region"),
        ((32, 36), "center body"),
    ],
)
def test_dagger_metal_cannot_appear_outside_dagger_or_over_center_body(
    valid_sprite: Path,
    spec: dict,
    point: Point,
    message: str,
) -> None:
    image = _open_image(valid_sprite)
    image.putpixel(point, _rgba(spec, "metalDark"))
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(message in error for error in errors)


@pytest.mark.parametrize(
    ("minimum_y", "maximum_y"),
    [(36, 42), (35, 47)],
)
def test_dagger_metal_height_must_be_eight_to_twelve_pixels(
    valid_sprite: Path,
    spec: dict,
    minimum_y: int,
    maximum_y: int,
) -> None:
    image = _open_image(valid_sprite)
    for palette_name in spec["semanticRegions"]["daggerMetal"]["paletteNames"]:
        _replace_color(image, _rgba(spec, palette_name), _rgba(spec, "skinBase"))
    colors = ("metalDark", "metalMid", "metalLight", "poisonAccent")
    y_values = (minimum_y, minimum_y + 2, maximum_y - 2, maximum_y)
    for palette_name, y in zip(colors, y_values):
        image.putpixel((45, y), _rgba(spec, palette_name))
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(
        "daggerMetal" in error and "height" in error
        for error in errors
    )


def test_external_four_neighbor_boundary_must_use_outline_color(
    valid_sprite: Path,
    spec: dict,
) -> None:
    image = _open_image(valid_sprite)
    image.putpixel((32, 13), _rgba(spec, "skinBase"))
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any(
        "external boundary" in error and "outlineDarkNavy" in error
        for error in errors
    )


def test_external_outline_cannot_contain_two_by_two_block(
    valid_sprite: Path,
    spec: dict,
) -> None:
    image = _open_image(valid_sprite)
    outline = _rgba(spec, "outlineDarkNavy")
    for y in (13, 14):
        for x in (31, 32):
            image.putpixel((x, y), outline)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any("external outline" in error and "2x2" in error for error in errors)


def test_opaque_pixels_must_form_exactly_one_eight_connected_component(
    valid_sprite: Path,
    spec: dict,
) -> None:
    image = _open_image(valid_sprite)
    outline = _rgba(spec, "outlineDarkNavy")
    image.putpixel((5, 5), outline)
    image.putpixel((6, 5), outline)
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any("8-connected" in error and "component" in error for error in errors)


def test_isolated_opaque_pixel_is_rejected(valid_sprite: Path, spec: dict) -> None:
    image = _open_image(valid_sprite)
    image.putpixel((5, 5), _rgba(spec, "outlineDarkNavy"))
    _save(image, valid_sprite)

    errors = validate_sprite(valid_sprite, SPEC_PATH)

    assert any("isolated opaque pixel" in error for error in errors)


def test_cli_reports_success_to_stdout(
    valid_sprite: Path,
    capsys: pytest.CaptureFixture[str],
) -> None:
    result = main(["--image", str(valid_sprite), "--spec", str(SPEC_PATH)])

    captured = capsys.readouterr()
    assert result == 0
    assert "passed" in captured.out
    assert captured.err == ""


def test_cli_reports_all_violations_to_stderr(
    valid_sprite: Path,
    capsys: pytest.CaptureFixture[str],
) -> None:
    image = _open_image(valid_sprite)
    image.putpixel((31, 31), (1, 2, 3, 128))
    _save(image, valid_sprite)

    result = main(["--image", str(valid_sprite), "--spec", str(SPEC_PATH)])

    captured = capsys.readouterr()
    assert result == 1
    assert captured.out == ""
    assert "alpha" in captured.err
    assert "outside contract palette" in captured.err
