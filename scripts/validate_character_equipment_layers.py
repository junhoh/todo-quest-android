"""Validate Todo Quest gameplay equipment character-layer variants."""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys
from collections.abc import Mapping, Sequence

from PIL import Image


SCRIPTS_DIR = pathlib.Path(__file__).resolve().parent
REPOSITORY_ROOT = SCRIPTS_DIR.parent
DEFAULT_SPEC_PATHS = tuple(
    REPOSITORY_ROOT / "docs" / "art" / "equipment" / name
    for name in (
        "todo-quest-helmet-layers-spec.json",
        "todo-quest-top-bottom-layers-spec.json",
        "todo-quest-gloves-shoes-layers-spec.json",
        "todo-quest-weapon-layers-spec.json",
    )
)


Point = tuple[int, int]
Rgba = tuple[int, int, int, int]
Bounds = tuple[int, int, int, int]

EXPECTED_PALETTE = {
    "hairBlack": "#11151C",
    "eyeDarkNavy": "#1D3557",
    "outlineDarkNavy": "#263B5A",
    "hairHighlight": "#35445C",
    "underDark": "#3A3F45",
    "blueShadow": "#2853A6",
    "underMid": "#737982",
    "underLight": "#B7B0A3",
    "skinShadow": "#D99872",
    "skinLight": "#FFD3AE",
    "lightCream": "#F4EFE3",
    "bluePrimary": "#4F86E8",
    "blueHighlight": "#7FB3FF",
    "tealAccent": "#5CC8A7",
    "goldAccent": "#F2C14E",
    "redAccent": "#E05252",
}
EXPECTED_ITEMS = {
    1003: {
        "displayNameKorean": "가죽 모자",
        "key": "headgear_leather_hat",
        "sourcePath": "layers/headgear_leather_hat.png",
        "runtimePath": "character/layers/headgear_leather_hat.png",
        "bounds": [19, 4, 45, 22],
        "previewKeys": {
            "equipped1x": "leather-hat-equipped@1x",
            "equipped8x": "leather-hat-equipped@8x",
        },
    },
    1004: {
        "displayNameKorean": "철 투구",
        "key": "headgear_iron_helmet",
        "sourcePath": "layers/headgear_iron_helmet.png",
        "runtimePath": "character/layers/headgear_iron_helmet.png",
        "bounds": [18, 4, 46, 29],
        "previewKeys": {
            "equipped1x": "iron-helmet-equipped@1x",
            "equipped8x": "iron-helmet-equipped@8x",
        },
    },
}
EXPECTED_PREVIEWS = {
    "leather-hat-equipped@1x": (1003, 1, "previews/leather-hat-equipped.png", 64),
    "leather-hat-equipped@8x": (1003, 8, "previews/leather-hat-equipped@8x.png", 512),
    "iron-helmet-equipped@1x": (1004, 1, "previews/iron-helmet-equipped.png", 64),
    "iron-helmet-equipped@8x": (1004, 8, "previews/iron-helmet-equipped@8x.png", 512),
}
EXPECTED_OUTFIT_ITEMS = {
    1005: {
        "displayNameKorean": "천 상의",
        "key": "top_cloth",
        "slot": "top",
        "sourcePath": "layers/top_cloth.png",
        "runtimePath": "character/layers/top_cloth.png",
        "bounds": [20, 29, 44, 45],
        "previewKeys": {
            "equipped1x": "top-cloth-equipped@1x",
            "equipped8x": "top-cloth-equipped@8x",
        },
    },
    1006: {
        "displayNameKorean": "가죽 갑옷",
        "key": "top_leather_armor",
        "slot": "top",
        "sourcePath": "layers/top_leather_armor.png",
        "runtimePath": "character/layers/top_leather_armor.png",
        "bounds": [20, 29, 44, 45],
        "previewKeys": {
            "equipped1x": "top-leather-armor-equipped@1x",
            "equipped8x": "top-leather-armor-equipped@8x",
        },
    },
    1007: {
        "displayNameKorean": "철 흉갑",
        "key": "top_iron_breastplate",
        "slot": "top",
        "sourcePath": "layers/top_iron_breastplate.png",
        "runtimePath": "character/layers/top_iron_breastplate.png",
        "bounds": [20, 29, 44, 45],
        "previewKeys": {
            "equipped1x": "top-iron-breastplate-equipped@1x",
            "equipped8x": "top-iron-breastplate-equipped@8x",
        },
    },
    1008: {
        "displayNameKorean": "천 바지",
        "key": "bottom_cloth_pants",
        "slot": "bottom",
        "sourcePath": "layers/bottom_cloth_pants.png",
        "runtimePath": "character/layers/bottom_cloth_pants.png",
        "bounds": [24, 41, 40, 54],
        "previewKeys": {
            "equipped1x": "bottom-cloth-pants-equipped@1x",
            "equipped8x": "bottom-cloth-pants-equipped@8x",
        },
    },
    1009: {
        "displayNameKorean": "가죽 바지",
        "key": "bottom_leather_pants",
        "slot": "bottom",
        "sourcePath": "layers/bottom_leather_pants.png",
        "runtimePath": "character/layers/bottom_leather_pants.png",
        "bounds": [24, 41, 40, 54],
        "previewKeys": {
            "equipped1x": "bottom-leather-pants-equipped@1x",
            "equipped8x": "bottom-leather-pants-equipped@8x",
        },
    },
    1010: {
        "displayNameKorean": "강철 각반",
        "key": "bottom_steel_greaves",
        "slot": "bottom",
        "sourcePath": "layers/bottom_steel_greaves.png",
        "runtimePath": "character/layers/bottom_steel_greaves.png",
        "bounds": [24, 41, 40, 54],
        "previewKeys": {
            "equipped1x": "bottom-steel-greaves-equipped@1x",
            "equipped8x": "bottom-steel-greaves-equipped@8x",
        },
    },
}
EXPECTED_OUTFIT_PREVIEWS = {
    preview_key: (
        equipment_id,
        scale,
        f"previews/{preview_key.replace('@1x', '').replace('@8x', '@8x')}.png",
        64 * scale,
    )
    for equipment_id, item in EXPECTED_OUTFIT_ITEMS.items()
    for scale, preview_name in ((1, "equipped1x"), (8, "equipped8x"))
    for preview_key in (item["previewKeys"][preview_name],)
}
EXPECTED_OUTFIT_PREVIEWS.update(
    {
        "top-bottom-combination-matrix@1x": (
            None,
            1,
            "previews/top-bottom-combination-matrix.png",
            192,
        ),
        "top-bottom-combination-matrix@4x": (
            None,
            4,
            "previews/top-bottom-combination-matrix@4x.png",
            768,
        ),
    }
)
EXPECTED_OUTFIT_LAYER_CONTRACTS = {
    "top": {
        "requiredOpaqueRegions": {"waistOverlap": [24, 41, 40, 43]},
        "hiddenOverlapOutlineForbiddenRegions": {},
        "twoPixelHorizontalOutlineForbidden": [],
    },
    "bottom": {
        "requiredOpaqueRegions": {
            "waistOverlap": [24, 41, 40, 43],
            "leftAnkleOverlap": [24, 53, 31, 54],
            "rightAnkleOverlap": [33, 53, 40, 54],
        },
        "hiddenOverlapOutlineForbiddenRegions": {
            "waistOverlap": [24, 41, 40, 43],
        },
        "twoPixelHorizontalOutlineForbidden": [
            {
                "name": "ankleOverlap",
                "xRanges": [[24, 31], [33, 40]],
                "rows": [53, 54],
            }
        ],
    },
}
EXPECTED_GLOVES_SHOES_ITEMS = {
    1011: {
        "displayNameKorean": "가죽 장갑",
        "equipmentKey": "leather_gloves",
        "key": "gloves_leather",
        "renderSlot": "hands_front",
        "equipmentSlot": "GLOVES",
        "sourcePath": "layers/gloves_leather.png",
        "runtimePath": "character/layers/gloves_leather.png",
        "bounds": [21, 39, 43, 45],
        "previewKeys": {
            "equipped1x": "leather-gloves-equipped@1x",
            "equipped8x": "leather-gloves-equipped@8x",
        },
    },
    1015: {
        "displayNameKorean": "강철 건틀릿",
        "equipmentKey": "steel_gauntlets",
        "key": "gloves_steel_gauntlets",
        "renderSlot": "hands_front",
        "equipmentSlot": "GLOVES",
        "sourcePath": "layers/gloves_steel_gauntlets.png",
        "runtimePath": "character/layers/gloves_steel_gauntlets.png",
        "bounds": [21, 39, 43, 45],
        "previewKeys": {
            "equipped1x": "steel-gauntlets-equipped@1x",
            "equipped8x": "steel-gauntlets-equipped@8x",
        },
    },
    1012: {
        "displayNameKorean": "여행자의 장화",
        "equipmentKey": "travelers_boots",
        "key": "shoes_travelers_boots",
        "renderSlot": "shoes",
        "equipmentSlot": "SHOES",
        "sourcePath": "layers/shoes_travelers_boots.png",
        "runtimePath": "character/layers/shoes_travelers_boots.png",
        "bounds": [23, 53, 41, 58],
        "previewKeys": {
            "equipped1x": "travelers-boots-equipped@1x",
            "equipped8x": "travelers-boots-equipped@8x",
        },
    },
    1016: {
        "displayNameKorean": "바람걸음 장화",
        "equipmentKey": "windwalker_boots",
        "key": "shoes_windwalker_boots",
        "renderSlot": "shoes",
        "equipmentSlot": "SHOES",
        "sourcePath": "layers/shoes_windwalker_boots.png",
        "runtimePath": "character/layers/shoes_windwalker_boots.png",
        "bounds": [23, 53, 41, 58],
        "previewKeys": {
            "equipped1x": "windwalker-boots-equipped@1x",
            "equipped8x": "windwalker-boots-equipped@8x",
        },
    },
}
EXPECTED_GLOVES_SHOES_PREVIEWS = {
    preview_key: (
        equipment_id,
        scale,
        f"previews/{preview_key.replace('@1x', '').replace('@8x', '@8x')}.png",
        64 * scale,
    )
    for equipment_id, item in EXPECTED_GLOVES_SHOES_ITEMS.items()
    for scale, preview_name in ((1, "equipped1x"), (8, "equipped8x"))
    for preview_key in (item["previewKeys"][preview_name],)
}
EXPECTED_GLOVES_SHOES_PREVIEWS.update(
    {
        "gloves-shoes-combination-matrix@1x": (
            None,
            1,
            "previews/gloves-shoes-combination-matrix.png",
            128,
        ),
        "gloves-shoes-combination-matrix@4x": (
            None,
            4,
            "previews/gloves-shoes-combination-matrix@4x.png",
            512,
        ),
    }
)
EXPECTED_GLOVES_SHOES_LAYERS = {
    "hands_front": {
        "renderSlot": "hands_front",
        "equipmentSlot": "GLOVES",
        "referenceAlphaMask": {
            "baseCharacterSourceId": "hands_front",
            "path": "../character/layers/hands_front.png",
            "opaqueBounds": [21, 39, 43, 45],
            "opaquePixelCount": 38,
            "alphaMaskSha256": "115452260a8f6d94e7dd000bda02875d52ec02a0516be5b547b82da8fc3169e3",
        },
        "connectivity": {
            "opaqueNeighborMode": 8,
            "opaqueComponentCount": 2,
            "allowIsolatedOpaquePixels": False,
            "components": [
                {
                    "name": "leftHand",
                    "opaqueBounds": [21, 39, 24, 45],
                    "opaquePixelCount": 19,
                },
                {
                    "name": "rightHand",
                    "opaqueBounds": [40, 39, 43, 45],
                    "opaquePixelCount": 19,
                },
            ],
        },
        "requiredOpaquePoints": {"primaryGripAnchor": [42, 42]},
    },
    "shoes": {
        "renderSlot": "shoes",
        "equipmentSlot": "SHOES",
        "referenceAlphaMask": {
            "baseCharacterSourceId": "shoes_adventure",
            "path": "../character/layers/shoes_adventure.png",
            "opaqueBounds": [23, 53, 41, 58],
            "opaquePixelCount": 104,
            "alphaMaskSha256": "9d42064b05d8ede2c5ca9ce1cb0dc7c0551ed11f2e9f855e6e25fe0e69f0f104",
        },
        "connectivity": {
            "opaqueNeighborMode": 8,
            "opaqueComponentCount": 2,
            "allowIsolatedOpaquePixels": False,
            "components": [
                {
                    "name": "leftFoot",
                    "opaqueBounds": [23, 53, 31, 58],
                    "opaquePixelCount": 52,
                },
                {
                    "name": "rightFoot",
                    "opaqueBounds": [33, 53, 41, 58],
                    "opaquePixelCount": 52,
                },
            ],
        },
        "requiredOpaqueRegions": {
            "leftAnkleOverlap": [24, 53, 31, 54],
            "rightAnkleOverlap": [33, 53, 40, 54],
        },
        "soleContract": {
            "soleY": 58,
            "centerX": 32,
            "centerPixelTransparent": True,
            "leftOpaqueRange": [23, 31],
            "rightOpaqueRange": [33, 41],
        },
    },
}
LOADOUT_COMPOSITION_ORDER = [
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
]
LOADOUT_PREVIEW_NAMES = [
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
]
LOADOUT_EMPTY_SLOTS = {
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
LOADOUT_ADVENTURE_SLOTS = {
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
        "sourceIds": [
            "weapon_back_default_sword",
            "weapon_held_default_sword",
            "weapon_front_default_sword",
        ],
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
EXPECTED_WEAPON_ITEMS = {
    1001: {
        "displayNameKorean": "낡은 검",
        "equipmentKey": "worn_sword",
        "weaponType": "LONGSWORD",
        "key": "weapon_worn_sword",
        "sourcePath": "layers/weapon_worn_sword.png",
        "runtimePath": "character/layers/weapon_worn_sword.png",
        "previewKeys": {
            "equipped1x": "worn-sword-equipped@1x",
            "equipped8x": "worn-sword-equipped@8x",
        },
    },
    1002: {
        "displayNameKorean": "철 장검",
        "equipmentKey": "iron_longsword",
        "weaponType": "LONGSWORD",
        "key": "weapon_iron_longsword",
        "sourcePath": "layers/weapon_iron_longsword.png",
        "runtimePath": "character/layers/weapon_iron_longsword.png",
        "previewKeys": {
            "equipped1x": "iron-longsword-equipped@1x",
            "equipped8x": "iron-longsword-equipped@8x",
        },
    },
    1017: {
        "displayNameKorean": "물푸레나무 창",
        "equipmentKey": "ash_spear",
        "weaponType": "SPEAR",
        "key": "weapon_ash_spear",
        "sourcePath": "layers/weapon_ash_spear.png",
        "runtimePath": "character/layers/weapon_ash_spear.png",
        "previewKeys": {
            "equipped1x": "ash-spear-equipped@1x",
            "equipped8x": "ash-spear-equipped@8x",
        },
    },
    1018: {
        "displayNameKorean": "강철 철퇴",
        "equipmentKey": "steel_mace",
        "weaponType": "BLUNT",
        "key": "weapon_steel_mace",
        "sourcePath": "layers/weapon_steel_mace.png",
        "runtimePath": "character/layers/weapon_steel_mace.png",
        "previewKeys": {
            "equipped1x": "steel-mace-equipped@1x",
            "equipped8x": "steel-mace-equipped@8x",
        },
    },
}
EXPECTED_WEAPON_PREVIEWS = {
    preview_key: (
        equipment_id,
        scale,
        f"previews/{preview_key.replace('@1x', '').replace('@8x', '@8x')}.png",
        64 * scale,
    )
    for equipment_id, item in EXPECTED_WEAPON_ITEMS.items()
    for scale, preview_name in ((1, "equipped1x"), (8, "equipped8x"))
    for preview_key in (item["previewKeys"][preview_name],)
}
EXPECTED_WEAPON_PREVIEWS.update(
    {
        "weapon-combination-matrix@1x": (
            None,
            1,
            "previews/weapon-combination-matrix.png",
            128,
        ),
        "weapon-combination-matrix@4x": (
            None,
            4,
            "previews/weapon-combination-matrix@4x.png",
            512,
        ),
    }
)
EXPECTED_WEAPON_LAYER = {
    "renderSlot": "weapon_front",
    "equipmentSlot": "WEAPON",
    "allowedWeaponTypes": ["LONGSWORD", "SPEAR", "BLUNT"],
    "allowedAlphaValues": [0, 255],
    "transparentPixelRgba": [0, 0, 0, 0],
    "chromaKey": "#FF00FF",
    "chromaKeyAllowed": False,
    "alphaCompositing": "source-over",
    "interpolation": "nearest-neighbor",
    "opaqueEnvelope": [40, 4, 58, 58],
    "faceProtectedRegion": [20, 7, 44, 28],
    "faceProtectedRegionInclusive": True,
    "handOverlapContract": {
        "region": [40, 39, 44, 45],
        "allowedOpaqueEnvelope": [41, 39, 44, 45],
        "allowedPart": "handle",
        "forbiddenParts": ["blade", "spearhead", "maceHead"],
    },
    "connectivity": {
        "opaqueNeighborMode": 8,
        "opaqueComponentCount": 1,
        "allowIsolatedOpaquePixels": False,
    },
    "requiredOpaquePoints": {"primaryGripAnchor": [42, 42]},
    "coordinatePreservation": {
        "singleCanvasOrigin": [0, 0],
        "translationAllowed": False,
        "croppingAllowed": False,
        "scalingAllowed": False,
        "thumbnailOpaqueBoundsReadOnlyScalingAllowed": True,
    },
}
HASH_FIELDS = ("fileSha256", "rawRgbaSha256", "alphaMaskSha256")
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def _load_spec(spec_path: pathlib.Path) -> dict:
    value = json.loads(spec_path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError("specification must be a JSON object")
    return value


def _error(spec_path: pathlib.Path, message: str) -> str:
    return f"{spec_path}: {message}"


def _mapping(value: object, name: str) -> Mapping:
    if not isinstance(value, Mapping):
        raise TypeError(f"{name} must be an object")
    return value


def _integer(value: object, name: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise TypeError(f"{name} must be an integer")
    return value


def _integer_array(value: object, name: str, length: int) -> list[int]:
    if isinstance(value, (str, bytes)) or not isinstance(value, Sequence):
        raise TypeError(f"{name} must be an array")
    if len(value) != length:
        raise ValueError(f"{name} must contain exactly {length} integers")
    return [_integer(item, f"{name}[{index}]") for index, item in enumerate(value)]


def _safe_relative_path(value: object, name: str) -> str:
    if not isinstance(value, str) or not value:
        raise TypeError(f"{name} must be a non-empty string")
    if "\\" in value or re.match(r"^[A-Za-z]:", value):
        raise ValueError(f"{name} must be a safe relative path using forward slashes")
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ValueError(f"{name} must be a safe relative path")
    if path.suffix.lower() != ".png":
        raise ValueError(f"{name} must reference a PNG")
    return value


def _artifact_contract_errors(
    artifact_value: object,
    owner: str,
    expected_bounds: list[int] | None,
) -> list[str]:
    artifact = _mapping(artifact_value, owner)
    errors: list[str] = []
    status = artifact.get("status")
    if status not in ("pendingGeneration", "available"):
        errors.append(f"{owner}.status must be pendingGeneration or available")

    bounds_value = artifact.get("opaqueBounds")
    if expected_bounds is not None:
        try:
            bounds = _integer_array(bounds_value, f"{owner}.opaqueBounds", 4)
            if bounds != expected_bounds:
                errors.append(
                    f"{owner}.opaqueBounds must be {expected_bounds}; got {bounds}"
                )
        except (TypeError, ValueError) as error:
            errors.append(str(error))
    elif bounds_value is not None:
        try:
            _integer_array(bounds_value, f"{owner}.opaqueBounds", 4)
        except (TypeError, ValueError) as error:
            errors.append(str(error))

    hashes_value = artifact.get("hashes")
    try:
        hashes = _mapping(hashes_value, f"{owner}.hashes")
    except TypeError as error:
        return errors + [str(error)]

    if status == "pendingGeneration":
        for field in ("opaquePixelCount", "fileByteCount"):
            if artifact.get(field) is not None:
                errors.append(
                    f"{owner}.{field} must be null while status is pendingGeneration"
                )
        for field in HASH_FIELDS:
            if hashes.get(field) is not None:
                errors.append(
                    f"{owner}.hashes.{field} must be null while status is pendingGeneration"
                )
    elif status == "available":
        for field in ("opaquePixelCount", "fileByteCount"):
            value = artifact.get(field)
            if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                errors.append(f"{owner}.{field} must be a positive integer")
        if bounds_value is None:
            errors.append(f"{owner}.opaqueBounds is required when available")
        for field in HASH_FIELDS:
            value = hashes.get(field)
            if not isinstance(value, str) or not SHA256_PATTERN.fullmatch(value):
                errors.append(f"{owner}.hashes.{field} must be a lowercase SHA-256")
    return errors


def _common_layer_contract_errors(
    layer_value: object,
    owner: str,
    slot: str,
) -> list[str]:
    layer = _mapping(layer_value, owner)
    errors: list[str] = []
    expected_values = {
        "slot": slot,
        "allowedAlphaValues": [0, 255],
        "transparentPixelRgba": [0, 0, 0, 0],
        "chromaKey": "#FF00FF",
        "chromaKeyAllowed": False,
        "alphaCompositing": "source-over",
        "interpolation": "nearest-neighbor",
    }
    for name, expected in expected_values.items():
        if layer.get(name) != expected:
            errors.append(f"{owner}.{name} must be {expected!r}")

    outline = _mapping(layer.get("outline"), f"{owner}.outline")
    if dict(outline) != {
        "paletteName": "outlineDarkNavy",
        "color": "#263B5A",
        "widthLogicalPixels": 1,
        "externalBoundaryNeighborMode": 4,
        "allExternalBoundaryPixelsUseOutlineColor": True,
        "forbidExternalOutline2x2Blocks": True,
    }:
        errors.append(f"{owner}.outline must define the 1 logical pixel #263B5A boundary")

    connectivity = _mapping(layer.get("connectivity"), f"{owner}.connectivity")
    if dict(connectivity) != {
        "opaqueNeighborMode": 8,
        "opaqueComponentCount": 1,
        "allowIsolatedOpaquePixels": False,
    }:
        errors.append(f"{owner}.connectivity must require one 8-connected component")

    coordinates = _mapping(
        layer.get("coordinatePreservation"),
        f"{owner}.coordinatePreservation",
    )
    if dict(coordinates) != {
        "singleCanvasOrigin": [0, 0],
        "translationAllowed": False,
        "croppingAllowed": False,
        "scalingAllowed": False,
        "thumbnailOpaqueBoundsReadOnlyScalingAllowed": True,
    }:
        errors.append(f"{owner}.coordinatePreservation must keep the 64x64 origin")
    return errors


def _outfit_contract_errors(spec: dict, items: list[Mapping]) -> list[str]:
    errors: list[str] = []
    layers = _mapping(spec.get("layerContracts"), "layerContracts")
    if set(layers) != {"top", "bottom"}:
        errors.append("layerContracts must contain exactly top and bottom")
    for slot, expected_specific in EXPECTED_OUTFIT_LAYER_CONTRACTS.items():
        if slot not in layers:
            continue
        owner = f"layerContracts.{slot}"
        layer = _mapping(layers[slot], owner)
        errors.extend(_common_layer_contract_errors(layer, owner, slot))
        for name, expected in expected_specific.items():
            if layer.get(name) != expected:
                errors.append(f"{owner}.{name} must be {expected!r}")

    expected_composition = {
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
    composition = _mapping(spec.get("compositionContract"), "compositionContract")
    if dict(composition) != expected_composition:
        errors.append("compositionContract must preserve the top/bottom/shoes overlap contract")

    if len(items) != len(EXPECTED_OUTFIT_ITEMS):
        errors.append("items must contain exactly the 1005 through 1010 outfit variants")
    seen_ids: set[int] = set()
    seen_keys: set[str] = set()
    for index, item in enumerate(items):
        owner = f"items[{index}]"
        equipment_id = item.get("equipmentId")
        if equipment_id not in EXPECTED_OUTFIT_ITEMS:
            errors.append(f"{owner}.equipmentId is not a supported outfit equipment id")
            continue
        if equipment_id in seen_ids:
            errors.append(f"{owner}.equipmentId must be unique")
        seen_ids.add(equipment_id)
        expected = EXPECTED_OUTFIT_ITEMS[equipment_id]
        key = expected["key"]
        if key in seen_keys:
            errors.append(f"{owner}.layerKey must be unique")
        seen_keys.add(key)
        if item.get("displayNameKorean") != expected["displayNameKorean"]:
            errors.append(f"{owner}.displayNameKorean is incorrect")
        if item.get("imageKey") != key or item.get("layerKey") != key:
            errors.append(f"{owner} imageKey and layerKey must both be {key}")
        if item.get("slot") != expected["slot"]:
            errors.append(f"{owner}.slot must be {expected['slot']}")
        for field in ("sourcePath", "runtimePath"):
            try:
                actual = _safe_relative_path(item.get(field), f"{owner}.{field}")
                if actual != expected[field]:
                    errors.append(f"{owner}.{field} must be {expected[field]}")
            except (TypeError, ValueError) as error:
                errors.append(str(error))
        if item.get("previewKeys") != expected["previewKeys"]:
            errors.append(f"{owner}.previewKeys must match the declared previews")
        errors.extend(
            _artifact_contract_errors(
                item.get("sourceArtifact"),
                f"{owner}.sourceArtifact",
                expected["bounds"],
            )
        )
        errors.extend(
            _artifact_contract_errors(
                item.get("runtimeArtifact"),
                f"{owner}.runtimeArtifact",
                expected["bounds"],
            )
        )

    previews = _mapping(spec.get("previews"), "previews")
    matrix = _mapping(previews.get("combinationMatrix"), "previews.combinationMatrix")
    expected_matrix = {
        "topEquipmentIds": [1005, 1006, 1007],
        "bottomEquipmentIds": [1008, 1009, 1010],
        "columns": 3,
        "rows": 3,
        "cellWidth": 64,
        "cellHeight": 64,
        "previewKeys": {
            "matrix1x": "top-bottom-combination-matrix@1x",
            "matrix4x": "top-bottom-combination-matrix@4x",
        },
    }
    if dict(matrix) != expected_matrix:
        errors.append("previews.combinationMatrix must define the fixed 3x3 outfit matrix")

    artifacts = _mapping(previews.get("artifacts"), "previews.artifacts")
    if set(artifacts) != set(EXPECTED_OUTFIT_PREVIEWS):
        errors.append("previews.artifacts must declare twelve outfit previews and two matrix previews")
    for key, expected in EXPECTED_OUTFIT_PREVIEWS.items():
        if key not in artifacts:
            continue
        preview = _mapping(artifacts[key], f"previews.artifacts.{key}")
        equipment_id, scale, path, size = expected
        expected_fields = {
            "scale": scale,
            "path": path,
            "width": size,
            "height": size,
            "mode": "RGBA",
        }
        if equipment_id is None:
            expected_fields["kind"] = "combinationMatrix"
        else:
            expected_fields["equipmentId"] = equipment_id
        for field, value in expected_fields.items():
            if preview.get(field) != value:
                errors.append(f"previews.artifacts.{key}.{field} must be {value!r}")
        try:
            _safe_relative_path(preview.get("path"), f"previews.artifacts.{key}.path")
        except (TypeError, ValueError) as error:
            errors.append(str(error))
        errors.extend(
            _artifact_contract_errors(
                preview.get("artifact"),
                f"previews.artifacts.{key}.artifact",
                None,
            )
        )
    return errors


def _loadout_art_contract_errors(spec: dict) -> list[str]:
    """Validate the shared loadout art manifest before or after generation."""

    errors: list[str] = []
    contract = spec.get("loadoutArtContract")
    if not isinstance(contract, Mapping):
        return ["loadout art contract must define empty gameplay slots"]
    status = contract.get("status")
    if contract.get("contractVersion") != 1 or status not in {
        "pendingGeneration",
        "available",
    }:
        errors.append("loadout art contract must be pending or available at version 1")
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
    if contract.get("alwaysPresentSourceIds") != [
        "body_base",
        "hair_back_default",
        "hair_front_default",
        "hands_front",
    ]:
        errors.append("empty loadouts must always preserve body, hair, and hands sources")
    if contract.get("emptyGameplaySlots") != LOADOUT_EMPTY_SLOTS:
        errors.append("empty gameplay slots must use the fixed fallback and transparent overlays")
    if contract.get("neutralTrainingFallback") != {
        "sourceIds": ["top_default", "bottom_default", "shoes_default"],
        "paletteNames": ["underDark", "underMid", "underLight"],
        "descriptionKorean": "회갈색 계열의 중립 훈련복",
    }:
        errors.append("neutral training fallback must use the three default training layers")

    adventure_set = contract.get("adventureShopSet")
    slots = adventure_set.get("slots") if isinstance(adventure_set, Mapping) else None
    if not isinstance(adventure_set, Mapping) or adventure_set.get("setKey") != "adventure_set":
        errors.append("adventure shop set must retain its stable set key")
    if not isinstance(slots, Mapping):
        errors.append("adventure shop set must define all seven gameplay slots")
    else:
        if slots.get("GLOVES") != LOADOUT_ADVENTURE_SLOTS["GLOVES"]:
            errors.append("adventure shop set must include the gloves_adventure layer key")
        if slots.get("WEAPON") != LOADOUT_ADVENTURE_SLOTS["WEAPON"]:
            errors.append("adventure sword must remain one item backed by three split sources")
        if dict(slots) != LOADOUT_ADVENTURE_SLOTS:
            errors.append("adventure shop set must preserve all seven stable layer keys")

    planned = contract.get("plannedCanonicalLayer")
    expected_planned_fields = {
        "id": "gloves_adventure",
        "slot": "hands_front",
        "sourcePath": "docs/art/character/layers/gloves_adventure.png",
        "runtimePath": "app/src/main/assets/character/layers/gloves_adventure.png",
        "replacesSourceIdWhenEquipped": "hands_front",
        "referenceAlphaMaskSourceId": "hands_front",
    }
    if not isinstance(planned, Mapping) or any(
        planned.get(name) != value
        for name, value in expected_planned_fields.items()
    ):
        errors.append("planned gloves_adventure must preserve its source and runtime identity")
    elif status == "pendingGeneration":
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
    elif status == "available":
        source_artifact = planned.get("sourceArtifact")
        runtime_artifact = planned.get("runtimeArtifact")
        errors.extend(
            _artifact_contract_errors(
                source_artifact,
                "loadoutArtContract.plannedCanonicalLayer.sourceArtifact",
                [21, 39, 43, 45],
            )
        )
        errors.extend(
            _artifact_contract_errors(
                runtime_artifact,
                "loadoutArtContract.plannedCanonicalLayer.runtimeArtifact",
                [21, 39, 43, 45],
            )
        )
        if source_artifact != runtime_artifact:
            errors.append("generated gloves_adventure artifacts must be byte-identical")
    if contract.get("compositionOrder") != LOADOUT_COMPOSITION_ORDER:
        errors.append("loadout composition order must match character schema v5")
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
    if not isinstance(regeneration, Mapping):
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
        if regeneration.get("generatedPreviewTileNames") != LOADOUT_PREVIEW_NAMES:
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


def _gloves_shoes_contract_errors(
    spec: dict,
    items: list[Mapping],
) -> list[str]:
    errors = _loadout_art_contract_errors(spec)
    layers = _mapping(spec.get("layerContracts"), "layerContracts")
    if set(layers) != set(EXPECTED_GLOVES_SHOES_LAYERS):
        errors.append("layerContracts must contain exactly hands_front and shoes")

    common_values = {
        "allowedAlphaValues": [0, 255],
        "transparentPixelRgba": [0, 0, 0, 0],
        "chromaKey": "#FF00FF",
        "chromaKeyAllowed": False,
        "alphaCompositing": "source-over",
        "interpolation": "nearest-neighbor",
    }
    expected_coordinates = {
        "singleCanvasOrigin": [0, 0],
        "translationAllowed": False,
        "croppingAllowed": False,
        "scalingAllowed": False,
        "thumbnailOpaqueBoundsReadOnlyScalingAllowed": True,
    }
    for render_slot, expected in EXPECTED_GLOVES_SHOES_LAYERS.items():
        if render_slot not in layers:
            continue
        owner = f"layerContracts.{render_slot}"
        layer = _mapping(layers[render_slot], owner)
        for name, value in common_values.items():
            if layer.get(name) != value:
                errors.append(f"{owner}.{name} must be {value!r}")
        for name in ("renderSlot", "equipmentSlot", "referenceAlphaMask", "connectivity"):
            if layer.get(name) != expected[name]:
                errors.append(f"{owner}.{name} must preserve the fixed {render_slot} mask contract")
        for name in ("requiredOpaquePoints", "requiredOpaqueRegions", "soleContract"):
            if name in expected and layer.get(name) != expected[name]:
                errors.append(f"{owner}.{name} must be {expected[name]!r}")
            if name not in expected and name in layer:
                errors.append(f"{owner}.{name} is not part of the {render_slot} contract")
        coordinates = _mapping(
            layer.get("coordinatePreservation"),
            f"{owner}.coordinatePreservation",
        )
        if dict(coordinates) != expected_coordinates:
            errors.append(f"{owner}.coordinatePreservation must keep the 64x64 origin")

    expected_composition = {
        "gloveVariantReplaces": "hands_front",
        "shoeVariantReplaces": "shoes_adventure",
        "gloveGripOrder": ["weapon_held", "hands_front", "weapon_front"],
        "allSourcesShareCanvasOrigin": True,
        "thumbnailUsesOpaqueBoundsReadOnly": True,
    }
    composition = _mapping(spec.get("compositionContract"), "compositionContract")
    if dict(composition) != expected_composition:
        errors.append("compositionContract must preserve the hands grip and shoes source replacement order")

    if len(items) != len(EXPECTED_GLOVES_SHOES_ITEMS):
        errors.append("items must contain exactly the 1011, 1012, 1015, and 1016 variants")
    seen_ids: set[int] = set()
    seen_keys: set[str] = set()
    for index, item in enumerate(items):
        owner = f"items[{index}]"
        equipment_id = item.get("equipmentId")
        if equipment_id not in EXPECTED_GLOVES_SHOES_ITEMS:
            errors.append(f"{owner}.equipmentId is not a supported gloves/shoes equipment id")
            continue
        if equipment_id in seen_ids:
            errors.append(f"{owner}.equipmentId must be unique")
        seen_ids.add(equipment_id)
        expected = EXPECTED_GLOVES_SHOES_ITEMS[equipment_id]
        key = expected["key"]
        if key in seen_keys:
            errors.append(f"{owner}.layerKey must be unique")
        seen_keys.add(key)
        for name in (
            "displayNameKorean",
            "equipmentKey",
            "renderSlot",
            "equipmentSlot",
        ):
            if item.get(name) != expected[name]:
                errors.append(f"{owner}.{name} must be {expected[name]!r}")
        if item.get("imageKey") != key or item.get("layerKey") != key:
            errors.append(f"{owner} imageKey and layerKey must both be {key}")
        for field in ("sourcePath", "runtimePath"):
            try:
                actual = _safe_relative_path(item.get(field), f"{owner}.{field}")
                if actual != expected[field]:
                    errors.append(f"{owner}.{field} must be {expected[field]}")
            except (TypeError, ValueError) as error:
                errors.append(str(error))
        if item.get("previewKeys") != expected["previewKeys"]:
            errors.append(f"{owner}.previewKeys must match the declared previews")
        errors.extend(
            _artifact_contract_errors(
                item.get("sourceArtifact"),
                f"{owner}.sourceArtifact",
                expected["bounds"],
            )
        )
        errors.extend(
            _artifact_contract_errors(
                item.get("runtimeArtifact"),
                f"{owner}.runtimeArtifact",
                expected["bounds"],
            )
        )

    previews = _mapping(spec.get("previews"), "previews")
    matrix = _mapping(previews.get("combinationMatrix"), "previews.combinationMatrix")
    expected_matrix = {
        "gloveEquipmentIds": [1011, 1015],
        "shoeEquipmentIds": [1012, 1016],
        "columns": 2,
        "rows": 2,
        "cellWidth": 64,
        "cellHeight": 64,
        "previewKeys": {
            "matrix1x": "gloves-shoes-combination-matrix@1x",
            "matrix4x": "gloves-shoes-combination-matrix@4x",
        },
    }
    if dict(matrix) != expected_matrix:
        errors.append("previews.combinationMatrix must define the fixed 2x2 gloves/shoes matrix")

    artifacts = _mapping(previews.get("artifacts"), "previews.artifacts")
    if set(artifacts) != set(EXPECTED_GLOVES_SHOES_PREVIEWS):
        errors.append("previews.artifacts must declare eight equipped previews and two matrix previews")
    for key, expected in EXPECTED_GLOVES_SHOES_PREVIEWS.items():
        if key not in artifacts:
            continue
        preview = _mapping(artifacts[key], f"previews.artifacts.{key}")
        equipment_id, scale, path, size = expected
        expected_fields = {
            "scale": scale,
            "path": path,
            "width": size,
            "height": size,
            "mode": "RGBA",
        }
        if equipment_id is None:
            expected_fields["kind"] = "combinationMatrix"
        else:
            expected_fields["equipmentId"] = equipment_id
        for field, value in expected_fields.items():
            if preview.get(field) != value:
                errors.append(f"previews.artifacts.{key}.{field} must be {value!r}")
        try:
            _safe_relative_path(preview.get("path"), f"previews.artifacts.{key}.path")
        except (TypeError, ValueError) as error:
            errors.append(str(error))
        errors.extend(
            _artifact_contract_errors(
                preview.get("artifact"),
                f"previews.artifacts.{key}.artifact",
                None,
            )
        )
    return errors


def _weapon_contract_errors(
    spec: dict,
    items: list[Mapping],
) -> list[str]:
    errors: list[str] = []
    layers = _mapping(spec.get("layerContracts"), "layerContracts")
    if set(layers) != {"weapon_front"}:
        errors.append("layerContracts must contain exactly weapon_front")
    elif dict(_mapping(layers["weapon_front"], "layerContracts.weapon_front")) != EXPECTED_WEAPON_LAYER:
        errors.append(
            "layerContracts.weapon_front must preserve the envelope, grip, face, "
            "hand-handle, and single-component weapon contract"
        )

    expected_composition = {
        "gameplayWeaponSourceCountPerItem": 1,
        "gameplayWeaponRenderSlot": "weapon_front",
        "allSourcesShareCanvasOrigin": True,
        "thumbnailUsesOpaqueBoundsReadOnly": True,
        "characterSchemaVersion": 5,
        "weaponGroup": {
            "groupId": "weapon",
            "zOrder": "topmost",
            "drawnAfterAllCharacterGroups": True,
            "containsAllGameplayWeaponSources": True,
        },
    }
    composition = _mapping(spec.get("compositionContract"), "compositionContract")
    if dict(composition) != expected_composition:
        errors.append(
            "compositionContract must define the schema-v5 topmost weapon group"
        )

    if len(items) != len(EXPECTED_WEAPON_ITEMS):
        errors.append("items must contain exactly the 1001, 1002, 1017, and 1018 weapons")
    seen_ids: set[int] = set()
    seen_equipment_keys: set[str] = set()
    seen_layer_keys: set[str] = set()
    for index, item in enumerate(items):
        owner = f"items[{index}]"
        equipment_id = item.get("equipmentId")
        if equipment_id not in EXPECTED_WEAPON_ITEMS:
            errors.append(f"{owner}.equipmentId is not a supported weapon equipment id")
            continue
        if equipment_id in seen_ids:
            errors.append(f"{owner}.equipmentId must be unique")
        seen_ids.add(equipment_id)
        expected = EXPECTED_WEAPON_ITEMS[equipment_id]
        for name in ("displayNameKorean", "equipmentKey", "weaponType"):
            if item.get(name) != expected[name]:
                errors.append(f"{owner}.{name} must be {expected[name]!r}")
        equipment_key = item.get("equipmentKey")
        if equipment_key in seen_equipment_keys:
            errors.append(f"{owner}.equipmentKey must be unique")
        if isinstance(equipment_key, str):
            seen_equipment_keys.add(equipment_key)
        layer_key = expected["key"]
        if item.get("imageKey") != layer_key or item.get("layerKey") != layer_key:
            errors.append(
                f"{owner} imageKey and layerKey must both be {layer_key}"
            )
        if layer_key in seen_layer_keys:
            errors.append(f"{owner}.layerKey must be unique")
        seen_layer_keys.add(layer_key)
        if item.get("renderSlot") != "weapon_front":
            errors.append(f"{owner}.renderSlot must be weapon_front")
        if item.get("equipmentSlot") != "WEAPON":
            errors.append(f"{owner}.equipmentSlot must be WEAPON")
        for field in ("sourcePath", "runtimePath"):
            try:
                actual = _safe_relative_path(item.get(field), f"{owner}.{field}")
                if actual != expected[field]:
                    errors.append(f"{owner}.{field} must be {expected[field]}")
            except (TypeError, ValueError) as error:
                errors.append(str(error))
        if item.get("previewKeys") != expected["previewKeys"]:
            errors.append(f"{owner}.previewKeys must match the declared previews")
        errors.extend(
            _artifact_contract_errors(
                item.get("sourceArtifact"),
                f"{owner}.sourceArtifact",
                None,
            )
        )
        errors.extend(
            _artifact_contract_errors(
                item.get("runtimeArtifact"),
                f"{owner}.runtimeArtifact",
                None,
            )
        )

    previews = _mapping(spec.get("previews"), "previews")
    matrix = _mapping(previews.get("combinationMatrix"), "previews.combinationMatrix")
    expected_matrix = {
        "weaponEquipmentIds": [1001, 1002, 1017, 1018],
        "columns": 2,
        "rows": 2,
        "cellWidth": 64,
        "cellHeight": 64,
        "previewKeys": {
            "matrix1x": "weapon-combination-matrix@1x",
            "matrix4x": "weapon-combination-matrix@4x",
        },
    }
    if dict(matrix) != expected_matrix:
        errors.append("previews.combinationMatrix must define the fixed 2x2 weapon matrix")

    artifacts = _mapping(previews.get("artifacts"), "previews.artifacts")
    if set(artifacts) != set(EXPECTED_WEAPON_PREVIEWS):
        errors.append(
            "previews.artifacts must declare eight equipped weapon previews and two matrix previews"
        )
    for key, expected in EXPECTED_WEAPON_PREVIEWS.items():
        if key not in artifacts:
            continue
        preview = _mapping(artifacts[key], f"previews.artifacts.{key}")
        equipment_id, scale, path, size = expected
        expected_fields = {
            "scale": scale,
            "path": path,
            "width": size,
            "height": size,
            "mode": "RGBA",
        }
        if equipment_id is None:
            expected_fields["kind"] = "combinationMatrix"
        else:
            expected_fields["equipmentId"] = equipment_id
        for field, value in expected_fields.items():
            if preview.get(field) != value:
                errors.append(f"previews.artifacts.{key}.{field} must be {value!r}")
        try:
            _safe_relative_path(preview.get("path"), f"previews.artifacts.{key}.path")
        except (TypeError, ValueError) as error:
            errors.append(str(error))
        errors.extend(
            _artifact_contract_errors(
                preview.get("artifact"),
                f"previews.artifacts.{key}.artifact",
                None,
            )
        )
    return errors


def _contract_errors(spec: dict) -> list[str]:
    errors: list[str] = []
    if spec.get("schemaVersion") != 1:
        errors.append("schemaVersion must be 1")
    if spec.get("contractKind") != "character-equipment-layer-variants":
        errors.append("contractKind must be character-equipment-layer-variants")

    items_value = spec.get("items")
    if not isinstance(items_value, list):
        errors.append("items must be an array")
        return errors
    profile_items = [
        _mapping(item, f"items[{index}]")
        for index, item in enumerate(items_value)
    ]
    equipment_ids = {item.get("equipmentId") for item in profile_items}
    is_weapon_contract = equipment_ids == set(EXPECTED_WEAPON_ITEMS)

    base = _mapping(spec.get("baseCharacterContract"), "baseCharacterContract")
    if base.get("specPath") != "../character/character-modular-sheet-spec.json":
        errors.append("baseCharacterContract.specPath must reference character schema v5")
    if base.get("schemaVersion") != 5 or base.get("canonicalSourceCount") != 15:
        errors.append("baseCharacterContract must preserve schema v5 and 15 sources")
    if is_weapon_contract:
        expected_base = {
            "specPath": "../character/character-modular-sheet-spec.json",
            "schemaVersion": 5,
            "canonicalSourceCount": 15,
            "relationship": (
                "independent gameplay weapon variants for the schema-v5 topmost weapon group"
            ),
        }
        if dict(base) != expected_base:
            errors.append(
                "baseCharacterContract must reference the schema-v5 topmost weapon group"
            )

    paths = _mapping(spec.get("pathContract"), "pathContract")
    expected_paths = {
        "sourcePathsRelativeTo": "spec-directory",
        "previewPathsRelativeTo": "spec-directory",
        "runtimePathRootRelativeToSpec": "../../../app/src/main/assets",
        "safeRelativePathsRequired": True,
    }
    for name, expected in expected_paths.items():
        if paths.get(name) != expected:
            errors.append(f"pathContract.{name} must be {expected!r}")

    canvas = _mapping(spec.get("canvas"), "canvas")
    expected_canvas = {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "boundsInclusive": [0, 0, 63, 63],
        "centerX": 32,
        "characterSoleY": 58,
        "anchorProfile": "canvas-64-center-x-32-sole-y-58-schema-v5",
    }
    if dict(canvas) != expected_canvas:
        errors.append("canvas must match the exact 64x64 character anchor profile")

    palette = _mapping(spec.get("productionPalette"), "productionPalette")
    if palette.get("colorCount") != 16 or palette.get("colors") != EXPECTED_PALETTE:
        errors.append("productionPalette must contain the exact schema v5 16 colors")
    if palette.get("opaquePixelsOnly") is not True:
        errors.append("productionPalette.opaquePixelsOnly must be true")

    if is_weapon_contract:
        errors.extend(_weapon_contract_errors(spec, profile_items))
        return errors
    if equipment_ids == set(EXPECTED_GLOVES_SHOES_ITEMS):
        errors.extend(_gloves_shoes_contract_errors(spec, profile_items))
        return errors
    if equipment_ids == set(EXPECTED_OUTFIT_ITEMS):
        errors.extend(_outfit_contract_errors(spec, profile_items))
        return errors
    if equipment_ids != set(EXPECTED_ITEMS):
        errors.append(
            "items must select a supported helmet (1003-1004), outfit (1005-1010), "
            "gloves/shoes (1011, 1012, 1015, 1016), or weapon "
            "(1001, 1002, 1017, 1018) contract"
        )
        return errors

    layer = _mapping(spec.get("layerContract"), "layerContract")
    expected_layer_values = {
        "slot": "headgear_front",
        "allowedAlphaValues": [0, 255],
        "transparentPixelRgba": [0, 0, 0, 0],
        "chromaKey": "#FF00FF",
        "chromaKeyAllowed": False,
        "alphaCompositing": "source-over",
        "interpolation": "nearest-neighbor",
        "faceProtectedRegion": [23, 20, 41, 28],
        "faceProtectedRegionInclusive": True,
    }
    for name, expected in expected_layer_values.items():
        if layer.get(name) != expected:
            errors.append(f"layerContract.{name} must be {expected!r}")
    outline = _mapping(layer.get("outline"), "layerContract.outline")
    expected_outline = {
        "paletteName": "outlineDarkNavy",
        "color": "#263B5A",
        "widthLogicalPixels": 1,
        "externalBoundaryNeighborMode": 4,
        "allExternalBoundaryPixelsUseOutlineColor": True,
        "forbidExternalOutline2x2Blocks": True,
    }
    if dict(outline) != expected_outline:
        errors.append("layerContract.outline must define the 1 logical pixel #263B5A boundary")
    connectivity = _mapping(layer.get("connectivity"), "layerContract.connectivity")
    if dict(connectivity) != {
        "opaqueNeighborMode": 8,
        "opaqueComponentCount": 1,
        "allowIsolatedOpaquePixels": False,
    }:
        errors.append("layerContract.connectivity must require one 8-connected component")
    coordinates = _mapping(
        layer.get("coordinatePreservation"),
        "layerContract.coordinatePreservation",
    )
    if dict(coordinates) != {
        "singleCanvasOrigin": [0, 0],
        "translationAllowed": False,
        "croppingAllowed": False,
        "scalingAllowed": False,
        "thumbnailOpaqueBoundsReadOnlyScalingAllowed": True,
    }:
        errors.append("layerContract.coordinatePreservation must keep the 64x64 origin")

    items_value = spec.get("items")
    if not isinstance(items_value, list) or len(items_value) != 2:
        errors.append("items must contain exactly the 1003 and 1004 variants")
        items: list[Mapping] = []
    else:
        items = [
            _mapping(item, f"items[{index}]")
            for index, item in enumerate(items_value)
        ]
    seen_ids: set[int] = set()
    seen_keys: set[str] = set()
    for index, item in enumerate(items):
        owner = f"items[{index}]"
        equipment_id = item.get("equipmentId")
        if equipment_id not in EXPECTED_ITEMS:
            errors.append(f"{owner}.equipmentId must be 1003 or 1004")
            continue
        if equipment_id in seen_ids:
            errors.append(f"{owner}.equipmentId must be unique")
        seen_ids.add(equipment_id)
        expected = EXPECTED_ITEMS[equipment_id]
        key = expected["key"]
        if item.get("displayNameKorean") != expected["displayNameKorean"]:
            errors.append(f"{owner}.displayNameKorean is incorrect")
        if item.get("imageKey") != key or item.get("layerKey") != key:
            errors.append(f"{owner} imageKey and layerKey must both be {key}")
        if key in seen_keys:
            errors.append(f"{owner}.layerKey must be unique")
        seen_keys.add(key)
        if item.get("slot") != "headgear_front":
            errors.append(f"{owner}.slot must be headgear_front")
        for field in ("sourcePath", "runtimePath"):
            try:
                actual = _safe_relative_path(item.get(field), f"{owner}.{field}")
                if actual != expected[field]:
                    errors.append(f"{owner}.{field} must be {expected[field]}")
            except (TypeError, ValueError) as error:
                errors.append(str(error))
        if item.get("previewKeys") != expected["previewKeys"]:
            errors.append(f"{owner}.previewKeys must match the declared previews")
        design = _mapping(item.get("design"), f"{owner}.design")
        forbidden = design.get("forbiddenFeatures")
        if equipment_id == 1003:
            if not (
                design.get("material") == "brown-leather"
                and design.get("silhouette") == "low-rounded-crown"
                and design.get("shortBrowBand") is True
                and design.get("openFace") is True
                and forbidden == ["horns", "feathers"]
            ):
                errors.append(f"{owner}.design must define the open leather cap")
        else:
            if not (
                design.get("material") == "steel"
                and design.get("silhouette") == "open-symmetric-dome"
                and design.get("browGuard") is True
                and design.get("cheekGuards") == "outer-only"
                and design.get("openEyesAndExpression") is True
                and design.get("mirrorSymmetryRequired") is True
                and design.get("visor") is False
                and forbidden == ["horns", "plume"]
            ):
                errors.append(f"{owner}.design must define the open symmetric iron helmet")
        errors.extend(
            _artifact_contract_errors(
                item.get("sourceArtifact"),
                f"{owner}.sourceArtifact",
                expected["bounds"],
            )
        )
        errors.extend(
            _artifact_contract_errors(
                item.get("runtimeArtifact"),
                f"{owner}.runtimeArtifact",
                expected["bounds"],
            )
        )

    previews = _mapping(spec.get("previews"), "previews")
    artifacts = _mapping(previews.get("artifacts"), "previews.artifacts")
    if set(artifacts) != set(EXPECTED_PREVIEWS):
        errors.append("previews.artifacts must declare exactly four helmet previews")
    for key, expected in EXPECTED_PREVIEWS.items():
        if key not in artifacts:
            continue
        preview = _mapping(artifacts[key], f"previews.artifacts.{key}")
        equipment_id, scale, path, size = expected
        expected_fields = {
            "equipmentId": equipment_id,
            "scale": scale,
            "path": path,
            "width": size,
            "height": size,
            "mode": "RGBA",
        }
        for field, value in expected_fields.items():
            if preview.get(field) != value:
                errors.append(f"previews.artifacts.{key}.{field} must be {value!r}")
        try:
            _safe_relative_path(preview.get("path"), f"previews.artifacts.{key}.path")
        except (TypeError, ValueError) as error:
            errors.append(str(error))
        errors.extend(
            _artifact_contract_errors(
                preview.get("artifact"),
                f"previews.artifacts.{key}.artifact",
                None,
            )
        )
    return errors


def validate_contract(spec_path: pathlib.Path) -> list[str]:
    """Validate only JSON structure and values; PNG files may not exist yet."""
    spec_path = pathlib.Path(spec_path)
    try:
        spec = _load_spec(spec_path)
        return [_error(spec_path, message) for message in _contract_errors(spec)]
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
        return [_error(spec_path, f"invalid equipment layer specification: {error}")]


def _hex_rgba(value: str) -> Rgba:
    raw = value.removeprefix("#")
    return tuple(int(raw[index:index + 2], 16) for index in (0, 2, 4)) + (255,)


def _first_point(index: int, width: int) -> Point:
    return index % width, index // width


def _opaque_points(image: Image.Image) -> set[Point]:
    return {
        _first_point(index, image.width)
        for index, pixel in enumerate(image.get_flattened_data())
        if pixel[3] != 0
    }


def _opaque_bounds(points: set[Point]) -> Bounds | None:
    if not points:
        return None
    return (
        min(x for x, _ in points),
        min(y for _, y in points),
        max(x for x, _ in points),
        max(y for _, y in points),
    )


def _sorted(points: set[Point] | list[Point]) -> list[Point]:
    return sorted(points, key=lambda point: (point[1], point[0]))


def _neighbor_offsets(mode: int) -> tuple[Point, ...]:
    if mode == 4:
        return (-1, 0), (1, 0), (0, -1), (0, 1)
    if mode == 8:
        return tuple(
            (dx, dy)
            for dy in (-1, 0, 1)
            for dx in (-1, 0, 1)
            if dx or dy
        )
    raise ValueError("neighbor mode must be 4 or 8")


def _components(points: set[Point], mode: int) -> list[set[Point]]:
    remaining = set(points)
    offsets = _neighbor_offsets(mode)
    result: list[set[Point]] = []
    while remaining:
        start = min(remaining, key=lambda point: (point[1], point[0]))
        remaining.remove(start)
        component = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            discovered = {
                (x + dx, y + dy)
                for dx, dy in offsets
                if (x + dx, y + dy) in remaining
            }
            remaining.difference_update(discovered)
            component.update(discovered)
            stack.extend(discovered)
        result.append(component)
    return result


def _external_transparent(image: Image.Image, opaque: set[Point]) -> set[Point]:
    transparent = {
        (x, y)
        for y in range(image.height)
        for x in range(image.width)
        if (x, y) not in opaque
    }
    stack = [
        point
        for point in transparent
        if point[0] in (0, image.width - 1) or point[1] in (0, image.height - 1)
    ]
    exterior = set(stack)
    remaining = transparent - exterior
    while stack:
        x, y = stack.pop()
        discovered = {
            (x + dx, y + dy)
            for dx, dy in _neighbor_offsets(4)
            if (x + dx, y + dy) in remaining
        }
        remaining.difference_update(discovered)
        exterior.update(discovered)
        stack.extend(discovered)
    return exterior


def _external_boundary(image: Image.Image, opaque: set[Point]) -> set[Point]:
    exterior = _external_transparent(image, opaque)
    return {
        (x, y)
        for x, y in opaque
        if any(
            not (0 <= x + dx < image.width and 0 <= y + dy < image.height)
            or (x + dx, y + dy) in exterior
            for dx, dy in _neighbor_offsets(4)
        )
    }


def _image_hashes(path: pathlib.Path, image: Image.Image) -> dict[str, str]:
    return {
        "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "rawRgbaSha256": hashlib.sha256(image.tobytes("raw", "RGBA")).hexdigest(),
        "alphaMaskSha256": hashlib.sha256(image.getchannel("A").tobytes()).hexdigest(),
    }


def _metadata_errors(
    path: pathlib.Path,
    image: Image.Image,
    artifact: Mapping,
) -> list[str]:
    errors: list[str] = []
    opaque = _opaque_points(image)
    actual = {
        "opaqueBounds": list(_opaque_bounds(opaque)) if opaque else None,
        "opaquePixelCount": len(opaque),
        "fileByteCount": path.stat().st_size,
    }
    for field, value in actual.items():
        if artifact.get(field) != value:
            errors.append(
                f"{path}: stored {field} differs: expected {artifact.get(field)!r}, got {value!r}"
            )
    hashes = _mapping(artifact.get("hashes"), "artifact.hashes")
    for field, value in _image_hashes(path, image).items():
        if hashes.get(field) != value:
            errors.append(
                f"{path}: stored {field} differs: expected {hashes.get(field)!r}, got {value}"
            )
    return errors


def _layer_contract(spec: dict, item: Mapping | None = None) -> Mapping:
    if "layerContracts" in spec:
        layers = _mapping(spec["layerContracts"], "layerContracts")
        if item is not None:
            slot = item.get("slot") or item.get("renderSlot")
        else:
            slot = "top" if "top" in layers else next(iter(layers), None)
        return _mapping(layers.get(slot), f"layerContracts.{slot}")
    return _mapping(spec.get("layerContract"), "layerContract")


def _region_points(bounds: Sequence[int]) -> set[Point]:
    left, top, right, bottom = bounds
    return {
        (x, y)
        for y in range(top, bottom + 1)
        for x in range(left, right + 1)
    }


def _region_label(name: str) -> str:
    return {
        "waistOverlap": "waist overlap",
        "leftAnkleOverlap": "left ankle overlap",
        "rightAnkleOverlap": "right ankle overlap",
    }.get(name, name)


def _basic_image_errors(
    path: pathlib.Path,
    image: Image.Image,
    spec: dict,
    expected_size: tuple[int, int],
    item: Mapping | None = None,
) -> list[str]:
    errors: list[str] = []
    if image.size != expected_size:
        errors.append(f"{path}: image size must be {expected_size}; got {image.size}")
    if image.mode != "RGBA":
        errors.append(f"{path}: image mode must be RGBA; got {image.mode}")
    if errors:
        return errors

    palette = {_hex_rgba(value)[:3] for value in spec["productionPalette"]["colors"].values()}
    layer = _layer_contract(spec, item)
    allowed_alpha = set(layer["allowedAlphaValues"])
    transparent = tuple(layer["transparentPixelRgba"])
    chroma = _hex_rgba(layer["chromaKey"])[:3]
    invalid_alpha: list[tuple[Point, int]] = []
    invalid_palette: list[tuple[Point, tuple[int, int, int]]] = []
    invalid_transparent: list[tuple[Point, Rgba]] = []
    chroma_points: list[Point] = []
    for index, pixel in enumerate(image.get_flattened_data()):
        point = _first_point(index, image.width)
        if pixel[3] not in allowed_alpha:
            invalid_alpha.append((point, pixel[3]))
        if pixel[3] == 0 and pixel != transparent:
            invalid_transparent.append((point, pixel))
        if pixel[3] != 0 and pixel[:3] not in palette:
            invalid_palette.append((point, pixel[:3]))
        if pixel[:3] == chroma:
            chroma_points.append(point)
    if invalid_alpha:
        point, alpha = invalid_alpha[0]
        errors.append(f"{path}: alpha must be 0 or 255; first at {point} with alpha={alpha}")
    if invalid_palette:
        point, rgb = invalid_palette[0]
        errors.append(f"{path}: opaque pixel is outside the production palette; first at {point} with RGB={rgb}")
    if invalid_transparent:
        point, rgba = invalid_transparent[0]
        errors.append(f"{path}: transparent pixel RGBA must be (0, 0, 0, 0); first at {point} with RGBA={rgba}")
    if chroma_points:
        errors.append(f"{path}: chroma key is forbidden; first at {_sorted(chroma_points)[0]}")
    return errors


def _layer_image_errors(
    path: pathlib.Path,
    image: Image.Image,
    spec: dict,
    item: Mapping,
) -> list[str]:
    errors = _basic_image_errors(path, image, spec, (64, 64), item)
    if image.size != (64, 64) or image.mode != "RGBA":
        return errors

    opaque = _opaque_points(image)
    expected_bounds = tuple(item["sourceArtifact"]["opaqueBounds"])
    actual_bounds = _opaque_bounds(opaque)
    if actual_bounds != expected_bounds:
        outside = _sorted(
            {
                (x, y)
                for x, y in opaque
                if not (
                    expected_bounds[0] <= x <= expected_bounds[2]
                    and expected_bounds[1] <= y <= expected_bounds[3]
                )
            }
        )
        first = outside[0] if outside else (actual_bounds[0], actual_bounds[1]) if actual_bounds else (0, 0)
        errors.append(
            f"{path}: opaque bounds must be {expected_bounds}; got {actual_bounds}, first at {first}"
        )

    layer = _layer_contract(spec, item)
    envelope = layer.get("opaqueEnvelope")
    if envelope is not None:
        outside_envelope = _sorted(opaque - _region_points(envelope))
        if outside_envelope:
            errors.append(
                f"{path}: opaque pixels must stay inside opaque envelope "
                f"{tuple(envelope)}; first at {outside_envelope[0]}"
            )

    face = layer.get("faceProtectedRegion")
    if face is not None:
        face_points = _sorted(opaque & _region_points(face))
        if face_points:
            errors.append(
                f"{path}: face protected region {tuple(face)} must be transparent; first at {face_points[0]}"
            )

    hand_overlap = layer.get("handOverlapContract")
    if isinstance(hand_overlap, Mapping):
        hand_points = opaque & _region_points(hand_overlap["region"])
        invalid_hand_points = _sorted(
            hand_points - _region_points(hand_overlap["allowedOpaqueEnvelope"])
        )
        if invalid_hand_points:
            errors.append(
                f"{path}: hand region may overlap only the handle envelope; "
                f"first at {invalid_hand_points[0]}"
            )

    for name, region in layer.get("requiredOpaqueRegions", {}).items():
        missing = _sorted(_region_points(region) - opaque)
        if missing:
            errors.append(
                f"{path}: {_region_label(name)} must be fully opaque; first at {missing[0]}"
            )

    connectivity = _mapping(layer.get("connectivity"), "layer.connectivity")
    neighbor_mode = connectivity["opaqueNeighborMode"]
    components = _components(opaque, neighbor_mode)
    expected_component_count = connectivity["opaqueComponentCount"]
    if len(components) != expected_component_count:
        first = _sorted(opaque)[0] if opaque else (0, 0)
        expected_label = "one" if expected_component_count == 1 else str(expected_component_count)
        errors.append(
            f"{path}: opaque pixels must form exactly {expected_label} "
            f"{neighbor_mode}-connected component(s); got {len(components)}, first at {first}"
        )
    expected_components = connectivity.get("components", [])
    ordered_components = sorted(
        components,
        key=lambda component: (
            _opaque_bounds(component)[1],
            _opaque_bounds(component)[0],
        ),
    )
    for index, expected in enumerate(expected_components):
        if index >= len(ordered_components):
            errors.append(f"{path}: {expected['name']} component is missing")
            continue
        component = ordered_components[index]
        actual_bounds = list(_opaque_bounds(component))
        if actual_bounds != expected["opaqueBounds"] or len(component) != expected["opaquePixelCount"]:
            errors.append(
                f"{path}: {expected['name']} component must have bounds "
                f"{expected['opaqueBounds']} and {expected['opaquePixelCount']} opaque pixels; "
                f"got {actual_bounds} and {len(component)}"
            )
    isolated = [
        point
        for point in _sorted(opaque)
        if not any(
            (point[0] + dx, point[1] + dy) in opaque
            for dx, dy in _neighbor_offsets(8)
        )
    ]
    if isolated:
        errors.append(f"{path}: isolated opaque pixel is forbidden; first at {isolated[0]}")

    reference_mask = layer.get("referenceAlphaMask")
    if isinstance(reference_mask, Mapping):
        alpha_hash = hashlib.sha256(image.getchannel("A").tobytes()).hexdigest()
        if alpha_hash != reference_mask["alphaMaskSha256"]:
            errors.append(
                f"{path}: alpha mask must exactly match {reference_mask['baseCharacterSourceId']}"
            )
    for name, point_value in layer.get("requiredOpaquePoints", {}).items():
        point = tuple(point_value)
        if point not in opaque:
            if name == "primaryGripAnchor" and item.get("equipmentSlot") == "GLOVES":
                label = "glove grip mask"
            elif name == "primaryGripAnchor":
                label = "primary grip anchor"
            else:
                label = name
            errors.append(f"{path}: {label} must remain opaque at {point}")

    sole = layer.get("soleContract")
    if isinstance(sole, Mapping):
        y = sole["soleY"]
        sole_points = [
            (x, y)
            for name in ("leftOpaqueRange", "rightOpaqueRange")
            for x in range(sole[name][0], sole[name][1] + 1)
        ]
        missing = [point for point in sole_points if point not in opaque]
        if missing:
            errors.append(f"{path}: sole row must remain opaque; first at {missing[0]}")
        center = (sole["centerX"], y)
        if sole["centerPixelTransparent"] and center in opaque:
            errors.append(f"{path}: sole row center must remain transparent at {center}")

    outline_contract = layer.get("outline")
    if isinstance(outline_contract, Mapping):
        outline = _hex_rgba(outline_contract["color"])
        boundary = _external_boundary(image, opaque)
        hidden_regions = {
            point
            for region in layer.get("hiddenOverlapOutlineForbiddenRegions", {}).values()
            for point in _region_points(region)
        }
        invalid_boundary = [
            point
            for point in _sorted(boundary - hidden_regions)
            if image.getpixel(point) != outline
        ]
        if invalid_boundary:
            errors.append(
                f"{path}: external boundary must use the 1 logical pixel #263B5A outline; first at {invalid_boundary[0]}"
            )
        outline_points = {point for point in opaque if image.getpixel(point) == outline}
        first_block: Point | None = None
        for y in range(image.height - 1):
            for x in range(image.width - 1):
                block = {(x, y), (x + 1, y), (x, y + 1), (x + 1, y + 1)}
                if block <= outline_points and block & boundary:
                    first_block = x, y
                    break
            if first_block is not None:
                break
        if first_block is not None:
            errors.append(f"{path}: external outline contains a forbidden 2x2 block at {first_block}")

        for name, region in layer.get("hiddenOverlapOutlineForbiddenRegions", {}).items():
            forbidden = [
                point
                for point in _sorted(_region_points(region))
                if image.getpixel(point) == outline
            ]
            if forbidden:
                label = "hidden waist seam" if name == "waistOverlap" else f"hidden {name} seam"
                errors.append(
                    f"{path}: {label} must not contain outlineDarkNavy; first at {forbidden[0]}"
                )

        for contract in layer.get("twoPixelHorizontalOutlineForbidden", []):
            rows = contract["rows"]
            points = [
                (x, y)
                for y in rows
                for start, end in contract["xRanges"]
                for x in range(start, end + 1)
            ]
            if all(image.getpixel(point) == outline for point in points):
                errors.append(
                    f"{path}: {contract['name']} has a forbidden 2-pixel horizontal outline; first at {points[0]}"
                )

    if item.get("design", {}).get("mirrorSymmetryRequired") is True:
        mismatch: tuple[Point, Point] | None = None
        for y in range(64):
            for x in range(32):
                mirror = 64 - x
                if mirror >= 64:
                    continue
                if image.getpixel((x, y)) != image.getpixel((mirror, y)):
                    mismatch = (x, y), (mirror, y)
                    break
            if mismatch:
                break
        if mismatch:
            errors.append(
                f"{path}: iron helmet must be RGBA-symmetric around x=32; first at {mismatch[0]} versus {mismatch[1]}"
            )
    return errors


def _open_image(path: pathlib.Path) -> tuple[Image.Image | None, list[str]]:
    if not path.is_file():
        return None, [f"{path}: PNG is missing"]
    try:
        with Image.open(path) as stored:
            stored.load()
            return stored.copy(), []
    except OSError as error:
        return None, [f"{path}: could not read PNG: {error}"]


def _resolved_paths(
    spec_path: pathlib.Path,
    spec: dict,
    item: Mapping,
) -> tuple[pathlib.Path, pathlib.Path]:
    source = spec_path.parent / item["sourcePath"]
    runtime_root = (
        spec_path.parent
        / spec["pathContract"]["runtimePathRootRelativeToSpec"]
    ).resolve()
    runtime = runtime_root / item["runtimePath"]
    return source, runtime


def _required_available(path: pathlib.Path, artifact: Mapping, owner: str) -> list[str]:
    if artifact.get("status") == "available":
        return []
    return [f"{path}: {owner}.status must be available for this validation boundary"]


def _reference_alpha_mask_errors(
    spec_path: pathlib.Path,
    spec: dict,
) -> list[str]:
    equipment_ids = {item.get("equipmentId") for item in spec.get("items", [])}
    if equipment_ids != set(EXPECTED_GLOVES_SHOES_ITEMS):
        return []

    errors: list[str] = []
    for render_slot, layer in spec["layerContracts"].items():
        reference = layer["referenceAlphaMask"]
        path = (spec_path.parent / reference["path"]).resolve()
        image, load_errors = _open_image(path)
        errors.extend(load_errors)
        if image is None:
            continue
        errors.extend(
            _basic_image_errors(
                path,
                image,
                spec,
                (64, 64),
                {"renderSlot": render_slot},
            )
        )
        if image.size != (64, 64) or image.mode != "RGBA":
            continue
        opaque = _opaque_points(image)
        actual_bounds = list(_opaque_bounds(opaque)) if opaque else None
        if actual_bounds != reference["opaqueBounds"]:
            errors.append(
                f"{path}: reference alpha mask bounds must be {reference['opaqueBounds']}; "
                f"got {actual_bounds}"
            )
        if len(opaque) != reference["opaquePixelCount"]:
            errors.append(
                f"{path}: reference alpha mask must contain {reference['opaquePixelCount']} "
                f"opaque pixels; got {len(opaque)}"
            )
        alpha_hash = hashlib.sha256(image.getchannel("A").tobytes()).hexdigest()
        if alpha_hash != reference["alphaMaskSha256"]:
            errors.append(
                f"{path}: reference alpha mask SHA-256 must be "
                f"{reference['alphaMaskSha256']}; got {alpha_hash}"
            )
        components = sorted(
            _components(opaque, layer["connectivity"]["opaqueNeighborMode"]),
            key=lambda component: (
                _opaque_bounds(component)[1],
                _opaque_bounds(component)[0],
            ),
        )
        for index, expected in enumerate(layer["connectivity"]["components"]):
            if index >= len(components):
                errors.append(f"{path}: reference {expected['name']} component is missing")
                continue
            component = components[index]
            if (
                list(_opaque_bounds(component)) != expected["opaqueBounds"]
                or len(component) != expected["opaquePixelCount"]
            ):
                errors.append(
                    f"{path}: reference {expected['name']} component does not match its fixed mask"
                )
    return errors


def _validate_sources_loaded(spec_path: pathlib.Path, spec: dict) -> list[str]:
    errors = _reference_alpha_mask_errors(spec_path, spec)
    source_images: dict[int, Image.Image] = {}
    for item in spec["items"]:
        source_path, _ = _resolved_paths(spec_path, spec, item)
        artifact = item["sourceArtifact"]
        errors.extend(_required_available(source_path, artifact, "sourceArtifact"))
        image, load_errors = _open_image(source_path)
        errors.extend(load_errors)
        if image is None:
            continue
        source_images[item["equipmentId"]] = image
        errors.extend(_layer_image_errors(source_path, image, spec, item))
        if (
            artifact.get("status") == "available"
            and image.mode == "RGBA"
            and image.size == (64, 64)
        ):
            errors.extend(_metadata_errors(source_path, image, artifact))

    preview_images: dict[str, Image.Image] = {}
    for key, preview in spec["previews"]["artifacts"].items():
        path = spec_path.parent / preview["path"]
        artifact = preview["artifact"]
        errors.extend(_required_available(path, artifact, "preview artifact"))
        image, load_errors = _open_image(path)
        errors.extend(load_errors)
        if image is None:
            continue
        preview_images[key] = image
        errors.extend(
            _basic_image_errors(
                path,
                image,
                spec,
                (preview["width"], preview["height"]),
            )
        )
        if artifact.get("status") == "available" and image.mode == "RGBA":
            errors.extend(_metadata_errors(path, image, artifact))

    for item in spec["items"]:
        one_key = item["previewKeys"]["equipped1x"]
        eight_key = item["previewKeys"]["equipped8x"]
        one = preview_images.get(one_key)
        eight = preview_images.get(eight_key)
        if one is None or eight is None or one.mode != "RGBA" or eight.mode != "RGBA":
            continue
        expected = one.resize(eight.size, Image.Resampling.NEAREST)
        mismatch: Point | None = None
        for index, (actual_pixel, expected_pixel) in enumerate(
            zip(eight.get_flattened_data(), expected.get_flattened_data())
        ):
            if actual_pixel != expected_pixel:
                mismatch = _first_point(index, eight.width)
                break
        if mismatch is not None:
            eight_path = spec_path.parent / spec["previews"]["artifacts"][eight_key]["path"]
            errors.append(
                f"{eight_path}: 8x preview must be the nearest-neighbor enlargement of {one_key}; first at {mismatch}"
            )

    matrix_contract = spec.get("previews", {}).get("combinationMatrix")
    if isinstance(matrix_contract, Mapping):
        one_key = matrix_contract["previewKeys"]["matrix1x"]
        four_key = matrix_contract["previewKeys"]["matrix4x"]
        one = preview_images.get(one_key)
        four = preview_images.get(four_key)
        if (
            one is not None
            and four is not None
            and one.mode == "RGBA"
            and four.mode == "RGBA"
        ):
            expected = one.resize(four.size, Image.Resampling.NEAREST)
            mismatch = next(
                (
                    _first_point(index, four.width)
                    for index, (actual_pixel, expected_pixel) in enumerate(
                        zip(four.get_flattened_data(), expected.get_flattened_data())
                    )
                    if actual_pixel != expected_pixel
                ),
                None,
            )
            if mismatch is not None:
                four_path = (
                    spec_path.parent
                    / spec["previews"]["artifacts"][four_key]["path"]
                )
                errors.append(
                    f"{four_path}: 4x matrix preview must be the nearest-neighbor enlargement of {one_key}; first at {mismatch}"
                )
    return errors


def validate_sources(spec_path: pathlib.Path) -> list[str]:
    """Validate canonical layer PNGs, previews, and their stored metadata."""
    spec_path = pathlib.Path(spec_path)
    contract_errors = validate_contract(spec_path)
    if contract_errors:
        return contract_errors
    spec = _load_spec(spec_path)
    return _validate_sources_loaded(spec_path, spec)


def validate_all(spec_path: pathlib.Path) -> list[str]:
    """Validate sources and canonical/runtime byte equality."""
    spec_path = pathlib.Path(spec_path)
    contract_errors = validate_contract(spec_path)
    if contract_errors:
        return contract_errors
    spec = _load_spec(spec_path)
    errors = _validate_sources_loaded(spec_path, spec)
    for item in spec["items"]:
        source_path, runtime_path = _resolved_paths(spec_path, spec, item)
        artifact = item["runtimeArtifact"]
        errors.extend(_required_available(runtime_path, artifact, "runtimeArtifact"))
        image, load_errors = _open_image(runtime_path)
        errors.extend(load_errors)
        if image is None:
            continue
        errors.extend(_layer_image_errors(runtime_path, image, spec, item))
        if artifact.get("status") == "available":
            errors.extend(_metadata_errors(runtime_path, image, artifact))
        if source_path.is_file() and runtime_path.read_bytes() != source_path.read_bytes():
            errors.append(
                f"{runtime_path}: runtime PNG must be byte-identical to canonical source {source_path}"
            )
    return errors


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate Todo Quest character equipment layer variants."
    )
    parser.add_argument("--spec", type=pathlib.Path)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--check-contract", action="store_true")
    modes.add_argument("--check-sources", action="store_true")
    modes.add_argument("--check", action="store_true")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    spec_paths = (args.spec,) if args.spec is not None else DEFAULT_SPEC_PATHS
    mode = (
        "check-contract"
        if args.check_contract
        else "check-sources"
        if args.check_sources
        else "check"
    )
    errors: list[str] = []
    for spec_path in spec_paths:
        if mode == "check-contract":
            spec_errors = validate_contract(spec_path)
        elif mode == "check-sources":
            spec_errors = validate_sources(spec_path)
        else:
            spec_errors = validate_all(spec_path)
        errors.extend(f"{spec_path}: {error}" for error in spec_errors)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(
        f"Character equipment layer {mode} passed: "
        + ", ".join(str(path) for path in spec_paths)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
