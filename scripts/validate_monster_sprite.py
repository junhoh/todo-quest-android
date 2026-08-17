"""Validate one Todo Quest monster PNG against its pixel-art contract."""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
from collections.abc import Mapping, Sequence

from PIL import Image


Rgb = tuple[int, int, int]
Rgba = tuple[int, int, int, int]
Point = tuple[int, int]
BoundingBox = tuple[int, int, int, int]


def load_spec(path: pathlib.Path) -> dict:
    """Load a monster sprite contract from a UTF-8 JSON file."""
    spec = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(spec, dict):
        raise ValueError("monster sprite specification must be a JSON object")
    return spec


def _hex_to_rgb(value: str) -> Rgb:
    if not isinstance(value, str):
        raise TypeError("RGB color must be a string")
    normalized = value.removeprefix("#")
    if len(normalized) != 6:
        raise ValueError(f"invalid RGB color: {value}")
    return tuple(int(normalized[index:index + 2], 16) for index in (0, 2, 4))


def _format_rgb(color: Rgb) -> str:
    return "#" + "".join(f"{component:02X}" for component in color)


def _first_point(index: int, width: int) -> Point:
    return index % width, index // width


def _opaque_points(image: Image.Image) -> set[Point]:
    return {
        _first_point(index, image.width)
        for index, pixel in enumerate(image.get_flattened_data())
        if pixel[3] != 0
    }


def _opaque_bounding_box(points: set[Point]) -> BoundingBox | None:
    if not points:
        return None
    return (
        min(point[0] for point in points),
        min(point[1] for point in points),
        max(point[0] for point in points),
        max(point[1] for point in points),
    )


def _point_in_region(point: Point, region: Sequence[int], inclusive: bool) -> bool:
    left, top, right, bottom = region
    x, y = point
    if inclusive:
        return left <= x <= right and top <= y <= bottom
    return left < x < right and top < y < bottom


def _palette_points(
    image: Image.Image,
    spec: dict,
    palette_names: Sequence[str],
) -> list[Point]:
    colors = {_hex_to_rgb(spec["palette"][name]) for name in palette_names}
    return [
        _first_point(index, image.width)
        for index, pixel in enumerate(image.get_flattened_data())
        if pixel[3] != 0 and pixel[:3] in colors
    ]


def _mapping(value: object, field_name: str) -> Mapping:
    if not isinstance(value, Mapping):
        raise TypeError(f"{field_name} must be an object")
    return value


def _integer(value: object, field_name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{field_name} must be an integer")
    return value


def _boolean(value: object, field_name: str) -> bool:
    if not isinstance(value, bool):
        raise TypeError(f"{field_name} must be a boolean")
    return value


def _integer_range(
    value: object,
    field_name: str,
    expected_length: int,
) -> tuple[int, ...]:
    if isinstance(value, (str, bytes)) or not isinstance(value, Sequence):
        raise TypeError(f"{field_name} must be an array")
    if len(value) != expected_length:
        raise ValueError(
            f"{field_name} must contain exactly {expected_length} integers"
        )
    return tuple(
        _integer(item, f"{field_name}[{index}]")
        for index, item in enumerate(value)
    )


def _count_range(contract: object, field_name: str) -> tuple[int, int | None]:
    values = _mapping(contract, field_name)
    minimum = _integer(values["min"], f"{field_name}.min")
    maximum_value = values.get("max")
    maximum = (
        None
        if maximum_value is None
        else _integer(maximum_value, f"{field_name}.max")
    )
    if minimum < 0:
        raise ValueError(f"{field_name}.min must be non-negative")
    if maximum is not None and maximum < minimum:
        raise ValueError(f"{field_name}.max must be greater than or equal to min")
    return minimum, maximum


def _palette_names(
    semantic_name: str,
    contract: Mapping,
    spec: dict,
) -> tuple[tuple[str, ...], str]:
    has_single = "paletteName" in contract
    has_multiple = "paletteNames" in contract
    if has_single == has_multiple:
        raise ValueError(
            f"semanticRegions.{semantic_name} must declare exactly one of "
            "paletteName or paletteNames"
        )

    if has_single:
        palette_name = contract["paletteName"]
        if not isinstance(palette_name, str):
            raise TypeError(
                f"semanticRegions.{semantic_name}.paletteName must be a string"
            )
        names = (palette_name,)
        label = palette_name
    else:
        palette_names = contract["paletteNames"]
        if isinstance(palette_names, (str, bytes)) or not isinstance(
            palette_names,
            Sequence,
        ):
            raise TypeError(
                f"semanticRegions.{semantic_name}.paletteNames must be an array"
            )
        if not palette_names:
            raise ValueError(
                f"semanticRegions.{semantic_name}.paletteNames must not be empty"
            )
        if not all(isinstance(name, str) for name in palette_names):
            raise TypeError(
                f"semanticRegions.{semantic_name}.paletteNames must contain strings"
            )
        names = tuple(palette_names)
        label = "palette color"

    palette = _mapping(spec["palette"], "palette")
    missing = [name for name in names if name not in palette]
    if missing:
        raise ValueError(
            f"semanticRegions.{semantic_name} references unknown palette name "
            f"{missing[0]}"
        )
    return names, label


def _validate_pixels(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    palette = {_hex_to_rgb(value) for value in spec["palette"].values()}
    chroma_key = _hex_to_rgb(spec["chromaKey"])
    allowed_alpha_values = set(spec["allowedAlphaValues"])
    count_transparent = spec.get("transparentPixelsCountTowardPalette", False)
    chroma_key_allowed = spec.get("chromaKeyAllowedInFinalPng", False)

    invalid_alpha: list[tuple[Point, int]] = []
    outside_palette: list[tuple[Point, Rgb]] = []
    chroma_key_points: list[Point] = []
    for index, pixel in enumerate(image.get_flattened_data()):
        point = _first_point(index, image.width)
        rgb = pixel[:3]
        alpha = pixel[3]
        if alpha not in allowed_alpha_values:
            invalid_alpha.append((point, alpha))
        if (alpha != 0 or count_transparent) and rgb not in palette:
            outside_palette.append((point, rgb))
        if not chroma_key_allowed and rgb == chroma_key:
            chroma_key_points.append(point)

    if invalid_alpha:
        point, alpha = invalid_alpha[0]
        errors.append(
            f"pixel alpha must be one of {sorted(allowed_alpha_values)}; "
            f"found {len(invalid_alpha)} invalid pixel(s), first at {point} "
            f"with alpha={alpha}"
        )
    if outside_palette:
        point, color = outside_palette[0]
        errors.append(
            f"pixel color {_format_rgb(color)} at {point} is outside contract palette "
            f"({len(outside_palette)} invalid pixel(s))"
        )
    if chroma_key_points:
        errors.append(
            f"chroma key {_format_rgb(chroma_key)} remains at {chroma_key_points[0]} "
            f"({len(chroma_key_points)} pixel(s))"
        )
    return errors


def _validate_geometry(
    image: Image.Image,
    spec: dict,
    opaque: set[Point],
) -> list[str]:
    box = _opaque_bounding_box(opaque)
    if box is None:
        return ["sprite has no opaque pixels for geometry validation"]

    errors: list[str] = []
    expected_box = tuple(spec["expectedOpaqueBoundingBox"])
    if not spec.get("boundingBoxInclusive", True):
        box_for_contract = box[0], box[1], box[2] + 1, box[3] + 1
    else:
        box_for_contract = box
    if box_for_contract != expected_box:
        errors.append(
            f"opaque bounding box must be {expected_box}; got {box_for_contract}"
        )

    left, top, right, bottom = box
    center_x = spec["centerX"]
    if left + right != center_x * 2:
        errors.append(
            f"center axis must be x={center_x}; opaque bounding box spans "
            f"x={left}..{right}"
        )

    margins = (
        left,
        top,
        image.width - 1 - right,
        image.height - 1 - bottom,
    )
    minimum_margin = spec["minimumMargin"]
    if min(margins) < minimum_margin:
        errors.append(
            f"minimum margin must be at least {minimum_margin}; got "
            f"left={margins[0]}, top={margins[1]}, right={margins[2]}, "
            f"bottom={margins[3]}"
        )

    sole_y = spec["soleY"]
    if bottom != sole_y:
        errors.append(f"sole must be y={sole_y}; got y={bottom}")

    if "opaqueHeight" in spec:
        actual_height = bottom - top + 1
        if actual_height != spec["opaqueHeight"]:
            errors.append(
                f"opaque height must be {spec['opaqueHeight']}; got {actual_height}"
            )
    return errors


def _validate_required_palette(image: Image.Image, spec: dict) -> list[str]:
    count_transparent = spec.get("transparentPixelsCountTowardPalette", False)
    present_colors = {
        pixel[:3]
        for pixel in image.get_flattened_data()
        if pixel[3] != 0 or count_transparent
    }
    missing_names = [
        name
        for name in spec["requiredPaletteNames"]
        if _hex_to_rgb(spec["palette"][name]) not in present_colors
    ]
    if not missing_names:
        return []
    return [f"required palette color is missing: {', '.join(missing_names)}"]


def _validate_pixel_count(
    semantic_name: str,
    palette_name: str,
    points: Sequence[Point],
    contract: dict,
) -> list[str]:
    if "pixelCount" not in contract:
        return []
    minimum, maximum = _count_range(
        contract["pixelCount"],
        f"semanticRegions.{semantic_name}.pixelCount",
    )
    count = len(points)
    if count >= minimum and (maximum is None or count <= maximum):
        return []
    if maximum is None:
        expected = f"at least {minimum}"
    else:
        expected = f"{minimum}..{maximum}"
    return [
        f"{semantic_name} {palette_name} pixel count must be {expected}; "
        f"got {count}"
    ]


def _validate_region_points(
    semantic_name: str,
    palette_name: str,
    points: Sequence[Point],
    contract: dict,
) -> list[str]:
    only_within = contract.get("onlyWithinRegion", False)
    if not isinstance(only_within, bool):
        raise TypeError(
            f"semanticRegions.{semantic_name}.onlyWithinRegion must be a boolean"
        )
    if not only_within:
        return []
    region = _integer_range(
        contract["region"],
        f"semanticRegions.{semantic_name}.region",
        4,
    )
    inclusive = _boolean(
        contract.get("regionInclusive", True),
        f"semanticRegions.{semantic_name}.regionInclusive",
    )
    outside = [
        point
        for point in points
        if not _point_in_region(point, region, inclusive)
    ]
    if not outside:
        return []
    return [
        f"{semantic_name} {palette_name} appears outside {semantic_name} region; "
        f"found {len(outside)} invalid pixel(s), first at {outside[0]}"
    ]


def _validate_semantic_regions(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    contracts = _mapping(spec.get("semanticRegions", {}), "semanticRegions")

    for semantic_name, contract_value in contracts.items():
        if not isinstance(semantic_name, str):
            raise TypeError("semanticRegions names must be strings")
        contract = _mapping(
            contract_value,
            f"semanticRegions.{semantic_name}",
        )
        palette_names, palette_label = _palette_names(
            semantic_name,
            contract,
            spec,
        )
        points = _palette_points(image, spec, palette_names)
        errors.extend(
            _validate_pixel_count(
                semantic_name,
                palette_label,
                points,
                contract,
            )
        )
        errors.extend(
            _validate_region_points(
                semantic_name,
                palette_label,
                points,
                contract,
            )
        )

        if "mustNotOverlapXRange" in contract:
            center_range = _integer_range(
                contract["mustNotOverlapXRange"],
                f"semanticRegions.{semantic_name}.mustNotOverlapXRange",
                2,
            )
            center_inclusive = _boolean(
                contract.get("xRangeInclusive", True),
                f"semanticRegions.{semantic_name}.xRangeInclusive",
            )
            over_center = [
                point
                for point in points
                if (
                    center_range[0] <= point[0] <= center_range[1]
                    if center_inclusive
                    else center_range[0] < point[0] < center_range[1]
                )
            ]
            if over_center:
                errors.append(
                    f"{semantic_name} palette color overlaps center body x-range "
                    f"{center_range}; first at {over_center[0]}"
                )

        if "opaqueBoundingBoxHeight" in contract:
            minimum, maximum = _count_range(
                contract["opaqueBoundingBoxHeight"],
                f"semanticRegions.{semantic_name}.opaqueBoundingBoxHeight",
            )
            if points:
                minimum_y = min(point[1] for point in points)
                maximum_y = max(point[1] for point in points)
                height = maximum_y - minimum_y + 1
                if height < minimum or (
                    maximum is not None and height > maximum
                ):
                    maximum_text = (
                        f"..{maximum}"
                        if maximum is not None
                        else " or more"
                    )
                    errors.append(
                        f"{semantic_name} opaque bounding box height must be "
                        f"{minimum}{maximum_text}px; got {height}"
                    )
            else:
                errors.append(
                    f"{semantic_name} opaque bounding box height cannot be measured"
                )
    return errors


def _validate_ground_contacts(opaque: set[Point], spec: dict) -> list[str]:
    errors: list[str] = []
    contacts_value = spec.get("groundContacts")
    if contacts_value is None:
        return errors
    contacts = _mapping(contacts_value, "groundContacts")

    for contact_name, contract_value in contacts.items():
        if not isinstance(contact_name, str):
            raise TypeError("groundContacts names must be strings")
        contract = _mapping(
            contract_value,
            f"groundContacts.{contact_name}",
        )
        x_range = _integer_range(
            contract["xRange"],
            f"groundContacts.{contact_name}.xRange",
            2,
        )
        y = _integer(contract["y"], f"groundContacts.{contact_name}.y")
        minimum = _integer(
            contract["minimumOpaquePixels"],
            f"groundContacts.{contact_name}.minimumOpaquePixels",
        )
        if minimum < 0:
            raise ValueError(
                f"groundContacts.{contact_name}.minimumOpaquePixels "
                "must be non-negative"
            )
        inclusive = _boolean(
            contract.get("xRangeInclusive", True),
            f"groundContacts.{contact_name}.xRangeInclusive",
        )
        count = sum(
            1
            for x, point_y in opaque
            if point_y == y
            and (
                x_range[0] <= x <= x_range[1]
                if inclusive
                else x_range[0] < x < x_range[1]
            )
        )
        if count < minimum:
            errors.append(
                f"{contact_name} ground contact at y={y} in x-range {x_range} "
                f"must contain at least {minimum} opaque pixel(s); got {count}"
            )
    return errors


def _validate_transparent_regions(image: Image.Image, spec: dict) -> list[str]:
    errors: list[str] = []
    regions_value = spec.get("transparentRegions")
    if regions_value is None:
        return errors
    regions = _mapping(regions_value, "transparentRegions")

    for region_name, contract_value in regions.items():
        if not isinstance(region_name, str):
            raise TypeError("transparentRegions names must be strings")
        contract = _mapping(
            contract_value,
            f"transparentRegions.{region_name}",
        )
        region = _integer_range(
            contract["region"],
            f"transparentRegions.{region_name}.region",
            4,
        )
        inclusive = _boolean(
            contract.get("regionInclusive", True),
            f"transparentRegions.{region_name}.regionInclusive",
        )
        minimum, maximum = _count_range(
            contract["pixelCount"],
            f"transparentRegions.{region_name}.pixelCount",
        )
        transparent_count = sum(
            1
            for y in range(image.height)
            for x in range(image.width)
            if _point_in_region((x, y), region, inclusive)
            and image.getpixel((x, y))[3] == 0
        )
        if transparent_count < minimum or (
            maximum is not None and transparent_count > maximum
        ):
            if maximum is None:
                expected = f"minimum {minimum}"
            else:
                expected = f"minimum {minimum} and maximum {maximum}"
            errors.append(
                f"{region_name} transparent pixel count must be {expected}; "
                f"got {transparent_count}"
            )
    return errors


def _validate_mirrored_regions(image: Image.Image, spec: dict) -> list[str]:
    regions_value = spec.get("mirroredRegions")
    if regions_value is None:
        return []
    regions = _mapping(regions_value, "mirroredRegions")
    errors: list[str] = []

    for region_name, contract_value in regions.items():
        if not isinstance(region_name, str):
            raise TypeError("mirroredRegions names must be strings")
        field_name = f"mirroredRegions.{region_name}"
        contract = _mapping(contract_value, field_name)
        axis_x = _integer(contract["axisX"], f"{field_name}.axisX")
        left_region = _integer_range(
            contract["leftRegion"],
            f"{field_name}.leftRegion",
            4,
        )
        right_region = _integer_range(
            contract["rightRegion"],
            f"{field_name}.rightRegion",
            4,
        )
        inclusive = _boolean(
            contract.get("regionInclusive", True),
            f"{field_name}.regionInclusive",
        )
        comparison = contract["comparison"]
        if not isinstance(comparison, str):
            raise TypeError(f"{field_name}.comparison must be a string")
        if comparison != "rgba":
            raise ValueError(f"{field_name}.comparison must be rgba")
        if not 0 <= axis_x < image.width:
            raise ValueError(
                f"{field_name}.axisX must be within image bounds; got {axis_x}"
            )

        effective_regions: list[BoundingBox] = []
        for side, region in (
            ("leftRegion", left_region),
            ("rightRegion", right_region),
        ):
            left, top, right, bottom = region
            if not (
                0 <= left < image.width
                and 0 <= right < image.width
                and 0 <= top < image.height
                and 0 <= bottom < image.height
            ):
                raise ValueError(
                    f"{field_name}.{side} must be within image bounds; got {region}"
                )
            inset = 0 if inclusive else 1
            effective = left + inset, top + inset, right - inset, bottom - inset
            if effective[0] > effective[2] or effective[1] > effective[3]:
                raise ValueError(
                    f"{field_name}.{side} must describe a non-empty region"
                )
            effective_regions.append(effective)

        left_effective, right_effective = effective_regions
        left_width = left_effective[2] - left_effective[0] + 1
        left_height = left_effective[3] - left_effective[1] + 1
        right_width = right_effective[2] - right_effective[0] + 1
        right_height = right_effective[3] - right_effective[1] + 1
        if (left_width, left_height) != (right_width, right_height):
            raise ValueError(
                f"{field_name} leftRegion and rightRegion must have the same "
                "width and height"
            )

        overlaps = (
            max(left_effective[0], right_effective[0])
            <= min(left_effective[2], right_effective[2])
            and max(left_effective[1], right_effective[1])
            <= min(left_effective[3], right_effective[3])
        )
        if overlaps:
            raise ValueError(
                f"{field_name} leftRegion and rightRegion must not overlap"
            )
        if left_effective[2] >= axis_x:
            raise ValueError(
                f"{field_name}.leftRegion must stay strictly left of axisX={axis_x}"
            )
        if right_effective[0] <= axis_x:
            raise ValueError(
                f"{field_name}.rightRegion must stay strictly right of axisX={axis_x}"
            )

        expected_right = (
            2 * axis_x - left_region[2],
            left_region[1],
            2 * axis_x - left_region[0],
            left_region[3],
        )
        if right_region != expected_right:
            raise ValueError(
                f"{field_name} regions must be exact reflections around "
                f"axisX={axis_x}; expected rightRegion {expected_right}, "
                f"got {right_region}"
            )

        mismatch_found = False
        for y in range(left_effective[1], left_effective[3] + 1):
            for left_x in range(left_effective[0], left_effective[2] + 1):
                left_point = left_x, y
                right_point = 2 * axis_x - left_x, y
                left_rgba = image.getpixel(left_point)
                right_rgba = image.getpixel(right_point)
                if left_rgba != right_rgba:
                    errors.append(
                        f"{field_name} rgba mismatch: left {left_point}="
                        f"{left_rgba}, right {right_point}={right_rgba}"
                    )
                    mismatch_found = True
                    break
            if mismatch_found:
                break
    return errors


def _neighbor_offsets(mode: int) -> tuple[Point, ...]:
    if mode == 4:
        return (-1, 0), (1, 0), (0, -1), (0, 1)
    if mode == 8:
        return tuple(
            (delta_x, delta_y)
            for delta_y in (-1, 0, 1)
            for delta_x in (-1, 0, 1)
            if delta_x != 0 or delta_y != 0
        )
    raise ValueError("neighbor mode must be 4 or 8")


def _external_transparent_points(
    image: Image.Image,
    opaque: set[Point],
    neighbor_mode: int,
) -> set[Point]:
    transparent = {
        (x, y)
        for y in range(image.height)
        for x in range(image.width)
        if (x, y) not in opaque
    }
    remaining = set(transparent)
    stack = [
        point
        for point in transparent
        if point[0] in (0, image.width - 1)
        or point[1] in (0, image.height - 1)
    ]
    exterior = set(stack)
    remaining.difference_update(exterior)
    offsets = _neighbor_offsets(neighbor_mode)
    while stack:
        x, y = stack.pop()
        discovered = {
            (x + delta_x, y + delta_y)
            for delta_x, delta_y in offsets
            if (x + delta_x, y + delta_y) in remaining
        }
        remaining.difference_update(discovered)
        exterior.update(discovered)
        stack.extend(discovered)
    return exterior


def _external_boundary_points(
    image: Image.Image,
    opaque: set[Point],
    neighbor_mode: int,
) -> set[Point]:
    exterior = _external_transparent_points(image, opaque, neighbor_mode)
    offsets = _neighbor_offsets(neighbor_mode)
    return {
        (x, y)
        for x, y in opaque
        if any(
            not (0 <= x + delta_x < image.width and 0 <= y + delta_y < image.height)
            or (x + delta_x, y + delta_y) in exterior
            for delta_x, delta_y in offsets
        )
    }


def _validate_outline(
    image: Image.Image,
    spec: dict,
    opaque: set[Point],
) -> list[str]:
    contract = spec["outline"]
    mode = contract["externalBoundaryNeighborMode"]
    boundary = _external_boundary_points(image, opaque, mode)
    outline: Rgba = (*_hex_to_rgb(contract["color"]), 255)
    errors: list[str] = []

    if contract.get("allExternalBoundaryPixelsUseOutlineColor", False):
        mismatches = [
            point
            for point in sorted(boundary, key=lambda item: (item[1], item[0]))
            if image.getpixel(point) != outline
        ]
        if mismatches:
            point = mismatches[0]
            errors.append(
                "external boundary must use outlineDarkNavy "
                f"{_format_rgb(outline[:3])}; found {len(mismatches)} invalid "
                f"pixel(s), first at {point}"
            )

    if contract.get("forbidExternalOutline2x2Blocks", False):
        outline_points = {
            point for point in opaque if image.getpixel(point) == outline
        }
        first_block = None
        for y in range(image.height - 1):
            for x in range(image.width - 1):
                block = {
                    (x, y),
                    (x + 1, y),
                    (x, y + 1),
                    (x + 1, y + 1),
                }
                if block <= outline_points and block & boundary:
                    first_block = x, y
                    break
            if first_block is not None:
                break
        if first_block is not None:
            errors.append(
                f"external outline contains a forbidden 2x2 block at {first_block}"
            )
    return errors


def _connected_components(
    points: set[Point],
    neighbor_mode: int,
) -> list[set[Point]]:
    remaining = set(points)
    offsets = _neighbor_offsets(neighbor_mode)
    components: list[set[Point]] = []
    while remaining:
        start = min(remaining, key=lambda item: (item[1], item[0]))
        remaining.remove(start)
        component = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            discovered = {
                (x + delta_x, y + delta_y)
                for delta_x, delta_y in offsets
                if (x + delta_x, y + delta_y) in remaining
            }
            remaining.difference_update(discovered)
            component.update(discovered)
            stack.extend(discovered)
        components.append(component)
    return components


def _validate_connectivity(opaque: set[Point], spec: dict) -> list[str]:
    contract = spec["connectivity"]
    mode = contract["opaqueNeighborMode"]
    components = _connected_components(opaque, mode)
    errors: list[str] = []
    expected_count = contract["opaqueComponentCount"]
    if len(components) != expected_count:
        errors.append(
            f"opaque pixels must form exactly {expected_count} {mode}-connected "
            f"component(s); got {len(components)}"
        )

    if not contract.get("allowIsolatedOpaquePixels", True):
        offsets = _neighbor_offsets(mode)
        isolated = [
            point
            for point in sorted(opaque, key=lambda item: (item[1], item[0]))
            if not any(
                (point[0] + delta_x, point[1] + delta_y) in opaque
                for delta_x, delta_y in offsets
            )
        ]
        if isolated:
            errors.append(
                f"isolated opaque pixel is forbidden; found {len(isolated)}, "
                f"first at {isolated[0]}"
            )
    return errors


def _validate_loaded_sprite(image: Image.Image, spec: dict) -> list[str]:
    if spec["schemaVersion"] != 1:
        raise ValueError(f"unsupported schemaVersion: {spec['schemaVersion']}")

    asset = spec["asset"]
    expected_size = asset["width"], asset["height"]
    errors: list[str] = []
    if image.size != expected_size:
        errors.append(f"sprite size must be {expected_size}; got {image.size}")
    if image.mode != asset["mode"]:
        errors.append(f"sprite mode must be {asset['mode']}; got {image.mode}")
    if errors:
        return errors

    opaque = _opaque_points(image)
    errors.extend(_validate_pixels(image, spec))
    errors.extend(_validate_geometry(image, spec, opaque))
    errors.extend(_validate_required_palette(image, spec))
    errors.extend(_validate_semantic_regions(image, spec))
    errors.extend(_validate_ground_contacts(opaque, spec))
    errors.extend(_validate_transparent_regions(image, spec))
    errors.extend(_validate_mirrored_regions(image, spec))
    errors.extend(_validate_outline(image, spec, opaque))
    errors.extend(_validate_connectivity(opaque, spec))
    return errors


def validate_sprite(image_path: pathlib.Path, spec_path: pathlib.Path) -> list[str]:
    """Return every contract violation without changing the source PNG."""
    try:
        spec = load_spec(spec_path)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        return [f"could not load specification {spec_path}: {error}"]

    try:
        with Image.open(image_path) as image:
            image.load()
            return _validate_loaded_sprite(image, spec)
    except OSError as error:
        return [f"could not load image {image_path}: {error}"]
    except (KeyError, TypeError, ValueError, IndexError) as error:
        return [f"invalid monster sprite specification: {error}"]


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate a Todo Quest monster PNG against its JSON contract."
    )
    parser.add_argument("--image", required=True, type=pathlib.Path, help="PNG to validate")
    parser.add_argument("--spec", required=True, type=pathlib.Path, help="contract JSON")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    errors = validate_sprite(args.image, args.spec)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    print(f"Monster sprite validation passed: {args.image}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
