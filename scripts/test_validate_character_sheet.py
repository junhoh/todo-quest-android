import copy
import hashlib
import json
import sys
from pathlib import Path

import pytest
from PIL import Image, ImageDraw


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
SPEC_PATH = ROOT / "docs" / "art" / "character" / "character-base-spec.json"
MODULAR_SPEC_PATH = (
    ROOT / "docs" / "art" / "character" / "character-modular-sheet-spec.json"
)
REFINEMENT_SPEC_PATH = (
    ROOT / "scripts" / "fixtures" / "character-modular-sheet-spec-v3.json"
)
MODULAR_SOURCE_SHEET_PATH = (
    ROOT / "docs" / "art" / "character" / "todo-quest-character-modular-sheet.png"
)
BASE_BODY_PATH = (
    ROOT / "docs" / "art" / "character" / "todo-quest-character-base-body.png"
)
HEADGEAR_LAYER_PATH = (
    ROOT / "docs" / "art" / "character" / "layers" / "headgear_adventure.png"
)
ACCESSORY_LAYER_PATH = (
    ROOT / "docs" / "art" / "character" / "layers" / "accessory_adventure.png"
)
sys.path.insert(0, str(SCRIPTS_DIR))

from validate_character_sheet import main, validate_sheet  # noqa: E402


def _rgb(hex_color: str) -> tuple[int, int, int]:
    value = hex_color.removeprefix("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4))


def _tile_origin(spec: dict, name: str) -> tuple[int, int]:
    tile = next(item for item in spec["tileMap"] if item["name"] == name)
    return (
        tile["column"] * spec["logicalTile"]["width"],
        tile["row"] * spec["logicalTile"]["height"],
    )


def _local_box(spec: dict, name: str) -> tuple[int, int, int, int]:
    origin_x, origin_y = _tile_origin(spec, name)
    left, top, right, bottom = spec["commonBoundingBox"]
    return (
        origin_x + left,
        origin_y + top,
        origin_x + right,
        origin_y + bottom,
    )


def _put_local(
    image: Image.Image,
    spec: dict,
    tile_name: str,
    point: tuple[int, int],
    color: tuple[int, int, int, int],
) -> None:
    origin_x, origin_y = _tile_origin(spec, tile_name)
    image.putpixel((origin_x + point[0], origin_y + point[1]), color)


def _paste_local(
    image: Image.Image,
    spec: dict,
    tile_name: str,
    tile: Image.Image,
) -> None:
    image.paste(tile, _tile_origin(spec, tile_name))


def _new_local_tile(spec: dict) -> Image.Image:
    return Image.new(
        "RGBA",
        (spec["logicalTile"]["width"], spec["logicalTile"]["height"]),
        (0, 0, 0, 0),
    )


def _tile_image_for_test(image: Image.Image, spec: dict, name: str) -> Image.Image:
    origin_x, origin_y = _tile_origin(spec, name)
    width = spec["logicalTile"]["width"]
    height = spec["logicalTile"]["height"]
    return image.crop((origin_x, origin_y, origin_x + width, origin_y + height))


def _rgba(spec: dict, palette_name: str) -> tuple[int, int, int, int]:
    return (*_rgb(spec["palette"][palette_name]), 255)


def _save_image(image: Image.Image, path: Path) -> None:
    image.save(path)


@pytest.fixture
def spec() -> dict:
    return json.loads(SPEC_PATH.read_text(encoding="utf-8"))


@pytest.fixture
def valid_sheet(tmp_path: Path, spec: dict) -> Path:
    sheet = spec["sheet"]
    image = Image.new("RGBA", (sheet["width"], sheet["height"]), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    sprite_color = (*_rgb(spec["palette"]["hairBlack"]), 255)

    draw.rectangle(_local_box(spec, "equipped"), fill=sprite_color)
    draw.rectangle(_local_box(spec, "base"), fill=sprite_color)

    guide_red = (*_rgb(spec["palette"]["guideRed"]), 255)
    for point in spec["anchorGuidePixels"]:
        _put_local(image, spec, "anchors", tuple(point), guide_red)

    palette_origin_x, palette_origin_y = _tile_origin(spec, "palette")
    for index, hex_color in enumerate(spec["palette"].values()):
        image.putpixel(
            (palette_origin_x + index, palette_origin_y),
            (*_rgb(hex_color), 255),
        )

    image_path = tmp_path / "character-sheet.png"
    image.save(image_path)
    return image_path


def _schema_v2_spec() -> dict:
    spec = json.loads(REFINEMENT_SPEC_PATH.read_text(encoding="utf-8"))
    spec["schemaVersion"] = 2
    spec["tileMap"] = [
        {"name": "equipped", "row": 0, "column": 0},
        {"name": "body-base", "row": 0, "column": 1},
        {"name": "default-hair-underwear", "row": 0, "column": 2},
        {"name": "anchors", "row": 0, "column": 3},
        {"name": "palette", "row": 0, "column": 4},
        {"name": "rear-hair-layer", "row": 1, "column": 0},
        {"name": "shoes-layer", "row": 1, "column": 1},
        {"name": "lower-layer", "row": 1, "column": 2},
        {"name": "upper-layer", "row": 1, "column": 3},
        {"name": "front-hair-layer", "row": 1, "column": 4},
        {"name": "head-gear-layer", "row": 1, "column": 5},
        {"name": "accessory-layer", "row": 1, "column": 6},
        {"name": "composite", "row": 1, "column": 7},
    ]
    spec["reservedTiles"] = [
        {"name": "reserved-transparent", "row": 0, "column": column, "requiredAlpha": 0}
        for column in (5, 6, 7)
    ]
    spec["guide"] = copy.deepcopy(spec["guide"])
    spec["guide"]["baseTile"] = "default-hair-underwear"
    spec["layerOrder"] = [
        "rear-hair-layer",
        "body-base",
        "shoes-layer",
        "lower-layer",
        "upper-layer",
        "front-hair-layer",
        "head-gear-layer",
        "accessory-layer",
    ]
    spec["layerBounds"] = {
        "rear-hair-layer": {"minY": 4, "maxY": 30, "inclusive": True},
        "shoes-layer": {"minY": 53, "maxY": 58, "inclusive": True},
        "lower-layer": {"minY": 39, "maxY": 52, "inclusive": True},
        "upper-layer": {"minY": 24, "maxY": 39, "inclusive": True},
        "front-hair-layer": {"minY": 4, "maxY": 30, "inclusive": True},
        "head-gear-layer": {"minY": 4, "maxY": 25, "inclusive": True},
        "accessory-layer": {"minY": 4, "maxY": 58, "inclusive": True},
    }
    spec["compositionContracts"] = {
        "alphaCompositing": "source-over",
        "default-hair-underwear": {
            "sources": ["rear-hair-layer", "body-base", "front-hair-layer"],
            "pixelExact": True,
        },
        "equipped": {"sourcesFrom": "layerOrder", "pixelExact": True},
        "composite": {"sourcesFrom": "layerOrder", "pixelExact": True},
        "geometry": {
            "tiles": [
                "equipped",
                "body-base",
                "default-hair-underwear",
                "composite",
            ],
            "commonAllowedBox": [20, 4, 44, 58],
            "sameBoundingBox": True,
            "sameCenterX": 32,
            "sameSoleY": 58,
            "excludedTiles": ["anchors", "palette"],
        },
    }
    return spec


@pytest.fixture
def modular_spec(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> dict:
    spec = _schema_v2_spec()
    spec_path = tmp_path / "schema-v2-spec.json"
    spec_path.write_text(json.dumps(spec, ensure_ascii=False), encoding="utf-8")
    monkeypatch.setitem(globals(), "MODULAR_SPEC_PATH", spec_path)
    return spec


@pytest.fixture
def valid_modular_sheet(tmp_path: Path, modular_spec: dict) -> Path:
    spec = modular_spec
    sheet = spec["sheet"]
    image = Image.new("RGBA", (sheet["width"], sheet["height"]), (0, 0, 0, 0))

    layers = {
        name: _new_local_tile(spec)
        for name in ["body-base", *spec["layerBounds"]]
    }
    allowed_box = tuple(spec["commonAllowedBox"])
    ImageDraw.Draw(layers["body-base"]).rectangle(
        allowed_box,
        fill=_rgba(spec, "skinLight"),
    )
    layer_rectangles = {
        "rear-hair-layer": ((20, 4, 44, 10), "hairHighlight"),
        "shoes-layer": ((26, 53, 38, 58), "goldAccent"),
        "lower-layer": ((24, 39, 40, 52), "underDark"),
        "upper-layer": ((22, 24, 42, 39), "bluePrimary"),
        "front-hair-layer": ((20, 4, 44, 12), "hairBlack"),
        "head-gear-layer": ((26, 4, 38, 6), "blueHighlight"),
        "accessory-layer": ((20, 32, 20, 44), "goldAccent"),
    }
    for name, (box, palette_name) in layer_rectangles.items():
        ImageDraw.Draw(layers[name]).rectangle(box, fill=_rgba(spec, palette_name))

    default_hair = _new_local_tile(spec)
    for name in spec["compositionContracts"]["default-hair-underwear"]["sources"]:
        default_hair = Image.alpha_composite(default_hair, layers[name])

    equipped = _new_local_tile(spec)
    for name in spec["layerOrder"]:
        equipped = Image.alpha_composite(equipped, layers[name])

    _paste_local(image, spec, "equipped", equipped)
    _paste_local(image, spec, "body-base", layers["body-base"])
    _paste_local(image, spec, "default-hair-underwear", default_hair)
    _paste_local(image, spec, "composite", equipped)
    for name in spec["layerBounds"]:
        _paste_local(image, spec, name, layers[name])

    anchors = default_hair.copy()
    guide_color = (*_rgb(spec["guide"]["overlayColor"]), 255)
    guide_points = [
        tuple(point)
        for line_type in ("verticalLines", "horizontalLines")
        for line in spec["guide"][line_type]
        for point in line["pixelCoordinates"]
    ]
    for point in guide_points:
        anchors.putpixel(point, guide_color)
    _paste_local(image, spec, spec["guide"]["tile"], anchors)

    palette_grid = spec["paletteGrid"]
    palette_tile = _new_local_tile(spec)
    origin_x, origin_y = palette_grid["origin"]
    stride_x = palette_grid["cellWidth"] + palette_grid["gap"]
    stride_y = palette_grid["cellHeight"] + palette_grid["gap"]
    palette_draw = ImageDraw.Draw(palette_tile)
    for index, palette_name in enumerate(palette_grid["colorOrder"]):
        column = index % palette_grid["columns"]
        row = index // palette_grid["columns"]
        left = origin_x + column * stride_x
        top = origin_y + row * stride_y
        palette_draw.rectangle(
            (
                left,
                top,
                left + palette_grid["cellWidth"] - 1,
                top + palette_grid["cellHeight"] - 1,
            ),
            fill=_rgba(spec, palette_name),
        )
    _paste_local(image, spec, "palette", palette_tile)

    image_path = tmp_path / "modular-character-sheet.png"
    image.save(image_path)
    return image_path


def _outlined_tile(
    spec: dict,
    points: set[tuple[int, int]],
    primary_palette_name: str,
    accent_palette_names: tuple[str, ...] = (),
) -> Image.Image:
    tile = _new_local_tile(spec)
    outline = _rgba(spec, "outlineDarkNavy")
    primary = _rgba(spec, primary_palette_name)
    interior_points: list[tuple[int, int]] = []
    for point in sorted(points, key=lambda item: (item[1], item[0])):
        x, y = point
        is_boundary = any(
            (x + delta_x, y + delta_y) not in points
            for delta_x, delta_y in ((-1, 0), (1, 0), (0, -1), (0, 1))
        )
        tile.putpixel(point, outline if is_boundary else primary)
        if not is_boundary:
            interior_points.append(point)

    for point, palette_name in zip(interior_points, accent_palette_names):
        tile.putpixel(point, _rgba(spec, palette_name))
    return tile


def _rectangle_points(left: int, top: int, right: int, bottom: int) -> set[tuple[int, int]]:
    return {
        (x, y)
        for y in range(top, bottom + 1)
        for x in range(left, right + 1)
    }


def _source_tile(image: Image.Image, row: int, column: int) -> Image.Image:
    return image.crop((column * 64, row * 64, column * 64 + 64, row * 64 + 64))


def _build_refinement_base_body(spec: dict, source_sheet: Image.Image) -> Image.Image:
    source_body = _source_tile(source_sheet, 0, 1)
    opaque_points = {
        (x, y)
        for y in range(source_body.height)
        for x in range(source_body.width)
        if source_body.getpixel((x, y))[3] != 0
    }
    base_body = _new_local_tile(spec)
    boundary_points: set[tuple[int, int]] = set()
    for point in opaque_points:
        x, y = point
        is_boundary = any(
            (x + delta_x, y + delta_y) not in opaque_points
            for delta_x, delta_y in ((-1, 0), (1, 0), (0, -1), (0, 1))
        )
        if is_boundary:
            boundary_points.add(point)
        base_body.putpixel(
            point,
            _rgba(spec, "outlineDarkNavy" if is_boundary else "skinLight"),
        )

    neutral_points = [
        point
        for point in sorted(opaque_points, key=lambda item: (item[1], item[0]))
        if point not in boundary_points and 36 <= point[1] <= 52
    ]
    for point, palette_name in zip(
        neutral_points,
        spec["externalLayers"]["base-body"]["neutralUnderwearPaletteNames"],
    ):
        base_body.putpixel(point, _rgba(spec, palette_name))

    protected_left, protected_top, protected_right, protected_bottom = spec[
        "faceProtectionContract"
    ]["protectedRegion"]
    for y in range(protected_top, protected_bottom + 1):
        for x in range(protected_left, protected_right + 1):
            base_body.putpixel((x, y), source_body.getpixel((x, y)))

    for feature_pixels in spec["faceProtectionContract"]["features"].values():
        for protected_pixel in feature_pixels:
            base_body.putpixel(
                tuple(protected_pixel["coordinate"]),
                tuple(protected_pixel["rgba"]),
            )
    return base_body


def _compose_refinement_sources(
    spec: dict,
    layers: dict[str, Image.Image],
    sources: list[str],
) -> Image.Image:
    result = _new_local_tile(spec)
    for source in sources:
        result = Image.alpha_composite(result, layers[source])
    return result


@pytest.fixture
def valid_refinement_fixture(tmp_path: Path) -> dict[str, object]:
    spec = json.loads(REFINEMENT_SPEC_PATH.read_text(encoding="utf-8"))
    with Image.open(BASE_BODY_PATH) as source:
        base_body = source.convert("RGBA")

    layers: dict[str, Image.Image] = {"base-body": base_body}

    hair_back_points = _rectangle_points(20, 4, 44, 30)
    hair_front_points = _rectangle_points(20, 4, 44, 18)
    layers["default-hair-back-layer"] = _outlined_tile(
        spec,
        hair_back_points,
        "hairBlack",
        ("hairHighlight",),
    )
    layers["default-hair-front-layer"] = _outlined_tile(
        spec,
        hair_front_points,
        "hairBlack",
        ("hairHighlight",),
    )
    layers["default-top-layer"] = _outlined_tile(
        spec,
        _rectangle_points(20, 24, 44, 39),
        "lightCream",
        ("underLight",),
    )

    default_bottom_points = _rectangle_points(23, 39, 41, 43)
    default_bottom_points.update(_rectangle_points(23, 44, 30, 46))
    default_bottom_points.update(_rectangle_points(34, 44, 41, 46))
    layers["default-bottom-layer"] = _outlined_tile(
        spec,
        default_bottom_points,
        "underDark",
        ("underMid",),
    )

    default_shoe_points = _rectangle_points(23, 53, 31, 58)
    default_shoe_points.update(_rectangle_points(33, 53, 41, 58))
    layers["default-shoes-layer"] = _outlined_tile(
        spec,
        default_shoe_points,
        "lightCream",
        ("underLight",),
    )

    adventure_shoe_points = {
        (x, y)
        for y in range(53, 59)
        for x in range(base_body.width)
        if base_body.getpixel((x, y))[3] != 0
    }
    layers["adventure-shoes-layer"] = _outlined_tile(
        spec,
        adventure_shoe_points,
        "blueShadow",
        (
            "goldAccent",
            "lightCream",
            "underLight",
            "hairHighlight",
            "underDark",
            "underMid",
        ),
    )
    for y in range(53, 55):
        for x in (*range(24, 32), *range(33, 41)):
            layers["adventure-shoes-layer"].putpixel(
                (x, y),
                _rgba(spec, "blueShadow"),
            )
    for x, palette_name in zip(
        range(24, 30),
        (
            "goldAccent",
            "lightCream",
            "underLight",
            "hairHighlight",
            "underDark",
            "underMid",
        ),
    ):
        layers["adventure-shoes-layer"].putpixel(
            (x, 55),
            _rgba(spec, palette_name),
        )

    adventure_bottom_points = _rectangle_points(24, 41, 40, 48)
    adventure_bottom_points.update(_rectangle_points(24, 49, 31, 54))
    adventure_bottom_points.update(_rectangle_points(33, 49, 40, 54))
    layers["adventure-bottom-layer"] = _outlined_tile(
        spec,
        adventure_bottom_points,
        "eyeDarkNavy",
        ("underDark",),
    )
    for y in range(41, 44):
        for x in range(24, 41):
            layers["adventure-bottom-layer"].putpixel(
                (x, y),
                _rgba(spec, "eyeDarkNavy"),
            )
    layers["adventure-bottom-layer"].putpixel(
        (25, 44),
        _rgba(spec, "underDark"),
    )

    protected_hands = {
        tuple(point) for point in spec["handProtectionContract"]["pixelCoordinates"]
    }
    neutral_colors = {
        _rgba(spec, palette_name)
        for palette_name in spec["externalLayers"]["base-body"][
            "neutralUnderwearPaletteNames"
        ]
    }
    adventure_top_points = {
        (x, y)
        for y in range(24, 44)
        for x in range(24, 41)
        if base_body.getpixel((x, y))[3] != 0
    }
    neutral_top_points = {
        (x, y)
        for y in range(29, 44)
        for x in range(base_body.width)
        if base_body.getpixel((x, y)) in neutral_colors
    }
    adventure_top_points.update(neutral_top_points)
    adventure_top_points.update(
        (64 - x, y)
        for x, y in neutral_top_points
        if base_body.getpixel((64 - x, y))[3] != 0
    )
    adventure_top_points.difference_update(protected_hands)
    layers["adventure-top-layer"] = _outlined_tile(
        spec,
        adventure_top_points,
        "bluePrimary",
        ("blueShadow", "blueHighlight", "lightCream"),
    )
    with Image.open(HEADGEAR_LAYER_PATH) as source:
        layers["head-gear-layer"] = source.convert("RGBA")
    with Image.open(ACCESSORY_LAYER_PATH) as source:
        layers["accessory-layer"] = source.convert("RGBA")

    contracts = spec["compositionContracts"]
    for target in ("default-outfit", "default-hair-preview", "equipped", "composite"):
        layers[target] = _compose_refinement_sources(
            spec,
            layers,
            contracts[target]["sources"],
        )

    anchors = layers["default-hair-preview"].copy()
    guide_color = (*_rgb(spec["guide"]["overlayColor"]), 255)
    for point in {
        tuple(raw_point)
        for line_type in ("verticalLines", "horizontalLines")
        for line in spec["guide"][line_type]
        for raw_point in line["pixelCoordinates"]
    }:
        anchors.putpixel(point, guide_color)
    layers["anchors"] = anchors

    palette_tile = _new_local_tile(spec)
    grid = spec["paletteGrid"]
    palette_draw = ImageDraw.Draw(palette_tile)
    for index, palette_name in enumerate(grid["colorOrder"]):
        column = index % grid["columns"]
        row = index // grid["columns"]
        left = grid["origin"][0] + column * (grid["cellWidth"] + grid["gap"])
        top = grid["origin"][1] + row * (grid["cellHeight"] + grid["gap"])
        palette_draw.rectangle(
            (
                left,
                top,
                left + grid["cellWidth"] - 1,
                top + grid["cellHeight"] - 1,
            ),
            fill=_rgba(spec, palette_name),
        )
    layers["palette"] = palette_tile

    image = Image.new(
        "RGBA",
        (spec["sheet"]["width"], spec["sheet"]["height"]),
        (0, 0, 0, 0),
    )
    for definition in spec["tileMap"]:
        _paste_local(image, spec, definition["name"], layers[definition["name"]])

    preserved_hashes = spec["targetedEditContract"]["preservedTileRgbaSha256"]
    for name in spec["targetedEditContract"]["preservedTiles"]:
        preserved_hashes[name] = hashlib.sha256(layers[name].tobytes()).hexdigest()

    image_path = tmp_path / "refinement-sheet.png"
    image.save(image_path)
    base_body_path = tmp_path / spec["externalLayers"]["base-body"]["path"]
    base_body.save(base_body_path)
    reference_name = spec["externalLayers"]["base-body"]["originalBodyBaseReference"][
        "capturedFromSheet"
    ]
    (tmp_path / reference_name).write_bytes(MODULAR_SOURCE_SHEET_PATH.read_bytes())
    spec_path = tmp_path / "refinement-spec.json"
    spec_path.write_text(json.dumps(spec, ensure_ascii=False), encoding="utf-8")
    return {
        "image": image_path,
        "spec": spec_path,
        "base_body": base_body_path,
        "data": spec,
    }


def _open_rgba(path: Path) -> Image.Image:
    with Image.open(path) as image:
        return image.copy()


def test_valid_sheet_passes(valid_sheet: Path) -> None:
    assert validate_sheet(valid_sheet, SPEC_PATH) == []


def test_wrong_size_fails(valid_sheet: Path) -> None:
    image = _open_rgba(valid_sheet)
    image.crop((0, 0, image.width - 1, image.height)).save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("size" in error for error in errors)


def test_wrong_mode_fails(valid_sheet: Path) -> None:
    _open_rgba(valid_sheet).convert("RGB").save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("mode" in error for error in errors)


def test_partial_alpha_fails(valid_sheet: Path) -> None:
    image = _open_rgba(valid_sheet)
    image.putpixel((10, 100), (17, 21, 28, 128))
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("alpha" in error for error in errors)


@pytest.mark.parametrize(
    ("color", "message"),
    [
        ((1, 2, 3, 255), "outside contract palette"),
        ((255, 0, 255, 255), "chroma key"),
    ],
)
def test_disallowed_color_fails(
    valid_sheet: Path,
    color: tuple[int, int, int, int],
    message: str,
) -> None:
    image = _open_rgba(valid_sheet)
    image.putpixel((150, 100), color)
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any(message in error for error in errors)


def test_equipped_and_base_bounding_boxes_must_match(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    base_x, base_y = _tile_origin(spec, "base")
    for y in range(4, 59):
        image.putpixel((base_x + 20, base_y + y), (0, 0, 0, 0))
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("bounding boxes must match" in error for error in errors)


def test_equipped_and_base_center_axis_must_match_contract(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    color = (*_rgb(spec["palette"]["hairBlack"]), 255)
    for tile_name in ("equipped", "base"):
        origin_x, origin_y = _tile_origin(spec, tile_name)
        for y in range(4, 59):
            image.putpixel((origin_x + 20, origin_y + y), (0, 0, 0, 0))
            image.putpixel((origin_x + 45, origin_y + y), color)
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("center axis" in error for error in errors)


def test_equipped_and_base_minimum_margin_is_enforced(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    color = (*_rgb(spec["palette"]["hairBlack"]), 255)
    for tile_name in ("equipped", "base"):
        _put_local(image, spec, tile_name, (2, 30), color)
        _put_local(image, spec, tile_name, (62, 30), color)
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("minimum margin" in error for error in errors)


def test_equipped_and_base_sole_y_is_enforced(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    for tile_name in ("equipped", "base"):
        origin_x, origin_y = _tile_origin(spec, tile_name)
        for x in range(20, 45):
            image.putpixel((origin_x + x, origin_y + 58), (0, 0, 0, 0))
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("sole" in error for error in errors)


def test_every_anchor_guide_pixel_must_be_guide_red(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    wrong_color = (*_rgb(spec["palette"]["bluePrimary"]), 255)
    _put_local(
        image,
        spec,
        "anchors",
        tuple(spec["anchorGuidePixels"][4]),
        wrong_color,
    )
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("anchor guide pixel" in error for error in errors)


def test_palette_tile_must_contain_all_contract_colors(
    valid_sheet: Path,
    spec: dict,
) -> None:
    image = _open_rgba(valid_sheet)
    palette_x, palette_y = _tile_origin(spec, "palette")
    replacement = (*_rgb(spec["palette"]["hairBlack"]), 255)
    image.putpixel((palette_x + 7, palette_y), replacement)
    image.save(valid_sheet)

    errors = validate_sheet(valid_sheet, SPEC_PATH)

    assert any("missing contract colors" in error for error in errors)


def test_cli_reports_success_to_stdout(valid_sheet: Path, capsys: pytest.CaptureFixture[str]) -> None:
    result = main(["--image", str(valid_sheet), "--spec", str(SPEC_PATH)])

    captured = capsys.readouterr()
    assert result == 0
    assert "passed" in captured.out
    assert captured.err == ""


def test_cli_reports_each_error_to_stderr(valid_sheet: Path, capsys: pytest.CaptureFixture[str]) -> None:
    image = _open_rgba(valid_sheet)
    image.putpixel((10, 100), (17, 21, 28, 128))
    image.save(valid_sheet)

    result = main(["--image", str(valid_sheet), "--spec", str(SPEC_PATH)])

    captured = capsys.readouterr()
    assert result == 1
    assert captured.out == ""
    assert "alpha" in captured.err


def test_valid_schema_v2_modular_sheet_passes(valid_modular_sheet: Path) -> None:
    assert validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH) == []


def test_schema_v2_wrong_size_fails(valid_modular_sheet: Path) -> None:
    image = _open_rgba(valid_modular_sheet)
    image.crop((0, 0, image.width - 1, image.height)).save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any("size" in error for error in errors)


def test_schema_v2_wrong_mode_fails(valid_modular_sheet: Path) -> None:
    _open_rgba(valid_modular_sheet).convert("RGB").save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any("mode" in error for error in errors)


@pytest.mark.parametrize(
    ("color", "message"),
    [
        ((1, 2, 3, 255), "outside contract palette"),
        ((17, 21, 28, 128), "alpha"),
        ((255, 0, 255, 255), "chroma key"),
    ],
)
def test_schema_v2_invalid_pixels_fail(
    valid_modular_sheet: Path,
    color: tuple[int, int, int, int],
    message: str,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    image.putpixel((500, 100), color)
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(message in error and "(500, 100)" in error for error in errors)


@pytest.mark.parametrize("reserved_index", [0, 1, 2])
def test_schema_v2_reserved_tiles_must_be_transparent(
    valid_modular_sheet: Path,
    modular_spec: dict,
    reserved_index: int,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    reserved = modular_spec["reservedTiles"][reserved_index]
    tile_width = modular_spec["logicalTile"]["width"]
    tile_height = modular_spec["logicalTile"]["height"]
    point = (
        reserved["column"] * tile_width + 7,
        reserved["row"] * tile_height + 9,
    )
    image.putpixel(point, _rgba(modular_spec, "hairBlack"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "reserved-transparent" in error
        and f"column={reserved['column']}" in error
        and "local (7, 9)" in error
        for error in errors
    )


def test_schema_v2_geometry_common_allowed_box_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, "equipped", (19, 30), _rgba(modular_spec, "hairBlack"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "equipped" in error and "commonAllowedBox" in error and "(19, 30)" in error
        for error in errors
    )


def test_schema_v2_geometry_center_axis_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    origin_x, origin_y = _tile_origin(modular_spec, "body-base")
    for y in range(4, 59):
        image.putpixel((origin_x + 20, origin_y + y), (0, 0, 0, 0))
        image.putpixel((origin_x + 45, origin_y + y), _rgba(modular_spec, "skinLight"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any("body-base" in error and "center axis" in error for error in errors)


def test_schema_v2_geometry_sole_y_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    origin_x, origin_y = _tile_origin(modular_spec, "default-hair-underwear")
    for x in range(20, 45):
        image.putpixel((origin_x + x, origin_y + 58), (0, 0, 0, 0))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "default-hair-underwear" in error and "sole" in error and "y=57" in error
        for error in errors
    )


def test_schema_v2_geometry_minimum_margin_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, "composite", (1, 1), _rgba(modular_spec, "hairBlack"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "composite" in error and "minimum margin" in error and "(1, 1)" in error
        for error in errors
    )


def test_schema_v2_default_hair_composition_must_be_pixel_exact(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(
        image,
        modular_spec,
        "default-hair-underwear",
        (21, 20),
        _rgba(modular_spec, "underDark"),
    )
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "default-hair-underwear" in error
        and "composition" in error
        and "local (21, 20)" in error
        for error in errors
    )


@pytest.mark.parametrize("target", ["equipped", "composite"])
def test_schema_v2_layer_order_composition_must_be_pixel_exact(
    valid_modular_sheet: Path,
    modular_spec: dict,
    target: str,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, target, (21, 20), _rgba(modular_spec, "underDark"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        target in error and "composition" in error and "local (21, 20)" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("layer_name", "point"),
    [
        ("rear-hair-layer", (21, 31)),
        ("shoes-layer", (21, 52)),
        ("lower-layer", (21, 38)),
        ("upper-layer", (21, 23)),
        ("front-hair-layer", (21, 31)),
        ("head-gear-layer", (21, 26)),
        ("accessory-layer", (21, 59)),
    ],
)
def test_schema_v2_isolated_layers_stay_inside_y_bounds(
    valid_modular_sheet: Path,
    modular_spec: dict,
    layer_name: str,
    point: tuple[int, int],
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, layer_name, point, _rgba(modular_spec, "hairBlack"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        layer_name in error and "layerBounds" in error and f"local {point}" in error
        for error in errors
    )


def test_schema_v2_required_guide_pixel_must_use_overlay_color(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    point = tuple(modular_spec["guide"]["verticalLines"][0]["pixelCoordinates"][0])
    _put_local(
        image,
        modular_spec,
        modular_spec["guide"]["tile"],
        point,
        _rgba(modular_spec, "bluePrimary"),
    )
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "anchors" in error and "guide" in error and f"local {point}" in error
        for error in errors
    )


def test_schema_v2_guide_pixels_must_not_be_copied_to_character_tiles(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    point = (32, 25)
    _put_local(
        image,
        modular_spec,
        "upper-layer",
        point,
        (*_rgb(modular_spec["guide"]["overlayColor"]), 255),
    )
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "upper-layer" in error
        and "guide" in error
        and "copied" in error
        and "local (32, 25)" in error
        for error in errors
    )


def test_schema_v2_palette_grid_origin_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    palette_name = modular_spec["paletteGrid"]["colorOrder"][0]
    _put_local(image, modular_spec, "palette", (8, 9), _rgba(modular_spec, palette_name))
    _put_local(image, modular_spec, "palette", (18, 9), (0, 0, 0, 0))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "palette" in error and "grid" in error and "local (8, 9)" in error
        for error in errors
    )


def test_schema_v2_palette_grid_cell_size_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, "palette", (18, 18), (0, 0, 0, 0))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "palette" in error and "cell" in error and "local (18, 18)" in error
        for error in errors
    )


def test_schema_v2_palette_grid_gap_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    _put_local(image, modular_spec, "palette", (19, 9), _rgba(modular_spec, "hairBlack"))
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "palette" in error and "grid gap" in error and "local (19, 9)" in error
        for error in errors
    )


def test_schema_v2_palette_grid_color_order_is_enforced(
    valid_modular_sheet: Path,
    modular_spec: dict,
) -> None:
    image = _open_rgba(valid_modular_sheet)
    grid = modular_spec["paletteGrid"]
    origin_x, origin_y = grid["origin"]
    draw = ImageDraw.Draw(image)
    palette_origin_x, palette_origin_y = _tile_origin(modular_spec, "palette")
    first = _rgba(modular_spec, grid["colorOrder"][0])
    second = _rgba(modular_spec, grid["colorOrder"][1])
    draw.rectangle(
        (
            palette_origin_x + origin_x,
            palette_origin_y + origin_y,
            palette_origin_x + origin_x + grid["cellWidth"] - 1,
            palette_origin_y + origin_y + grid["cellHeight"] - 1,
        ),
        fill=second,
    )
    second_left = origin_x + grid["cellWidth"] + grid["gap"]
    draw.rectangle(
        (
            palette_origin_x + second_left,
            palette_origin_y + origin_y,
            palette_origin_x + second_left + grid["cellWidth"] - 1,
            palette_origin_y + origin_y + grid["cellHeight"] - 1,
        ),
        fill=first,
    )
    image.save(valid_modular_sheet)

    errors = validate_sheet(valid_modular_sheet, MODULAR_SPEC_PATH)

    assert any(
        "palette" in error
        and "color order" in error
        and f"local ({origin_x}, {origin_y})" in error
        for error in errors
    )


def _refinement_image(fixture: dict[str, object]) -> Path:
    return Path(fixture["image"])


def _refinement_spec_path(fixture: dict[str, object]) -> Path:
    return Path(fixture["spec"])


def _refinement_base_body(fixture: dict[str, object]) -> Path:
    return Path(fixture["base_body"])


def _refinement_spec(fixture: dict[str, object]) -> dict:
    return fixture["data"]  # type: ignore[return-value]


def _save_refinement_spec(fixture: dict[str, object], spec: dict) -> None:
    _refinement_spec_path(fixture).write_text(
        json.dumps(spec, ensure_ascii=False),
        encoding="utf-8",
    )


def test_valid_schema_v3_refinement_sheet_with_external_base_body_passes(
    valid_refinement_fixture: dict[str, object],
) -> None:
    assert validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    ) == []


def test_valid_schema_v3_adventure_overlap_fixture_passes(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    top = _tile_image_for_test(image, spec, "adventure-top-layer")
    bottom = _tile_image_for_test(image, spec, "adventure-bottom-layer")
    shoes = _tile_image_for_test(image, spec, "adventure-shoes-layer")
    base_body = _open_rgba(_refinement_base_body(valid_refinement_fixture))

    assert all(
        top.getpixel((x, y))[3] == bottom.getpixel((x, y))[3] == 255
        for y in range(41, 44)
        for x in range(24, 41)
    )
    assert all(
        bottom.getpixel((x, y))[3] == shoes.getpixel((x, y))[3] == 255
        for y in range(53, 55)
        for x in (*range(24, 32), *range(33, 41))
    )
    assert all(bottom.getpixel((32, y))[3] == 0 for y in range(49, 55))
    assert all(
        shoes.getpixel((x, y))[3] == 255
        for y in range(53, 59)
        for x in range(64)
        if base_body.getpixel((x, y))[3] != 0
    )
    assert validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    ) == []


def test_schema_v3_missing_external_base_body_fails(
    valid_refinement_fixture: dict[str, object],
) -> None:
    _refinement_base_body(valid_refinement_fixture).unlink()

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any("base-body" in error and "could not load" in error for error in errors)


@pytest.mark.parametrize("invalid_kind", ["size", "mode"])
def test_schema_v3_external_base_body_size_and_mode_are_enforced(
    valid_refinement_fixture: dict[str, object],
    invalid_kind: str,
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    image = _open_rgba(path)
    if invalid_kind == "size":
        image = image.crop((0, 0, 63, 64))
    else:
        image = image.convert("RGB")
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any("base-body" in error and invalid_kind in error for error in errors)


def test_schema_v3_external_path_must_stay_below_spec_directory(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = copy.deepcopy(_refinement_spec(valid_refinement_fixture))
    spec["externalLayers"]["base-body"]["path"] = "../outside.png"
    _save_refinement_spec(valid_refinement_fixture, spec)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any("base-body" in error and "outside spec directory" in error for error in errors)


def test_schema_v3_external_base_body_palette_is_enforced(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((32, 40), (1, 2, 3, 255))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error and "local (32, 40)" in error and "palette" in error
        for error in errors
    )


def test_schema_v3_external_base_body_partial_alpha_is_enforced(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((32, 40), (*image.getpixel((32, 40))[:3], 128))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error and "local (32, 40)" in error and "alpha" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        ("allowed-box", "commonAllowedBox"),
        ("center", "center axis"),
        ("sole", "sole"),
    ],
)
def test_schema_v3_external_base_body_geometry_is_enforced(
    valid_refinement_fixture: dict[str, object],
    mutation: str,
    message: str,
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(path)
    if mutation == "allowed-box":
        image.putpixel((19, 30), _rgba(spec, "outlineDarkNavy"))
    elif mutation == "center":
        shifted = Image.new("RGBA", image.size, (0, 0, 0, 0))
        shifted.paste(image, (1, 0))
        image = shifted
    else:
        for x in range(image.width):
            image.putpixel((x, 58), (0, 0, 0, 0))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error and message in error and "local" in error
        for error in errors
    )


def test_schema_v3_external_base_body_alpha_silhouette_is_enforced(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((32, 40), (0, 0, 0, 0))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error
        and "alpha silhouette" in error
        and "local (32, 40)" in error
        for error in errors
    )


def test_schema_v3_validation_uses_embedded_original_body_contract(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    reference_name = spec["externalLayers"]["base-body"][
        "originalBodyBaseReference"
    ]["capturedFromSheet"]
    reference_path = _refinement_spec_path(valid_refinement_fixture).parent / reference_name
    reference_path.unlink()

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert errors == []


def test_schema_v3_external_base_body_face_pixels_are_protected(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((25, 20), _rgba(spec, "skinLight"))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error and "left-eye" in error and "local (25, 20)" in error
        for error in errors
    )


def test_schema_v3_external_base_body_neutral_palette_is_enforced(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((32, 45), _rgba(spec, "bluePrimary"))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "base-body" in error and "neutral" in error and "local (32, 45)" in error
        for error in errors
    )


def test_schema_v3_neutral_palette_does_not_reject_preserved_head_pixels(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_base_body(valid_refinement_fixture)
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(path)
    image.putpixel((28, 29), _rgba(spec, "hairHighlight"))
    image.save(path)

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert not any("neutral/anatomy palette" in error for error in errors)


def test_schema_v3_empty_tile_map_position_fails(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _paste_local(image, spec, "default-top-layer", _new_local_tile(spec))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-top-layer" in error and "no opaque pixels" in error
        for error in errors
    )


def test_schema_v3_tile_pixels_cannot_be_swapped_between_positions(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    top = _tile_image_for_test(image, spec, "default-top-layer")
    bottom = _tile_image_for_test(image, spec, "default-bottom-layer")
    _paste_local(image, spec, "default-top-layer", bottom)
    _paste_local(image, spec, "default-bottom-layer", top)
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any("default-top-layer" in error and "local" in error for error in errors)


@pytest.mark.parametrize(
    "target",
    ["default-outfit", "default-hair-preview", "anchors", "equipped", "composite"],
)
def test_schema_v3_compositions_are_pixel_exact(
    valid_refinement_fixture: dict[str, object],
    target: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, target, (31, 35), _rgba(spec, "guideRed"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(target in error and "local (31, 35)" in error for error in errors)


def test_schema_v3_equipped_and_composite_must_be_equal(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "equipped", (31, 35), _rgba(spec, "guideRed"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipped" in error and "composite" in error and "local (31, 35)" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("layer_name", "point"),
    [
        ("default-hair-back-layer", (20, 31)),
        ("default-top-layer", (20, 40)),
        ("default-bottom-layer", (23, 38)),
        ("default-shoes-layer", (23, 52)),
        ("adventure-top-layer", (32, 44)),
        ("adventure-bottom-layer", (32, 40)),
        ("adventure-shoes-layer", (23, 52)),
    ],
)
def test_schema_v3_layers_stay_inside_bounds_and_seams(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
    point: tuple[int, int],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, layer_name, point, _rgba(spec, "outlineDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(layer_name in error and "local" in error for error in errors)


@pytest.mark.parametrize(
    ("layer_name", "row", "message"),
    [
        ("default-top-layer", 39, "hem"),
        ("default-bottom-layer", 39, "waist seam"),
        ("default-shoes-layer", 53, "ankle seam"),
        ("adventure-top-layer", 43, "hem"),
        ("adventure-bottom-layer", 41, "waist"),
        ("adventure-shoes-layer", 53, "ankle seam"),
    ],
)
def test_schema_v3_required_layer_seams_are_enforced(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
    row: int,
    message: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    origin_x, origin_y = _tile_origin(spec, layer_name)
    for x in range(64):
        image.putpixel((origin_x + x, origin_y + row), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        layer_name in error and message in error and "local" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("point", "message"),
    [
        ((32, 20), "central face open"),
        ((25, 19), "eye neighborhood"),
        ((31, 26), "nose"),
        ((32, 27), "mouth"),
    ],
)
def test_schema_v3_default_front_hair_respects_face_exclusions(
    valid_refinement_fixture: dict[str, object],
    point: tuple[int, int],
    message: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(
        image,
        spec,
        "default-hair-front-layer",
        point,
        _rgba(spec, "outlineDarkNavy"),
    )
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-hair-front-layer" in error
        and message in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_default_hair_alpha_silhouette_is_mirrored(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "default-hair-back-layer", (20, 20), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-hair" in error
        and "alpha silhouette" in error
        and "local (20, 20)" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("layer_name", "end_y"),
    [("default-top-layer", 39), ("adventure-top-layer", 43)],
)
def test_schema_v3_left_and_right_sleeves_have_matching_bounds(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
    end_y: int,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    origin_x, origin_y = _tile_origin(spec, layer_name)
    for x in range(20, 32):
        image.putpixel((origin_x + x, origin_y + end_y), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        layer_name in error and "sleeve" in error and "local" in error
        for error in errors
    )


def test_schema_v3_default_shorts_hems_end_at_same_y(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    origin_x, origin_y = _tile_origin(spec, "default-bottom-layer")
    for x in range(20, 32):
        image.putpixel((origin_x + x, origin_y + 46), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-bottom-layer" in error and "shorts hem" in error and "local" in error
        for error in errors
    )


def test_schema_v3_default_shorts_hem_y_is_enforced(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    origin_x, origin_y = _tile_origin(spec, "default-bottom-layer")
    for x in range(64):
        image.putpixel((origin_x + x, origin_y + 46), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-bottom-layer" in error and "hem y=46" in error and "local" in error
        for error in errors
    )


@pytest.mark.parametrize("layer_name", ["default-shoes-layer", "adventure-shoes-layer"])
def test_schema_v3_left_and_right_shoes_have_matching_boxes_and_soles(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    origin_x, origin_y = _tile_origin(spec, layer_name)
    for x in range(0, 32):
        image.putpixel((origin_x + x, origin_y + 58), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        layer_name in error and "shoe" in error and "local" in error
        for error in errors
    )


def test_schema_v3_previous_y42_y43_no_overlap_fixture_fails_waist_overlap(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    for x in range(24, 41):
        _put_local(image, spec, "adventure-top-layer", (x, 43), (0, 0, 0, 0))
        for y in range(41, 43):
            _put_local(image, spec, "adventure-bottom-layer", (x, y), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "layerBoundaryContracts.adventureWaistOverlap" in error
        and "adventure-top-layer" in error
        and "adventure-bottom-layer" in error
        and "local (24, 41)" in error
        for error in errors
    )


@pytest.mark.parametrize("layer_name", ["adventure-top-layer", "adventure-bottom-layer"])
def test_schema_v3_waist_overlap_requires_every_shared_pixel(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (30, 42)
    _put_local(image, spec, layer_name, point, (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "layerBoundaryContracts.adventureWaistOverlap" in error
        and layer_name in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_waist_overlap_forbids_shared_pixels_outside_region(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (25, 40)
    _put_local(image, spec, "adventure-bottom-layer", point, _rgba(spec, "eyeDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "layerBoundaryContracts.adventureWaistOverlap" in error
        and "adventure-top-layer" in error
        and "adventure-bottom-layer" in error
        and "outside" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize("layer_name", ["adventure-bottom-layer", "adventure-shoes-layer"])
def test_schema_v3_ankle_overlap_requires_every_shared_pixel(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (25, 53)
    _put_local(image, spec, layer_name, point, (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "layerBoundaryContracts.adventureAnkleOverlap" in error
        and layer_name in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_ankle_overlap_forbids_shared_pixels_outside_region(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (25, 52)
    _put_local(image, spec, "adventure-shoes-layer", point, _rgba(spec, "blueShadow"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "layerBoundaryContracts.adventureAnkleOverlap" in error
        and "adventure-bottom-layer" in error
        and "adventure-shoes-layer" in error
        and "outside" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("point", "replacement", "contract_part"),
    [
        ((32, 47), (0, 0, 0, 0), "requiredOpaqueRegions"),
        ((32, 49), "eyeDarkNavy", "requiredTransparentRegion"),
        ((24, 49), (0, 0, 0, 0), "requiredOpaqueRegions"),
    ],
)
def test_schema_v3_adventure_bottom_matches_required_base_body_mask(
    valid_refinement_fixture: dict[str, object],
    point: tuple[int, int],
    replacement: tuple[int, int, int, int] | str,
    contract_part: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    color = _rgba(spec, replacement) if isinstance(replacement, str) else replacement
    _put_local(image, spec, "adventure-bottom-layer", point, color)
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipmentMappingContracts.adventure-bottom-layer" in error
        and contract_part in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_adventure_shoes_cover_every_base_body_foot_pixel(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (23, 55)
    _put_local(image, spec, "adventure-shoes-layer", point, (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipmentMappingContracts.adventure-shoes-layer" in error
        and "coverAllBaseBodyFootAlpha" in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_adventure_shoes_require_sole_y_58(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    for x in range(64):
        _put_local(image, spec, "adventure-shoes-layer", (x, 58), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipmentMappingContracts.adventure-shoes-layer" in error
        and "soleY=58" in error
        and "local (23, 58)" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("point", "contract_part"),
    [
        ((20, 30), "primaryMappingYRange"),
        ((20, 24), "collarShoulderPreservation"),
    ],
)
def test_schema_v3_adventure_top_respects_base_body_mapping(
    valid_refinement_fixture: dict[str, object],
    point: tuple[int, int],
    contract_part: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "adventure-top-layer", point, _rgba(spec, "outlineDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipmentMappingContracts.adventure-top-layer" in error
        and contract_part in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("contract_name", "layer_name", "point"),
    [
        ("adventureWaistOverlap", "adventure-bottom-layer", (24, 41)),
        ("adventureAnkleOverlap", "adventure-shoes-layer", (24, 53)),
    ],
)
def test_schema_v3_hidden_overlap_cannot_contain_outline_dark_navy(
    valid_refinement_fixture: dict[str, object],
    contract_name: str,
    layer_name: str,
    point: tuple[int, int],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, layer_name, point, _rgba(spec, "outlineDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        f"layerBoundaryContracts.{contract_name}" in error
        and layer_name in error
        and "outlineDarkNavy" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("layer_name", "row", "x_ranges", "point"),
    [
        ("adventure-top-layer", 42, ((24, 40),), (24, 42)),
        ("adventure-bottom-layer", 53, ((24, 31), (33, 40)), (24, 53)),
    ],
)
def test_schema_v3_adjacent_complete_outline_rows_fail_two_pixel_outline_contract(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
    row: int,
    x_ranges: tuple[tuple[int, int], ...],
    point: tuple[int, int],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    for start_x, end_x in x_ranges:
        for x in range(start_x, end_x + 1):
            _put_local(image, spec, layer_name, (x, row), _rgba(spec, "outlineDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        layer_name in error
        and "2-pixel horizontal outline" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("layer_name", "point"),
    [
        ("adventure-top-layer", (27, 30)),
        ("adventure-bottom-layer", (32, 47)),
        ("adventure-shoes-layer", (23, 55)),
    ],
)
def test_schema_v3_equipment_union_cannot_expose_base_body_outfit_pixels(
    valid_refinement_fixture: dict[str, object],
    layer_name: str,
    point: tuple[int, int],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, layer_name, point, (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "equipmentMappingContracts" in error
        and "equipment union exposes base-body" in error
        and layer_name in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_adventure_top_rejects_bottom_only_palette_semantics(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (32, 35)
    _put_local(image, spec, "adventure-top-layer", point, _rgba(spec, "eyeDarkNavy"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "designContracts.adventure-top" in error
        and "adventure-top-layer" in error
        and "bottom-only palette" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    "palette_name",
    [
        "bluePrimary",
        "blueShadow",
        "blueHighlight",
        "tealAccent",
        "lightCream",
        "skinShadow",
        "skinLight",
        "hairBlack",
    ],
)
def test_schema_v3_adventure_bottom_rejects_forbidden_palette_colors(
    valid_refinement_fixture: dict[str, object],
    palette_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (30, 48)
    _put_local(image, spec, "adventure-bottom-layer", point, _rgba(spec, palette_name))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "designContracts.adventure-bottom" in error
        and "adventure-bottom-layer" in error
        and "allowed palette" in error
        and palette_name in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_adventure_top_cannot_cover_a_protected_hand_pixel(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (24, 39)
    _put_local(image, spec, "adventure-top-layer", point, _rgba(spec, "bluePrimary"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "handProtectionContract" in error
        and "adventure-top-layer" in error
        and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_completed_composites_preserve_base_body_hand_rgba(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    point = (24, 39)
    for target in ("equipped", "composite"):
        _put_local(image, spec, target, point, _rgba(spec, "bluePrimary"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "handProtectionContract" in error
        and "equipped" in error
        and "base-body" in error
        and f"local {point}" in error
        for error in errors
    )


@pytest.mark.parametrize(
    "tile_name",
    [
        "default-outfit",
        "default-hair-preview",
        "anchors",
        "palette",
        "default-top-layer",
        "default-bottom-layer",
        "default-shoes-layer",
        "default-hair-back-layer",
        "default-hair-front-layer",
        "head-gear-layer",
        "accessory-layer",
    ],
)
def test_schema_v3_targeted_edit_contract_preserves_all_eleven_other_tiles(
    valid_refinement_fixture: dict[str, object],
    tile_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    tile = _tile_image_for_test(image, spec, tile_name)
    point = next(
        (x, y)
        for y in range(tile.height)
        for x in range(tile.width)
        if tile.getpixel((x, y))[3]
    )
    actual = tile.getpixel(point)
    replacement = _rgba(spec, "guideRed")
    if actual == replacement:
        replacement = _rgba(spec, "hairBlack")
    _put_local(image, spec, tile_name, point, replacement)
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "targetedEditContract" in error
        and tile_name in error
        and "preserved tile" in error
        and "local" in error
        for error in errors
    )


def test_schema_v3_new_optional_contract_fields_can_be_absent(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = copy.deepcopy(_refinement_spec(valid_refinement_fixture))
    spec.pop("handProtectionContract")
    spec.pop("targetedEditContract")
    spec.pop("equipmentMappingContracts")
    spec["layerBoundaryContracts"].pop("adventureWaistOverlap")
    spec["layerBoundaryContracts"].pop("adventureAnkleOverlap")
    spec["designContracts"].pop("adventure-shoes")
    for name in ("adventure-top", "adventure-bottom"):
        contract = spec["designContracts"][name]
        contract.pop("allowedPaletteNames")
        contract.pop("allOtherPaletteNamesForbidden", None)
        contract.pop("forbiddenPaletteNames", None)
    _save_refinement_spec(valid_refinement_fixture, spec)

    assert validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    ) == []


@pytest.mark.parametrize(
    ("contract_name", "palette_name"),
    [
        ("default-hair", "hairBlack"),
        ("default-top", "lightCream"),
        ("default-bottom", "underDark"),
        ("default-shoes", "lightCream"),
        ("adventure-top", "bluePrimary"),
        ("adventure-bottom", "eyeDarkNavy"),
        ("adventure-shoes", "blueShadow"),
    ],
)
def test_schema_v3_design_layers_contain_required_palette_colors(
    valid_refinement_fixture: dict[str, object],
    contract_name: str,
    palette_name: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    contract = spec["designContracts"][contract_name]
    layer_names = contract["layers"] if "layers" in contract else [contract["layer"]]
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    forbidden_color = _rgba(spec, palette_name)
    replacement = _rgba(spec, "skinLight")
    for layer_name in layer_names:
        tile = _tile_image_for_test(image, spec, layer_name)
        for y in range(tile.height):
            for x in range(tile.width):
                if tile.getpixel((x, y)) == forbidden_color:
                    _put_local(image, spec, layer_name, (x, y), replacement)
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        contract_name in error and palette_name in error and "local" in error
        for error in errors
    )


def test_schema_v3_adventure_bottom_must_be_darker_than_top(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    top = _tile_image_for_test(image, spec, "adventure-top-layer")
    required = [
        name
        for name in spec["designContracts"]["adventure-top"]["requiredPaletteNames"]
        if name != "outlineDarkNavy"
    ]
    interior_points = [
        (x, y)
        for y in range(top.height)
        for x in range(top.width)
        if top.getpixel((x, y))[3]
        and top.getpixel((x, y)) != _rgba(spec, "outlineDarkNavy")
    ]
    for point in interior_points:
        _put_local(image, spec, "adventure-top-layer", point, _rgba(spec, "hairBlack"))
    for point, palette_name in zip(interior_points, required):
        _put_local(image, spec, "adventure-top-layer", point, _rgba(spec, palette_name))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "adventure-bottom" in error and "darker" in error and "local" in error
        for error in errors
    )


def test_schema_v3_external_silhouette_requires_dark_navy_outline(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "default-top-layer", (20, 24), _rgba(spec, "lightCream"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-top-layer" in error
        and "external silhouette" in error
        and "local (20, 24)" in error
        for error in errors
    )


def test_schema_v3_isolated_opaque_pixel_fails_connectivity_contract(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(
        image,
        spec,
        "default-bottom-layer",
        (20, 45),
        _rgba(spec, "outlineDarkNavy"),
    )
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "default-bottom-layer" in error
        and "connected component" in error
        and "local (20, 45)" in error
        for error in errors
    )


@pytest.mark.parametrize(
    ("color", "message"),
    [
        ((1, 2, 3, 255), "palette"),
        ((17, 21, 28, 128), "alpha"),
        ((255, 0, 255, 255), "chroma key"),
    ],
)
def test_schema_v3_pixel_palette_alpha_and_chroma_key_validation_continues(
    valid_refinement_fixture: dict[str, object],
    color: tuple[int, int, int, int],
    message: str,
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "accessory-layer", (63, 63), color)
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "accessory-layer" in error
        and "local (63, 63)" in error
        and message in error
        for error in errors
    )


def test_schema_v3_palette_grid_still_requires_all_sixteen_colors(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    grid = spec["paletteGrid"]
    _put_local(image, spec, "palette", tuple(grid["origin"]), (0, 0, 0, 0))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "palette" in error and "cell 0" in error and "local" in error
        for error in errors
    )


def test_schema_v3_guides_still_require_exact_overlay_pixels(
    valid_refinement_fixture: dict[str, object],
) -> None:
    spec = _refinement_spec(valid_refinement_fixture)
    point = tuple(spec["guide"]["verticalLines"][0]["pixelCoordinates"][0])
    image = _open_rgba(_refinement_image(valid_refinement_fixture))
    _put_local(image, spec, "anchors", point, _rgba(spec, "bluePrimary"))
    image.save(_refinement_image(valid_refinement_fixture))

    errors = validate_sheet(
        _refinement_image(valid_refinement_fixture),
        _refinement_spec_path(valid_refinement_fixture),
    )

    assert any(
        "anchors" in error and "guide" in error and f"local {point}" in error
        for error in errors
    )


def test_schema_v3_sheet_size_and_geometry_validation_continues(
    valid_refinement_fixture: dict[str, object],
) -> None:
    path = _refinement_image(valid_refinement_fixture)
    image = _open_rgba(path)
    image.crop((0, 0, image.width - 1, image.height)).save(path)

    errors = validate_sheet(path, _refinement_spec_path(valid_refinement_fixture))

    assert any("sheet size" in error for error in errors)
