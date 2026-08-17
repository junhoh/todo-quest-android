import hashlib
import sys
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parent
ROOT = SCRIPTS_DIR.parent
SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "npc"
    / "todo-quest-blacksmith-shopkeeper-front-idle-spec.json"
)
CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "npc"
    / "todo-quest-blacksmith-shopkeeper-front-idle.png"
)
RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_blacksmith_shopkeeper_front_idle.png"
)
FAIRY_SPEC_PATH = (
    ROOT
    / "docs"
    / "art"
    / "npc"
    / "todo-quest-fairy-guide-front-idle-spec.json"
)
FAIRY_CANONICAL_PATH = (
    ROOT
    / "docs"
    / "art"
    / "npc"
    / "todo-quest-fairy-guide-front-idle.png"
)
FAIRY_RUNTIME_PATH = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "res"
    / "drawable-nodpi"
    / "todo_quest_fairy_guide_front_idle.png"
)
sys.path.insert(0, str(SCRIPTS_DIR))

from validate_monster_sprite import validate_sprite  # noqa: E402


def test_blacksmith_shopkeeper_canonical_sprite_matches_contract() -> None:
    assert validate_sprite(CANONICAL_PATH, SPEC_PATH) == []


def test_fairy_guide_canonical_sprite_matches_contract() -> None:
    assert validate_sprite(FAIRY_CANONICAL_PATH, FAIRY_SPEC_PATH) == []


def test_fairy_guide_runtime_sprite_is_byte_identical_to_canonical() -> None:
    assert FAIRY_RUNTIME_PATH.read_bytes() == FAIRY_CANONICAL_PATH.read_bytes()


def test_fairy_guide_runtime_sprite_sha256_matches_canonical() -> None:
    canonical_sha256 = hashlib.sha256(FAIRY_CANONICAL_PATH.read_bytes()).hexdigest()
    runtime_sha256 = hashlib.sha256(FAIRY_RUNTIME_PATH.read_bytes()).hexdigest()

    assert runtime_sha256 == canonical_sha256


def test_blacksmith_shopkeeper_runtime_sprite_is_byte_identical_to_canonical() -> None:
    assert RUNTIME_PATH.read_bytes() == CANONICAL_PATH.read_bytes()


def test_blacksmith_shopkeeper_runtime_sprite_sha256_matches_canonical() -> None:
    canonical_sha256 = hashlib.sha256(CANONICAL_PATH.read_bytes()).hexdigest()
    runtime_sha256 = hashlib.sha256(RUNTIME_PATH.read_bytes()).hexdigest()

    assert runtime_sha256 == canonical_sha256
