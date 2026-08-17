import copy
import hashlib
import itertools
import json
import sys
from collections import deque
from pathlib import Path

import pytest
from PIL import Image, ImageDraw


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
CANONICAL_SPEC_PATH = (
    ROOT / "docs" / "art" / "character" / "character-modular-sheet-spec.json"
)
CANONICAL_BODY_PATH = (
    ROOT / "docs" / "art" / "character" / "todo-quest-character-base-body.png"
)
CURRENT_MODULAR_SHEET_PATH = (
    ROOT / "docs" / "art" / "character" / "todo-quest-character-modular-sheet.png"
)
RUNTIME_MODULAR_SHEET_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_character_modular_sheet.png"
)
RUNTIME_LAYER_ROOT = ROOT / "app" / "src" / "main" / "assets"
LEGACY_BASE_SHEET_PATH = (
    ROOT / "docs" / "art" / "character" / "todo-quest-character-base-sheet.png"
)
LEGACY_BASE_SPEC_PATH = (
    ROOT / "docs" / "art" / "character" / "character-base-spec.json"
)

EXPECTED_LOADOUT_ART_CONTRACT = {
    "contractVersion": 1,
    "status": "available",
    "canvas": {
        "width": 64,
        "height": 64,
        "mode": "RGBA",
        "origin": [0, 0],
        "centerX": 32,
        "soleY": 58,
        "interpolation": "nearest-neighbor",
    },
    "alwaysPresentSourceIds": [
        "body_base",
        "hair_back_default",
        "hair_front_default",
        "hands_front",
    ],
    "emptyGameplaySlots": {
        "HELMET": {"representation": "transparent-overlay", "sourceIds": []},
        "CHEST": {"representation": "neutral-training-fallback", "sourceIds": ["top_default"]},
        "LEGS": {"representation": "neutral-training-fallback", "sourceIds": ["bottom_default"]},
        "GLOVES": {"representation": "transparent-overlay", "sourceIds": []},
        "SHOES": {"representation": "neutral-training-fallback", "sourceIds": ["shoes_default"]},
        "ACCESSORY": {"representation": "transparent-overlay", "sourceIds": []},
        "WEAPON": {"representation": "transparent-overlay", "sourceIds": []},
    },
    "neutralTrainingFallback": {
        "sourceIds": ["top_default", "bottom_default", "shoes_default"],
        "paletteNames": ["underDark", "underMid", "underLight"],
        "descriptionKorean": "회갈색 계열의 중립 훈련복",
    },
    "adventureShopSet": {
        "setKey": "adventure_set",
        "slots": {
            "HELMET": {"layerKey": "headgear_adventure", "sourceIds": ["headgear_adventure"]},
            "CHEST": {"layerKey": "top_adventure", "sourceIds": ["top_adventure"]},
            "LEGS": {"layerKey": "bottom_adventure", "sourceIds": ["bottom_adventure"]},
            "GLOVES": {"layerKey": "gloves_adventure", "sourceIds": ["gloves_adventure"]},
            "SHOES": {"layerKey": "shoes_adventure", "sourceIds": ["shoes_adventure"]},
            "ACCESSORY": {"layerKey": "accessory_adventure", "sourceIds": ["accessory_adventure"]},
            "WEAPON": {
                "layerKey": "weapon_default_sword",
                "sourceIds": [
                    "weapon_back_default_sword",
                    "weapon_held_default_sword",
                    "weapon_front_default_sword",
                ],
                "mergedRuntimePngAllowed": False,
            },
        },
    },
    "plannedCanonicalLayer": {
        "id": "gloves_adventure",
        "slot": "hands_front",
        "sourcePath": "docs/art/character/layers/gloves_adventure.png",
        "runtimePath": "app/src/main/assets/character/layers/gloves_adventure.png",
        "replacesSourceIdWhenEquipped": "hands_front",
        "referenceAlphaMaskSourceId": "hands_front",
        "sourceArtifact": {
            "status": "available",
            "opaqueBounds": [21, 39, 43, 45],
            "opaquePixelCount": 38,
            "fileByteCount": -1,
            "hashes": {
                "fileSha256": "0" * 64,
                "rawRgbaSha256": "0" * 64,
                "alphaMaskSha256": "0" * 64,
            },
        },
        "runtimeArtifact": {
            "status": "available",
            "opaqueBounds": [21, 39, 43, 45],
            "opaquePixelCount": 38,
            "fileByteCount": -1,
            "hashes": {
                "fileSha256": "0" * 64,
                "rawRgbaSha256": "0" * 64,
                "alphaMaskSha256": "0" * 64,
            },
        },
    },
    "compositionOrder": [
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
    ],
    "schemaPromotion": {
        "currentCharacterSchemaVersion": 5,
        "targetCharacterSchemaVersion": 6,
        "existingLayerIdsKeepMeaning": True,
        "existingLayerIdsMayBeRenamedOrRemoved": False,
        "storedAppearanceIdsRemainCompatible": True,
        "newLayerIds": ["gloves_adventure"],
    },
    "regenerationManifest": {
        "changedCanonicalSourceIds": [
            "top_default",
            "bottom_default",
            "shoes_default",
            "gloves_adventure",
        ],
        "runtimeMirrors": [
            {
                "sourcePath": "docs/art/character/layers/top_default.png",
                "runtimePath": "app/src/main/assets/character/layers/top_default.png",
            },
            {
                "sourcePath": "docs/art/character/layers/bottom_default.png",
                "runtimePath": "app/src/main/assets/character/layers/bottom_default.png",
            },
            {
                "sourcePath": "docs/art/character/layers/shoes_default.png",
                "runtimePath": "app/src/main/assets/character/layers/shoes_default.png",
            },
            {
                "sourcePath": "docs/art/character/layers/gloves_adventure.png",
                "runtimePath": "app/src/main/assets/character/layers/gloves_adventure.png",
            },
        ],
        "generatedPreviewTileNames": [
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
        ],
        "generatedSheetDocsPath": "docs/art/character/todo-quest-character-modular-sheet.png",
        "generatedSheetRuntimePath": "app/src/main/res/drawable-nodpi/todo_quest_character_modular_sheet.png",
        "runtimeReadsGeneratedSheet": False,
    },
}
sys.path.insert(0, str(SCRIPTS_DIR))

from build_character_assets import (  # noqa: E402
    EXPECTED_TILE_NAMES,
    WEAPON_LAYER_IDS,
    build_generated_assets,
    check_assets,
    load_source_layers,
    main,
    validate_combinations,
    validate_contract,
    validate_layer,
    write_assets,
)
from validate_character_sheet import validate_sheet  # noqa: E402


def _rgb(value: str) -> tuple[int, int, int]:
    raw = value.removeprefix("#")
    return tuple(int(raw[index:index + 2], 16) for index in (0, 2, 4))


def _rgba(spec: dict, name: str) -> tuple[int, int, int, int]:
    return (*_rgb(spec["productionPalette"]["colors"][name]), 255)


def _bounds(image: Image.Image) -> list[int] | None:
    box = image.getchannel("A").getbbox()
    if box is None:
        return None
    left, top, right, bottom = box
    return [left, top, right - 1, bottom - 1]


def _opaque_points(image: Image.Image) -> set[tuple[int, int]]:
    return {
        (x, y)
        for y in range(image.height)
        for x in range(image.width)
        if image.getpixel((x, y))[3] != 0
    }


def _bounds_from_points(points: set[tuple[int, int]]) -> list[int] | None:
    if not points:
        return None
    return [
        min(x for x, _ in points),
        min(y for _, y in points),
        max(x for x, _ in points),
        max(y for _, y in points),
    ]


def _connected_components(
    points: set[tuple[int, int]],
    *,
    diagonal: bool,
) -> list[set[tuple[int, int]]]:
    offsets = [
        (dx, dy)
        for dy in (-1, 0, 1)
        for dx in (-1, 0, 1)
        if (dx, dy) != (0, 0) and (diagonal or dx == 0 or dy == 0)
    ]
    remaining = set(points)
    components: list[set[tuple[int, int]]] = []
    while remaining:
        seed = min(remaining, key=lambda point: (point[1], point[0]))
        remaining.remove(seed)
        queue = deque([seed])
        component = {seed}
        while queue:
            x, y = queue.popleft()
            for dx, dy in offsets:
                neighbor = x + dx, y + dy
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    component.add(neighbor)
                    queue.append(neighbor)
        components.append(component)
    return components


def _hashes(path: Path, image: Image.Image) -> dict[str, str]:
    return {
        "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
        "rawRgbaSha256": hashlib.sha256(image.tobytes()).hexdigest(),
        "alphaMaskSha256": hashlib.sha256(
            image.getchannel("A").tobytes()
        ).hexdigest(),
    }


def _save_layer(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def _body_skin_points(spec: dict, body: Image.Image) -> set[tuple[int, int]]:
    colors = {_rgba(spec, "skinLight"), _rgba(spec, "skinShadow")}
    points: set[tuple[int, int]] = set()
    for region in spec["semanticAnchors"]["handProtectedRegions"].values():
        left, top, right, bottom = region
        points.update(
            (x, y)
            for y in range(top, bottom + 1)
            for x in range(left, right + 1)
            if body.getpixel((x, y)) in colors
        )
    return points


def _outfit_layer(
    spec: dict,
    body: Image.Image,
    kind: str,
    color_name: str,
) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    color = _rgba(spec, color_name)
    protected_hands = _body_skin_points(spec, body)
    if kind == "top":
        points = {
            (x, y)
            for y in range(29, 44)
            for x in range(24, 41)
        }
    elif kind == "bottom":
        points = {
            (x, y)
            for y in range(41, 55)
            for x in range(24, 41)
        }
    else:
        points = {
            (x, y)
            for y in range(53, 59)
            for x in (*range(23, 32), *range(33, 42))
        }
    points.difference_update(protected_hands)
    for point in points:
        image.putpixel(point, color)
    return image


@pytest.fixture
def v4_fixture(tmp_path: Path) -> dict[str, object]:
    character_dir = tmp_path / "character"
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    spec["generatedOverlayMasks"] = {
        "anchors-preview": {
            "baseTile": "body-default-hair",
            "pixelCoordinates": [[32, 2], [32, 32], [32, 58]],
        },
        "layer-bounds-preview": {
            "baseTile": "runtime-equipped-reference",
            "pixelCoordinates": [[20, 4], [44, 4], [20, 58], [44, 58]],
        },
    }
    spec["generatedPreviews"] = {
        "directory": "previews",
        "scales": [1, 8],
        "status": "pendingGeneration",
        "artifacts": {},
    }
    body_path = character_dir / spec["geometryCanonicalReference"]["path"]
    body_path.parent.mkdir(parents=True, exist_ok=True)
    body_path.write_bytes(CANONICAL_BODY_PATH.read_bytes())
    with Image.open(body_path) as source:
        body = source.copy()

    transparent = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    layers: dict[str, Image.Image] = {"body_base": body}
    layers["hair_back_default"] = transparent.copy()
    ImageDraw.Draw(layers["hair_back_default"]).rectangle(
        (20, 8, 44, 18), fill=_rgba(spec, "hairBlack")
    )
    layers["hair_front_default"] = transparent.copy()
    ImageDraw.Draw(layers["hair_front_default"]).rectangle(
        (24, 8, 40, 18), fill=_rgba(spec, "hairHighlight")
    )
    layers["hands_front"] = transparent.copy()
    for point in _body_skin_points(spec, body):
        layers["hands_front"].putpixel(point, body.getpixel(point))
    layers["gloves_adventure"] = transparent.copy()
    for point in _body_skin_points(spec, body):
        x, y = point
        if (x <= 23 and y in {40, 42, 43, 44}) or (
            x >= 41 and y in {40, 42, 43, 44}
        ):
            color = "bluePrimary"
        elif x in {24, 40} and y <= 40:
            color = "tealAccent"
        else:
            color = "outlineDarkNavy"
        layers["gloves_adventure"].putpixel(point, _rgba(spec, color))
    layers["top_default"] = _outfit_layer(spec, body, "top", "underLight")
    layers["top_adventure"] = _outfit_layer(spec, body, "top", "bluePrimary")
    layers["bottom_default"] = _outfit_layer(spec, body, "bottom", "underMid")
    layers["bottom_adventure"] = _outfit_layer(
        spec, body, "bottom", "eyeDarkNavy"
    )
    layers["shoes_default"] = _outfit_layer(spec, body, "shoes", "lightCream")
    layers["shoes_adventure"] = _outfit_layer(
        spec, body, "shoes", "blueShadow"
    )
    layers["headgear_adventure"] = transparent.copy()
    ImageDraw.Draw(layers["headgear_adventure"]).rectangle(
        (27, 4, 37, 6), fill=_rgba(spec, "blueHighlight")
    )
    layers["accessory_adventure"] = transparent.copy()
    layers["accessory_adventure"].putpixel((20, 30), _rgba(spec, "redAccent"))
    layers["accessory_adventure"].putpixel((44, 30), _rgba(spec, "tealAccent"))
    layers["weapon_back_default_sword"] = transparent.copy()
    layers["weapon_back_default_sword"].putpixel(
        (44, 36), _rgba(spec, "outlineDarkNavy")
    )
    layers["weapon_held_default_sword"] = transparent.copy()
    layers["weapon_held_default_sword"].putpixel(
        (42, 42), _rgba(spec, "goldAccent")
    )
    layers["weapon_front_default_sword"] = transparent.copy()
    layers["weapon_front_default_sword"].putpixel(
        (42, 42), _rgba(spec, "lightCream")
    )

    for definition in spec["canonicalLayers"]:
        layer_id = definition["id"]
        path = character_dir / definition["sourcePath"]
        if layer_id != "body_base":
            _save_layer(path, layers[layer_id])
        stored = layers[layer_id]
        definition["sourceArtifact"] = {
            "status": "available",
            "opaqueBounds": _bounds(stored),
            "opaquePixelCount": sum(
                pixel[3] != 0 for pixel in stored.get_flattened_data()
            ),
            "fileByteCount": path.stat().st_size,
            "hashes": _hashes(path, stored),
        }
        definition["runtimeArtifact"] = {"status": "pendingGeneration"}

    geometry = spec["geometryCanonicalReference"]
    geometry["fileByteCount"] = body_path.stat().st_size
    geometry["opaquePixelCount"] = sum(
        pixel[3] != 0 for pixel in body.get_flattened_data()
    )
    geometry["opaqueBounds"] = _bounds(body)
    geometry.update(_hashes(body_path, body))
    spec_path = character_dir / "character-modular-sheet-spec.json"
    spec_path.write_text(
        json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return {
        "spec": spec,
        "spec_path": spec_path,
        "character_dir": character_dir,
        "runtime_root": tmp_path / "runtime-assets",
        "runtime_sheet": tmp_path / "runtime-drawable" / "character-sheet.png",
    }


def _fixture_spec(fixture: dict[str, object]) -> dict:
    return json.loads(Path(fixture["spec_path"]).read_text(encoding="utf-8"))


def _save_fixture_spec(fixture: dict[str, object], spec: dict) -> None:
    Path(fixture["spec_path"]).write_text(
        json.dumps(spec, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def test_canonical_schema_v5_contract_and_reference_pass() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))

    assert spec["schemaVersion"] == 6
    assert spec["zOrder"] == [
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
    assert validate_contract(CANONICAL_SPEC_PATH) == []


def test_loadout_art_contract_declares_empty_fallback_adventure_set_and_regeneration() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))

    expected = copy.deepcopy(EXPECTED_LOADOUT_ART_CONTRACT)
    planned = spec["loadoutArtContract"]["plannedCanonicalLayer"]
    expected["plannedCanonicalLayer"]["sourceArtifact"] = planned["sourceArtifact"]
    expected["plannedCanonicalLayer"]["runtimeArtifact"] = planned["runtimeArtifact"]
    assert spec["loadoutArtContract"] == expected
    assert planned["sourceArtifact"]["status"] == "available"
    assert planned["runtimeArtifact"]["status"] == "available"
    assert planned["sourceArtifact"] == planned["runtimeArtifact"]
    assert (
        spec["loadoutArtContract"]["adventureShopSet"]["slots"]["WEAPON"]
        ["sourceIds"]
        == list(WEAPON_LAYER_IDS)
    )
    assert (
        spec["loadoutArtContract"]["regenerationManifest"]
        ["generatedPreviewTileNames"]
        == list(EXPECTED_TILE_NAMES)
    )


@pytest.mark.parametrize(
    ("mutator", "expected"),
    [
        (
            lambda contract: contract["emptyGameplaySlots"].pop("CHEST"),
            "empty gameplay slots",
        ),
        (
            lambda contract: contract["adventureShopSet"]["slots"].pop("GLOVES"),
            "gloves_adventure",
        ),
        (
            lambda contract: contract["adventureShopSet"]["slots"]["WEAPON"].update(
                mergedRuntimePngAllowed=True
            ),
            "three split sources",
        ),
        (
            lambda contract: contract["regenerationManifest"]
            ["generatedPreviewTileNames"].remove("adventure-equipped"),
            "preview regeneration",
        ),
        (
            lambda contract: contract["regenerationManifest"]["runtimeMirrors"].pop(),
            "runtime mirror",
        ),
    ],
)
def test_loadout_art_contract_rejects_missing_fallback_glove_mirror_or_regeneration(
    v4_fixture: dict[str, object], mutator, expected: str
) -> None:
    spec = _fixture_spec(v4_fixture)
    mutator(spec["loadoutArtContract"])
    _save_fixture_spec(v4_fixture, spec)

    errors = validate_contract(Path(v4_fixture["spec_path"]))

    assert any(expected in error for error in errors)


def test_schema_v6_preserves_existing_adventure_and_always_present_source_hashes() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    expected_non_weapon_hashes = {
        "body_base": "465ba078046e3bc48b8c6477b4913d5bea5359795313a0a2bc4fe7205b973478",
        "hair_back_default": "af797866b575f1ceecf31d324bc7bab19313bb8f295609dc8b44f6a55201a7c8",
        "hair_front_default": "8df4a8e6c31453e8091e3cbd19a4309706f1e905cb5d6babb850c186204ba675",
        "hands_front": "c6c8068b2dfa6aea53ecacd208e3a77b2dfa27346d8b75f7ca56c298d6c4b039",
        "top_adventure": "4278a1ddd6be0a7cac5ae5c69fefcc94cc2d67b4a7f4162cd8b483827edc792e",
        "bottom_adventure": "f689ae531232efb6c227db282ede4292c18b918f8bd7cfc4fdeb1e716974bf96",
        "shoes_adventure": "e41bb37f56deff8c156e369427291a8b3eb1ed3b0fb423ff45be1cc6401335d6",
        "headgear_adventure": "7dd56f9690a475ec3441c71e4a5101369120ea60de8ac694e0bab9e19c25767f",
        "accessory_adventure": "69a688be1a4a04e7b221bf4e193195071d9c5774edef99fc804599a908474b0e",
    }

    actual_non_weapon_hashes = {
        layer["id"]: layer["sourceArtifact"]["hashes"]["fileSha256"]
        for layer in spec["canonicalLayers"]
        if layer["id"] in expected_non_weapon_hashes
    }
    assert actual_non_weapon_hashes == expected_non_weapon_hashes
    assert (
        spec["generatedPreviews"]["artifacts"]["weapon-off@1x"]["hashes"]
        ["rawRgbaSha256"]
        == "4d7d7f4187cb32baf7d3b945642f3c80777fe5e8fd32a9eb3d81f7aa9356695a"
    )


@pytest.mark.parametrize(
    ("mutator", "expected"),
    [
        (
            lambda spec: spec["geometryCanonicalReference"].update(
                path="layers/body_base.png"
            ),
            "external base-body",
        ),
        (
            lambda spec: spec["legacyArtDirectionReference"].update(
                sheetPath=spec["geometryCanonicalReference"]["path"]
            ),
            "legacy",
        ),
        (
            lambda spec: next(
                layer
                for layer in spec["canonicalLayers"]
                if layer["id"] == "weapon_back_default_sword"
            ).update(
                sourcePath=next(
                    layer
                    for layer in spec["canonicalLayers"]
                    if layer["id"] == "weapon_held_default_sword"
                )["sourcePath"]
            ),
            "weapon",
        ),
        (
            lambda spec: spec["hashSerialization"].update(
                sheetRgba="32768 RGBA pixels"
            ),
            "65536",
        ),
        (
            lambda spec: spec["generatedSheet"]["tileMap"][0].update(
                kind="croppedRuntimeSource"
            ),
            "tile kind",
        ),
    ],
)
def test_contract_rejects_reference_and_generation_errors(
    v4_fixture: dict[str, object], mutator, expected: str
) -> None:
    spec = _fixture_spec(v4_fixture)
    mutator(spec)
    _save_fixture_spec(v4_fixture, spec)

    errors = validate_contract(Path(v4_fixture["spec_path"]))

    assert any(expected in error for error in errors)


def test_check_layer_accepts_byte_identical_runtime_and_red_teal_pixels(
    v4_fixture: dict[str, object],
) -> None:
    spec = _fixture_spec(v4_fixture)
    runtime_root = Path(v4_fixture["runtime_root"])
    for layer_id in ("accessory_adventure", "body_base"):
        definition = next(
            item for item in spec["canonicalLayers"] if item["id"] == layer_id
        )
        source = Path(v4_fixture["character_dir"]) / definition["sourcePath"]
        runtime = runtime_root / definition["runtimePath"]
        runtime.parent.mkdir(parents=True, exist_ok=True)
        runtime.write_bytes(source.read_bytes())

    assert validate_layer(
        Path(v4_fixture["spec_path"]),
        "accessory_adventure",
        runtime_root=runtime_root,
    ) == []
    assert validate_layer(
        Path(v4_fixture["spec_path"]), "body_base", runtime_root=runtime_root
    ) == []


@pytest.mark.parametrize(
    ("mutation", "expected"),
    [
        ("size", "64x64"),
        ("mode", "RGBA"),
        ("alpha", "alpha"),
        ("palette", "production palette"),
        ("stale-hash", "rawRgbaSha256"),
        ("guide", "debug guide"),
        ("bounds", "opaqueBounds"),
    ],
)
def test_check_layer_rejects_pixel_and_artifact_contract_errors(
    v4_fixture: dict[str, object], mutation: str, expected: str
) -> None:
    spec = _fixture_spec(v4_fixture)
    definition = next(
        item for item in spec["canonicalLayers"] if item["id"] == "top_default"
    )
    source_path = Path(v4_fixture["character_dir"]) / definition["sourcePath"]
    with Image.open(source_path) as stored:
        image = stored.copy()
    if mutation == "size":
        image = image.crop((0, 0, 63, 64))
        image.save(source_path)
    elif mutation == "mode":
        image.convert("RGB").save(source_path)
    elif mutation == "alpha":
        image.putpixel((30, 30), (*image.getpixel((30, 30))[:3], 128))
        image.save(source_path)
    elif mutation == "palette":
        image.putpixel((30, 30), (1, 2, 3, 255))
        image.save(source_path)
    elif mutation == "guide":
        image.putpixel((30, 30), (*_rgb(spec["debugGuideColor"]["value"]), 255))
        image.save(source_path)
    elif mutation == "bounds":
        definition["sourceArtifact"]["opaqueBounds"] = [0, 0, 1, 1]
        _save_fixture_spec(v4_fixture, spec)
    else:
        definition["sourceArtifact"]["hashes"]["rawRgbaSha256"] = "0" * 64
        _save_fixture_spec(v4_fixture, spec)

    errors = validate_layer(
        Path(v4_fixture["spec_path"]),
        "top_default",
        runtime_root=Path(v4_fixture["runtime_root"]),
    )

    assert any(expected in error for error in errors)


def test_validate_combinations_builds_all_128_states_without_mutating_inputs(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    spec = _fixture_spec(v4_fixture)
    layers, errors = load_source_layers(spec_path, spec)
    assert errors == []
    before = {name: image.tobytes() for name, image in layers.items()}

    states, errors = validate_combinations(spec, layers)

    assert errors == []
    assert len(states) == 128
    assert len({state.key for state in states}) == 128
    assert all(state.image.size == (64, 64) for state in states)
    assert before == {name: image.tobytes() for name, image in layers.items()}


def test_validate_combinations_rejects_overlap_gap_fixture(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    spec = _fixture_spec(v4_fixture)
    layers, errors = load_source_layers(spec_path, spec)
    assert errors == []
    layers["top_adventure"].putpixel((30, 42), (0, 0, 0, 0))

    _, errors = validate_combinations(spec, layers)

    assert any("waist overlap" in error and "(30, 42)" in error for error in errors)


def test_generated_debug_tiles_use_spec_masks_without_color_key_search(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    spec = _fixture_spec(v4_fixture)
    layers, errors = load_source_layers(spec_path, spec)
    assert errors == []

    generated, errors = build_generated_assets(spec, layers)

    assert errors == []
    debug_rgba = (*_rgb(spec["debugGuideColor"]["value"]), 255)
    for name, contract in spec["generatedOverlayMasks"].items():
        tile = generated.tiles[name]
        actual = {
            (x, y)
            for y in range(64)
            for x in range(64)
            if tile.getpixel((x, y)) == debug_rgba
        }
        assert actual == {tuple(point) for point in contract["pixelCoordinates"]}
    equipped = generated.tiles["runtime-equipped-reference"]
    assert equipped.getpixel((20, 30)) == _rgba(spec, "redAccent")
    assert equipped.getpixel((44, 30)) == _rgba(spec, "tealAccent")
    assert equipped.getpixel((22, 43)) == layers["gloves_adventure"].getpixel((22, 43))
    for point in _body_skin_points(spec, layers["body_base"]):
        expected = layers["gloves_adventure"].getpixel(point)
        for weapon_layer_id in WEAPON_LAYER_IDS:
            weapon_pixel = layers[weapon_layer_id].getpixel(point)
            if weapon_pixel[3] != 0:
                expected = weapon_pixel
        assert equipped.getpixel(point) == expected


def test_write_then_check_persists_deterministic_assets_and_hashes(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    runtime_root = Path(v4_fixture["runtime_root"])
    runtime_sheet = Path(v4_fixture["runtime_sheet"])

    assert write_assets(spec_path, runtime_root, runtime_sheet) == []
    written = _fixture_spec(v4_fixture)
    assert written["generatedSheet"]["status"] == "available"
    assert len(written["generatedSheet"]["hashes"]["tileRgbaSha256"]) == 16
    assert written["generatedPreviews"]["status"] == "available"
    assert len(written["generatedPreviews"]["artifacts"]) == 32
    for definition in written["canonicalLayers"]:
        source = Path(v4_fixture["character_dir"]) / definition["sourcePath"]
        runtime = runtime_root / definition["runtimePath"]
        assert runtime.read_bytes() == source.read_bytes()
        assert definition["runtimeArtifact"]["status"] == "available"
    assert runtime_sheet.read_bytes() == (
        Path(v4_fixture["character_dir"]) / written["generatedSheet"]["path"]
    ).read_bytes()

    before = {
        path: hashlib.sha256(path.read_bytes()).hexdigest()
        for path in [
            spec_path,
            runtime_sheet,
            *runtime_root.rglob("*.png"),
            *Path(v4_fixture["character_dir"]).rglob("*.png"),
        ]
    }
    assert check_assets(spec_path, runtime_root, runtime_sheet) == []
    after = {path: hashlib.sha256(path.read_bytes()).hexdigest() for path in before}
    assert after == before


def test_canonical_generated_assets_are_complete_and_runtime_copies_are_identical(
) -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    tile_names = [item["name"] for item in spec["generatedSheet"]["tileMap"]]
    expected_preview_keys = {
        f"{name}@{scale}x"
        for name in tile_names
        for scale in spec["generatedPreviews"]["scales"]
    }

    assert check_assets(CANONICAL_SPEC_PATH) == []
    assert len(tile_names) == 16
    assert set(spec["generatedPreviews"]["artifacts"]) == expected_preview_keys

    with Image.open(CURRENT_MODULAR_SHEET_PATH) as stored:
        sheet = stored.copy()
    assert sheet.size == (512, 128)
    assert sheet.mode == "RGBA"
    assert len(list(sheet.get_flattened_data())) == 65_536
    assert hashlib.sha256(sheet.tobytes()).hexdigest() == (
        spec["generatedSheet"]["hashes"]["rawRgbaSha256"]
    )
    assert RUNTIME_MODULAR_SHEET_PATH.read_bytes() == (
        CURRENT_MODULAR_SHEET_PATH.read_bytes()
    )

    debug_rgba = (*_rgb(spec["debugGuideColor"]["value"]), 255)
    for definition in spec["canonicalLayers"]:
        source = CANONICAL_SPEC_PATH.parent / definition["sourcePath"]
        runtime = RUNTIME_LAYER_ROOT / definition["runtimePath"]
        assert runtime.read_bytes() == source.read_bytes()
        with Image.open(runtime) as stored:
            assert debug_rgba not in set(stored.convert("RGBA").get_flattened_data())

    for name in tile_names:
        preview_1x = CANONICAL_SPEC_PATH.parent / "previews" / f"{name}.png"
        preview_8x = CANONICAL_SPEC_PATH.parent / "previews" / f"{name}@8x.png"
        with Image.open(preview_1x) as stored:
            image_1x = stored.copy()
        with Image.open(preview_8x) as stored:
            image_8x = stored.copy()
        assert image_1x.size == (64, 64)
        assert image_1x.mode == "RGBA"
        assert image_8x.size == (512, 512)
        assert image_8x.mode == "RGBA"
        assert image_8x.tobytes() == image_1x.resize(
            (512, 512), Image.Resampling.NEAREST
        ).tobytes()

    layers, errors = load_source_layers(CANONICAL_SPEC_PATH, spec)
    assert errors == []
    states, errors = validate_combinations(spec, layers)
    assert errors == []
    assert len(states) == 128

    palette_path = CANONICAL_SPEC_PATH.parent / "previews" / "palette.png"
    with Image.open(palette_path) as stored:
        palette_colors = {
            pixel for pixel in stored.get_flattened_data() if pixel[3] != 0
        }
    assert palette_colors == {
        (*_rgb(value), 255)
        for value in spec["productionPalette"]["colors"].values()
    }


def test_check_is_read_only_when_runtime_copy_is_stale(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    runtime_root = Path(v4_fixture["runtime_root"])
    runtime_sheet = Path(v4_fixture["runtime_sheet"])
    assert write_assets(spec_path, runtime_root, runtime_sheet) == []
    runtime = next(runtime_root.rglob("top_default.png"))
    runtime.write_bytes(b"stale")
    before = runtime.read_bytes()

    errors = check_assets(spec_path, runtime_root, runtime_sheet)

    assert any("runtime" in error and "top_default" in error for error in errors)
    assert runtime.read_bytes() == before


def test_schema_v5_sheet_validator_rebuilds_from_independent_layers(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    assert write_assets(
        spec_path,
        Path(v4_fixture["runtime_root"]),
        Path(v4_fixture["runtime_sheet"]),
    ) == []
    spec = _fixture_spec(v4_fixture)
    sheet_path = Path(v4_fixture["character_dir"]) / spec["generatedSheet"]["path"]

    assert validate_sheet(sheet_path, spec_path) == []


def test_schema_v5_sheet_validator_rejects_non_layer_pixel(
    v4_fixture: dict[str, object],
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    assert write_assets(
        spec_path,
        Path(v4_fixture["runtime_root"]),
        Path(v4_fixture["runtime_sheet"]),
    ) == []
    spec = _fixture_spec(v4_fixture)
    sheet_path = Path(v4_fixture["character_dir"]) / spec["generatedSheet"]["path"]
    with Image.open(sheet_path) as stored:
        image = stored.copy()
    image.putpixel((0, 0), _rgba(spec, "redAccent"))
    image.save(sheet_path)

    errors = validate_sheet(sheet_path, spec_path)

    assert any("independent layers" in error for error in errors)


def test_write_requires_every_source_layer(v4_fixture: dict[str, object]) -> None:
    spec = _fixture_spec(v4_fixture)
    definition = next(
        item for item in spec["canonicalLayers"] if item["id"] == "top_default"
    )
    (Path(v4_fixture["character_dir"]) / definition["sourcePath"]).unlink()

    errors = write_assets(
        Path(v4_fixture["spec_path"]),
        Path(v4_fixture["runtime_root"]),
        Path(v4_fixture["runtime_sheet"]),
    )

    assert any("top_default" in error and "missing" in error for error in errors)
    assert not Path(v4_fixture["runtime_root"]).exists()


def test_cli_contract_and_layer_modes_report_results(
    v4_fixture: dict[str, object], capsys: pytest.CaptureFixture[str]
) -> None:
    spec_path = Path(v4_fixture["spec_path"])
    assert main(["--spec", str(spec_path), "--check-contract"]) == 0
    assert "contract validation passed" in capsys.readouterr().out

    assert main(
        ["--spec", str(spec_path), "--check-layer", "body_base"]
    ) == 0
    assert "layer validation passed" in capsys.readouterr().out


def test_headgear_adventure_preserves_current_front_tile_and_slot() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    definition = next(
        item for item in spec["canonicalLayers"] if item["id"] == "headgear_adventure"
    )
    layer_path = CANONICAL_SPEC_PATH.parent / definition["sourcePath"]

    assert definition["slot"] == "headgear_front"
    assert definition["sourcePath"] == "layers/headgear_adventure.png"
    assert definition["runtimePath"] == "character/layers/headgear_adventure.png"
    assert not any(
        item["slot"] == "headgear_back" for item in spec["canonicalLayers"]
    )
    assert spec["currentlyUnusedLayerSlots"]["headgear_back"]["status"] == "unused"
    assert not (CANONICAL_SPEC_PATH.parent / "layers" / "headgear_back.png").exists()
    assert validate_layer(CANONICAL_SPEC_PATH, "headgear_adventure") == []

    with Image.open(layer_path) as stored:
        actual = stored.copy()

    assert actual.size == (64, 64)
    assert actual.mode == "RGBA"
    assert _bounds(actual) == [20, 7, 44, 25]
    assert sum(pixel[3] != 0 for pixel in actual.get_flattened_data()) == 363
    assert hashlib.sha256(actual.tobytes()).hexdigest() == (
        "4cd87e2791d5d59877ccd674b63f385382d21d5502bd487dd281f85a5dbf99ae"
    )


def test_accessory_adventure_preserves_current_front_tile_and_slot() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    definition = next(
        item for item in spec["canonicalLayers"] if item["id"] == "accessory_adventure"
    )
    layer_path = CANONICAL_SPEC_PATH.parent / definition["sourcePath"]

    assert definition["slot"] == "accessory_front"
    assert definition["sourcePath"] == "layers/accessory_adventure.png"
    assert definition["runtimePath"] == "character/layers/accessory_adventure.png"
    assert not any(
        item["slot"] == "accessory_back" for item in spec["canonicalLayers"]
    )
    assert spec["currentlyUnusedLayerSlots"]["accessory_back"]["status"] == "unused"
    assert not (CANONICAL_SPEC_PATH.parent / "layers" / "accessory_back.png").exists()
    assert validate_layer(CANONICAL_SPEC_PATH, "accessory_adventure") == []

    with Image.open(layer_path) as stored:
        actual = stored.copy()

    assert actual.size == (64, 64)
    assert actual.mode == "RGBA"
    assert _bounds(actual) == [20, 24, 44, 36]
    assert sum(pixel[3] != 0 for pixel in actual.get_flattened_data()) == 119
    assert _rgba(spec, "tealAccent") in set(actual.get_flattened_data())
    assert hashlib.sha256(actual.tobytes()).hexdigest() == (
        "bf3361d94522aac7bb4d105df0c2de912a11b4016c564aa5f6aacc243277d806"
    )


def test_legacy_combined_accessory_weapon_tile_is_design_reference_only() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    legacy_spec = json.loads(LEGACY_BASE_SPEC_PATH.read_text(encoding="utf-8"))
    legacy_definition = next(
        item
        for item in legacy_spec["tileMap"]
        if item["name"] == "accessory-weapon-layer"
    )
    canonical_paths = {
        item["sourcePath"] for item in spec["canonicalLayers"]
    } | {item["runtimePath"] for item in spec["canonicalLayers"]}

    assert legacy_definition == {
        "name": "accessory-weapon-layer",
        "row": 1,
        "column": 5,
    }
    assert all("accessory-weapon" not in path for path in canonical_paths)
    assert all(
        item["sourcePath"] != spec["legacyArtDirectionReference"]["sheetPath"]
        for item in spec["canonicalLayers"]
    )

    with Image.open(LEGACY_BASE_SHEET_PATH) as stored:
        legacy_combined = stored.convert("RGBA").crop((5 * 64, 64, 6 * 64, 2 * 64))
    with Image.open(
        CANONICAL_SPEC_PATH.parent / "layers" / "accessory_adventure.png"
    ) as stored:
        accessory = stored.copy()

    assert hashlib.sha256(legacy_combined.tobytes()).hexdigest() == (
        "fd82cde724ba710589d67b4d5989a87a77e06d7783096a2b597cc688116e1b67"
    )
    assert accessory.tobytes() != legacy_combined.tobytes()


def test_default_sword_layers_remap_only_legacy_weapon_component() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    with Image.open(LEGACY_BASE_SHEET_PATH) as stored:
        legacy = stored.convert("RGBA").crop((5 * 64, 64, 6 * 64, 2 * 64))

    target_bounds = [41, 17, 44, 58]
    components_by_connectivity: list[list[set[tuple[int, int]]]] = []
    for diagonal in (False, True):
        matching = [
            component
            for component in _connected_components(
                _opaque_points(legacy), diagonal=diagonal
            )
            if _bounds_from_points(component) == target_bounds
        ]
        assert len(matching) == 1
        components_by_connectivity.append(matching)
    assert components_by_connectivity[0][0] == components_by_connectivity[1][0]
    source_component = components_by_connectivity[0][0]
    assert len(source_component) == 95

    split_contract = {
        "weapon_back_default_sword": (range(17, 43), [42, 13, 43, 38], 51),
        "weapon_held_default_sword": (range(44, 59), [41, 40, 44, 54], 40),
        "weapon_front_default_sword": (range(43, 44), [41, 39, 44, 39], 4),
    }
    remapped_source_union: set[tuple[int, int]] = set()
    loaded: dict[str, Image.Image] = {}
    for layer_id, (source_rows, expected_bounds, expected_count) in split_contract.items():
        assert validate_layer(CANONICAL_SPEC_PATH, layer_id) == []
        definition = next(
            item for item in spec["canonicalLayers"] if item["id"] == layer_id
        )
        with Image.open(CANONICAL_SPEC_PATH.parent / definition["sourcePath"]) as stored:
            layer = stored.copy()
        loaded[layer_id] = layer
        expected = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        source_points = {
            point for point in source_component if point[1] in source_rows
        }
        for x, y in source_points:
            expected.putpixel((x, y - 4), legacy.getpixel((x, y)))
        assert layer.tobytes() == expected.tobytes()
        assert _bounds(layer) == expected_bounds
        assert len(_opaque_points(layer)) == expected_count
        remapped_source_union.update((x, y + 4) for x, y in _opaque_points(layer))

    assert remapped_source_union == source_component
    grip = tuple(spec["semanticAnchors"]["primaryGripAnchor"])
    assert grip == (42, 42)
    assert loaded["weapon_held_default_sword"].getpixel(grip) == legacy.getpixel(
        (42, 46)
    )
    assert loaded["weapon_held_default_sword"].getpixel((43, 54)) == _rgba(
        spec, "redAccent"
    )


def test_default_sword_grip_uses_hands_front_and_guard_occlusion() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    layers, errors = load_source_layers(CANONICAL_SPEC_PATH, spec)
    assert errors == []
    base_ids = {
        "hair_back_default",
        "body_base",
        "shoes_adventure",
        "bottom_adventure",
        "top_adventure",
        "hands_front",
        "hair_front_default",
        "headgear_adventure",
        "accessory_adventure",
    }
    weapon_ids = {
        "weapon_back_default_sword",
        "weapon_held_default_sword",
        "weapon_front_default_sword",
    }

    def compose(selected_ids: set[str]) -> Image.Image:
        result = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        for slot in spec["zOrder"]:
            for definition in spec["canonicalLayers"]:
                if definition["slot"] == slot and definition["id"] in selected_ids:
                    result = Image.alpha_composite(result, layers[definition["id"]])
        return result

    without_weapon = compose(base_ids)
    with_weapon = compose(base_ids | weapon_ids)
    body = layers["body_base"]
    hands = layers["hands_front"]
    held = layers["weapon_held_default_sword"]
    front = layers["weapon_front_default_sword"]
    grip = tuple(spec["semanticAnchors"]["primaryGripAnchor"])
    assert held.getpixel(grip)[3] == 255
    assert hands.getpixel(grip)[3] == 255
    assert front.getpixel(grip)[3] == 0
    assert with_weapon.getpixel(grip) == held.getpixel(grip)

    hand_points = _body_skin_points(spec, body)
    guard_points = _opaque_points(front)
    guard_hand_overlap = hand_points.intersection(guard_points)
    assert guard_points == {(41, 39), (42, 39), (43, 39), (44, 39)}
    assert guard_hand_overlap == {(41, 39), (42, 39)}
    assert all(
        with_weapon.getpixel(point) == front.getpixel(point)
        for point in guard_hand_overlap
    )
    for point in hand_points:
        expected = hands.getpixel(point)
        for weapon_layer in (
            layers["weapon_back_default_sword"],
            held,
            front,
        ):
            if weapon_layer.getpixel(point)[3] != 0:
                expected = weapon_layer.getpixel(point)
        assert with_weapon.getpixel(point) == expected

    face_region = spec["layerInterfaceContracts"]["faceProtection"][
        "featureRegion"
    ]
    face_points = {
        point
        for point in _points_in_bounds(face_region)
        if body.getpixel(point)[3] != 0
    }
    for point in face_points:
        expected = without_weapon.getpixel(point)
        for weapon_layer in (
            layers["weapon_back_default_sword"],
            held,
            front,
        ):
            if weapon_layer.getpixel(point)[3] != 0:
                expected = weapon_layer.getpixel(point)
        assert with_weapon.getpixel(point) == expected
    composite_bounds = _bounds(with_weapon)
    assert composite_bounds is not None
    assert (composite_bounds[0] + composite_bounds[2]) / 2 == spec["centerX"]
    assert composite_bounds[3] == spec["soleY"]


def test_default_outfit_layers_satisfy_common_interface() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    with Image.open(CANONICAL_BODY_PATH) as stored:
        body = stored.copy()
    layer_dir = CANONICAL_SPEC_PATH.parent / "layers"
    layers: dict[str, Image.Image] = {}
    for layer_id in ("top_default", "bottom_default", "shoes_default"):
        assert validate_layer(CANONICAL_SPEC_PATH, layer_id) == []
        with Image.open(layer_dir / f"{layer_id}.png") as stored:
            layers[layer_id] = stored.copy()

    top = layers["top_default"]
    bottom = layers["bottom_default"]
    shoes = layers["shoes_default"]
    outline = _rgba(spec, "outlineDarkNavy")
    neutral_colors = {
        _rgba(spec, "underDark"),
        _rgba(spec, "underMid"),
        _rgba(spec, "underLight"),
    }
    for layer in layers.values():
        opaque_colors = {
            pixel for pixel in layer.get_flattened_data() if pixel[3] != 0
        }
        assert opaque_colors <= neutral_colors | {outline}
        assert opaque_colors & neutral_colors
    face_points = _points_in_bounds(spec["semanticAnchors"]["faceProtectedRegion"])
    hand_points = _body_skin_points(spec, body)
    waist_points = _points_in_bounds(spec["semanticAnchors"]["waistOverlapBand"])
    ankle_points = set().union(
        *(
            _points_in_bounds(region)
            for region in spec["semanticAnchors"]["ankleOverlapBands"].values()
        )
    )

    assert all(top.getpixel(point)[3] == 0 for point in face_points)
    assert all(top.getpixel(point)[3] == 0 for point in hand_points)
    assert all(
        top.getpixel((x, 29))[3] == 0 or body.getpixel((x, 29))[3] != 0
        for x in range(64)
    )
    torso_neutral = {
        point
        for point in _points_in_bounds(spec["semanticAnchors"]["torsoBounds"])
        if body.getpixel(point)[:3]
        in {
            _rgba(spec, "underDark")[:3],
            _rgba(spec, "underMid")[:3],
            _rgba(spec, "underLight")[:3],
        }
    }
    assert torso_neutral
    assert all(top.getpixel(point)[3] == 255 for point in torso_neutral)
    assert all(top.getpixel(point)[3] == 255 for point in waist_points)
    assert all(top.getpixel(point) != outline for point in waist_points)

    assert all(bottom.getpixel(point)[3] == 0 for point in hand_points)
    assert all(bottom.getpixel(point)[3] == 255 for point in waist_points)
    assert all(bottom.getpixel(point) != outline for point in waist_points)
    assert all(
        bottom.getpixel((x, y))[3] == 255
        for y in range(41, 49)
        for x in range(24, 41)
    )
    assert all(
        bottom.getpixel((x, y))[3] == 255
        for y in range(49, 55)
        for x in (*range(24, 32), *range(33, 41))
    )
    assert all(bottom.getpixel((32, y))[3] == 0 for y in range(49, 55))
    assert all(bottom.getpixel(point)[3] == 255 for point in ankle_points)
    assert all(bottom.getpixel(point) != outline for point in ankle_points)

    assert all(
        pixel[3] == 0
        for y in (*range(0, 53), *range(59, 64))
        for pixel in (shoes.getpixel((x, y)) for x in range(64))
    )
    base_foot_points = {
        (x, y)
        for y in range(53, 59)
        for x in range(64)
        if body.getpixel((x, y))[3] != 0
    }
    assert all(shoes.getpixel(point)[3] == 255 for point in base_foot_points)
    assert all(shoes.getpixel(point)[3] == 255 for point in ankle_points)
    assert all(shoes.getpixel(point) != outline for point in ankle_points)
    assert any(shoes.getpixel((x, 58))[3] == 255 for x in range(0, 32))
    assert any(shoes.getpixel((x, 58))[3] == 255 for x in range(33, 64))
    assert all(
        shoes.getpixel((32 - offset, y))[3]
        == shoes.getpixel((32 + offset, y))[3]
        for y in range(53, 59)
        for offset in range(1, 33)
        if 32 + offset < 64
    )


def test_adventure_gloves_use_bare_hand_mask_and_connect_to_blue_teal_cuffs() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    layer_dir = CANONICAL_SPEC_PATH.parent / "layers"
    with Image.open(layer_dir / "hands_front.png") as stored:
        hands = stored.copy()
    with Image.open(layer_dir / "gloves_adventure.png") as stored:
        gloves = stored.copy()

    assert validate_layer(CANONICAL_SPEC_PATH, "gloves_adventure") == []
    assert gloves.getchannel("A").tobytes() == hands.getchannel("A").tobytes()
    assert _bounds(gloves) == [21, 39, 43, 45]
    assert len(_opaque_points(gloves)) == 38
    allowed = {
        _rgba(spec, "outlineDarkNavy"),
        _rgba(spec, "blueShadow"),
        _rgba(spec, "bluePrimary"),
        _rgba(spec, "blueHighlight"),
        _rgba(spec, "tealAccent"),
    }
    assert {
        pixel for pixel in gloves.get_flattened_data() if pixel[3] != 0
    } <= allowed
    assert gloves.getpixel((24, 39)) == _rgba(spec, "tealAccent")
    assert gloves.getpixel((40, 39)) == _rgba(spec, "tealAccent")
    assert _rgba(spec, "bluePrimary") in set(gloves.get_flattened_data())


def test_adventure_outfit_layers_satisfy_common_interface() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    with Image.open(CANONICAL_BODY_PATH) as stored:
        body = stored.copy()
    layer_dir = CANONICAL_SPEC_PATH.parent / "layers"
    layers: dict[str, Image.Image] = {}
    for layer_id in ("top_adventure", "bottom_adventure", "shoes_adventure"):
        assert validate_layer(CANONICAL_SPEC_PATH, layer_id) == []
        with Image.open(layer_dir / f"{layer_id}.png") as stored:
            layers[layer_id] = stored.copy()

    top = layers["top_adventure"]
    bottom = layers["bottom_adventure"]
    shoes = layers["shoes_adventure"]
    outline = _rgba(spec, "outlineDarkNavy")
    face_points = _points_in_bounds(spec["semanticAnchors"]["faceProtectedRegion"])
    hand_points = _body_skin_points(spec, body)
    waist_points = _points_in_bounds(spec["semanticAnchors"]["waistOverlapBand"])
    ankle_points = set().union(
        *(
            _points_in_bounds(region)
            for region in spec["semanticAnchors"]["ankleOverlapBands"].values()
        )
    )

    assert all(top.getpixel(point)[3] == 0 for point in face_points)
    assert all(top.getpixel(point)[3] == 0 for point in hand_points)
    torso_neutral = {
        point
        for point in _points_in_bounds(spec["semanticAnchors"]["torsoBounds"])
        if body.getpixel(point)[:3]
        in {
            _rgba(spec, "underDark")[:3],
            _rgba(spec, "underMid")[:3],
            _rgba(spec, "underLight")[:3],
        }
    }
    assert torso_neutral
    assert all(top.getpixel(point)[3] == 255 for point in torso_neutral)
    assert all(top.getpixel(point)[3] == 255 for point in waist_points)
    assert all(top.getpixel(point) != outline for point in waist_points)
    top_colors = set(top.get_flattened_data())
    assert _rgba(spec, "bluePrimary") in top_colors
    assert _rgba(spec, "lightCream") in top_colors
    assert _rgba(spec, "tealAccent") in top_colors

    required_bottom = {
        (x, y)
        for y in range(41, 49)
        for x in range(24, 41)
    } | {
        (x, y)
        for y in range(49, 55)
        for x in (*range(24, 32), *range(33, 41))
    }
    actual_bottom = {
        (x, y)
        for y in range(64)
        for x in range(64)
        if bottom.getpixel((x, y))[3] != 0
    }
    assert actual_bottom == required_bottom
    assert all(bottom.getpixel(point) != outline for point in waist_points)
    assert all(bottom.getpixel(point) != outline for point in ankle_points)
    bottom_colors = set(bottom.get_flattened_data())
    assert _rgba(spec, "eyeDarkNavy") in bottom_colors
    assert _rgba(spec, "underDark") in bottom_colors

    assert all(
        pixel[3] == 0
        for y in (*range(0, 53), *range(59, 64))
        for pixel in (shoes.getpixel((x, y)) for x in range(64))
    )
    base_foot_points = {
        (x, y)
        for y in range(53, 59)
        for x in range(64)
        if body.getpixel((x, y))[3] != 0
    }
    assert all(shoes.getpixel(point)[3] == 255 for point in base_foot_points)
    assert all(shoes.getpixel(point)[3] == 255 for point in ankle_points)
    assert all(shoes.getpixel(point) != outline for point in ankle_points)
    shoes_colors = set(shoes.get_flattened_data())
    assert _rgba(spec, "blueShadow") in shoes_colors
    assert _rgba(spec, "lightCream") in shoes_colors
    assert _rgba(spec, "goldAccent") in shoes_colors
    assert all(
        shoes.getpixel((32 - offset, y))[3]
        == shoes.getpixel((32 + offset, y))[3]
        for y in range(53, 59)
        for offset in range(1, 33)
        if 32 + offset < 64
    )


def test_mixed_outfit_composites_preserve_common_interfaces() -> None:
    spec = json.loads(CANONICAL_SPEC_PATH.read_text(encoding="utf-8"))
    layer_dir = CANONICAL_SPEC_PATH.parent / "layers"
    with Image.open(CANONICAL_BODY_PATH) as stored:
        body = stored.copy()
    with Image.open(layer_dir / "hands_front.png") as stored:
        hands = stored.copy()
    layers: dict[str, Image.Image] = {}
    for layer_id in (
        "top_default",
        "top_adventure",
        "bottom_default",
        "bottom_adventure",
        "shoes_default",
        "shoes_adventure",
    ):
        with Image.open(layer_dir / f"{layer_id}.png") as stored:
            layers[layer_id] = stored.copy()

    waist_points = _points_in_bounds(spec["semanticAnchors"]["waistOverlapBand"])
    ankle_points = set().union(
        *(
            _points_in_bounds(region)
            for region in spec["semanticAnchors"]["ankleOverlapBands"].values()
        )
    )
    face_points = {
        point
        for point in _points_in_bounds(
            spec["layerInterfaceContracts"]["faceProtection"]["featureRegion"]
        )
        if body.getpixel(point)[3] != 0
    }
    hand_points = _body_skin_points(spec, body)
    neutral_colors = {
        _rgba(spec, "underDark"),
        _rgba(spec, "underMid"),
        _rgba(spec, "underLight"),
    }
    neutral_regions = (
        spec["semanticAnchors"]["torsoBounds"],
        [24, 44, 40, 52],
        *spec["semanticAnchors"]["ankleOverlapBands"].values(),
        [23, 55, 41, 58],
    )
    neutral_points = {
        point
        for region in neutral_regions
        for point in _points_in_bounds(region)
        if body.getpixel(point) in neutral_colors
    }
    outline = _rgba(spec, "outlineDarkNavy")
    combinations: dict[tuple[str, str, str], Image.Image] = {}

    for top_id, bottom_id, shoes_id in itertools.product(
        ("top_default", "top_adventure"),
        ("bottom_default", "bottom_adventure"),
        ("shoes_default", "shoes_adventure"),
    ):
        top = layers[top_id]
        bottom = layers[bottom_id]
        shoes = layers[shoes_id]
        assert all(top.getpixel(point)[3] == 255 for point in waist_points)
        assert all(bottom.getpixel(point)[3] == 255 for point in waist_points)
        assert all(top.getpixel(point) != outline for point in waist_points)
        assert all(bottom.getpixel(point) != outline for point in waist_points)
        assert all(bottom.getpixel(point)[3] == 255 for point in ankle_points)
        assert all(shoes.getpixel(point)[3] == 255 for point in ankle_points)
        assert all(bottom.getpixel(point) != outline for point in ankle_points)
        assert all(shoes.getpixel(point) != outline for point in ankle_points)

        composite = body.copy()
        for layer in (shoes, bottom, top, hands):
            composite = Image.alpha_composite(composite, layer)
        combinations[(top_id, bottom_id, shoes_id)] = composite

        assert all(composite.getpixel(point) == body.getpixel(point) for point in face_points)
        assert all(composite.getpixel(point) == body.getpixel(point) for point in hand_points)
        assert all(
            any(layer.getpixel(point)[3] != 0 for layer in (top, bottom, shoes))
            for point in neutral_points
        )
        assert _bounds(composite) == [20, 7, 44, 58]

    assert ("top_default", "bottom_adventure", "shoes_default") in combinations
    assert ("top_adventure", "bottom_default", "shoes_adventure") in combinations


def _points_in_bounds(bounds: list[int]) -> set[tuple[int, int]]:
    left, top, right, bottom = bounds
    return {
        (x, y)
        for y in range(top, bottom + 1)
        for x in range(left, right + 1)
    }
