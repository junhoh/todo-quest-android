"""Validate the Todo Quest character design sheet against its pixel contract."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import sys
from collections.abc import Sequence

from PIL import Image


SCRIPTS_DIR = pathlib.Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPTS_DIR.parent
DEFAULT_IMAGE_PATH = (
    REPOSITORY_ROOT
    / "docs"
    / "art"
    / "character"
    / "todo-quest-character-modular-sheet.png"
)
DEFAULT_SPEC_PATH = (
    REPOSITORY_ROOT
    / "docs"
    / "art"
    / "character"
    / "character-modular-sheet-spec.json"
)


Rgb = tuple[int, int, int]
Rgba = tuple[int, int, int, int]
BoundingBox = tuple[int, int, int, int]
ImageMap = dict[str, Image.Image]


def load_spec(path: pathlib.Path) -> dict:
    """Load a character sheet contract from a UTF-8 JSON file."""
    spec = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(spec, dict):
        raise ValueError("character sheet specification must be a JSON object")
    return spec


def _hex_to_rgb(value: str) -> Rgb:
    normalized = value.removeprefix("#")
    if len(normalized) != 6:
        raise ValueError(f"invalid RGB color: {value}")
    return tuple(int(normalized[index:index + 2], 16) for index in (0, 2, 4))


def _format_rgb(color: Rgb) -> str:
    return "#" + "".join(f"{component:02X}" for component in color)


def _format_rgba(color: Rgba) -> str:
    return f"{_format_rgb(color[:3])} alpha={color[3]}"


def _tile_definition(spec: dict, name: str) -> dict:
    try:
        return next(tile for tile in spec["tileMap"] if tile["name"] == name)
    except StopIteration as error:
        raise ValueError(f"tileMap does not define the {name!r} tile") from error


def _tile_image(image: Image.Image, spec: dict, name: str) -> Image.Image:
    tile = _tile_definition(spec, name)
    width = spec["logicalTile"]["width"]
    height = spec["logicalTile"]["height"]
    left = tile["column"] * width
    top = tile["row"] * height
    return image.crop((left, top, left + width, top + height))


def _tile_image_at(
    image: Image.Image,
    spec: dict,
    row: int,
    column: int,
) -> Image.Image:
    width = spec["logicalTile"]["width"]
    height = spec["logicalTile"]["height"]
    left = column * width
    top = row * height
    return image.crop((left, top, left + width, top + height))


def _safe_spec_relative_path(
    spec_path: pathlib.Path,
    raw_path: str,
    owner_name: str,
) -> pathlib.Path:
    relative_path = pathlib.Path(raw_path)
    if relative_path.is_absolute():
        raise ValueError(
            f"{owner_name} path must be relative to the spec directory: {raw_path}"
        )
    base_directory = spec_path.resolve().parent
    resolved = (base_directory / relative_path).resolve()
    try:
        resolved.relative_to(base_directory)
    except ValueError as error:
        raise ValueError(
            f"{owner_name} path resolves outside spec directory: {raw_path}"
        ) from error
    return resolved


def _load_rgba_contract_image(
    path: pathlib.Path,
    name: str,
    expected_size: tuple[int, int],
    expected_mode: str,
) -> tuple[Image.Image | None, list[str]]:
    try:
        with Image.open(path) as image:
            image.load()
            loaded = image.copy()
    except OSError as error:
        return None, [f"{name} external layer could not load {path}: {error}"]

    errors: list[str] = []
    if loaded.size != expected_size:
        errors.append(
            f"{name} external layer size must be {expected_size}; got {loaded.size}"
        )
    if loaded.mode != expected_mode:
        errors.append(
            f"{name} external layer mode must be {expected_mode}; got {loaded.mode}"
        )
    if errors:
        return None, errors
    return loaded, []


def _load_external_layers(
    spec_path: pathlib.Path,
    spec: dict,
) -> tuple[ImageMap, list[str]]:
    external_layers: ImageMap = {}
    errors: list[str] = []
    for name, contract in spec.get("externalLayers", {}).items():
        try:
            path = _safe_spec_relative_path(spec_path, contract["path"], name)
        except (KeyError, TypeError, ValueError) as error:
            errors.append(str(error))
            continue
        image, load_errors = _load_rgba_contract_image(
            path,
            name,
            (contract["width"], contract["height"]),
            contract["mode"],
        )
        errors.extend(load_errors)
        if image is not None:
            external_layers[name] = image
    return external_layers, errors


def _load_original_body_reference(
    spec_path: pathlib.Path,
    spec: dict,
) -> tuple[Image.Image | None, Image.Image | None, list[str]]:
    contract = spec["externalLayers"]["base-body"]["originalBodyBaseReference"]
    if (
        contract.get("validationMode") == "embedded-contract"
        and not contract.get("capturedFromSheetRequiredAtValidation", False)
    ):
        return None, None, []
    try:
        path = _safe_spec_relative_path(
            spec_path,
            contract["capturedFromSheet"],
            "base-body original reference",
        )
    except (KeyError, TypeError, ValueError) as error:
        return None, None, [str(error)]
    try:
        source_bytes = path.read_bytes()
        with Image.open(path) as source:
            source.load()
            reference_sheet = source.copy()
            tile = _tile_image_at(
                reference_sheet,
                spec,
                contract["tile"]["row"],
                contract["tile"]["column"],
            )
    except OSError as error:
        return None, None, [
            f"base-body original reference could not load {path}: {error}"
        ]

    expected_sheet_hash = contract.get("capturedFromSheetSha256")
    actual_sheet_hash = hashlib.sha256(source_bytes).hexdigest()
    if expected_sheet_hash and actual_sheet_hash != expected_sheet_hash:
        return None, None, [
            "base-body original reference SHA-256 differs from contract; "
            f"got {actual_sheet_hash}"
        ]
    return tile, reference_sheet, []


def _opaque_bounding_box(tile: Image.Image) -> BoundingBox | None:
    pillow_box = tile.getchannel("A").getbbox()
    if pillow_box is None:
        return None
    left, top, right_exclusive, bottom_exclusive = pillow_box
    return left, top, right_exclusive - 1, bottom_exclusive - 1


def _first_point(index: int, width: int) -> tuple[int, int]:
    return index % width, index // width


def _tile_point_context(spec: dict, point: tuple[int, int]) -> str:
    tile_width = spec["logicalTile"]["width"]
    tile_height = spec["logicalTile"]["height"]
    column = point[0] // tile_width
    row = point[1] // tile_height
    local = point[0] % tile_width, point[1] % tile_height
    definitions = [*spec["tileMap"], *spec.get("reservedTiles", [])]
    tile = next(
        (
            item
            for item in definitions
            if item["row"] == row and item["column"] == column
        ),
        None,
    )
    name = tile["name"] if tile is not None else "undefined"
    return (
        f"{name} tile (row={row}, column={column}) at local {local}, "
        f"global {point}"
    )


def _first_opaque_point_outside_y(
    tile: Image.Image,
    minimum_y: int,
    maximum_y: int,
    inclusive: bool,
) -> tuple[int, int] | None:
    for index, pixel in enumerate(tile.get_flattened_data()):
        if pixel[3] == 0:
            continue
        point = _first_point(index, tile.width)
        y = point[1]
        inside = minimum_y <= y <= maximum_y if inclusive else minimum_y < y < maximum_y
        if not inside:
            return point
    return None


def _first_pixel_difference(
    expected: Image.Image,
    actual: Image.Image,
    ignored_points: set[tuple[int, int]] | None = None,
) -> tuple[tuple[int, int], Rgba, Rgba] | None:
    ignored = ignored_points or set()
    for index, (expected_pixel, actual_pixel) in enumerate(
        zip(expected.get_flattened_data(), actual.get_flattened_data())
    ):
        point = _first_point(index, expected.width)
        if point not in ignored and expected_pixel != actual_pixel:
            return point, expected_pixel, actual_pixel
    return None


def _validate_pixels(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    palette = {_hex_to_rgb(value) for value in spec["palette"].values()}
    chroma_key = _hex_to_rgb(spec["chromaKey"])
    count_transparent = spec.get("transparentPixelsCountTowardPalette", False)
    chroma_key_allowed = spec.get("chromaKeyAllowedInFinalPng", False)

    partial_alpha_count = 0
    first_partial_alpha: tuple[tuple[int, int], int] | None = None
    outside_palette_count = 0
    first_outside_palette: tuple[tuple[int, int], Rgb] | None = None
    chroma_key_count = 0
    first_chroma_key: tuple[int, int] | None = None

    for index, pixel in enumerate(image.get_flattened_data()):
        red, green, blue, alpha = pixel
        point = _first_point(index, image.width)
        rgb = red, green, blue

        if alpha not in (0, 255):
            partial_alpha_count += 1
            if first_partial_alpha is None:
                first_partial_alpha = point, alpha

        if not chroma_key_allowed and rgb == chroma_key:
            chroma_key_count += 1
            if first_chroma_key is None:
                first_chroma_key = point

        if (alpha != 0 or count_transparent) and rgb not in palette:
            outside_palette_count += 1
            if first_outside_palette is None:
                first_outside_palette = point, rgb

    if first_partial_alpha is not None:
        point, alpha = first_partial_alpha
        errors.append(
            "pixel alpha must be 0 or 255; "
            f"found {partial_alpha_count} invalid pixel(s), first at {point} with alpha={alpha}"
        )
    if first_outside_palette is not None:
        point, color = first_outside_palette
        errors.append(
            f"pixel color {_format_rgb(color)} at {point} is outside contract palette "
            f"({outside_palette_count} invalid pixel(s))"
        )
    if first_chroma_key is not None:
        errors.append(
            f"chroma key {_format_rgb(chroma_key)} remains at {first_chroma_key} "
            f"({chroma_key_count} pixel(s))"
        )

    return errors


def _validate_modular_pixels(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    palette = {_hex_to_rgb(value) for value in spec["palette"].values()}
    chroma_key = _hex_to_rgb(spec["chromaKey"])
    allowed_alpha_values = set(spec["allowedAlphaValues"])
    count_transparent = spec.get("transparentPixelsCountTowardPalette", False)
    chroma_key_allowed = spec.get("chromaKeyAllowedInFinalPng", False)

    partial_alpha_count = 0
    first_partial_alpha: tuple[tuple[int, int], int] | None = None
    outside_palette_count = 0
    first_outside_palette: tuple[tuple[int, int], Rgb] | None = None
    chroma_key_count = 0
    first_chroma_key: tuple[int, int] | None = None

    for index, pixel in enumerate(image.get_flattened_data()):
        red, green, blue, alpha = pixel
        point = _first_point(index, image.width)
        rgb = red, green, blue

        if alpha not in allowed_alpha_values:
            partial_alpha_count += 1
            if first_partial_alpha is None:
                first_partial_alpha = point, alpha

        if not chroma_key_allowed and rgb == chroma_key:
            chroma_key_count += 1
            if first_chroma_key is None:
                first_chroma_key = point

        if (alpha != 0 or count_transparent) and rgb not in palette:
            outside_palette_count += 1
            if first_outside_palette is None:
                first_outside_palette = point, rgb

    if first_partial_alpha is not None:
        point, alpha = first_partial_alpha
        errors.append(
            f"{_tile_point_context(spec, point)} has alpha={alpha}; "
            f"allowed alpha values are {sorted(allowed_alpha_values)} "
            f"({partial_alpha_count} invalid pixel(s))"
        )
    if first_outside_palette is not None:
        point, color = first_outside_palette
        errors.append(
            f"{_tile_point_context(spec, point)} has pixel color {_format_rgb(color)} "
            f"outside contract palette ({outside_palette_count} invalid pixel(s))"
        )
    if first_chroma_key is not None:
        errors.append(
            f"{_tile_point_context(spec, first_chroma_key)} retains chroma key "
            f"{_format_rgb(chroma_key)} ({chroma_key_count} pixel(s))"
        )

    return errors


def _validate_named_pixels(image: Image.Image, spec: dict, name: str) -> list[str]:
    errors: list[str] = []
    palette = {_hex_to_rgb(value) for value in spec["palette"].values()}
    chroma_key = _hex_to_rgb(spec["chromaKey"])
    allowed_alpha_values = set(spec["allowedAlphaValues"])
    count_transparent = spec.get("transparentPixelsCountTowardPalette", False)
    chroma_key_allowed = spec.get("chromaKeyAllowedInFinalPng", False)

    for index, pixel in enumerate(image.get_flattened_data()):
        point = _first_point(index, image.width)
        rgb = pixel[:3]
        alpha = pixel[3]
        if alpha not in allowed_alpha_values:
            errors.append(
                f"{name} external layer at local {point} has alpha={alpha}; "
                f"allowed alpha values are {sorted(allowed_alpha_values)}"
            )
            break

    for index, pixel in enumerate(image.get_flattened_data()):
        point = _first_point(index, image.width)
        rgb = pixel[:3]
        alpha = pixel[3]
        if (alpha != 0 or count_transparent) and rgb not in palette:
            errors.append(
                f"{name} external layer at local {point} has pixel color "
                f"{_format_rgb(rgb)} outside contract palette"
            )
            break

    if not chroma_key_allowed:
        for index, pixel in enumerate(image.get_flattened_data()):
            if pixel[:3] == chroma_key:
                point = _first_point(index, image.width)
                errors.append(
                    f"{name} external layer retains chroma key "
                    f"{_format_rgb(chroma_key)} at local {point}"
                )
                break
    return errors


def _validate_reference_tiles(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    expected_box = tuple(spec["commonBoundingBox"])
    center_x = spec["centerX"]
    minimum_margin = spec["minimumMargin"]
    sole_y = spec["soleY"]
    tile_width = spec["logicalTile"]["width"]
    tile_height = spec["logicalTile"]["height"]
    bounding_boxes: dict[str, BoundingBox | None] = {}

    for name in ("equipped", "base"):
        tile = _tile_image(image, spec, name)
        box = _opaque_bounding_box(tile)
        bounding_boxes[name] = box

        if box is None:
            errors.append(f"{name} tile has no opaque pixels")
            continue

        if box != expected_box:
            errors.append(
                f"{name} bounding box must be {expected_box}; got {box}"
            )

        left, top, right, bottom = box
        doubled_center = left + right
        if doubled_center != center_x * 2:
            errors.append(
                f"{name} center axis must be x={center_x}; "
                f"bounding box spans x={left}..{right}"
            )

        margins = (left, top, tile_width - 1 - right, tile_height - 1 - bottom)
        if min(margins) < minimum_margin:
            errors.append(
                f"{name} minimum margin must be at least {minimum_margin}; "
                f"got left={margins[0]}, top={margins[1]}, "
                f"right={margins[2]}, bottom={margins[3]}"
            )

        if bottom != sole_y:
            errors.append(f"{name} sole must be y={sole_y}; got y={bottom}")

    equipped_box = bounding_boxes["equipped"]
    base_box = bounding_boxes["base"]
    if equipped_box != base_box:
        errors.append(
            "equipped and base bounding boxes must match; "
            f"got equipped={equipped_box}, base={base_box}"
        )

    return errors


def _validate_anchor_guides(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    tile = _tile_image(image, spec, "anchors")
    guide_red: Rgba = (*_hex_to_rgb(spec["palette"]["guideRed"]), 255)

    for raw_point in spec["anchorGuidePixels"]:
        point = tuple(raw_point)
        actual = tile.getpixel(point)
        if actual != guide_red:
            errors.append(
                f"anchor guide pixel at local {point} must be {_format_rgb(guide_red[:3])}; "
                f"got {_format_rgba(actual)}"
            )

    return errors


def _validate_palette_tile(image: Image.Image, spec: dict) -> list[str]:
    tile = _tile_image(image, spec, "palette")
    present_colors = {
        pixel[:3]
        for pixel in tile.get_flattened_data()
        if pixel[3] != 0 or spec.get("transparentPixelsCountTowardPalette", False)
    }
    required_colors = {_hex_to_rgb(value) for value in spec["palette"].values()}
    missing_colors = sorted(required_colors - present_colors)
    if not missing_colors:
        return []

    formatted = ", ".join(_format_rgb(color) for color in missing_colors)
    return [f"palette tile is missing contract colors: {formatted}"]


def _validate_reserved_tiles(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    for definition in spec["reservedTiles"]:
        tile = _tile_image_at(
            image,
            spec,
            definition["row"],
            definition["column"],
        )
        required_alpha = definition["requiredAlpha"]
        first_invalid: tuple[tuple[int, int], int] | None = None
        invalid_count = 0
        for index, alpha in enumerate(tile.getchannel("A").get_flattened_data()):
            if alpha != required_alpha:
                invalid_count += 1
                if first_invalid is None:
                    first_invalid = _first_point(index, tile.width), alpha
        if first_invalid is not None:
            point, alpha = first_invalid
            errors.append(
                f"{definition['name']} tile (row={definition['row']}, "
                f"column={definition['column']}) must have alpha={required_alpha}; "
                f"found alpha={alpha} first at local {point} "
                f"({invalid_count} invalid pixel(s))"
            )
    return errors


def _first_opaque_point_outside_box(
    tile: Image.Image,
    allowed_box: BoundingBox,
    inclusive: bool,
) -> tuple[int, int] | None:
    left, top, right, bottom = allowed_box
    for index, pixel in enumerate(tile.get_flattened_data()):
        if pixel[3] == 0:
            continue
        x, y = _first_point(index, tile.width)
        if inclusive:
            inside = left <= x <= right and top <= y <= bottom
        else:
            inside = left < x < right and top < y < bottom
        if not inside:
            return x, y
    return None


def _validate_modular_geometry(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contract = spec["compositionContracts"]["geometry"]
    allowed_box = tuple(contract["commonAllowedBox"])
    inclusive = spec.get("commonAllowedBoxInclusive", True)
    center_x = contract["sameCenterX"]
    sole_y = contract["sameSoleY"]
    minimum_margin = spec["minimumMargin"]
    tile_width = spec["logicalTile"]["width"]
    tile_height = spec["logicalTile"]["height"]
    bounding_boxes: dict[str, BoundingBox | None] = {}

    for name in contract["tiles"]:
        tile = _tile_image(image, spec, name)
        box = _opaque_bounding_box(tile)
        bounding_boxes[name] = box
        if box is None:
            errors.append(f"{name} tile has no opaque pixels for geometry validation")
            continue

        outside_point = _first_opaque_point_outside_box(tile, allowed_box, inclusive)
        if outside_point is not None:
            errors.append(
                f"{name} tile exceeds commonAllowedBox {allowed_box}; "
                f"first outside pixel at local {outside_point}"
            )

        left, top, right, bottom = box
        if left + right != center_x * 2:
            errors.append(
                f"{name} tile center axis must be x={center_x}; "
                f"bounding box {box} starts at local {(left, top)}"
            )

        if bottom != sole_y:
            errors.append(
                f"{name} tile sole must be y={sole_y}; got y={bottom} "
                f"at local {(left, bottom)}"
            )

        margins = (left, top, tile_width - 1 - right, tile_height - 1 - bottom)
        if min(margins) < minimum_margin:
            errors.append(
                f"{name} tile minimum margin must be at least {minimum_margin}; "
                f"first opaque pixel at local {(left, top)}, "
                f"got left={margins[0]}, top={margins[1]}, "
                f"right={margins[2]}, bottom={margins[3]}"
            )

    if contract.get("sameBoundingBox"):
        reference_name = contract["tiles"][0]
        reference_box = bounding_boxes[reference_name]
        for name in contract["tiles"][1:]:
            box = bounding_boxes[name]
            if box != reference_box:
                first_point = None if box is None else (box[0], box[1])
                errors.append(
                    f"{name} tile bounding box must match {reference_name} tile; "
                    f"got {box} versus {reference_box}, first opaque local {first_point}"
                )

    return errors


def _validate_layer_bounds(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    for name, bounds in spec["layerBounds"].items():
        tile = _tile_image(image, spec, name)
        point = _first_opaque_point_outside_y(
            tile,
            bounds["minY"],
            bounds["maxY"],
            bounds.get("inclusive", True),
        )
        if point is not None:
            interval = (
                f"{bounds['minY']}..{bounds['maxY']}"
                if bounds.get("inclusive", True)
                else f"({bounds['minY']}, {bounds['maxY']})"
            )
            errors.append(
                f"{name} tile violates layerBounds y={interval}; "
                f"first opaque pixel outside range at local {point}"
            )
    return errors


def _compose_tiles(image: Image.Image, spec: dict, sources: list[str]) -> Image.Image:
    size = spec["logicalTile"]["width"], spec["logicalTile"]["height"]
    result = Image.new("RGBA", size, (0, 0, 0, 0))
    for source in sources:
        result = Image.alpha_composite(result, _tile_image(image, spec, source))
    return result


def _validate_compositions(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contracts = spec["compositionContracts"]
    if contracts["alphaCompositing"] != "source-over":
        raise ValueError("compositionContracts.alphaCompositing must be 'source-over'")

    for target, contract in contracts.items():
        if target in {"alphaCompositing", "geometry"}:
            continue
        if not contract.get("pixelExact"):
            continue
        if "sources" in contract:
            sources = contract["sources"]
        else:
            sources = spec[contract["sourcesFrom"]]
        expected = _compose_tiles(image, spec, sources)
        actual = _tile_image(image, spec, target)
        difference = _first_pixel_difference(expected, actual)
        if difference is not None:
            point, expected_pixel, actual_pixel = difference
            errors.append(
                f"{target} tile composition differs from {sources}; "
                f"first mismatch at local {point}, "
                f"expected {_format_rgba(expected_pixel)}, "
                f"got {_format_rgba(actual_pixel)}"
            )
    return errors


def _guide_points(spec: dict) -> list[tuple[int, int]]:
    points: list[tuple[int, int]] = []
    seen: set[tuple[int, int]] = set()
    guide = spec["guide"]
    for group_name in ("verticalLines", "horizontalLines"):
        for line in guide[group_name]:
            for raw_point in line["pixelCoordinates"]:
                point = tuple(raw_point)
                if point not in seen:
                    seen.add(point)
                    points.append(point)
    return points


def _validate_modular_guides(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    guide = spec["guide"]
    guide_tile_name = guide["tile"]
    guide_tile = _tile_image(image, spec, guide_tile_name)
    base_tile = _tile_image(image, spec, guide["baseTile"])
    guide_color: Rgba = (*_hex_to_rgb(guide["overlayColor"]), 255)
    points = _guide_points(spec)
    point_set = set(points)

    for point in points:
        actual = guide_tile.getpixel(point)
        if actual != guide_color:
            errors.append(
                f"{guide_tile_name} tile guide pixel at local {point} must be "
                f"{_format_rgb(guide_color[:3])}; got {_format_rgba(actual)}"
            )
            break

    base_difference = _first_pixel_difference(base_tile, guide_tile, point_set)
    if base_difference is not None:
        point, expected_pixel, actual_pixel = base_difference
        errors.append(
            f"{guide_tile_name} tile must match {guide['baseTile']} outside guide pixels; "
            f"first mismatch at local {point}, expected {_format_rgba(expected_pixel)}, "
            f"got {_format_rgba(actual_pixel)}"
        )

    if guide.get("excludedFromCharacterTiles"):
        excluded_names = {guide_tile_name, "palette"}
        for tile_definition in spec["tileMap"]:
            name = tile_definition["name"]
            if name in excluded_names:
                continue
            tile = _tile_image(image, spec, name)
            copied_point = next(
                (point for point in points if tile.getpixel(point) == guide_color),
                None,
            )
            if copied_point is not None:
                errors.append(
                    f"{name} tile has a guide pixel copied outside {guide_tile_name}; "
                    f"first copied guide coordinate at local {copied_point}"
                )

    return errors


def _validate_palette_grid(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    tile = _tile_image(image, spec, "palette")
    grid = spec["paletteGrid"]
    columns = grid["columns"]
    rows = grid["rows"]
    cell_width = grid["cellWidth"]
    cell_height = grid["cellHeight"]
    gap = grid["gap"]
    origin_x, origin_y = grid["origin"]
    color_order = grid["colorOrder"]
    if len(color_order) != columns * rows:
        raise ValueError("paletteGrid.colorOrder size must match columns * rows")

    cell_points: dict[tuple[int, int], tuple[int, Rgba]] = {}
    for index, palette_name in enumerate(color_order):
        column = index % columns
        row = index // columns
        left = origin_x + column * (cell_width + gap)
        top = origin_y + row * (cell_height + gap)
        color: Rgba = (*_hex_to_rgb(spec["palette"][palette_name]), 255)
        for y in range(top, top + cell_height):
            for x in range(left, left + cell_width):
                cell_points[(x, y)] = index, color

    grid_right = origin_x + columns * cell_width + (columns - 1) * gap - 1
    grid_bottom = origin_y + rows * cell_height + (rows - 1) * gap - 1
    for index, pixel in enumerate(tile.get_flattened_data()):
        point = _first_point(index, tile.width)
        if pixel[3] == 0 or point in cell_points:
            continue
        inside_extent = (
            origin_x <= point[0] <= grid_right
            and origin_y <= point[1] <= grid_bottom
        )
        location = "grid gap" if inside_extent else "grid position"
        errors.append(
            f"palette tile {location} must be transparent; "
            f"first unexpected pixel at local {point}"
        )
        break

    for point, (index, expected) in cell_points.items():
        actual = tile.getpixel(point)
        if actual == expected:
            continue
        palette_name = color_order[index]
        if actual[3] == 0:
            errors.append(
                f"palette tile cell {index} ({palette_name}) must be exactly "
                f"{cell_width}x{cell_height}px; first mismatch at local {point}"
            )
        else:
            errors.append(
                f"palette tile color order mismatch in cell {index} ({palette_name}); "
                f"first mismatch at local {point}, expected {_format_rgba(expected)}, "
                f"got {_format_rgba(actual)}"
            )
        break

    return errors


def _validate_v3_tile_map(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    columns = spec["logicalTile"]["columns"]
    rows = spec["logicalTile"]["rows"]
    definitions = spec["tileMap"]
    positions: dict[tuple[int, int], str] = {}
    for definition in definitions:
        name = definition["name"]
        position = definition["row"], definition["column"]
        if position in positions:
            errors.append(
                f"{name} tile reuses row={position[0]}, column={position[1]} "
                f"already assigned to {positions[position]}"
            )
        elif not (0 <= position[0] < rows and 0 <= position[1] < columns):
            errors.append(
                f"{name} tile position row={position[0]}, column={position[1]} "
                f"is outside the {rows}x{columns} tile map"
            )
        else:
            positions[position] = name

    expected_positions = {(row, column) for row in range(rows) for column in range(columns)}
    missing_positions = sorted(expected_positions - set(positions))
    if missing_positions:
        row, column = missing_positions[0]
        errors.append(f"tileMap has no tile at row={row}, column={column}")

    for definition in definitions:
        name = definition["name"]
        if _opaque_bounding_box(_tile_image(image, spec, name)) is None:
            errors.append(
                f"{name} tile (row={definition['row']}, column={definition['column']}) "
                "has no opaque pixels"
            )
    return errors


def _geometry_errors_for_named_image(
    image: Image.Image,
    name: str,
    allowed_box: BoundingBox,
    inclusive: bool,
    center_x: int,
    sole_y: int,
    minimum_margin: int,
) -> tuple[list[str], BoundingBox | None]:
    box = _opaque_bounding_box(image)
    if box is None:
        return [f"{name} has no opaque pixels for geometry validation"], None

    errors: list[str] = []
    outside_point = _first_opaque_point_outside_box(image, allowed_box, inclusive)
    if outside_point is not None:
        errors.append(
            f"{name} exceeds commonAllowedBox {allowed_box}; "
            f"first outside pixel at local {outside_point}"
        )

    left, top, right, bottom = box
    if left + right != center_x * 2:
        errors.append(
            f"{name} center axis must be x={center_x}; bounding box {box}, "
            f"first opaque local {(left, top)}"
        )
    if bottom != sole_y:
        errors.append(
            f"{name} sole must be y={sole_y}; got y={bottom} "
            f"at local {(left, bottom)}"
        )
    margins = (left, top, image.width - 1 - right, image.height - 1 - bottom)
    if min(margins) < minimum_margin:
        errors.append(
            f"{name} minimum margin must be at least {minimum_margin}; "
            f"first opaque local {(left, top)}, got left={margins[0]}, "
            f"top={margins[1]}, right={margins[2]}, bottom={margins[3]}"
        )
    return errors, box


def _validate_v3_geometry(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    errors: list[str] = []
    contract = spec["compositionContracts"]["geometry"]
    allowed_box = tuple(contract["commonAllowedBox"])
    inclusive = spec.get("commonAllowedBoxInclusive", True)
    center_x = contract["sameCenterX"]
    sole_y = contract["sameSoleY"]
    minimum_margin = spec["minimumMargin"]
    boxes: dict[str, BoundingBox | None] = {}

    for name in contract["tiles"]:
        tile_errors, box = _geometry_errors_for_named_image(
            _tile_image(image, spec, name),
            f"{name} tile",
            allowed_box,
            inclusive,
            center_x,
            sole_y,
            minimum_margin,
        )
        errors.extend(tile_errors)
        boxes[name] = box

    if contract.get("sameBoundingBox"):
        reference_name = contract["tiles"][0]
        reference_box = boxes[reference_name]
        for name in contract["tiles"][1:]:
            if boxes[name] != reference_box:
                box = boxes[name]
                first_point = None if box is None else (box[0], box[1])
                errors.append(
                    f"{name} tile bounding box must match {reference_name} tile; "
                    f"got {box} versus {reference_box}, first opaque local {first_point}"
                )

    for name in contract.get("externalLayers", []):
        external = external_layers.get(name)
        if external is None:
            continue
        external_errors, _ = _geometry_errors_for_named_image(
            external,
            f"{name} external layer",
            allowed_box,
            inclusive,
            center_x,
            sole_y,
            minimum_margin,
        )
        errors.extend(external_errors)
    return errors


def _validate_external_base_body(
    base_body: Image.Image,
    reference_body: Image.Image | None,
    spec: dict,
) -> list[str]:
    errors = _validate_named_pixels(base_body, spec, "base-body")
    contract = spec["externalLayers"]["base-body"]

    original_contract = contract["originalBodyBaseReference"]
    alpha_bitset_contract = original_contract.get("alphaMaskBitset")
    if (
        contract.get("sameAlphaSilhouetteAsOriginalBodyBase")
        and alpha_bitset_contract is not None
    ):
        try:
            expected_bits = bytes.fromhex(alpha_bitset_contract["value"])
        except (KeyError, TypeError, ValueError) as error:
            errors.append(f"base-body alpha silhouette bitset is invalid: {error}")
            expected_bits = b""
        expected_bit_count = base_body.width * base_body.height
        if len(expected_bits) * 8 != expected_bit_count:
            errors.append(
                "base-body alpha silhouette bitset size differs from 64x64 contract; "
                f"got {len(expected_bits) * 8} bits"
            )
        else:
            for index, actual_alpha in enumerate(
                base_body.getchannel("A").get_flattened_data()
            ):
                expected_opaque = bool(
                    expected_bits[index // 8] & (1 << (7 - index % 8))
                )
                expected_alpha = 255 if expected_opaque else 0
                if expected_alpha != actual_alpha:
                    point = _first_point(index, base_body.width)
                    errors.append(
                        "base-body external layer alpha silhouette differs from original; "
                        f"first mismatch at local {point}, expected alpha={expected_alpha}, "
                        f"got alpha={actual_alpha}"
                    )
                    break
    elif reference_body is not None and contract.get(
        "sameAlphaSilhouetteAsOriginalBodyBase"
    ):
        for index, (expected_alpha, actual_alpha) in enumerate(
            zip(
                reference_body.getchannel("A").get_flattened_data(),
                base_body.getchannel("A").get_flattened_data(),
            )
        ):
            if expected_alpha != actual_alpha:
                point = _first_point(index, base_body.width)
                errors.append(
                    "base-body external layer alpha silhouette differs from original; "
                    f"first mismatch at local {point}, expected alpha={expected_alpha}, "
                    f"got alpha={actual_alpha}"
                )
                break

    face_contract = spec["faceProtectionContract"]
    protected_left, protected_top, protected_right, protected_bottom = face_contract[
        "protectedRegion"
    ]
    if reference_body is not None and contract.get("sameFaceAsOriginalBodyBase"):
        protected_mismatch = None
        for y in range(protected_top, protected_bottom + 1):
            for x in range(protected_left, protected_right + 1):
                expected = reference_body.getpixel((x, y))
                actual = base_body.getpixel((x, y))
                if actual != expected:
                    protected_mismatch = (x, y), expected, actual
                    break
            if protected_mismatch is not None:
                break
        if protected_mismatch is not None:
            point, expected, actual = protected_mismatch
            errors.append(
                "base-body external layer protected face region differs at "
                f"local {point}; expected {_format_rgba(expected)}, "
                f"got {_format_rgba(actual)}"
            )

    for feature_name, protected_pixels in face_contract["features"].items():
        for protected_pixel in protected_pixels:
            point = tuple(protected_pixel["coordinate"])
            expected = tuple(protected_pixel["rgba"])
            actual = base_body.getpixel(point)
            if actual != expected:
                errors.append(
                    f"base-body external layer protected {feature_name} pixel differs "
                    f"at local {point}; expected {_format_rgba(expected)}, "
                    f"got {_format_rgba(actual)}"
                )
                break

    allowed_names = (
        contract["neutralUnderwearPaletteNames"]
        + contract["nonProtectedAnatomyPaletteNames"]
    )
    allowed_colors = {_hex_to_rgb(spec["palette"][name]) for name in allowed_names}
    neutral_region = contract["neutralUnderwearRegion"]
    neutral_min_y = neutral_region["minY"]
    neutral_max_y = neutral_region["maxY"]
    for index, pixel in enumerate(base_body.get_flattened_data()):
        if pixel[3] == 0:
            continue
        point = _first_point(index, base_body.width)
        if not neutral_min_y <= point[1] <= neutral_max_y:
            continue
        inside_protected = (
            protected_left <= point[0] <= protected_right
            and protected_top <= point[1] <= protected_bottom
        )
        if not inside_protected and pixel[:3] not in allowed_colors:
            errors.append(
                "base-body external layer neutral/anatomy palette violation at "
                f"local {point}; got {_format_rgba(pixel)}"
            )
            break
    return errors


def _compose_v3_sources(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
    sources: list[str],
) -> Image.Image:
    size = spec["logicalTile"]["width"], spec["logicalTile"]["height"]
    result = Image.new("RGBA", size, (0, 0, 0, 0))
    for source in sources:
        layer = external_layers.get(source)
        if layer is None:
            layer = _tile_image(image, spec, source)
        result = Image.alpha_composite(result, layer)
    return result


def _validate_v3_compositions(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    errors: list[str] = []
    contracts = spec["compositionContracts"]
    if contracts["alphaCompositing"] != "source-over":
        raise ValueError("compositionContracts.alphaCompositing must be 'source-over'")

    for target in ("default-outfit", "default-hair-preview", "equipped", "composite"):
        contract = contracts[target]
        expected = _compose_v3_sources(image, spec, external_layers, contract["sources"])
        actual = _tile_image(image, spec, target)
        difference = _first_pixel_difference(expected, actual)
        if difference is not None:
            point, expected_pixel, actual_pixel = difference
            errors.append(
                f"{target} tile composition differs from {contract['sources']}; "
                f"first mismatch at local {point}, "
                f"expected {_format_rgba(expected_pixel)}, "
                f"got {_format_rgba(actual_pixel)}"
            )

    equality = contracts["equippedCompositeEquality"]
    first_name, second_name = equality["tiles"]
    difference = _first_pixel_difference(
        _tile_image(image, spec, second_name),
        _tile_image(image, spec, first_name),
    )
    if difference is not None:
        point, expected_pixel, actual_pixel = difference
        errors.append(
            f"{first_name} tile must match {second_name} tile; "
            f"first mismatch at local {point}, expected {_format_rgba(expected_pixel)}, "
            f"got {_format_rgba(actual_pixel)}"
        )
    return errors


def _first_opaque_on_row(tile: Image.Image, y: int) -> tuple[int, int] | None:
    return next(
        ((x, y) for x in range(tile.width) if tile.getpixel((x, y))[3] != 0),
        None,
    )


def _first_opaque_at_or_beyond_y(
    tile: Image.Image,
    y: int,
    at_or_below: bool,
) -> tuple[int, int] | None:
    y_values = range(y, tile.height) if at_or_below else range(0, y + 1)
    for local_y in y_values:
        point = _first_opaque_on_row(tile, local_y)
        if point is not None:
            return point
    return None


def _first_transparent_on_segment(
    tile: Image.Image,
    y: int,
    x_range: Sequence[int],
) -> tuple[int, int] | None:
    start_x, end_x = x_range
    return next(
        ((x, y) for x in range(start_x, end_x + 1) if tile.getpixel((x, y))[3] == 0),
        None,
    )


def _validate_v3_layer_boundaries(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contracts = spec["layerBoundaryContracts"]

    for name in ("default-top-layer", "adventure-top-layer"):
        tile = _tile_image(image, spec, name)
        contract = contracts[name]
        hem_y = contract["hemY"]
        if _first_opaque_on_row(tile, hem_y) is None:
            box = _opaque_bounding_box(tile)
            point = None if box is None else (box[0], box[3])
            errors.append(
                f"{name} required hem at y={hem_y} is missing; first opaque local {point}"
            )
        forbidden_y = contract.get("pixelsAtOrBelowY")
        forbidden = contract.get("pixelsAtOrBelowForbidden", False)
        termination = contract.get("hemTermination")
        if (
            forbidden_y is None
            and isinstance(termination, dict)
            and termination.get("pixelsBelowHemForbidden")
        ):
            forbidden_y = termination["y"] + 1
            forbidden = True
        point = (
            None
            if forbidden_y is None
            else _first_opaque_at_or_beyond_y(tile, forbidden_y, True)
        )
        if forbidden and point is not None:
            errors.append(
                f"layerBoundaryContracts.{name} hem boundary forbids pixels at or "
                f"below y={forbidden_y}; "
                f"first forbidden pixel at local {point}"
            )

    default_bottom = _tile_image(image, spec, "default-bottom-layer")
    default_bottom_contract = contracts["default-bottom-layer"]
    default_seam_y = default_bottom_contract["waistSeamY"]
    if _first_opaque_on_row(default_bottom, default_seam_y) is None:
        box = _opaque_bounding_box(default_bottom)
        point = None if box is None else (box[0], box[1])
        errors.append(
            "default-bottom-layer required waist seam at "
            f"y={default_seam_y} is missing; first opaque local {point}"
        )

    default_hem_y = default_bottom_contract["hemY"]
    if _first_opaque_on_row(default_bottom, default_hem_y) is None:
        box = _opaque_bounding_box(default_bottom)
        point = None if box is None else (box[0], box[3])
        errors.append(
            f"default-bottom-layer required hem y={default_hem_y} is missing; "
            f"first opaque local {point}"
        )

    for name in ("default-shoes-layer", "adventure-shoes-layer"):
        tile = _tile_image(image, spec, name)
        seam_y = contracts[name]["ankleSeamY"]
        if _first_opaque_on_row(tile, seam_y) is None:
            box = _opaque_bounding_box(tile)
            point = None if box is None else (box[0], box[1])
            errors.append(
                f"{name} required ankle seam at y={seam_y} is missing; "
                f"first opaque local {point}"
            )
        sole_y = contracts[name]["soleY"]
        if _first_opaque_on_row(tile, sole_y) is None:
            box = _opaque_bounding_box(tile)
            point = None if box is None else (box[0], box[3])
            errors.append(
                f"{name} required sole y={sole_y} is missing; "
                f"first opaque local {point}"
            )

    adventure_bottom = _tile_image(image, spec, "adventure-bottom-layer")
    adventure_bottom_contract = contracts["adventure-bottom-layer"]
    waist_y = adventure_bottom_contract.get(
        "waistSeamY",
        adventure_bottom_contract.get("waistY"),
    )
    if waist_y is None:
        raise ValueError(
            "layerBoundaryContracts.adventure-bottom-layer must define waistY or "
            "waistSeamY"
        )
    if _first_opaque_on_row(adventure_bottom, waist_y) is None:
        box = _opaque_bounding_box(adventure_bottom)
        point = None if box is None else (box[0], box[1])
        errors.append(
            "adventure-bottom-layer required waist at "
            f"y={waist_y} is missing; first opaque local {point}"
        )

    forbidden_upper_y = adventure_bottom_contract.get("pixelsAtOrAboveY")
    if forbidden_upper_y is not None:
        point = _first_opaque_at_or_beyond_y(
            adventure_bottom,
            forbidden_upper_y,
            False,
        )
        if adventure_bottom_contract.get("pixelsAtOrAboveForbidden") and point is not None:
            errors.append(
                "layerBoundaryContracts.adventure-bottom-layer waist boundary forbids "
                f"pixels at or above y={forbidden_upper_y}; "
                f"first forbidden pixel at local {point}"
            )

    bottom_hem_y = adventure_bottom_contract.get("hemY")
    if bottom_hem_y is not None and _first_opaque_on_row(adventure_bottom, bottom_hem_y) is None:
        box = _opaque_bounding_box(adventure_bottom)
        local = None if box is None else (box[0], box[3])
        errors.append(
            "layerBoundaryContracts.adventure-bottom-layer required hem "
            f"y={bottom_hem_y} is missing; first opaque local {local}"
        )

    forbidden_lower_y = adventure_bottom_contract.get("pixelsAtOrBelowY")
    if forbidden_lower_y is not None:
        point = _first_opaque_at_or_beyond_y(
            adventure_bottom,
            forbidden_lower_y,
            True,
        )
        if adventure_bottom_contract.get("pixelsAtOrBelowForbidden") and point is not None:
            errors.append(
                "layerBoundaryContracts.adventure-bottom-layer hem boundary forbids "
                f"pixels at or below y={forbidden_lower_y}; "
                f"first forbidden pixel at local {point}"
            )
    return errors


def _overlap_x_ranges(contract: dict) -> list[tuple[int, int]]:
    raw_ranges = contract.get("xRanges")
    if raw_ranges is None and "xRange" in contract:
        raw_ranges = [contract["xRange"]]
    if not isinstance(raw_ranges, list) or not raw_ranges:
        raise ValueError("required overlap contract must define xRange or xRanges")
    return [tuple(raw_range) for raw_range in raw_ranges]


def _overlap_points(contract: dict) -> set[tuple[int, int]]:
    minimum_y, maximum_y = contract["yRange"]
    return {
        (x, y)
        for y in range(minimum_y, maximum_y + 1)
        for minimum_x, maximum_x in _overlap_x_ranges(contract)
        for x in range(minimum_x, maximum_x + 1)
    }


def _validate_v3_adventure_overlaps(image: Image.Image, spec: dict) -> list[str]:
    contracts = spec.get("layerBoundaryContracts", {})
    outline: Rgba = (*_hex_to_rgb(spec["palette"]["outlineDarkNavy"]), 255)
    errors: list[str] = []
    for contract_name in ("adventureWaistOverlap", "adventureAnkleOverlap"):
        contract = contracts.get(contract_name)
        if not isinstance(contract, dict):
            continue
        layer_names = contract.get("layersBackToFront", [])
        if len(layer_names) != 2:
            raise ValueError(
                f"layerBoundaryContracts.{contract_name}.layersBackToFront must "
                "contain two layers"
            )
        back_name, front_name = layer_names
        layers = {
            back_name: _tile_image(image, spec, back_name),
            front_name: _tile_image(image, spec, front_name),
        }
        required_points = _overlap_points(contract)
        ordered_points = sorted(required_points, key=lambda point: (point[1], point[0]))

        if contract.get("requiredSharedOpaqueRegion"):
            missing = next(
                (
                    (point, layer_name)
                    for point in ordered_points
                    for layer_name in layer_names
                    if layers[layer_name].getpixel(point)[3] == 0
                ),
                None,
            )
            if missing is not None:
                point, missing_layer = missing
                errors.append(
                    f"layerBoundaryContracts.{contract_name} requires {back_name} "
                    f"and {front_name} to share every opaque pixel; {missing_layer} "
                    f"is transparent first at local {point}"
                )

        if contract.get("sharedOpaqueCoordinatesOutsideRegionForbidden"):
            outside = next(
                (
                    (x, y)
                    for y in range(layers[back_name].height)
                    for x in range(layers[back_name].width)
                    if (x, y) not in required_points
                    and layers[back_name].getpixel((x, y))[3] != 0
                    and layers[front_name].getpixel((x, y))[3] != 0
                ),
                None,
            )
            if outside is not None:
                errors.append(
                    f"layerBoundaryContracts.{contract_name} forbids {back_name} "
                    f"and {front_name} shared opaque pixels outside the required "
                    f"region; first outside overlap at local {outside}"
                )

        hidden_name = contract.get("hiddenOverlapLayer")
        forbidden_names = contract.get("hiddenOverlapForbiddenPaletteNames", [])
        if hidden_name in layers and forbidden_names:
            forbidden_colors = {
                (*_hex_to_rgb(spec["palette"][name]), 255)
                for name in forbidden_names
            }
            forbidden = next(
                (
                    point
                    for point in ordered_points
                    if layers[hidden_name].getpixel(point) in forbidden_colors
                ),
                None,
            )
            if forbidden is not None:
                palette_name = _palette_name_for_rgb(
                    spec,
                    layers[hidden_name].getpixel(forbidden)[:3],
                )
                errors.append(
                    f"layerBoundaryContracts.{contract_name} hidden overlap in "
                    f"{hidden_name} forbids {palette_name}; first failure at local "
                    f"{forbidden}"
                )

        hem = contract.get("finalVisibleHem")
        if isinstance(hem, dict) and hem.get("rowCount") == 1:
            layer_name = hem["layer"]
            layer = _tile_image(image, spec, layer_name)
            hem_y = hem["y"]
            previous_y = hem_y - 1
            horizontal_points = [
                (x, y)
                for y in (previous_y, hem_y)
                for minimum_x, maximum_x in _overlap_x_ranges(contract)
                for x in range(minimum_x, maximum_x + 1)
            ]
            row_width = sum(
                maximum_x - minimum_x + 1
                for minimum_x, maximum_x in _overlap_x_ranges(contract)
            )
            if all(layer.getpixel(point) == outline for point in horizontal_points):
                first_x = min(point[0] for point in horizontal_points)
                errors.append(
                    f"layerBoundaryContracts.{contract_name} finalVisibleHem for "
                    f"{layer_name} has a 2-pixel horizontal outline across "
                    f"{row_width}px; first failure at local {(first_x, previous_y)}"
                )
    return errors


def _palette_name_for_rgb(spec: dict, color: Rgb) -> str:
    return next(
        (
            name
            for name, value in spec["palette"].items()
            if _hex_to_rgb(value) == color
        ),
        _format_rgb(color),
    )


def _validate_v3_adventure_design_palettes(
    image: Image.Image,
    spec: dict,
) -> list[str]:
    contracts = spec.get("designContracts", {})
    top_contract = contracts.get("adventure-top")
    bottom_contract = contracts.get("adventure-bottom")
    shoes_contract = contracts.get("adventure-shoes")
    errors: list[str] = []

    bottom_only_names: set[str] = set()
    if isinstance(top_contract, dict) and isinstance(bottom_contract, dict):
        bottom_only_names = set(bottom_contract.get("allowedPaletteNames", [])) - set(
            top_contract.get("allowedPaletteNames", [])
        )

    for contract_name, contract in (
        ("adventure-top", top_contract),
        ("adventure-bottom", bottom_contract),
        ("adventure-shoes", shoes_contract),
    ):
        if not isinstance(contract, dict) or "allowedPaletteNames" not in contract:
            continue
        layer_name = contract["layer"]
        tile = _tile_image(image, spec, layer_name)
        allowed_names = set(contract["allowedPaletteNames"])
        forbidden_names = set(contract.get("forbiddenPaletteNames", []))
        allowed_colors = {
            _hex_to_rgb(spec["palette"][name]) for name in allowed_names
        }
        for index, pixel in enumerate(tile.get_flattened_data()):
            if pixel[3] == 0:
                continue
            point = _first_point(index, tile.width)
            palette_name = _palette_name_for_rgb(spec, pixel[:3])
            if contract_name == "adventure-top" and palette_name in bottom_only_names:
                errors.append(
                    "designContracts.adventure-top forbids bottom-only palette "
                    f"semantics in {layer_name}; found {palette_name} first at "
                    f"local {point}"
                )
                break
            if pixel[:3] not in allowed_colors or palette_name in forbidden_names:
                errors.append(
                    f"designContracts.{contract_name} requires {layer_name} to use "
                    f"its allowed palette only; found {palette_name} first at "
                    f"local {point}"
                )
                break
    return errors


def _validate_v3_adventure_bottom_geometry(
    image: Image.Image,
    spec: dict,
) -> list[str]:
    boundary_contract = spec.get("layerBoundaryContracts", {}).get(
        "adventure-bottom-layer"
    )
    if not isinstance(boundary_contract, dict):
        return []

    errors: list[str] = []
    layer_name = "adventure-bottom-layer"
    tile = _tile_image(image, spec, layer_name)
    symmetry = boundary_contract.get("legSymmetry")
    if isinstance(symmetry, dict):
        left_box = _points_box(_points_in_x_range(tile, symmetry["leftXRange"]))
        right_box = _points_box(_points_in_x_range(tile, symmetry["rightXRange"]))
        comparisons = set(symmetry.get("compare", []))
        size_tolerance = symmetry.get("boundingBoxSizeTolerancePixels", 0)
        left_size = _box_size(left_box)
        right_size = _box_size(right_box)
        size_mismatch = left_size is None or right_size is None
        if left_size is not None and right_size is not None:
            size_mismatch = any(
                abs(left_value - right_value) > size_tolerance
                for left_value, right_value in zip(left_size, right_size)
            )
        if "bounding-box-size" in comparisons and size_mismatch:
            point = _first_box_point(left_box) or _first_box_point(right_box)
            errors.append(
                "layerBoundaryContracts.adventure-bottom-layer.legSymmetry "
                f"bounding-box mismatch for {layer_name}; left={left_box}, "
                f"right={right_box}, first failure at local {point}"
            )

        end_y_tolerance = symmetry.get("endYTolerancePixels", 0)
        end_y_mismatch = (
            left_box is None
            or right_box is None
            or abs(left_box[3] - right_box[3]) > end_y_tolerance
        )
        if "end-y" in comparisons and end_y_mismatch:
            point = _first_box_point(left_box) or _first_box_point(right_box)
            errors.append(
                "layerBoundaryContracts.adventure-bottom-layer.legSymmetry "
                f"end-y mismatch for {layer_name}; left={left_box}, right={right_box}, "
                f"first failure at local {point}"
            )

    separator = boundary_contract.get("crotchSeparator")
    if isinstance(separator, dict) and separator.get("orientation") == "vertical":
        center_x = separator["x"]
        width = separator["width"]
        start_x = center_x - (width - 1) // 2
        end_x = start_x + width - 1
        start_y = boundary_contract["waistSeamY"]
        end_y = boundary_contract["hemY"]
        expected: Rgba = (
            *_hex_to_rgb(spec["palette"][separator["paletteName"]]),
            255,
        )
        missing = next(
            (
                (x, y)
                for y in range(start_y, end_y + 1)
                for x in range(start_x, end_x + 1)
                if tile.getpixel((x, y)) != expected
            ),
            None,
        )
        if missing is not None:
            errors.append(
                "layerBoundaryContracts.adventure-bottom-layer.crotchSeparator "
                f"is missing from {layer_name}; first failure at local {missing}"
            )

        if start_x > 0 and end_x + 1 < tile.width:
            too_wide = next(
                (
                    (x, y)
                    for y in range(start_y + 1, end_y)
                    for x in (start_x - 1, end_x + 1)
                    if tile.getpixel((x, y)) == expected
                ),
                None,
            )
            if too_wide is not None:
                errors.append(
                    "layerBoundaryContracts.adventure-bottom-layer.crotchSeparator "
                    f"width exceeds {width}px in {layer_name}; first width failure at "
                    f"local {too_wide}"
                )
    return errors


def _points_for_mapping_region(region: dict) -> set[tuple[int, int]]:
    raw_x_ranges = region.get("xRanges")
    if raw_x_ranges is None and "xRange" in region:
        raw_x_ranges = [region["xRange"]]
    if not isinstance(raw_x_ranges, list) or not raw_x_ranges:
        raise ValueError("equipment mapping region must define xRange or xRanges")
    minimum_y, maximum_y = region["yRange"]
    return {
        (x, y)
        for y in range(minimum_y, maximum_y + 1)
        for minimum_x, maximum_x in raw_x_ranges
        for x in range(minimum_x, maximum_x + 1)
    }


def _reference_has_alpha_within(
    reference: Image.Image,
    point: tuple[int, int],
    distance: int,
) -> bool:
    x, y = point
    return any(
        reference.getpixel((reference_x, reference_y))[3] != 0
        for reference_y in range(max(0, y - distance), min(reference.height, y + distance + 1))
        for reference_x in range(max(0, x - distance), min(reference.width, x + distance + 1))
    )


def _validate_v3_equipment_mapping(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    contracts = spec.get("equipmentMappingContracts")
    if not isinstance(contracts, dict):
        return []
    base_body = external_layers.get("base-body")
    if base_body is None:
        return []

    errors: list[str] = []
    layer_images = {
        name: _tile_image(image, spec, name)
        for name in (
            "adventure-top-layer",
            "adventure-bottom-layer",
            "adventure-shoes-layer",
        )
    }

    top_name = "adventure-top-layer"
    top_contract = contracts.get(top_name)
    if isinstance(top_contract, dict):
        top = layer_images[top_name]
        minimum_y, maximum_y = top_contract["primaryMappingYRange"]
        expansion = top_contract["maximumChebyshevExpansionPixels"]
        outside = next(
            (
                (x, y)
                for y in range(minimum_y, maximum_y + 1)
                for x in range(top.width)
                if top.getpixel((x, y))[3] != 0
                and not _reference_has_alpha_within(base_body, (x, y), expansion)
            ),
            None,
        )
        if outside is not None:
            errors.append(
                f"equipmentMappingContracts.{top_name}.primaryMappingYRange "
                f"allows at most {expansion}px Chebyshev expansion from base-body; "
                f"first failure at local {outside}"
            )

        collar = top_contract.get("collarShoulderPreservation")
        if isinstance(collar, dict) and collar.get("directBaseAlphaOverlapOnly"):
            collar_minimum_y, collar_maximum_y = collar["yRange"]
            point = next(
                (
                    (x, y)
                    for y in range(collar_minimum_y, collar_maximum_y + 1)
                    for x in range(top.width)
                    if top.getpixel((x, y))[3] != 0
                    and base_body.getpixel((x, y))[3] == 0
                ),
                None,
            )
            if point is not None:
                errors.append(
                    f"equipmentMappingContracts.{top_name}.collarShoulderPreservation "
                    "requires direct base-body alpha overlap; first failure at local "
                    f"{point}"
                )

        coverage = top_contract.get("neutralUnderwearCoverage")
        if isinstance(coverage, dict) and coverage.get("required"):
            coverage_minimum_y, coverage_maximum_y = coverage["yRange"]
            neutral_names = spec["externalLayers"]["base-body"][
                "neutralUnderwearPaletteNames"
            ]
            neutral_colors = {
                _hex_to_rgb(spec["palette"][name]) for name in neutral_names
            }
            point = next(
                (
                    (x, y)
                    for y in range(coverage_minimum_y, coverage_maximum_y + 1)
                    for x in range(base_body.width)
                    if base_body.getpixel((x, y))[3] != 0
                    and base_body.getpixel((x, y))[:3] in neutral_colors
                    and top.getpixel((x, y))[3] == 0
                ),
                None,
            )
            if point is not None:
                errors.append(
                    f"equipmentMappingContracts.{top_name}.neutralUnderwearCoverage "
                    f"does not cover base-body first at local {point}"
                )

        transparent_coordinates = top_contract.get("transparentCoordinates")
        hand_contract = spec.get("handProtectionContract")
        if transparent_coordinates and isinstance(hand_contract, dict):
            point = next(
                (
                    tuple(raw_point)
                    for raw_point in hand_contract.get("pixelCoordinates", [])
                    if top.getpixel(tuple(raw_point))[3] != 0
                ),
                None,
            )
            if point is not None:
                errors.append(
                    f"equipmentMappingContracts.{top_name}.transparentCoordinates "
                    f"requires protected hands to remain transparent; first failure at "
                    f"local {point}"
                )

        box = _opaque_bounding_box(top)
        if box is not None:
            axis_x = top_contract["centerAxisX"]
            tolerance = top_contract["leftRightOuterExtentDifferenceTolerancePixels"]
            difference = abs((axis_x - box[0]) - (box[2] - axis_x))
            if difference > tolerance:
                errors.append(
                    f"equipmentMappingContracts.{top_name} center axis x={axis_x} "
                    f"outer extent difference exceeds {tolerance}px; first opaque local "
                    f"{(box[0], box[1])}"
                )

    bottom_name = "adventure-bottom-layer"
    bottom_contract = contracts.get(bottom_name)
    bottom_required: set[tuple[int, int]] = set()
    if isinstance(bottom_contract, dict):
        bottom = layer_images[bottom_name]
        for region in bottom_contract["requiredOpaqueRegions"]:
            bottom_required.update(_points_for_mapping_region(region))
        point = next(
            (
                point
                for point in sorted(bottom_required, key=lambda item: (item[1], item[0]))
                if bottom.getpixel(point)[3] == 0
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"equipmentMappingContracts.{bottom_name}.requiredOpaqueRegions "
                f"is missing an opaque pixel first at local {point}"
            )

        transparent = bottom_contract["requiredTransparentRegion"]
        separator_points = {
            (transparent["x"], y)
            for y in range(transparent["yRange"][0], transparent["yRange"][1] + 1)
        }
        point = next(
            (
                point
                for point in sorted(separator_points, key=lambda item: (item[1], item[0]))
                if bottom.getpixel(point)[3] != 0
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"equipmentMappingContracts.{bottom_name}.requiredTransparentRegion "
                f"must remain transparent first at local {point}"
            )

        if bottom_contract.get("opaqueCoordinatesOutsideRequiredRegionsForbidden"):
            point = next(
                (
                    (x, y)
                    for y in range(bottom.height)
                    for x in range(bottom.width)
                    if bottom.getpixel((x, y))[3] != 0
                    and (x, y) not in bottom_required
                ),
                None,
            )
            if point is not None:
                errors.append(
                    f"equipmentMappingContracts.{bottom_name} forbids opaque "
                    f"coordinates outside required regions; first failure at local {point}"
                )

        expansion = bottom_contract["maximumChebyshevExpansionPixels"]
        point = next(
            (
                (x, y)
                for y in range(bottom.height)
                for x in range(bottom.width)
                if bottom.getpixel((x, y))[3] != 0
                and not _reference_has_alpha_within(base_body, (x, y), expansion)
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"equipmentMappingContracts.{bottom_name} exceeds base-body by more "
                f"than {expansion}px; first failure at local {point}"
            )

    shoes_name = "adventure-shoes-layer"
    shoes_contract = contracts.get(shoes_name)
    if isinstance(shoes_contract, dict):
        shoes = layer_images[shoes_name]
        minimum_y, maximum_y = shoes_contract["mappingYRange"]
        if shoes_contract.get("coverAllBaseBodyFootAlpha"):
            point = next(
                (
                    (x, y)
                    for y in range(minimum_y, maximum_y + 1)
                    for x in range(base_body.width)
                    if base_body.getpixel((x, y))[3] != 0
                    and shoes.getpixel((x, y))[3] == 0
                ),
                None,
            )
            if point is not None:
                errors.append(
                    f"equipmentMappingContracts.{shoes_name}.coverAllBaseBodyFootAlpha "
                    f"is missing coverage first at local {point}"
                )

        expansion = shoes_contract["maximumChebyshevExpansionPixels"]
        point = next(
            (
                (x, y)
                for y in range(minimum_y, maximum_y + 1)
                for x in range(shoes.width)
                if shoes.getpixel((x, y))[3] != 0
                and not _reference_has_alpha_within(base_body, (x, y), expansion)
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"equipmentMappingContracts.{shoes_name} exceeds base-body feet by "
                f"more than {expansion}px; first failure at local {point}"
            )

        sole_y = shoes_contract["soleY"]
        box = _opaque_bounding_box(shoes)
        actual_sole_y = None if box is None else box[3]
        if sole_y != spec["soleY"] or actual_sole_y != sole_y:
            first_expected = next(
                (
                    (x, sole_y)
                    for x in range(base_body.width)
                    if base_body.getpixel((x, sole_y))[3] != 0
                ),
                (0, sole_y),
            )
            errors.append(
                f"equipmentMappingContracts.{shoes_name} requires soleY={spec['soleY']}; "
                f"got contract={sole_y}, layer={actual_sole_y}, first failure at local "
                f"{first_expected}"
            )

    neutral_names = spec["externalLayers"]["base-body"]["neutralUnderwearPaletteNames"]
    neutral_colors = {_hex_to_rgb(spec["palette"][name]) for name in neutral_names}
    coverage_points: list[tuple[tuple[int, int], str]] = []
    if isinstance(top_contract, dict):
        coverage = top_contract.get("neutralUnderwearCoverage", {})
        if isinstance(coverage, dict) and "yRange" in coverage:
            minimum_y, maximum_y = coverage["yRange"]
            coverage_points.extend(
                ((x, y), top_name)
                for y in range(minimum_y, maximum_y + 1)
                for x in range(base_body.width)
                if base_body.getpixel((x, y))[3] != 0
                and base_body.getpixel((x, y))[:3] in neutral_colors
            )
    if isinstance(bottom_contract, dict):
        coverage_points.extend(
            (point, bottom_name)
            for point in sorted(bottom_required, key=lambda item: (item[1], item[0]))
            if point[1] >= 44 and base_body.getpixel(point)[3] != 0
        )
    if isinstance(shoes_contract, dict):
        minimum_y, maximum_y = shoes_contract["mappingYRange"]
        coverage_points.extend(
            ((x, y), shoes_name)
            for y in range(max(55, minimum_y), maximum_y + 1)
            for x in range(base_body.width)
            if base_body.getpixel((x, y))[3] != 0
        )
    exposed = next(
        (
            (point, expected_layer)
            for point, expected_layer in coverage_points
            if all(layer.getpixel(point)[3] == 0 for layer in layer_images.values())
        ),
        None,
    )
    if exposed is not None:
        point, expected_layer = exposed
        errors.append(
            "equipmentMappingContracts equipment union exposes base-body neutral "
            f"underwear/default shoes through {expected_layer}; first failure at local "
            f"{point}"
        )
    return errors


def _validate_v3_hand_protection(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    contract = spec.get("handProtectionContract")
    if not isinstance(contract, dict):
        return []

    coordinates = [tuple(point) for point in contract.get("pixelCoordinates", [])]
    errors: list[str] = []
    transparency = contract.get("adventureTopTransparency")
    if isinstance(transparency, dict):
        layer_name = transparency["layer"]
        required_alpha = transparency["requiredAlpha"]
        tile = _tile_image(image, spec, layer_name)
        point = next(
            (
                point
                for point in coordinates
                if tile.getpixel(point)[3] != required_alpha
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"handProtectionContract requires {layer_name} alpha="
                f"{required_alpha}; first covered hand pixel at local {point}"
            )

    source_name = contract.get("compositeVisibility", {}).get(
        "expectedSource",
        "base-body",
    )
    source = external_layers.get(source_name)
    if source is not None:
        for expected_pixel in contract.get("pixels", []):
            point = tuple(expected_pixel["coordinate"])
            expected = tuple(expected_pixel["rgba"])
            actual = source.getpixel(point)
            if actual != expected:
                errors.append(
                    f"handProtectionContract {source_name} protected hand RGBA differs "
                    f"at local {point}; expected {_format_rgba(expected)}, "
                    f"got {_format_rgba(actual)}"
                )
                break

        visibility = contract.get("compositeVisibility")
        if isinstance(visibility, dict):
            for tile_name in visibility.get("tiles", []):
                tile = _tile_image(image, spec, tile_name)
                point = next(
                    (
                        point
                        for point in coordinates
                        if tile.getpixel(point) != source.getpixel(point)
                    ),
                    None,
                )
                if point is not None:
                    errors.append(
                        f"handProtectionContract requires {tile_name} to preserve "
                        f"{source_name} hand RGBA; first mismatch at local {point}, "
                        f"expected {_format_rgba(source.getpixel(point))}, "
                        f"got {_format_rgba(tile.getpixel(point))}"
                    )
    return errors


def _validate_v3_targeted_edit_contract(
    image: Image.Image,
    spec: dict,
) -> list[str]:
    contract = spec.get("targetedEditContract")
    if not isinstance(contract, dict) or not contract.get("preservedTilesPixelExact"):
        return []

    expected_hashes = contract.get("preservedTileRgbaSha256", {})
    errors: list[str] = []
    for tile_name in contract.get("preservedTiles", []):
        expected_hash = expected_hashes.get(tile_name)
        if expected_hash is None:
            continue
        tile = _tile_image(image, spec, tile_name)
        actual_hash = hashlib.sha256(tile.tobytes()).hexdigest()
        if actual_hash == expected_hash:
            continue
        point = _first_opaque_point(tile) or (0, 0)
        errors.append(
            f"targetedEditContract preserved tile {tile_name} differs from its "
            f"pixel-exact RGBA contract; first failing local coordinate at local {point}"
        )
    return errors


def _validate_preserved_v2_layers(
    image: Image.Image,
    reference_sheet: Image.Image | None,
    spec: dict,
) -> list[str]:
    errors: list[str] = []
    for name, contract in spec["layerBoundaryContracts"].items():
        if not isinstance(contract, dict) or not contract.get("preserveSchemaV2Pixels"):
            continue
        actual = _tile_image(image, spec, name)
        actual_hash = hashlib.sha256(actual.tobytes()).hexdigest()
        if actual_hash == contract["rgbaSha256"]:
            continue
        if reference_sheet is not None:
            definition = _tile_definition(spec, name)
            expected = _tile_image_at(
                reference_sheet,
                spec,
                definition["row"],
                definition["column"],
            )
            difference = _first_pixel_difference(expected, actual)
            if difference is not None:
                point, expected_pixel, actual_pixel = difference
                errors.append(
                    f"{name} must preserve schema v2 pixels; first mismatch at local {point}, "
                    f"expected {_format_rgba(expected_pixel)}, got {_format_rgba(actual_pixel)}"
                )
                continue
        errors.append(
            f"{name} SHA-256 differs from schema v2 contract; first opaque local "
            f"{None if _opaque_bounding_box(actual) is None else _opaque_bounding_box(actual)[:2]}"
        )
    return errors


def _validate_v3_face_exclusions(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contract = spec["faceProtectionContract"]
    exclusion = contract["hairExclusion"]
    layer_name = exclusion["layer"]
    hair = _tile_image(image, spec, layer_name)

    left, top, right, bottom = exclusion["centralFaceOpenRegion"]
    for y in range(top, bottom + 1):
        point = next(
            ((x, y) for x in range(left, right + 1) if hair.getpixel((x, y))[3]),
            None,
        )
        if point is not None:
            errors.append(
                f"{layer_name} violates central face open region; "
                f"first opaque pixel at local {point}"
            )
            break

    neighborhood = exclusion["eyeNeighborhood"]
    radius = neighborhood["radius"]
    eye_points = {
        tuple(pixel["coordinate"])
        for feature_name in neighborhood["sourceFeatures"]
        for pixel in contract["features"][feature_name]
    }
    forbidden_neighbors = {
        (x + delta_x, y + delta_y)
        for x, y in eye_points
        for delta_x in range(-radius, radius + 1)
        for delta_y in range(-radius, radius + 1)
    }
    copied_point = next(
        (
            point
            for point in sorted(forbidden_neighbors, key=lambda item: (item[1], item[0]))
            if 0 <= point[0] < hair.width
            and 0 <= point[1] < hair.height
            and hair.getpixel(point)[3]
        ),
        None,
    )
    if copied_point is not None:
        errors.append(
            f"{layer_name} violates eye neighborhood protection; "
            f"first opaque pixel at local {copied_point}"
        )

    for feature_name in exclusion["directOverlapForbiddenFeatures"]:
        point = next(
            (
                tuple(pixel["coordinate"])
                for pixel in contract["features"][feature_name]
                if hair.getpixel(tuple(pixel["coordinate"]))[3]
            ),
            None,
        )
        if point is not None:
            errors.append(
                f"{layer_name} overlaps protected {feature_name}; "
                f"first opaque pixel at local {point}"
            )
    return errors


def _points_in_x_range(
    tile: Image.Image,
    x_range: list[int],
) -> list[tuple[int, int]]:
    return [
        (x, y)
        for y in range(tile.height)
        for x in range(x_range[0], x_range[1] + 1)
        if tile.getpixel((x, y))[3]
    ]


def _points_box(points: list[tuple[int, int]]) -> BoundingBox | None:
    if not points:
        return None
    return (
        min(point[0] for point in points),
        min(point[1] for point in points),
        max(point[0] for point in points),
        max(point[1] for point in points),
    )


def _box_size(box: BoundingBox | None) -> tuple[int, int] | None:
    if box is None:
        return None
    return box[2] - box[0] + 1, box[3] - box[1] + 1


def _first_box_point(box: BoundingBox | None) -> tuple[int, int] | None:
    return None if box is None else (box[0], box[1])


def _validate_v3_symmetry(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contracts = spec["symmetryContracts"]
    axis_x = contracts["mirrorAxisX"]

    hair_contract = contracts["defaultHairAlpha"]
    hair_layers = [_tile_image(image, spec, name) for name in hair_contract["layers"]]
    for y in range(hair_layers[0].height):
        mismatch = None
        for x in range(hair_layers[0].width):
            mirror_x = axis_x * 2 - x
            if not 0 <= mirror_x < hair_layers[0].width:
                continue
            alpha = max(layer.getpixel((x, y))[3] for layer in hair_layers)
            mirror_alpha = max(
                layer.getpixel((mirror_x, y))[3] for layer in hair_layers
            )
            if alpha != mirror_alpha:
                mismatch = (x, y)
                break
        if mismatch is not None:
            errors.append(
                "default-hair-back-layer/default-hair-front-layer alpha silhouette "
                f"is not mirrored around x={axis_x}; first mismatch at local {mismatch}"
            )
            break

    for sleeve in contracts["sleeves"]:
        name = sleeve["layer"]
        tile = _tile_image(image, spec, name)
        left_box = _points_box(_points_in_x_range(tile, sleeve["leftXRange"]))
        right_box = _points_box(_points_in_x_range(tile, sleeve["rightXRange"]))
        if _box_size(left_box) != _box_size(right_box) or (
            left_box is not None
            and right_box is not None
            and left_box[3] != right_box[3]
        ):
            point = _first_box_point(left_box) or _first_box_point(right_box)
            errors.append(
                f"{name} left/right sleeve bounds and end-y must match; "
                f"left={left_box}, right={right_box}, first opaque local {point}"
            )

    shorts = contracts["shortsHem"]
    shorts_tile = _tile_image(image, spec, shorts["layer"])
    left_box = _points_box(_points_in_x_range(shorts_tile, shorts["leftXRange"]))
    right_box = _points_box(_points_in_x_range(shorts_tile, shorts["rightXRange"]))
    if left_box is None or right_box is None or left_box[3] != right_box[3]:
        point = _first_box_point(left_box) or _first_box_point(right_box)
        errors.append(
            f"{shorts['layer']} left/right shorts hem end-y must match; "
            f"left={left_box}, right={right_box}, first opaque local {point}"
        )

    for shoes in contracts["shoes"]:
        name = shoes["layer"]
        tile = _tile_image(image, spec, name)
        left_box = _points_box(_points_in_x_range(tile, [0, axis_x - 1]))
        right_box = _points_box(_points_in_x_range(tile, [axis_x + 1, tile.width - 1]))
        if _box_size(left_box) != _box_size(right_box) or (
            left_box is not None
            and right_box is not None
            and left_box[3] != right_box[3]
        ):
            point = _first_box_point(left_box) or _first_box_point(right_box)
            errors.append(
                f"{name} left/right shoe bounding boxes and sole-y must match; "
                f"left={left_box}, right={right_box}, first opaque local {point}"
            )
    return errors


def _first_opaque_point(tile: Image.Image) -> tuple[int, int] | None:
    for index, pixel in enumerate(tile.get_flattened_data()):
        if pixel[3]:
            return _first_point(index, tile.width)
    return None


def _validate_v3_design_colors(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contracts = spec["designContracts"]
    for contract_name, contract in contracts.items():
        layer_names = contract["layers"] if "layers" in contract else [contract["layer"]]
        tiles = [_tile_image(image, spec, name) for name in layer_names]
        present_colors = {
            pixel[:3]
            for tile in tiles
            for pixel in tile.get_flattened_data()
            if pixel[3]
        }
        first_point = next(
            (
                _first_opaque_point(tile)
                for tile in tiles
                if _first_opaque_point(tile) is not None
            ),
            None,
        )
        for palette_name in contract["requiredPaletteNames"]:
            required = _hex_to_rgb(spec["palette"][palette_name])
            if required not in present_colors:
                errors.append(
                    f"{contract_name} design ({', '.join(layer_names)} tile(s)) is "
                    f"missing required palette color {palette_name}; "
                    f"first opaque local {first_point}"
                )

    top_contract = contracts["adventure-top"]
    bottom_contract = contracts["adventure-bottom"]
    if bottom_contract.get("mustBeDarkerThan") == "adventure-top":
        outline = _hex_to_rgb(spec["palette"][spec["outlinePaletteName"]])

        def average_luminance(layer_name: str) -> float:
            values = []
            for pixel in _tile_image(image, spec, layer_name).get_flattened_data():
                if pixel[3] == 0 or pixel[:3] == outline:
                    continue
                red, green, blue = pixel[:3]
                values.append(0.2126 * red + 0.7152 * green + 0.0722 * blue)
            return sum(values) / len(values) if values else 0.0

        top_luminance = average_luminance(top_contract["layer"])
        bottom_luminance = average_luminance(bottom_contract["layer"])
        if bottom_luminance >= top_luminance:
            point = _first_opaque_point(_tile_image(image, spec, bottom_contract["layer"]))
            errors.append(
                "adventure-bottom-layer must be darker than adventure-top-layer; "
                f"got bottom={bottom_luminance:.2f}, top={top_luminance:.2f}, "
                f"first opaque local {point}"
            )
    return errors


def _first_non_outline_boundary(
    tile: Image.Image,
    outline: Rgba,
    ignored_points: set[tuple[int, int]] | None = None,
) -> tuple[tuple[int, int], Rgba] | None:
    ignored = ignored_points or set()
    opaque_points = {
        (x, y)
        for y in range(tile.height)
        for x in range(tile.width)
        if tile.getpixel((x, y))[3]
    }
    for point in sorted(opaque_points, key=lambda item: (item[1], item[0])):
        if point in ignored:
            continue
        x, y = point
        is_boundary = any(
            (x + delta_x, y + delta_y) not in opaque_points
            for delta_x, delta_y in ((-1, 0), (1, 0), (0, -1), (0, 1))
        )
        pixel = tile.getpixel(point)
        if is_boundary and pixel != outline:
            return point, pixel
    return None


def _validate_v3_outlines(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    errors: list[str] = []
    outline: Rgba = (*_hex_to_rgb(spec["outlineColor"]), 255)
    excluded = set(spec["opaqueConnectivityContract"]["excludedTiles"])
    ignored_by_layer: dict[str, set[tuple[int, int]]] = {}
    for contract_name in ("adventureWaistOverlap", "adventureAnkleOverlap"):
        contract = spec.get("layerBoundaryContracts", {}).get(contract_name)
        if not isinstance(contract, dict):
            continue
        hidden_name = contract.get("hiddenOverlapLayer")
        if isinstance(hidden_name, str):
            ignored_by_layer.setdefault(hidden_name, set()).update(
                _overlap_points(contract)
            )
    for contract_name, hidden_name, covering_name in (
        (
            "adventureWaistOverlap",
            "adventure-bottom-layer",
            "adventure-top-layer",
        ),
        (
            "adventureAnkleOverlap",
            "adventure-shoes-layer",
            "adventure-bottom-layer",
        ),
    ):
        if isinstance(
            spec.get("layerBoundaryContracts", {}).get(contract_name),
            dict,
        ):
            continue
        hidden = _tile_image(image, spec, hidden_name)
        covering = _tile_image(image, spec, covering_name)
        ignored_by_layer.setdefault(hidden_name, set()).update(
            (x, y)
            for y in range(hidden.height)
            for x in range(hidden.width)
            if hidden.getpixel((x, y))[3] != 0
            and covering.getpixel((x, y))[3] != 0
        )
    for definition in spec["tileMap"]:
        name = definition["name"]
        if name in excluded:
            continue
        failure = _first_non_outline_boundary(
            _tile_image(image, spec, name),
            outline,
            ignored_by_layer.get(name),
        )
        if failure is not None:
            point, pixel = failure
            errors.append(
                f"{name} tile external silhouette must use outlineDarkNavy; "
                f"first boundary mismatch at local {point}, got {_format_rgba(pixel)}"
            )

    for name, external in external_layers.items():
        failure = _first_non_outline_boundary(external, outline)
        if failure is not None:
            point, pixel = failure
            errors.append(
                f"{name} external layer external silhouette must use outlineDarkNavy; "
                f"first boundary mismatch at local {point}, got {_format_rgba(pixel)}"
            )
    return errors


def _connected_components(
    tile: Image.Image,
    connectivity: int,
) -> list[list[tuple[int, int]]]:
    remaining = {
        (x, y)
        for y in range(tile.height)
        for x in range(tile.width)
        if tile.getpixel((x, y))[3]
    }
    if connectivity == 8:
        offsets = [
            (delta_x, delta_y)
            for delta_y in (-1, 0, 1)
            for delta_x in (-1, 0, 1)
            if delta_x != 0 or delta_y != 0
        ]
    elif connectivity == 4:
        offsets = [(-1, 0), (1, 0), (0, -1), (0, 1)]
    else:
        raise ValueError("opaqueConnectivityContract.connectivity must be 4 or 8")

    components: list[list[tuple[int, int]]] = []
    while remaining:
        start = min(remaining, key=lambda item: (item[1], item[0]))
        remaining.remove(start)
        component = [start]
        stack = [start]
        while stack:
            x, y = stack.pop()
            neighbors = {
                (x + delta_x, y + delta_y) for delta_x, delta_y in offsets
            }
            discovered = neighbors & remaining
            remaining.difference_update(discovered)
            stack.extend(discovered)
            component.extend(discovered)
        components.append(component)
    return components


def _validate_v3_connectivity(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
) -> list[str]:
    errors: list[str] = []
    contract = spec["opaqueConnectivityContract"]
    excluded = set(contract["excludedTiles"])
    minimum_size = contract["minimumConnectedComponentPixelCount"]
    named_images = [
        (definition["name"], _tile_image(image, spec, definition["name"]), "tile")
        for definition in spec["tileMap"]
        if definition["name"] not in excluded
    ]
    named_images.extend(
        (name, external, "external layer")
        for name, external in external_layers.items()
    )
    for name, tile, kind in named_images:
        components = _connected_components(tile, contract["connectivity"])
        small = next(
            (component for component in components if len(component) < minimum_size),
            None,
        )
        if small is not None:
            point = min(small, key=lambda item: (item[1], item[0]))
            errors.append(
                f"{name} {kind} has an opaque connected component of "
                f"{len(small)} pixel(s) at local {point}; minimum is {minimum_size}"
            )
    return errors


def _validate_schema_v3(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap,
    reference_body: Image.Image | None,
    reference_sheet: Image.Image | None,
) -> list[str]:
    errors: list[str] = []
    errors.extend(_validate_modular_pixels(image, spec))
    errors.extend(_validate_v3_tile_map(image, spec))
    errors.extend(_validate_v3_geometry(image, spec, external_layers))
    errors.extend(_validate_layer_bounds(image, spec))
    errors.extend(_validate_v3_layer_boundaries(image, spec))
    errors.extend(_validate_v3_adventure_overlaps(image, spec))
    errors.extend(_validate_v3_adventure_bottom_geometry(image, spec))
    errors.extend(_validate_v3_equipment_mapping(image, spec, external_layers))
    errors.extend(_validate_v3_adventure_design_palettes(image, spec))
    errors.extend(_validate_v3_hand_protection(image, spec, external_layers))
    errors.extend(_validate_v3_compositions(image, spec, external_layers))
    errors.extend(_validate_modular_guides(image, spec))
    errors.extend(_validate_palette_grid(image, spec))
    errors.extend(_validate_v3_face_exclusions(image, spec))
    errors.extend(_validate_v3_symmetry(image, spec))
    errors.extend(_validate_v3_design_colors(image, spec))
    errors.extend(_validate_preserved_v2_layers(image, reference_sheet, spec))
    errors.extend(_validate_v3_targeted_edit_contract(image, spec))
    errors.extend(_validate_v3_outlines(image, spec, external_layers))
    errors.extend(_validate_v3_connectivity(image, spec, external_layers))
    base_body = external_layers.get("base-body")
    if base_body is not None:
        errors.extend(_validate_external_base_body(base_body, reference_body, spec))
    return errors


def _validate_modular_sheet(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    errors.extend(_validate_modular_pixels(image, spec))
    errors.extend(_validate_reserved_tiles(image, spec))
    errors.extend(_validate_modular_geometry(image, spec))
    errors.extend(_validate_layer_bounds(image, spec))
    errors.extend(_validate_compositions(image, spec))
    errors.extend(_validate_modular_guides(image, spec))
    errors.extend(_validate_palette_grid(image, spec))
    return errors


def _validate_loaded_sheet(
    image: Image.Image,
    spec: dict,
    external_layers: ImageMap | None = None,
    reference_body: Image.Image | None = None,
    reference_sheet: Image.Image | None = None,
) -> list[str]:
    expected_size = (spec["sheet"]["width"], spec["sheet"]["height"])
    expected_mode = spec["sheet"]["mode"]
    errors: list[str] = []

    if image.size != expected_size:
        errors.append(f"sheet size must be {expected_size}; got {image.size}")
    if image.mode != expected_mode:
        errors.append(f"sheet mode must be {expected_mode}; got {image.mode}")
    if errors:
        return errors

    schema_version = spec["schemaVersion"]
    if schema_version == 1:
        errors.extend(_validate_pixels(image, spec))
        errors.extend(_validate_reference_tiles(image, spec))
        errors.extend(_validate_anchor_guides(image, spec))
        errors.extend(_validate_palette_tile(image, spec))
    elif schema_version == 2:
        errors.extend(_validate_modular_sheet(image, spec))
    elif schema_version == 3:
        errors.extend(
            _validate_schema_v3(
                image,
                spec,
                external_layers or {},
                reference_body,
                reference_sheet,
            )
        )
    else:
        raise ValueError(f"unsupported schemaVersion: {schema_version}")
    return errors


def validate_sheet(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]:
    """Return contract violations without modifying the source PNG."""
    try:
        spec = load_spec(spec_path)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        return [f"could not load specification {spec_path}: {error}"]

    external_layers: ImageMap = {}
    reference_body: Image.Image | None = None
    reference_sheet: Image.Image | None = None
    if spec.get("schemaVersion") == 3:
        try:
            external_layers, external_errors = _load_external_layers(spec_path, spec)
            reference_body, reference_sheet, reference_errors = (
                _load_original_body_reference(spec_path, spec)
            )
        except (KeyError, TypeError, ValueError, IndexError) as error:
            return [f"invalid character sheet specification: {error}"]
        preload_errors = [*external_errors, *reference_errors]
        if preload_errors:
            return preload_errors

    try:
        with Image.open(image_path) as image:
            image.load()
            if spec.get("schemaVersion") in {5, 6}:
                from build_character_assets import validate_generated_sheet

                return validate_generated_sheet(image, spec_path, spec)
            return _validate_loaded_sheet(
                image,
                spec,
                external_layers,
                reference_body,
                reference_sheet,
            )
    except OSError as error:
        return [f"could not load image {image_path}: {error}"]
    except (KeyError, TypeError, ValueError, IndexError) as error:
        return [f"invalid character sheet specification: {error}"]


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate a Todo Quest character PNG against its JSON contract."
    )
    parser.add_argument(
        "--image",
        type=pathlib.Path,
        default=DEFAULT_IMAGE_PATH,
        help="PNG to validate",
    )
    parser.add_argument(
        "--spec",
        type=pathlib.Path,
        default=DEFAULT_SPEC_PATH,
        help="contract JSON",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    errors = validate_sheet(args.image, args.spec)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"Character sheet validation passed: {args.image}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
