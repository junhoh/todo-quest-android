"""Build and validate independent Todo Quest character layers."""

from __future__ import annotations

import argparse
import copy
import hashlib
import io
import itertools
import json
import pathlib
import sys
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

from PIL import Image


SCRIPTS_DIR = pathlib.Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPTS_DIR.parent
DEFAULT_SPEC_PATH = (
    REPOSITORY_ROOT
    / "docs"
    / "art"
    / "character"
    / "character-modular-sheet-spec.json"
)
DEFAULT_RUNTIME_SHEET_PATH = (
    REPOSITORY_ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_character_modular_sheet.png"
)
DEFAULT_EQUIPMENT_GLOVES_SPEC_PATH = (
    REPOSITORY_ROOT
    / "docs"
    / "art"
    / "equipment"
    / "todo-quest-gloves-shoes-layers-spec.json"
)

Rgba = tuple[int, int, int, int]
Point = tuple[int, int]
Bounds = tuple[int, int, int, int]
ImageMap = dict[str, Image.Image]

SCHEMA_V5_LAYER_IDS = (
    "body_base",
    "hair_back_default",
    "hair_front_default",
    "hands_front",
    "top_default",
    "bottom_default",
    "shoes_default",
    "top_adventure",
    "bottom_adventure",
    "shoes_adventure",
    "headgear_adventure",
    "accessory_adventure",
    "weapon_back_default_sword",
    "weapon_held_default_sword",
    "weapon_front_default_sword",
)
REQUIRED_LAYER_IDS = (
    "body_base",
    "hair_back_default",
    "hair_front_default",
    "hands_front",
    "gloves_adventure",
    "top_default",
    "bottom_default",
    "shoes_default",
    "top_adventure",
    "bottom_adventure",
    "shoes_adventure",
    "headgear_adventure",
    "accessory_adventure",
    "weapon_back_default_sword",
    "weapon_held_default_sword",
    "weapon_front_default_sword",
)
REQUIRED_SLOTS = (
    "accessory_back",
    "hair_back",
    "headgear_back",
    "body_base",
    "shoes",
    "bottom",
    "top",
    "hands_front",
    "face_overlay",
    "hair_front",
    "headgear_front",
    "accessory_front",
    "weapon_back",
    "weapon_held",
    "weapon_front",
)
WEAPON_LAYER_IDS = (
    "weapon_back_default_sword",
    "weapon_held_default_sword",
    "weapon_front_default_sword",
)
EXPECTED_TILE_NAMES = (
    "body-only",
    "body-default-hair",
    "default-equipped",
    "adventure-equipped",
    "mixed-default-top",
    "mixed-adventure-top",
    "headgear-off",
    "headgear-on",
    "accessory-off",
    "accessory-on",
    "weapon-off",
    "weapon-on",
    "anchors-preview",
    "palette",
    "layer-bounds-preview",
    "runtime-equipped-reference",
)
ALLOWED_TILE_KINDS = {
    "compositePreview",
    "generatedDebugOverlay",
    "generatedProductionPalette",
}
LOADOUT_ALWAYS_PRESENT_IDS = (
    "body_base",
    "hair_back_default",
    "hair_front_default",
    "hands_front",
)
EMPTY_GAMEPLAY_SLOT_CONTRACT = {
    "HELMET": {"representation": "transparent-overlay", "sourceIds": []},
    "CHEST": {
        "representation": "neutral-training-fallback",
        "sourceIds": ["top_default"],
    },
    "LEGS": {
        "representation": "neutral-training-fallback",
        "sourceIds": ["bottom_default"],
    },
    "GLOVES": {"representation": "transparent-overlay", "sourceIds": []},
    "SHOES": {
        "representation": "neutral-training-fallback",
        "sourceIds": ["shoes_default"],
    },
    "ACCESSORY": {"representation": "transparent-overlay", "sourceIds": []},
    "WEAPON": {"representation": "transparent-overlay", "sourceIds": []},
}
ADVENTURE_SHOP_SET_SLOTS = {
    "HELMET": {
        "layerKey": "headgear_adventure",
        "sourceIds": ["headgear_adventure"],
    },
    "CHEST": {"layerKey": "top_adventure", "sourceIds": ["top_adventure"]},
    "LEGS": {
        "layerKey": "bottom_adventure",
        "sourceIds": ["bottom_adventure"],
    },
    "GLOVES": {
        "layerKey": "gloves_adventure",
        "sourceIds": ["gloves_adventure"],
    },
    "SHOES": {
        "layerKey": "shoes_adventure",
        "sourceIds": ["shoes_adventure"],
    },
    "ACCESSORY": {
        "layerKey": "accessory_adventure",
        "sourceIds": ["accessory_adventure"],
    },
    "WEAPON": {
        "layerKey": "weapon_default_sword",
        "sourceIds": list(WEAPON_LAYER_IDS),
        "mergedRuntimePngAllowed": False,
    },
}
LOADOUT_RUNTIME_MIRRORS = [
    {
        "sourcePath": f"docs/art/character/layers/{layer_id}.png",
        "runtimePath": f"app/src/main/assets/character/layers/{layer_id}.png",
    }
    for layer_id in (
        "top_default",
        "bottom_default",
        "shoes_default",
        "gloves_adventure",
    )
]


@dataclass(frozen=True)
class ComposedState:
    """One deterministic loadout composition used by the invariant validator."""

    key: str
    top_id: str
    bottom_id: str
    shoes_id: str
    gloves: bool
    headgear: bool
    accessory: bool
    weapon: bool
    image: Image.Image


@dataclass(frozen=True)
class GeneratedAssets:
    """In-memory generated sheet and its sixteen source tiles."""

    sheet: Image.Image
    tiles: dict[str, Image.Image]


def _read_spec(spec_path: pathlib.Path) -> tuple[dict | None, list[str]]:
    try:
        value = json.loads(spec_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return None, [f"could not load schema v5 specification {spec_path}: {error}"]
    if not isinstance(value, dict):
        return None, ["schema v5 specification must be a JSON object"]
    return value, []


def _hex_rgb(value: str) -> tuple[int, int, int]:
    normalized = value.removeprefix("#")
    if len(normalized) != 6:
        raise ValueError(f"invalid RGB color {value!r}")
    return tuple(int(normalized[index:index + 2], 16) for index in (0, 2, 4))


def _rgba(spec: dict, palette_name: str) -> Rgba:
    return (*_hex_rgb(spec["productionPalette"]["colors"][palette_name]), 255)


def _format_rgba(pixel: Rgba) -> str:
    return "#" + "".join(f"{component:02X}" for component in pixel[:3]) + (
        f" alpha={pixel[3]}"
    )


def _first_point(index: int, width: int = 64) -> Point:
    return index % width, index // width


def _opaque_bounds(image: Image.Image) -> Bounds | None:
    box = image.getchannel("A").getbbox()
    if box is None:
        return None
    left, top, right, bottom = box
    return left, top, right - 1, bottom - 1


def _opaque_count(image: Image.Image) -> int:
    return sum(pixel[3] != 0 for pixel in image.get_flattened_data())


def _hashes(path: pathlib.Path, image: Image.Image) -> dict[str, str]:
    return {
        "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "rawRgbaSha256": hashlib.sha256(image.tobytes()).hexdigest(),
        "alphaMaskSha256": hashlib.sha256(
            image.getchannel("A").tobytes()
        ).hexdigest(),
    }


def _image_metadata(path: pathlib.Path, image: Image.Image) -> dict:
    return {
        "status": "available",
        "opaqueBounds": list(_opaque_bounds(image)) if _opaque_bounds(image) else None,
        "opaquePixelCount": _opaque_count(image),
        "fileByteCount": path.stat().st_size,
        "hashes": _hashes(path, image),
    }


def _safe_relative_path(
    base: pathlib.Path,
    raw_path: object,
    owner: str,
) -> tuple[pathlib.Path | None, list[str]]:
    if not isinstance(raw_path, str) or not raw_path:
        return None, [f"{owner} path must be a non-empty relative path"]
    relative = pathlib.Path(raw_path)
    if relative.is_absolute():
        return None, [f"{owner} path must be relative: {raw_path}"]
    resolved = (base / relative).resolve()
    try:
        resolved.relative_to(base.resolve())
    except ValueError:
        return None, [f"{owner} path resolves outside its root: {raw_path}"]
    return resolved, []


def _open_image(path: pathlib.Path, owner: str) -> tuple[Image.Image | None, list[str]]:
    try:
        with Image.open(path) as image:
            image.load()
            return image.copy(), []
    except OSError as error:
        return None, [f"{owner} could not load {path}: {error}"]


def _layer_by_id(spec: dict, layer_id: str) -> dict | None:
    return next(
        (
            definition
            for definition in spec.get("canonicalLayers", [])
            if definition.get("id") == layer_id
        ),
        None,
    )


def _points_in_region(raw_region: object) -> set[Point]:
    if not isinstance(raw_region, list) or len(raw_region) != 4:
        return set()
    left, top, right, bottom = raw_region
    if not all(isinstance(value, int) for value in raw_region):
        return set()
    return {
        (x, y)
        for y in range(top, bottom + 1)
        for x in range(left, right + 1)
    }


def _errors_for_geometry_reference(
    spec_path: pathlib.Path,
    spec: dict,
) -> list[str]:
    errors: list[str] = []
    reference = spec.get("geometryCanonicalReference")
    if not isinstance(reference, dict):
        return ["geometryCanonicalReference must be an object"]
    path, path_errors = _safe_relative_path(
        spec_path.parent,
        reference.get("path"),
        "geometry canonical reference",
    )
    errors.extend(path_errors)
    raw_path = reference.get("path")
    if raw_path != "todo-quest-character-base-body.png":
        errors.append("geometry canonical reference must be the external base-body PNG")
    body_definition = _layer_by_id(spec, "body_base")
    if body_definition is None:
        errors.append("canonicalLayers must define body_base")
    elif body_definition.get("sourcePath") != raw_path:
        errors.append(
            "body_base sourcePath must be the external base-body geometry canonical reference"
        )
    legacy = spec.get("legacyArtDirectionReference")
    if not isinstance(legacy, dict):
        errors.append("legacyArtDirectionReference must be an object")
    elif (
        legacy.get("sheetPath") != "todo-quest-character-base-sheet.png"
        or legacy.get("specPath") != "character-base-spec.json"
    ):
        errors.append("legacy art-direction reference must not be used as body_base")
    if path is None:
        return errors
    if not path.exists():
        errors.append(f"geometry canonical reference is missing: {path}")
        return errors
    image, load_errors = _open_image(path, "geometry canonical reference")
    errors.extend(load_errors)
    if image is None:
        return errors
    expected_size = (reference.get("width"), reference.get("height"))
    if image.size != expected_size:
        errors.append(
            f"geometry canonical reference size must be {expected_size}; got {image.size}"
        )
    if image.mode != reference.get("mode"):
        errors.append(
            "geometry canonical reference mode must be "
            f"{reference.get('mode')}; got {image.mode}"
        )
    if image.mode != "RGBA" or image.size != (64, 64):
        return errors
    actual_bounds = _opaque_bounds(image)
    if list(actual_bounds) != reference.get("opaqueBounds"):
        errors.append(
            "geometry canonical reference opaqueBounds differ: "
            f"expected {reference.get('opaqueBounds')}, got {actual_bounds}"
        )
    if _opaque_count(image) != reference.get("opaquePixelCount"):
        errors.append("geometry canonical reference opaque pixel count differs")
    if image.getchannel("A").getbbox() is not None:
        assert actual_bounds is not None
        if (actual_bounds[0] + actual_bounds[2]) / 2 != reference.get("centerX"):
            errors.append("geometry canonical reference centerX differs")
        if actual_bounds[3] != reference.get("soleY"):
            errors.append("geometry canonical reference soleY differs")
    actual_hashes = _hashes(path, image)
    for name, actual in actual_hashes.items():
        if reference.get(name) != actual:
            errors.append(
                f"geometry canonical reference {name} differs; got {actual}"
            )
    if reference.get("fileByteCount") != path.stat().st_size:
        errors.append("geometry canonical reference fileByteCount differs")
    return errors


def _validate_overlay_masks(spec: dict) -> list[str]:
    errors: list[str] = []
    masks = spec.get("generatedOverlayMasks")
    if not isinstance(masks, dict):
        return ["generatedOverlayMasks must define the generated debug masks"]
    for tile_name in ("anchors-preview", "layer-bounds-preview"):
        contract = masks.get(tile_name)
        if not isinstance(contract, dict):
            errors.append(f"generated overlay mask {tile_name} is missing")
            continue
        points = contract.get("pixelCoordinates")
        if not isinstance(points, list) or not points:
            errors.append(
                f"generated overlay mask {tile_name} must contain pixelCoordinates"
            )
            continue
        normalized: list[Point] = []
        for raw_point in points:
            if (
                not isinstance(raw_point, list)
                or len(raw_point) != 2
                or not all(isinstance(value, int) for value in raw_point)
            ):
                errors.append(
                    f"generated overlay mask {tile_name} has an invalid point {raw_point}"
                )
                continue
            point = raw_point[0], raw_point[1]
            if not (0 <= point[0] < 64 and 0 <= point[1] < 64):
                errors.append(
                    f"generated overlay mask {tile_name} point is outside 64x64: {point}"
                )
            normalized.append(point)
        if len(set(normalized)) != len(normalized):
            errors.append(f"generated overlay mask {tile_name} contains duplicates")
        if contract.get("baseTile") not in EXPECTED_TILE_NAMES:
            errors.append(f"generated overlay mask {tile_name} has unknown baseTile")
    return errors


def _available_glove_artifact(artifact: object) -> bool:
    if not isinstance(artifact, dict):
        return False
    hashes = artifact.get("hashes")
    return (
        artifact.get("status") == "available"
        and artifact.get("opaqueBounds") == [21, 39, 43, 45]
        and artifact.get("opaquePixelCount") == 38
        and isinstance(artifact.get("fileByteCount"), int)
        and artifact["fileByteCount"] > 0
        and isinstance(hashes, dict)
        and set(hashes) == {"fileSha256", "rawRgbaSha256", "alphaMaskSha256"}
        and all(
            isinstance(value, str)
            and len(value) == 64
            and all(character in "0123456789abcdef" for character in value)
            for value in hashes.values()
        )
    )


def _loadout_art_contract_errors(spec: dict) -> list[str]:
    """Validate the empty-slot and adventure shop-set art contract."""

    errors: list[str] = []
    contract = spec.get("loadoutArtContract")
    if not isinstance(contract, dict):
        return ["loadout art contract must define empty gameplay slots"]

    expected_fields = {
        "contractVersion",
        "status",
        "canvas",
        "alwaysPresentSourceIds",
        "emptyGameplaySlots",
        "neutralTrainingFallback",
        "adventureShopSet",
        "plannedCanonicalLayer",
        "compositionOrder",
        "schemaPromotion",
        "regenerationManifest",
    }
    if set(contract) != expected_fields:
        errors.append("loadout art contract fields must match the version 1 manifest")
    schema_version = spec.get("schemaVersion")
    expected_status = "pendingGeneration" if schema_version == 5 else "available"
    if contract.get("contractVersion") != 1 or contract.get("status") != expected_status:
        errors.append(
            f"loadout art contract must be {expected_status} at version 1"
        )
    if contract.get("canvas") != {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "origin": [0, 0],
        "centerX": 32,
        "soleY": 58,
        "interpolation": "nearest-neighbor",
    }:
        errors.append("loadout art contract must preserve the 64x64 same-origin pixel canvas")
    if contract.get("alwaysPresentSourceIds") != list(LOADOUT_ALWAYS_PRESENT_IDS):
        errors.append("empty loadouts must always preserve body, hair, and hands sources")
    if contract.get("emptyGameplaySlots") != EMPTY_GAMEPLAY_SLOT_CONTRACT:
        errors.append("empty gameplay slots must use the fixed fallback and transparent overlays")
    if contract.get("neutralTrainingFallback") != {
        "sourceIds": ["top_default", "bottom_default", "shoes_default"],
        "paletteNames": ["underDark", "underMid", "underLight"],
        "descriptionKorean": "회갈색 계열의 중립 훈련복",
    }:
        errors.append("neutral training fallback must use the three default training layers")

    adventure_set = contract.get("adventureShopSet")
    if not isinstance(adventure_set, dict) or adventure_set.get("setKey") != "adventure_set":
        errors.append("adventure shop set must retain its stable set key")
        adventure_slots: object = None
    else:
        adventure_slots = adventure_set.get("slots")
    if not isinstance(adventure_slots, dict):
        errors.append("adventure shop set must define all seven gameplay slots")
    else:
        glove = adventure_slots.get("GLOVES")
        if glove != ADVENTURE_SHOP_SET_SLOTS["GLOVES"]:
            errors.append("adventure shop set must include the gloves_adventure layer key")
        weapon = adventure_slots.get("WEAPON")
        if weapon != ADVENTURE_SHOP_SET_SLOTS["WEAPON"]:
            errors.append("adventure sword must remain one item backed by three split sources")
        non_weapon_slots = {
            slot: value
            for slot, value in ADVENTURE_SHOP_SET_SLOTS.items()
            if slot not in {"GLOVES", "WEAPON"}
        }
        if {
            slot: adventure_slots.get(slot) for slot in non_weapon_slots
        } != non_weapon_slots or set(adventure_slots) != set(ADVENTURE_SHOP_SET_SLOTS):
            errors.append("adventure shop set must preserve all existing layer keys")

    planned = contract.get("plannedCanonicalLayer")
    expected_planned_fields = {
        "id": "gloves_adventure",
        "slot": "hands_front",
        "sourcePath": "docs/art/character/layers/gloves_adventure.png",
        "runtimePath": "app/src/main/assets/character/layers/gloves_adventure.png",
        "replacesSourceIdWhenEquipped": "hands_front",
        "referenceAlphaMaskSourceId": "hands_front",
    }
    if not isinstance(planned, dict) or any(
        planned.get(name) != value for name, value in expected_planned_fields.items()
    ):
        errors.append("planned gloves_adventure must preserve its source and runtime identity")
    elif schema_version == 5:
        pending_artifact = {
            "status": "pendingGeneration",
            "opaqueBounds": [21, 39, 43, 45],
            "opaquePixelCount": None,
            "fileByteCount": None,
            "hashes": {
                "fileSha256": None,
                "rawRgbaSha256": None,
                "alphaMaskSha256": None,
            },
        }
        if (
            planned.get("sourceArtifact") != pending_artifact
            or planned.get("runtimeArtifact") != pending_artifact
        ):
            errors.append("planned gloves_adventure must declare pending artifacts")
    elif not (
        _available_glove_artifact(planned.get("sourceArtifact"))
        and planned.get("runtimeArtifact") == planned.get("sourceArtifact")
    ):
        errors.append("generated gloves_adventure must declare available byte-identical artifacts")
    if contract.get("compositionOrder") != list(REQUIRED_SLOTS):
        errors.append("loadout composition order must match the character z-order")
    if contract.get("schemaPromotion") != {
        "currentCharacterSchemaVersion": 5,
        "targetCharacterSchemaVersion": 6,
        "existingLayerIdsKeepMeaning": True,
        "existingLayerIdsMayBeRenamedOrRemoved": False,
        "storedAppearanceIdsRemainCompatible": True,
        "newLayerIds": ["gloves_adventure"],
    }:
        errors.append("schema promotion must preserve every existing layer key and appearance id")

    regeneration = contract.get("regenerationManifest")
    if not isinstance(regeneration, dict):
        errors.append("loadout art contract must define preview regeneration")
        errors.append("loadout art contract must define runtime mirror regeneration")
    else:
        if regeneration.get("changedCanonicalSourceIds") != [
            "top_default",
            "bottom_default",
            "shoes_default",
            "gloves_adventure",
        ]:
            errors.append("regeneration must include fallback layers and gloves_adventure")
        if regeneration.get("runtimeMirrors") != LOADOUT_RUNTIME_MIRRORS:
            errors.append("runtime mirror regeneration must cover every changed canonical layer")
        if regeneration.get("generatedPreviewTileNames") != list(EXPECTED_TILE_NAMES):
            errors.append("preview regeneration must include the complete sixteen-tile manifest")
        if (
            regeneration.get("generatedSheetDocsPath")
            != "docs/art/character/todo-quest-character-modular-sheet.png"
            or regeneration.get("generatedSheetRuntimePath")
            != "app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png"
            or regeneration.get("runtimeReadsGeneratedSheet") is not False
        ):
            errors.append("sheet regeneration must remain debug-only in docs and runtime mirrors")
    return errors


def _contract_errors(spec_path: pathlib.Path, spec: dict) -> list[str]:
    errors: list[str] = []
    schema_version = spec.get("schemaVersion")
    if schema_version not in {5, 6}:
        errors.append("schemaVersion must be 5 or 6")
    if spec.get("contractKind") != "independent-character-layer-sources":
        errors.append("contractKind must describe independent character layer sources")
    if spec.get("canvasBounds") != [0, 0, 63, 63]:
        errors.append("canvasBounds must be [0, 0, 63, 63]")
    if spec.get("layerSlots") != list(REQUIRED_SLOTS):
        errors.append("layerSlots differ from the schema v5 slot order")
    if spec.get("zOrder") != list(REQUIRED_SLOTS):
        errors.append("zOrder must match the schema v5 slot order")
    composition = spec.get("compositionContract", {})
    if (
        composition.get("runtimeReadsGeneratedSheet") is not False
        or composition.get("allSourcesShareLocalCoordinates") is not True
        or any(
            composition.get(field) is not False
            for field in (
                "itemTranslationAllowed",
                "itemCroppingAllowed",
                "itemScalingAllowed",
            )
        )
    ):
        errors.append(
            "composition must use independent same-origin layers and never the generated sheet"
        )

    errors.extend(_loadout_art_contract_errors(spec))

    pixel = spec.get("pixelContract", {})
    if (
        pixel.get("width") != 64
        or pixel.get("height") != 64
        or pixel.get("mode") != "RGBA"
        or pixel.get("allowedAlphaValues") != [0, 255]
    ):
        errors.append("pixelContract must require 64x64 RGBA with alpha 0/255")
    if pixel.get("transparentPixelRgba") != [0, 0, 0, 0]:
        errors.append("pixelContract must normalize transparent pixels to RGBA zero")

    palette = spec.get("productionPalette", {})
    colors = palette.get("colors", {})
    if palette.get("colorCount") != 16 or len(colors) != 16:
        errors.append("production palette must contain exactly 16 colors")
    debug = spec.get("debugGuideColor", {})
    if debug.get("value") in colors.values() or debug.get("productionPaletteMember") is not False:
        errors.append("debug guide color must not be added to the production palette")

    face_contract = spec.get("layerInterfaceContracts", {}).get("faceProtection", {})
    if (
        face_contract.get("featureRegion") != [20, 20, 44, 28]
        or face_contract.get("appearanceLayersMayReplaceBodyFacePixels") is not False
    ):
        errors.append("face protection must preserve the canonical 20..28 feature region")

    layers = spec.get("canonicalLayers")
    if not isinstance(layers, list):
        errors.append("canonicalLayers must be a list")
        layers = []
    ids = [item.get("id") for item in layers if isinstance(item, dict)]
    expected_layer_ids = (
        SCHEMA_V5_LAYER_IDS if schema_version == 5 else REQUIRED_LAYER_IDS
    )
    if tuple(ids) != expected_layer_ids:
        errors.append(
            f"canonicalLayers must define the {len(expected_layer_ids)} "
            f"schema v{schema_version} independent sources"
        )
    if len(ids) != len(set(ids)):
        errors.append("canonical layer ids must be unique")
    source_paths = [item.get("sourcePath") for item in layers if isinstance(item, dict)]
    runtime_paths = [item.get("runtimePath") for item in layers if isinstance(item, dict)]
    if len(source_paths) != len(set(source_paths)):
        errors.append("canonical source paths must be independent and unique")
    if len(runtime_paths) != len(set(runtime_paths)):
        errors.append("runtime layer paths must be independent and unique")
    forbidden = set(spec.get("pathContract", {}).get("forbiddenPerItemFields", []))
    for definition in layers:
        if not isinstance(definition, dict):
            errors.append("each canonical layer must be an object")
            continue
        layer_id = definition.get("id", "unknown")
        if definition.get("slot") not in REQUIRED_SLOTS:
            errors.append(f"layer {layer_id} uses an unknown slot")
        present_forbidden = forbidden.intersection(definition)
        if present_forbidden:
            errors.append(
                f"layer {layer_id} contains forbidden canvas transform fields: "
                f"{sorted(present_forbidden)}"
            )
        _, path_errors = _safe_relative_path(
            spec_path.parent, definition.get("sourcePath"), f"layer {layer_id} source"
        )
        errors.extend(path_errors)
        runtime_path = definition.get("runtimePath")
        if not isinstance(runtime_path, str) or not runtime_path.startswith(
            "character/layers/"
        ):
            errors.append(
                f"layer {layer_id} runtimePath must stay below character/layers"
            )
        for artifact_name in ("sourceArtifact", "runtimeArtifact"):
            artifact = definition.get(artifact_name)
            if not isinstance(artifact, dict) or artifact.get("status") not in {
                "available",
                "pendingGeneration",
            }:
                errors.append(
                    f"layer {layer_id} {artifact_name} must have an explicit status"
                )

    weapon_definitions = [
        _layer_by_id(spec, layer_id) for layer_id in WEAPON_LAYER_IDS
    ]
    if any(definition is None for definition in weapon_definitions):
        errors.append("weapon must have three independent source layers")
    else:
        weapon_sources = {item["sourcePath"] for item in weapon_definitions if item}
        weapon_runtime = {item["runtimePath"] for item in weapon_definitions if item}
        weapon_slots = {item["slot"] for item in weapon_definitions if item}
        if (
            len(weapon_sources) != 3
            or len(weapon_runtime) != 3
            or weapon_slots != {"weapon_back", "weapon_held", "weapon_front"}
        ):
            errors.append("weapon must use three independent source and runtime files")

    generated = spec.get("generatedSheet")
    if not isinstance(generated, dict):
        errors.append("generatedSheet must be an object")
    else:
        logical = generated.get("logicalTile", {})
        if (
            generated.get("width") != 512
            or generated.get("height") != 128
            or generated.get("mode") != "RGBA"
            or logical != {"width": 64, "height": 64, "columns": 8, "rows": 2}
        ):
            errors.append("generated sheet must be a 512x128 RGBA 8x2 tile grid")
        tile_map = generated.get("tileMap", [])
        names = [item.get("name") for item in tile_map if isinstance(item, dict)]
        positions = {
            (item.get("row"), item.get("column"))
            for item in tile_map
            if isinstance(item, dict)
        }
        if tuple(names) != EXPECTED_TILE_NAMES or len(positions) != 16:
            errors.append("generated sheet tile map must contain the fixed 16 tiles")
        for item in tile_map:
            if isinstance(item, dict) and item.get("kind") not in ALLOWED_TILE_KINDS:
                errors.append(
                    f"generated sheet tile kind is not deterministic: {item.get('kind')}"
                )
            if isinstance(item, dict) and set(item).intersection(
                {"sourcePath", "sourceSheet", "crop", "offset", "scale"}
            ):
                errors.append(
                    f"generated sheet tile {item.get('name')} may only use independent "
                    "compositions or deterministic debug/palette generation"
                )

    serialization = spec.get("hashSerialization", {})
    required_fragments = {
        "layerRgba": ("4096", "16384"),
        "layerAlpha": ("4096",),
        "sheetRgba": ("65536", "262144"),
    }
    for key, fragments in required_fragments.items():
        description = serialization.get(key, "")
        if not isinstance(description, str) or any(
            fragment not in description for fragment in fragments
        ):
            errors.append(
                f"hashSerialization {key} must describe {', '.join(fragments)} bytes/pixels"
            )

    previews = spec.get("generatedPreviews")
    if not isinstance(previews, dict):
        errors.append("generatedPreviews must define deterministic 1x and 8x outputs")
    elif previews.get("directory") != "previews" or previews.get("scales") != [1, 8]:
        errors.append("generatedPreviews must use previews/ at scales 1x and 8x")
    errors.extend(_validate_overlay_masks(spec))
    errors.extend(_errors_for_geometry_reference(spec_path, spec))
    return errors


def validate_contract(spec_path: pathlib.Path = DEFAULT_SPEC_PATH) -> list[str]:
    """Validate schema structure and the sole external geometry reference."""

    spec_path = pathlib.Path(spec_path)
    spec, load_errors = _read_spec(spec_path)
    if spec is None:
        return load_errors
    try:
        return _contract_errors(spec_path, spec)
    except (KeyError, TypeError, ValueError, IndexError) as error:
        return [f"invalid schema v5 contract: {error}"]


def _pixel_errors(image: Image.Image, spec: dict, owner: str) -> list[str]:
    errors: list[str] = []
    if image.size != (64, 64):
        errors.append(f"{owner} must be 64x64; got {image.size[0]}x{image.size[1]}")
    if image.mode != "RGBA":
        errors.append(f"{owner} mode must be RGBA; got {image.mode}")
    if errors:
        return errors
    palette = {
        (*_hex_rgb(value), 255)
        for value in spec["productionPalette"]["colors"].values()
    }
    debug = (*_hex_rgb(spec["debugGuideColor"]["value"]), 255)
    for index, pixel in enumerate(image.get_flattened_data()):
        point = _first_point(index)
        if pixel[3] not in (0, 255):
            errors.append(
                f"{owner} alpha must be 0 or 255 at {point}; got {pixel[3]}"
            )
            break
        if pixel[3] == 0:
            if pixel != (0, 0, 0, 0):
                errors.append(
                    f"{owner} transparent pixel must be RGBA zero at {point}; "
                    f"got {_format_rgba(pixel)}"
                )
                break
            continue
        if pixel == debug:
            errors.append(f"{owner} contains debug guide contamination at {point}")
            break
        if pixel not in palette:
            errors.append(
                f"{owner} pixel is outside the production palette at {point}: "
                f"{_format_rgba(pixel)}"
            )
            break
    bounds = _opaque_bounds(image)
    if bounds is None:
        errors.append(f"{owner} must contain at least one opaque pixel")
    else:
        envelope = spec["appearanceAllowedEnvelope"]
        if not (
            envelope[0] <= bounds[0] <= bounds[2] <= envelope[2]
            and envelope[1] <= bounds[1] <= bounds[3] <= envelope[3]
        ):
            errors.append(
                f"{owner} opaqueBounds {bounds} leave appearanceAllowedEnvelope {envelope}"
            )
    return errors


def _artifact_errors(
    path: pathlib.Path,
    image: Image.Image,
    artifact: object,
    owner: str,
) -> list[str]:
    if not isinstance(artifact, dict):
        return [f"{owner} artifact contract must be an object"]
    if artifact.get("status") == "pendingGeneration":
        return []
    if artifact.get("status") != "available":
        return [f"{owner} artifact status is invalid"]
    errors: list[str] = []
    actual_bounds = _opaque_bounds(image)
    expected_bounds = artifact.get("opaqueBounds")
    if (list(actual_bounds) if actual_bounds else None) != expected_bounds:
        errors.append(
            f"{owner} opaqueBounds differ: expected {expected_bounds}, got {actual_bounds}"
        )
    actual_count = _opaque_count(image)
    if (
        "opaquePixelCount" in artifact
        and artifact.get("opaquePixelCount") != actual_count
    ):
        errors.append(
            f"{owner} opaquePixelCount differs: expected "
            f"{artifact.get('opaquePixelCount')}, got {actual_count}"
        )
    if (
        "fileByteCount" in artifact
        and artifact.get("fileByteCount") != path.stat().st_size
    ):
        errors.append(f"{owner} fileByteCount differs")
    expected_hashes = artifact.get("hashes", {})
    for name, actual in _hashes(path, image).items():
        if expected_hashes.get(name) != actual:
            errors.append(f"{owner} {name} differs; got {actual}")
    return errors


def _hand_points(spec: dict, body: Image.Image) -> set[Point]:
    allowed = {_rgba(spec, "skinLight"), _rgba(spec, "skinShadow")}
    points: set[Point] = set()
    for region in spec["semanticAnchors"]["handProtectedRegions"].values():
        points.update(
            point
            for point in _points_in_region(region)
            if body.getpixel(point) in allowed
        )
    return points


def _profile_errors(
    layer_id: str,
    image: Image.Image,
    spec: dict,
    body: Image.Image | None,
) -> list[str]:
    errors: list[str] = []
    bounds = _opaque_bounds(image)
    if bounds is None:
        return errors
    if layer_id == "body_base":
        expected = tuple(spec["bodyOpaqueBounds"])
        if bounds != expected:
            errors.append(
                f"body_base opaqueBounds must match geometry reference {expected}; got {bounds}"
            )
        if (bounds[0] + bounds[2]) / 2 != spec["centerX"]:
            errors.append("body_base centerX differs from the canvas anchor profile")
        if bounds[3] != spec["soleY"]:
            errors.append("body_base soleY differs from the canvas anchor profile")
    if layer_id.startswith("shoes_") and bounds[3] != spec["soleY"]:
        errors.append(f"{layer_id} must reach soleY {spec['soleY']}")
    if layer_id in {"hands_front", "gloves_adventure"} and body is not None:
        expected_points = _hand_points(spec, body)
        actual_points = {
            _first_point(index)
            for index, pixel in enumerate(image.get_flattened_data())
            if pixel[3] != 0
        }
        expected_count = spec["layerInterfaceContracts"]["handsFront"][
            "expectedOpaquePixelCount"
        ]
        if len(actual_points) != expected_count:
            errors.append(
                f"{layer_id} opaque pixel count must be {expected_count}; "
                f"got {len(actual_points)}"
            )
        if actual_points != expected_points:
            errors.append(f"{layer_id} coordinates differ from the external body hands")
        if layer_id == "hands_front":
            for point in actual_points.intersection(expected_points):
                if image.getpixel(point) != body.getpixel(point):
                    errors.append(f"hands_front RGBA differs from body_base at {point}")
                    break
        else:
            allowed = {
                _rgba(spec, name)
                for name in (
                    "outlineDarkNavy",
                    "blueShadow",
                    "bluePrimary",
                    "blueHighlight",
                    "tealAccent",
                )
            }
            if any(image.getpixel(point) not in allowed for point in actual_points):
                errors.append("gloves_adventure must use only blue and teal adventure colors")
            for point in ((24, 39), (40, 39)):
                if image.getpixel(point) != _rgba(spec, "tealAccent"):
                    errors.append(
                        f"gloves_adventure cuff must use tealAccent at {point}"
                    )
    return errors


def _default_runtime_root(spec: dict) -> pathlib.Path:
    return REPOSITORY_ROOT / spec["pathContract"]["runtimePathRoot"]


def _source_path(spec_path: pathlib.Path, definition: dict) -> pathlib.Path:
    return spec_path.parent / definition["sourcePath"]


def _runtime_path(runtime_root: pathlib.Path, definition: dict) -> pathlib.Path:
    return runtime_root / definition["runtimePath"]


def _validate_one_layer(
    spec_path: pathlib.Path,
    spec: dict,
    definition: dict,
    runtime_root: pathlib.Path,
    body: Image.Image | None,
    verify_artifacts: bool,
) -> tuple[Image.Image | None, list[str]]:
    layer_id = definition["id"]
    source_path = _source_path(spec_path, definition)
    if not source_path.exists():
        return None, [f"layer {layer_id} source is missing: {source_path}"]
    image, load_errors = _open_image(source_path, f"layer {layer_id} source")
    errors = list(load_errors)
    if image is None:
        return None, errors
    errors.extend(_pixel_errors(image, spec, f"layer {layer_id} source"))
    if image.mode == "RGBA" and image.size == (64, 64):
        errors.extend(_profile_errors(layer_id, image, spec, body))
        if verify_artifacts:
            errors.extend(
                _artifact_errors(
                    source_path,
                    image,
                    definition.get("sourceArtifact"),
                    f"layer {layer_id} source",
                )
            )

    runtime_path = _runtime_path(runtime_root, definition)
    runtime_status = definition.get("runtimeArtifact", {}).get("status")
    if runtime_path.exists():
        runtime_image, runtime_errors = _open_image(
            runtime_path, f"layer {layer_id} runtime"
        )
        errors.extend(runtime_errors)
        if runtime_image is not None:
            errors.extend(
                _pixel_errors(runtime_image, spec, f"layer {layer_id} runtime")
            )
            if runtime_path.read_bytes() != source_path.read_bytes():
                errors.append(
                    f"layer {layer_id} runtime copy is not byte-identical to source"
                )
            if verify_artifacts and runtime_image.mode == "RGBA" and runtime_image.size == (64, 64):
                errors.extend(
                    _artifact_errors(
                        runtime_path,
                        runtime_image,
                        definition.get("runtimeArtifact"),
                        f"layer {layer_id} runtime",
                    )
                )
    elif runtime_status == "available":
        errors.append(f"layer {layer_id} runtime copy is missing: {runtime_path}")
    return image, errors


def validate_layer(
    spec_path: pathlib.Path,
    layer_id: str,
    runtime_root: pathlib.Path | None = None,
) -> list[str]:
    """Validate one source and its runtime copy, without changing either file."""

    spec_path = pathlib.Path(spec_path)
    spec, load_errors = _read_spec(spec_path)
    if spec is None:
        return load_errors
    errors = _contract_errors(spec_path, spec)
    definition = _layer_by_id(spec, layer_id)
    if definition is None:
        return [*errors, f"unknown canonical layer id: {layer_id}"]
    resolved_runtime_root = (
        pathlib.Path(runtime_root) if runtime_root is not None else _default_runtime_root(spec)
    )
    body: Image.Image | None = None
    body_definition = _layer_by_id(spec, "body_base")
    if body_definition is not None:
        body, _ = _open_image(
            _source_path(spec_path, body_definition), "body_base geometry source"
        )
    _, layer_errors = _validate_one_layer(
        spec_path,
        spec,
        definition,
        resolved_runtime_root,
        body,
        verify_artifacts=True,
    )
    errors.extend(layer_errors)
    return errors


def load_source_layers(
    spec_path: pathlib.Path,
    spec: dict,
    *,
    verify_artifacts: bool = False,
) -> tuple[ImageMap, list[str]]:
    """Load every canonical source in its unchanged 64x64 local coordinates."""

    spec_path = pathlib.Path(spec_path)
    images: ImageMap = {}
    errors: list[str] = []
    for definition in spec["canonicalLayers"]:
        source_path = _source_path(spec_path, definition)
        if not source_path.exists():
            errors.append(
                f"layer {definition['id']} source is missing: {source_path}"
            )
            continue
        image, load_errors = _open_image(
            source_path, f"layer {definition['id']} source"
        )
        errors.extend(load_errors)
        if image is not None:
            images[definition["id"]] = image
    body = images.get("body_base")
    for definition in spec["canonicalLayers"]:
        image = images.get(definition["id"])
        if image is None:
            continue
        errors.extend(
            _pixel_errors(image, spec, f"layer {definition['id']} source")
        )
        if image.mode == "RGBA" and image.size == (64, 64):
            errors.extend(_profile_errors(definition["id"], image, spec, body))
            if verify_artifacts:
                errors.extend(
                    _artifact_errors(
                        _source_path(spec_path, definition),
                        image,
                        definition.get("sourceArtifact"),
                        f"layer {definition['id']} source",
                    )
                )
    return images, errors


def _compose_ids(spec: dict, layers: ImageMap, selected_ids: Iterable[str]) -> Image.Image:
    selected = set(selected_ids)
    definitions = spec["canonicalLayers"]
    result = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for slot in spec["zOrder"]:
        for definition in definitions:
            if definition["slot"] == slot and definition["id"] in selected:
                result = Image.alpha_composite(result, layers[definition["id"]])
    return result


def _selected_ids(
    top_id: str,
    bottom_id: str,
    shoes_id: str,
    gloves: bool,
    headgear: bool,
    accessory: bool,
    weapon: bool,
) -> list[str]:
    selected = [
        "hair_back_default",
        "body_base",
        shoes_id,
        bottom_id,
        top_id,
    ]
    if weapon:
        selected.extend(("weapon_back_default_sword", "weapon_held_default_sword"))
    selected.append("gloves_adventure" if gloves else "hands_front")
    if weapon:
        selected.append("weapon_front_default_sword")
    selected.append("hair_front_default")
    if headgear:
        selected.append("headgear_adventure")
    if accessory:
        selected.append("accessory_adventure")
    return selected


def _append_once(errors: list[str], error: str) -> None:
    if error not in errors:
        errors.append(error)


def _first_missing_opaque(image: Image.Image, points: set[Point]) -> Point | None:
    return next(
        (
            point
            for point in sorted(points, key=lambda item: (item[1], item[0]))
            if image.getpixel(point)[3] == 0
        ),
        None,
    )


def _face_feature_points(spec: dict, body: Image.Image) -> set[Point]:
    contract = spec["layerInterfaceContracts"]["faceProtection"]
    region = _points_in_region(contract["featureRegion"])
    return {point for point in region if body.getpixel(point)[3] != 0}


def _neutral_underwear_points(spec: dict, body: Image.Image) -> set[Point]:
    neutral_colors = {
        _rgba(spec, "underDark"),
        _rgba(spec, "underMid"),
        _rgba(spec, "underLight"),
    }
    regions = [
        spec["semanticAnchors"]["torsoBounds"],
        [24, 44, 40, 52],
        *spec["semanticAnchors"]["ankleOverlapBands"].values(),
        [23, 55, 41, 58],
    ]
    points = set().union(*(_points_in_region(region) for region in regions))
    return {point for point in points if body.getpixel(point) in neutral_colors}


def validate_combinations(
    spec: dict,
    layers: ImageMap,
) -> tuple[list[ComposedState], list[str]]:
    """Compose and validate all 64 outfit/toggle combinations source-over."""

    missing = [layer_id for layer_id in REQUIRED_LAYER_IDS if layer_id not in layers]
    if missing:
        return [], [f"combination validation is missing layers: {', '.join(missing)}"]
    before = {layer_id: image.tobytes() for layer_id, image in layers.items()}
    errors: list[str] = []
    states: list[ComposedState] = []
    body = layers["body_base"]
    face_points = _face_feature_points(spec, body)
    hand_points = _hand_points(spec, body)
    neutral_points = _neutral_underwear_points(spec, body)
    waist_points = _points_in_region(spec["semanticAnchors"]["waistOverlapBand"])
    ankle_points = set().union(
        *(
            _points_in_region(region)
            for region in spec["semanticAnchors"]["ankleOverlapBands"].values()
        )
    )
    outline = _rgba(spec, "outlineDarkNavy")

    for top_id, bottom_id, shoes_id, gloves, headgear, accessory, weapon in itertools.product(
        ("top_default", "top_adventure"),
        ("bottom_default", "bottom_adventure"),
        ("shoes_default", "shoes_adventure"),
        (False, True),
        (False, True),
        (False, True),
        (False, True),
    ):
        key = (
            f"{top_id}|{bottom_id}|{shoes_id}|"
            f"gloves={int(gloves)}|head={int(headgear)}|"
            f"accessory={int(accessory)}|weapon={int(weapon)}"
        )
        selected = _selected_ids(
            top_id, bottom_id, shoes_id, gloves, headgear, accessory, weapon
        )
        image = _compose_ids(spec, layers, selected)
        expected_face = _compose_ids(
            spec,
            layers,
            [
                "hair_back_default",
                "body_base",
                "hair_front_default",
                *(["headgear_adventure"] if headgear else []),
                *(["accessory_adventure"] if accessory else []),
                *(WEAPON_LAYER_IDS if weapon else []),
            ],
        )
        state = ComposedState(
            key,
            top_id,
            bottom_id,
            shoes_id,
            gloves,
            headgear,
            accessory,
            weapon,
            image,
        )
        states.append(state)

        missing_top = _first_missing_opaque(layers[top_id], waist_points)
        missing_bottom = _first_missing_opaque(layers[bottom_id], waist_points)
        if missing_top is not None:
            _append_once(errors, f"{top_id} waist overlap gap at {missing_top}")
        if missing_bottom is not None:
            _append_once(errors, f"{bottom_id} waist overlap gap at {missing_bottom}")
        for layer_id in (top_id, bottom_id):
            outline_point = next(
                (
                    point
                    for point in sorted(waist_points)
                    if layers[layer_id].getpixel(point) == outline
                ),
                None,
            )
            if outline_point is not None:
                _append_once(
                    errors,
                    f"{layer_id} creates a hidden double outline in waist overlap at {outline_point}",
                )

        missing_bottom_ankle = _first_missing_opaque(
            layers[bottom_id], ankle_points
        )
        missing_shoes = _first_missing_opaque(layers[shoes_id], ankle_points)
        if missing_bottom_ankle is not None:
            _append_once(
                errors, f"{bottom_id} ankle overlap gap at {missing_bottom_ankle}"
            )
        if missing_shoes is not None:
            _append_once(errors, f"{shoes_id} ankle overlap gap at {missing_shoes}")
        for layer_id in (bottom_id, shoes_id):
            outline_point = next(
                (
                    point
                    for point in sorted(ankle_points)
                    if layers[layer_id].getpixel(point) == outline
                ),
                None,
            )
            if outline_point is not None:
                _append_once(
                    errors,
                    f"{layer_id} creates a hidden double outline in ankle overlap at {outline_point}",
                )

        for point in face_points:
            if image.getpixel(point) != expected_face.getpixel(point):
                _append_once(errors, f"{key} replaces face feature at {point}")
                break
        for point in hand_points:
            expected = layers[
                "gloves_adventure" if gloves else "hands_front"
            ].getpixel(point)
            if weapon:
                for weapon_layer_id in WEAPON_LAYER_IDS:
                    weapon_pixel = layers[weapon_layer_id].getpixel(point)
                    if weapon_pixel[3] != 0:
                        expected = weapon_pixel
            if image.getpixel(point) != expected:
                _append_once(errors, f"{key} replaces protected hand at {point}")
                break
        for point in neutral_points:
            if not any(
                layers[layer_id].getpixel(point)[3] != 0
                for layer_id in (top_id, bottom_id, shoes_id)
            ):
                _append_once(errors, f"{key} exposes neutral underwear at {point}")
                break

        bounds = _opaque_bounds(image)
        if bounds is None:
            _append_once(errors, f"{key} composite is empty")
        else:
            if (bounds[0] + bounds[2]) / 2 != spec["centerX"]:
                _append_once(
                    errors,
                    f"{key} centerX differs from {spec['centerX']}: bounds={bounds}",
                )
            if bounds[3] != spec["soleY"]:
                _append_once(
                    errors,
                    f"{key} soleY differs from {spec['soleY']}: bounds={bounds}",
                )

    after = {layer_id: image.tobytes() for layer_id, image in layers.items()}
    if after != before:
        errors.append("combination validation mutated an input layer position or RGBA value")
    return states, errors


def _full_adventure_ids(
    *,
    gloves: bool = True,
    headgear: bool = True,
    accessory: bool = True,
    weapon: bool = True,
) -> list[str]:
    return _selected_ids(
        "top_adventure",
        "bottom_adventure",
        "shoes_adventure",
        gloves,
        headgear,
        accessory,
        weapon,
    )


def _palette_tile(spec: dict) -> Image.Image:
    tile = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for index, value in enumerate(spec["productionPalette"]["colors"].values()):
        column = index % 4
        row = index // 4
        left = 4 + column * 15
        top = 4 + row * 15
        color = (*_hex_rgb(value), 255)
        for y in range(top, top + 10):
            for x in range(left, left + 10):
                tile.putpixel((x, y), color)
    return tile


def _overlay_tile(spec: dict, tiles: dict[str, Image.Image], name: str) -> Image.Image:
    contract = spec["generatedOverlayMasks"][name]
    tile = tiles[contract["baseTile"]].copy()
    color = (*_hex_rgb(spec["debugGuideColor"]["value"]), 255)
    for point in contract["pixelCoordinates"]:
        tile.putpixel(tuple(point), color)
    return tile


def build_generated_assets(
    spec: dict,
    layers: ImageMap,
) -> tuple[GeneratedAssets, list[str]]:
    """Build deterministic previews and the 512x128 sheet in memory."""

    missing = [layer_id for layer_id in REQUIRED_LAYER_IDS if layer_id not in layers]
    if missing:
        empty = GeneratedAssets(Image.new("RGBA", (512, 128)), {})
        return empty, [f"generated assets are missing layers: {', '.join(missing)}"]
    tiles: dict[str, Image.Image] = {}
    tiles["body-only"] = _compose_ids(spec, layers, ["body_base"])
    tiles["body-default-hair"] = _compose_ids(
        spec, layers, ["hair_back_default", "body_base", "hair_front_default"]
    )
    tiles["default-equipped"] = _compose_ids(
        spec,
        layers,
        _selected_ids(
            "top_default",
            "bottom_default",
            "shoes_default",
            False,
            False,
            False,
            False,
        ),
    )
    tiles["adventure-equipped"] = _compose_ids(
        spec, layers, _full_adventure_ids()
    )
    tiles["mixed-default-top"] = _compose_ids(
        spec,
        layers,
        _selected_ids(
            "top_default",
            "bottom_adventure",
            "shoes_adventure",
            True,
            True,
            True,
            True,
        ),
    )
    tiles["mixed-adventure-top"] = _compose_ids(
        spec,
        layers,
        _selected_ids(
            "top_adventure",
            "bottom_default",
            "shoes_default",
            True,
            True,
            True,
            True,
        ),
    )
    tiles["headgear-off"] = _compose_ids(
        spec, layers, _full_adventure_ids(headgear=False)
    )
    tiles["headgear-on"] = _compose_ids(
        spec, layers, _full_adventure_ids(headgear=True)
    )
    tiles["accessory-off"] = _compose_ids(
        spec, layers, _full_adventure_ids(accessory=False)
    )
    tiles["accessory-on"] = _compose_ids(
        spec, layers, _full_adventure_ids(accessory=True)
    )
    tiles["weapon-off"] = _compose_ids(
        spec, layers, _full_adventure_ids(weapon=False)
    )
    tiles["weapon-on"] = _compose_ids(
        spec, layers, _full_adventure_ids(weapon=True)
    )
    tiles["palette"] = _palette_tile(spec)
    tiles["runtime-equipped-reference"] = _compose_ids(
        spec, layers, _full_adventure_ids()
    )
    tiles["anchors-preview"] = _overlay_tile(spec, tiles, "anchors-preview")
    tiles["layer-bounds-preview"] = _overlay_tile(
        spec, tiles, "layer-bounds-preview"
    )

    sheet_contract = spec["generatedSheet"]
    sheet = Image.new("RGBA", (sheet_contract["width"], sheet_contract["height"]), (0, 0, 0, 0))
    for definition in sheet_contract["tileMap"]:
        sheet.paste(
            tiles[definition["name"]],
            (definition["column"] * 64, definition["row"] * 64),
        )
    return GeneratedAssets(sheet, tiles), []


def _png_bytes(image: Image.Image) -> bytes:
    stream = io.BytesIO()
    image.save(stream, format="PNG")
    return stream.getvalue()


def _preview_path(spec_path: pathlib.Path, spec: dict, name: str, scale: int) -> pathlib.Path:
    directory = spec_path.parent / spec["generatedPreviews"]["directory"]
    suffix = "" if scale == 1 else f"@{scale}x"
    return directory / f"{name}{suffix}.png"


def _generated_sheet_path(spec_path: pathlib.Path, spec: dict) -> pathlib.Path:
    return spec_path.parent / spec["generatedSheet"]["path"]


def _save_png(image: Image.Image, path: pathlib.Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG")


def _neutral_training_layer(
    spec: dict,
    layer_id: str,
    reference: Image.Image,
) -> Image.Image:
    """Recolor the preserved fallback silhouette with neutral training colors."""

    result = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    outline = _rgba(spec, "outlineDarkNavy")
    for y in range(64):
        for x in range(64):
            pixel = reference.getpixel((x, y))
            if pixel[3] == 0:
                continue
            if pixel == outline:
                color = outline
            elif layer_id == "top_default":
                distance = abs(x - 32)
                color = _rgba(
                    spec,
                    "underLight" if distance <= 3 else (
                        "underDark" if distance >= 9 else "underMid"
                    ),
                )
            elif layer_id == "bottom_default":
                color = _rgba(
                    spec,
                    "underMid" if y <= 43 else (
                        "underLight"
                        if 44 <= y <= 48 and x in {26, 27, 37, 38}
                        else "underDark"
                    ),
                )
            else:
                highlight = (
                    y in {55, 56, 57}
                    and x in {*range(26, 30), *range(35, 39)}
                )
                color = _rgba(
                    spec,
                    "underDark" if y <= 54 else (
                        "underLight" if highlight else "underMid"
                    ),
                )
            result.putpixel((x, y), color)
    return result


def _adventure_glove_layer(spec: dict, hands: Image.Image) -> Image.Image:
    """Create the blue/teal adventure glove on the exact bare-hand alpha mask."""

    result = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for y in range(64):
        for x in range(64):
            if hands.getpixel((x, y))[3] == 0:
                continue
            if (x, y) in {(24, 39), (24, 40), (40, 39), (40, 40)}:
                color_name = "tealAccent"
            elif (x, y) in {(23, 39), (41, 39)}:
                color_name = "blueShadow"
            elif (x, y) in {(22, 43), (42, 43)}:
                color_name = "blueHighlight"
            elif (x <= 23 and y in {40, 42, 43, 44}) or (
                x >= 41 and y in {40, 42, 43, 44}
            ):
                color_name = "bluePrimary"
            else:
                color_name = "outlineDarkNavy"
            result.putpixel((x, y), _rgba(spec, color_name))
    return result


def _promote_and_generate_loadout_sources(
    spec_path: pathlib.Path,
    spec: dict,
    runtime_root: pathlib.Path,
) -> list[str]:
    """Generate the four schema-v6 loadout sources and their runtime mirrors."""

    definitions = {
        definition["id"]: definition for definition in spec["canonicalLayers"]
    }
    required_references = (
        "top_default",
        "bottom_default",
        "shoes_default",
        "hands_front",
    )
    references: ImageMap = {}
    errors: list[str] = []
    for layer_id in required_references:
        definition = definitions.get(layer_id)
        if definition is None:
            errors.append(f"loadout generation is missing definition {layer_id}")
            continue
        source_path = _source_path(spec_path, definition)
        if not source_path.exists():
            errors.append(
                f"loadout reference {layer_id} source is missing: {source_path}"
            )
            continue
        image, image_errors = _open_image(
            source_path, f"loadout reference {layer_id}"
        )
        errors.extend(image_errors)
        if image is not None:
            references[layer_id] = image
    if errors:
        return errors

    generated = {
        layer_id: _neutral_training_layer(spec, layer_id, references[layer_id])
        for layer_id in ("top_default", "bottom_default", "shoes_default")
    }
    generated["gloves_adventure"] = _adventure_glove_layer(
        spec, references["hands_front"]
    )

    glove_definition = definitions.get("gloves_adventure")
    if glove_definition is None:
        glove_definition = {
            "id": "gloves_adventure",
            "slot": "hands_front",
            "sourcePath": "layers/gloves_adventure.png",
            "runtimePath": "character/layers/gloves_adventure.png",
            "sourceArtifact": {"status": "pendingGeneration"},
            "runtimeArtifact": {"status": "pendingGeneration"},
        }
        hands_index = next(
            index
            for index, definition in enumerate(spec["canonicalLayers"])
            if definition["id"] == "hands_front"
        )
        spec["canonicalLayers"].insert(hands_index + 1, glove_definition)
        definitions["gloves_adventure"] = glove_definition
    elif (
        glove_definition.get("slot") != "hands_front"
        or glove_definition.get("sourcePath") != "layers/gloves_adventure.png"
        or glove_definition.get("runtimePath")
        != "character/layers/gloves_adventure.png"
    ):
        return ["gloves_adventure must preserve its schema-v6 source identity"]

    for layer_id, image in generated.items():
        definition = definitions[layer_id]
        source_path = _source_path(spec_path, definition)
        runtime_path = _runtime_path(runtime_root, definition)
        _save_png(image, source_path)
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source_path.read_bytes())
        definition["sourceArtifact"] = _image_metadata(source_path, image)
        definition["runtimeArtifact"] = _image_metadata(runtime_path, image)

    spec["schemaVersion"] = 6
    contract = spec["loadoutArtContract"]
    contract["status"] = "available"
    planned = contract["plannedCanonicalLayer"]
    planned["sourceArtifact"] = copy.deepcopy(
        definitions["gloves_adventure"]["sourceArtifact"]
    )
    planned["runtimeArtifact"] = copy.deepcopy(
        definitions["gloves_adventure"]["runtimeArtifact"]
    )
    return []


def _sync_equipment_loadout_contract(spec_path: pathlib.Path, spec: dict) -> list[str]:
    if spec_path.resolve() != DEFAULT_SPEC_PATH.resolve():
        return []
    try:
        equipment = json.loads(
            DEFAULT_EQUIPMENT_GLOVES_SPEC_PATH.read_text(encoding="utf-8")
        )
        equipment["loadoutArtContract"] = copy.deepcopy(
            spec["loadoutArtContract"]
        )
        DEFAULT_EQUIPMENT_GLOVES_SPEC_PATH.write_text(
            json.dumps(equipment, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as error:
        return [f"could not synchronize equipment loadout contract: {error}"]
    return []


def _open_exact_rgba(path: pathlib.Path, owner: str) -> tuple[Image.Image | None, list[str]]:
    image, errors = _open_image(path, owner)
    if image is None:
        return None, errors
    if image.mode != "RGBA":
        errors.append(f"{owner} mode must be RGBA; got {image.mode}")
    return image, errors


def _refresh_written_spec(
    spec_path: pathlib.Path,
    spec: dict,
    runtime_root: pathlib.Path,
    generated: GeneratedAssets,
) -> list[str]:
    errors: list[str] = []
    stored_sources: ImageMap = {}
    for definition in spec["canonicalLayers"]:
        source_path = _source_path(spec_path, definition)
        runtime_path = _runtime_path(runtime_root, definition)
        source, source_errors = _open_exact_rgba(
            source_path, f"layer {definition['id']} stored source"
        )
        runtime, runtime_errors = _open_exact_rgba(
            runtime_path, f"layer {definition['id']} stored runtime"
        )
        errors.extend(source_errors)
        errors.extend(runtime_errors)
        if source is None or runtime is None:
            continue
        if runtime_path.read_bytes() != source_path.read_bytes():
            errors.append(
                f"layer {definition['id']} runtime copy changed during write"
            )
            continue
        stored_sources[definition["id"]] = source
        definition["sourceArtifact"] = _image_metadata(source_path, source)
        definition["runtimeArtifact"] = _image_metadata(runtime_path, runtime)

    body_definition = _layer_by_id(spec, "body_base")
    body = stored_sources.get("body_base")
    if body_definition is not None and body is not None:
        body_path = _source_path(spec_path, body_definition)
        geometry = spec["geometryCanonicalReference"]
        geometry["opaqueBounds"] = list(_opaque_bounds(body))
        geometry["opaquePixelCount"] = _opaque_count(body)
        geometry["fileByteCount"] = body_path.stat().st_size
        geometry.update(_hashes(body_path, body))

    preview_artifacts: dict[str, dict] = {}
    for name, tile in generated.tiles.items():
        for scale in spec["generatedPreviews"]["scales"]:
            path = _preview_path(spec_path, spec, name, scale)
            stored, stored_errors = _open_exact_rgba(
                path, f"generated preview {name}@{scale}x"
            )
            errors.extend(stored_errors)
            if stored is None:
                continue
            expected = tile if scale == 1 else tile.resize(
                (64 * scale, 64 * scale), Image.Resampling.NEAREST
            )
            if stored.size != expected.size or stored.tobytes() != expected.tobytes():
                errors.append(f"generated preview {name}@{scale}x changed after save")
            relative = path.relative_to(spec_path.parent).as_posix()
            preview_artifacts[f"{name}@{scale}x"] = {
                "path": relative,
                "width": stored.width,
                "height": stored.height,
                "fileByteCount": path.stat().st_size,
                "hashes": _hashes(path, stored),
            }
    spec["generatedPreviews"]["status"] = "available"
    spec["generatedPreviews"]["artifacts"] = preview_artifacts

    sheet_path = _generated_sheet_path(spec_path, spec)
    stored_sheet, sheet_errors = _open_exact_rgba(sheet_path, "generated sheet")
    errors.extend(sheet_errors)
    if stored_sheet is not None:
        if (
            stored_sheet.size != generated.sheet.size
            or stored_sheet.tobytes() != generated.sheet.tobytes()
        ):
            errors.append("generated sheet changed after save")
        tile_hashes: dict[str, str] = {}
        for definition in spec["generatedSheet"]["tileMap"]:
            left = definition["column"] * 64
            top = definition["row"] * 64
            tile = stored_sheet.crop((left, top, left + 64, top + 64))
            tile_hashes[definition["name"]] = hashlib.sha256(
                tile.tobytes()
            ).hexdigest()
        sheet_metadata = _image_metadata(sheet_path, stored_sheet)
        spec["generatedSheet"]["status"] = "available"
        spec["generatedSheet"]["fileByteCount"] = sheet_metadata[
            "fileByteCount"
        ]
        spec["generatedSheet"]["opaqueBounds"] = sheet_metadata["opaqueBounds"]
        spec["generatedSheet"]["opaquePixelCount"] = sheet_metadata[
            "opaquePixelCount"
        ]
        spec["generatedSheet"]["hashes"] = {
            **sheet_metadata["hashes"],
            "tileRgbaSha256": tile_hashes,
        }
    glove_definition = _layer_by_id(spec, "gloves_adventure")
    if glove_definition is not None:
        planned = spec["loadoutArtContract"]["plannedCanonicalLayer"]
        planned["sourceArtifact"] = copy.deepcopy(
            glove_definition["sourceArtifact"]
        )
        planned["runtimeArtifact"] = copy.deepcopy(
            glove_definition["runtimeArtifact"]
        )
    return errors


def write_assets(
    spec_path: pathlib.Path = DEFAULT_SPEC_PATH,
    runtime_root: pathlib.Path | None = None,
    runtime_sheet_path: pathlib.Path | None = None,
) -> list[str]:
    """Generate every runtime copy and preview, then refresh stored metadata."""

    spec_path = pathlib.Path(spec_path)
    spec, load_errors = _read_spec(spec_path)
    if spec is None:
        return load_errors
    errors = _contract_errors(spec_path, spec)
    if errors:
        return errors
    resolved_runtime_root = (
        pathlib.Path(runtime_root) if runtime_root is not None else _default_runtime_root(spec)
    )
    errors.extend(
        _promote_and_generate_loadout_sources(
            spec_path,
            spec,
            resolved_runtime_root,
        )
    )
    errors.extend(_contract_errors(spec_path, spec))
    if errors:
        return errors
    layers, layer_errors = load_source_layers(spec_path, spec)
    errors.extend(layer_errors)
    if errors:
        return errors
    _, combination_errors = validate_combinations(spec, layers)
    errors.extend(combination_errors)
    generated, generation_errors = build_generated_assets(spec, layers)
    errors.extend(generation_errors)
    if errors:
        return errors

    resolved_runtime_sheet = (
        pathlib.Path(runtime_sheet_path)
        if runtime_sheet_path is not None
        else DEFAULT_RUNTIME_SHEET_PATH
    )
    for definition in spec["canonicalLayers"]:
        source_path = _source_path(spec_path, definition)
        target_path = _runtime_path(resolved_runtime_root, definition)
        target_path.parent.mkdir(parents=True, exist_ok=True)
        target_path.write_bytes(source_path.read_bytes())

    for name, tile in generated.tiles.items():
        for scale in spec["generatedPreviews"]["scales"]:
            preview = tile if scale == 1 else tile.resize(
                (tile.width * scale, tile.height * scale),
                Image.Resampling.NEAREST,
            )
            _save_png(preview, _preview_path(spec_path, spec, name, scale))

    sheet_path = _generated_sheet_path(spec_path, spec)
    _save_png(generated.sheet, sheet_path)
    resolved_runtime_sheet.parent.mkdir(parents=True, exist_ok=True)
    resolved_runtime_sheet.write_bytes(sheet_path.read_bytes())
    errors.extend(
        _refresh_written_spec(
            spec_path, spec, resolved_runtime_root, generated
        )
    )
    if errors:
        return errors
    spec_path.write_text(
        json.dumps(spec, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return _sync_equipment_loadout_contract(spec_path, spec)


def _compare_generated_file(
    path: pathlib.Path,
    expected: Image.Image,
    owner: str,
) -> list[str]:
    if not path.exists():
        return [f"{owner} is missing: {path}"]
    image, load_errors = _open_exact_rgba(path, owner)
    if image is None:
        return load_errors
    errors = list(load_errors)
    if image.size != expected.size or image.tobytes() != expected.tobytes():
        errors.append(f"{owner} differs from deterministic in-memory generation")
    if path.read_bytes() != _png_bytes(expected):
        errors.append(f"{owner} PNG bytes differ from deterministic generation")
    return errors


def _stored_generated_metadata_errors(
    spec_path: pathlib.Path,
    spec: dict,
) -> list[str]:
    errors: list[str] = []
    sheet_path = _generated_sheet_path(spec_path, spec)
    sheet, sheet_errors = _open_exact_rgba(sheet_path, "generated sheet")
    errors.extend(sheet_errors)
    if sheet is not None:
        contract = spec["generatedSheet"]
        if contract.get("status") != "available":
            errors.append("generated sheet status must be available")
        metadata = _image_metadata(sheet_path, sheet)
        for key in ("fileByteCount", "opaqueBounds", "opaquePixelCount"):
            if contract.get(key) != metadata[key]:
                errors.append(f"generated sheet {key} differs")
        for name, actual in metadata["hashes"].items():
            if contract.get("hashes", {}).get(name) != actual:
                errors.append(f"generated sheet {name} differs")
        expected_tiles = contract.get("hashes", {}).get("tileRgbaSha256", {})
        for definition in contract["tileMap"]:
            left = definition["column"] * 64
            top = definition["row"] * 64
            actual = hashlib.sha256(
                sheet.crop((left, top, left + 64, top + 64)).tobytes()
            ).hexdigest()
            if expected_tiles.get(definition["name"]) != actual:
                errors.append(
                    f"generated sheet tile hash differs for {definition['name']}"
                )
    preview_contract = spec["generatedPreviews"]
    if preview_contract.get("status") != "available":
        errors.append("generated previews status must be available")
    artifacts = preview_contract.get("artifacts", {})
    for key, artifact in artifacts.items():
        path = spec_path.parent / artifact["path"]
        image, image_errors = _open_exact_rgba(path, f"generated preview {key}")
        errors.extend(image_errors)
        if image is None:
            continue
        if artifact.get("width") != image.width or artifact.get("height") != image.height:
            errors.append(f"generated preview {key} dimensions differ")
        if artifact.get("fileByteCount") != path.stat().st_size:
            errors.append(f"generated preview {key} fileByteCount differs")
        for name, actual in _hashes(path, image).items():
            if artifact.get("hashes", {}).get(name) != actual:
                errors.append(f"generated preview {key} {name} differs")
    return errors


def check_assets(
    spec_path: pathlib.Path = DEFAULT_SPEC_PATH,
    runtime_root: pathlib.Path | None = None,
    runtime_sheet_path: pathlib.Path | None = None,
) -> list[str]:
    """Rebuild in memory and compare every stored output without writing files."""

    spec_path = pathlib.Path(spec_path)
    spec, load_errors = _read_spec(spec_path)
    if spec is None:
        return load_errors
    errors = _contract_errors(spec_path, spec)
    resolved_runtime_root = (
        pathlib.Path(runtime_root) if runtime_root is not None else _default_runtime_root(spec)
    )
    resolved_runtime_sheet = (
        pathlib.Path(runtime_sheet_path)
        if runtime_sheet_path is not None
        else DEFAULT_RUNTIME_SHEET_PATH
    )
    layers, layer_errors = load_source_layers(
        spec_path, spec, verify_artifacts=True
    )
    errors.extend(layer_errors)
    for definition in spec.get("canonicalLayers", []):
        _, one_errors = _validate_one_layer(
            spec_path,
            spec,
            definition,
            resolved_runtime_root,
            layers.get("body_base"),
            verify_artifacts=True,
        )
        errors.extend(one_errors)
    if layer_errors:
        return errors
    _, combination_errors = validate_combinations(spec, layers)
    errors.extend(combination_errors)
    generated, generation_errors = build_generated_assets(spec, layers)
    errors.extend(generation_errors)
    if generation_errors:
        return errors

    sheet_path = _generated_sheet_path(spec_path, spec)
    errors.extend(_compare_generated_file(sheet_path, generated.sheet, "generated sheet"))
    if not resolved_runtime_sheet.exists():
        errors.append(f"runtime generated sheet is missing: {resolved_runtime_sheet}")
    elif sheet_path.exists() and resolved_runtime_sheet.read_bytes() != sheet_path.read_bytes():
        errors.append("runtime generated sheet is not byte-identical to docs sheet")
    for name, tile in generated.tiles.items():
        for scale in spec["generatedPreviews"]["scales"]:
            expected = tile if scale == 1 else tile.resize(
                (tile.width * scale, tile.height * scale), Image.Resampling.NEAREST
            )
            errors.extend(
                _compare_generated_file(
                    _preview_path(spec_path, spec, name, scale),
                    expected,
                    f"generated preview {name}@{scale}x",
                )
            )
    errors.extend(_stored_generated_metadata_errors(spec_path, spec))
    return list(dict.fromkeys(errors))


def validate_generated_sheet(
    image: Image.Image,
    spec_path: pathlib.Path,
    spec: dict,
) -> list[str]:
    """Validate a stored schema v5 sheet entirely from independent sources."""

    errors = _contract_errors(spec_path, spec)
    layers, layer_errors = load_source_layers(spec_path, spec, verify_artifacts=True)
    errors.extend(layer_errors)
    if layer_errors:
        return errors
    generated, generation_errors = build_generated_assets(spec, layers)
    errors.extend(generation_errors)
    if image.size != (512, 128):
        errors.append(f"schema v5 sheet size must be 512x128; got {image.size}")
    if image.mode != "RGBA":
        errors.append(f"schema v5 sheet mode must be RGBA; got {image.mode}")
    if image.size == generated.sheet.size and image.mode == "RGBA":
        if image.tobytes() != generated.sheet.tobytes():
            errors.append(
                "schema v5 sheet differs from independent layers and deterministic debug/palette tiles"
            )
    return errors


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build and validate Todo Quest character assets."
    )
    parser.add_argument("--spec", type=pathlib.Path, default=DEFAULT_SPEC_PATH)
    parser.add_argument("--runtime-root", type=pathlib.Path)
    parser.add_argument("--runtime-sheet", type=pathlib.Path)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check-contract", action="store_true")
    modes.add_argument("--check-layer", metavar="LAYER_ID")
    modes.add_argument("--write", action="store_true")
    modes.add_argument("--check", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    if args.check_contract:
        errors = validate_contract(args.spec)
        success = f"Character layer contract validation passed: {args.spec}"
    elif args.check_layer:
        errors = validate_layer(args.spec, args.check_layer, args.runtime_root)
        success = f"Character layer validation passed: {args.check_layer}"
    elif args.check:
        errors = check_assets(
            args.spec, args.runtime_root, args.runtime_sheet
        )
        success = f"Character asset check passed: {args.spec}"
    else:
        errors = write_assets(
            args.spec, args.runtime_root, args.runtime_sheet
        )
        success = f"Character asset generation passed: {args.spec}"
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(success)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
