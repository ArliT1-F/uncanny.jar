#!/usr/bin/env python3
"""
Project checks that can run without Minecraft.

This is NOT a substitute for `./gradlew build`. It cannot type-check, because the
Minecraft and Fabric API jars are not available here. What it does instead is
everything that does not need those jars:

  1. parse every .java file with a real Java 17 grammar and report syntax errors;
  2. check that every `import dev.uncanny.*` resolves to a file that exists;
  3. check that every file declares the class its filename promises;
  4. check that every registered block and item has a blockstate, a model and a
     translation;
  5. check that every model points at a texture that exists;
  6. check that every dimension JSON points at a dimension type and biome that
     exist, and that its client effects identifier is registered in Java;
  7. validate every JSON file in the project.

Run it with:

    python3 tools/check_project.py
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOT = os.path.join(ROOT, "src", "main", "java")
RES = os.path.join(ROOT, "src", "main", "resources")

failures = []
warnings = []
checks_run = 0


def check(name, condition, detail=""):
    global checks_run
    checks_run += 1
    if not condition:
        failures.append(f"{name}: {detail}")
    return condition


def java_files():
    for dirpath, _dirnames, filenames in os.walk(JAVA_ROOT):
        for filename in sorted(filenames):
            if filename.endswith(".java"):
                yield os.path.join(dirpath, filename)


def resource_files(subdir):
    base = os.path.join(RES, subdir)
    if not os.path.isdir(base):
        return
    for dirpath, _dirnames, filenames in os.walk(base):
        for filename in sorted(filenames):
            yield os.path.join(dirpath, filename)


# ------------------------------------------------------------------ 1. syntax
def check_syntax():
    try:
        import tree_sitter_java as tsj
        from tree_sitter import Language, Parser
    except ImportError:
        warnings.append("tree_sitter is not installed; skipping the syntax pass "
                        "(pip install tree-sitter tree-sitter-java)")
        return
    parser = Parser(Language(tsj.language()))
    parsed = 0
    for path in java_files():
        with open(path, "rb") as fh:
            tree = parser.parse(fh.read())
        parsed += 1
        if tree.root_node.has_error:
            bad = first_error(tree.root_node)
            failures.append(f"syntax: {rel(path)}:{bad[0]}:{bad[1]}")
    print(f"  parsed {parsed} java files")


def first_error(node):
    if node.is_missing or node.has_error and not any(c.has_error for c in node.children):
        return node.start_point[0] + 1, node.start_point[1] + 1
    for child in node.children:
        if child.has_error or child.is_missing:
            return first_error(child)
    return node.start_point[0] + 1, node.start_point[1] + 1


# ----------------------------------------------------------------- 2. imports
def collect_classes():
    classes = set()
    declared = []
    for path in java_files():
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        package = re.search(r"^package\s+([\w.]+);", text, re.M)
        if not package:
            failures.append(f"package: {rel(path)} has no package declaration")
            continue
        pkg = package.group(1)
        names = re.findall(
            r"^(?:public\s+|final\s+|abstract\s+|static\s+)*"
            r"(?:class|interface|enum|record)\s+(\w+)", text, re.M)
        if not names:
            failures.append(f"class: {rel(path)} declares no type")
            continue
        expected = os.path.basename(path)[:-5]
        check("filename", expected in names,
              f"{rel(path)} declares {names}, expected a type named {expected}")
        for name in names:
            classes.add(pkg + "." + name)
        declared.append((path, pkg, expected))
    return classes, declared


def check_imports(classes):
    for path in java_files():
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        for match in re.finditer(r"^import\s+(static\s+)?([\w.]+);", text, re.M):
            imported = match.group(2)
            if not imported.startswith("dev.uncanny."):
                continue
            if imported in classes:
                continue
            # Nested types and static imports: allow the outer class to match.
            parts = imported.split(".")
            ok = any(".".join(parts[:i]) in classes for i in range(len(parts), 3, -1))
            check("import", ok, f"{rel(path)} imports {imported}, which does not exist")


# ------------------------------------------------------- 3. blocks and items
def check_registry(classes):
    with open(os.path.join(JAVA_ROOT, "dev", "uncanny", "item", "UncannyBlocks.java"), encoding="utf-8") as fh:
        blocks_src = fh.read()
    with open(os.path.join(JAVA_ROOT, "dev", "uncanny", "item", "UncannyItems.java"), encoding="utf-8") as fh:
        items_src = fh.read()
    with open(os.path.join(RES, "assets", "uncanny", "lang", "en_us.json"), encoding="utf-8") as fh:
        lang = json.load(fh)

    block_ids = re.findall(r'register\("([\w]+)", new \w+Block', blocks_src)
    for block_id in block_ids:
        check("blockstate", os.path.exists(os.path.join(RES, "assets", "uncanny",
              "blockstates", block_id + ".json")), f"block uncanny:{block_id} has no blockstate")
        check("block model", os.path.exists(os.path.join(RES, "assets", "uncanny",
              "models", "block", block_id + ".json")), f"block uncanny:{block_id} has no model")
        check("lang", f"block.uncanny.{block_id}" in lang,
              f"block.uncanny.{block_id} missing from en_us.json")

    item_ids = re.findall(r'register(?:BlockItem)?\("([\w]+)"', items_src)
    for item_id in item_ids:
        check("item model", os.path.exists(os.path.join(RES, "assets", "uncanny",
              "models", "item", item_id + ".json")), f"item uncanny:{item_id} has no model")
        kind = "block" if item_id in block_ids else "item"
        check("lang", f"{kind}.uncanny.{item_id}" in lang,
              f"{kind}.uncanny.{item_id} missing from en_us.json")
    print(f"  {len(block_ids)} blocks, {len(item_ids)} items cross-referenced")
    return block_ids


# --------------------------------------------------------------- 4. textures
def check_textures():
    referenced = set()
    for path in list(resource_files("assets/uncanny/models")):
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        textures = data.get("textures", {})
        for value in textures.values():
            if isinstance(value, str) and value.startswith("uncanny:"):
                referenced.add(value.split(":", 1)[1])
    missing = [t for t in sorted(referenced)
               if not os.path.exists(os.path.join(RES, "assets", "uncanny", "textures", t + ".png"))]
    check("textures", not missing, f"models reference missing textures {missing}")
    print(f"  {len(referenced)} texture references checked")


# ------------------------------------------------------------- 5. dimensions
def check_dimensions(java_sources):
    joined = "\n".join(java_sources.values())
    for path in sorted(resource_files("data/uncanny/dimension")):
        name = os.path.basename(path)[:-5]
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        dtype = data["type"]
        check("dimension type", dtype.startswith("uncanny:"), f"{name} has a foreign type {dtype}")
        type_path = os.path.join(RES, "data", "uncanny", "dimension_type",
                                 dtype.split(":", 1)[1] + ".json")
        check("dimension type", os.path.exists(type_path), f"{name} points at missing {dtype}")

        with open(type_path, encoding="utf-8") as fh:
            type_data = json.load(fh)
        effects = type_data.get("effects")
        if effects:
            # The identifier must be registered on the client, or the layer renders
            # with an Overworld sky.
            key = effects.split(":", 1)[1].upper()
            check("effects", f"UncannyDimension.{key}" in joined,
                  f"{name} uses effects {effects} but UncannyDimension.{key} is not registered")
            check("effects", f"\"{name}\"" in joined or f"{key}," in joined,
                  f"{name} has no UncannyDimension entry")

        generator = data["generator"]
        biome = None
        if generator["type"] == "minecraft:flat":
            biome = generator["settings"]["biome"]
        elif generator["type"] == "minecraft:noise":
            biome = generator["biome_source"]["biome"]
        if biome:
            biome_path = os.path.join(RES, "data", "uncanny", "worldgen", "biome",
                                      biome.split(":", 1)[1] + ".json")
            check("biome", os.path.exists(biome_path), f"{name} points at missing biome {biome}")
    print(f"  {len(list(resource_files('data/uncanny/dimension')))} dimensions cross-referenced")


# --------------------------------------------------------------- 6. all json
def check_json():
    count = 0
    for base in ("assets", "data"):
        for path in resource_files(base):
            if not path.endswith(".json"):
                continue
            count += 1
            try:
                with open(path, encoding="utf-8") as fh:
                    json.load(fh)
            except json.JSONDecodeError as e:
                failures.append(f"json: {rel(path)}: {e}")
    print(f"  {count} json files validated")



# --------------------------------------------------- 8. calls into our own code
#
# Minecraft classes cannot be checked here, but every call the mod makes into its
# OWN classes can be: the method has to exist, and the argument count has to match
# one of the declared overloads. This catches the mistakes that are most likely in
# a few thousand lines of hand-written Java with no compiler: a renamed method, a
# dropped argument, a parameter added in one place and not another.
def check_static_calls():
    try:
        import tree_sitter_java as tsj
        from tree_sitter import Language, Parser
    except ImportError:
        warnings.append("tree_sitter missing; skipping the call-arity pass")
        return

    parser = Parser(Language(tsj.language()))
    methods = {}     # class simple name -> {method name -> set of arities}
    void_methods = set()  # (class, method) pairs that return nothing
    varargs = set()  # (class, method) pairs that take any arity
    parsed = {}

    container_kinds = ("class_declaration", "interface_declaration",
                       "enum_declaration", "record_declaration")
    member_kinds = ("method_declaration", "constructor_declaration")

    for path in java_files():
        with open(path, "rb") as fh:
            source = fh.read()
        tree = parser.parse(source)
        parsed[rel(path)] = tree

        def collect(node, owner):
            current = owner
            if node.type in container_kinds:
                for child in node.children:
                    if child.type == "identifier":
                        current = child.text.decode()
                        methods.setdefault(current, {})
                        break
            if node.type in member_kinds and current:
                name_node = node.child_by_field_name("name")
                params = node.child_by_field_name("parameters")
                if name_node is not None and params is not None:
                    name = name_node.text.decode()
                    arity = 0
                    for child in params.named_children:
                        if child.type == "spread_parameter":
                            varargs.add((current, name))
                        else:
                            arity += 1
                    methods[current].setdefault(name, set()).add(arity)
                    ret = node.child_by_field_name("type")
                    if ret is not None and ret.text.decode() == "void":
                        void_methods.add((current, name))
            for child in node.children:
                collect(child, current)

        collect(tree.root_node, None)

    # Methods that exist everywhere, or come from a supertype we cannot see.
    allowed_anywhere = {"toString", "equals", "hashCode", "getClass", "clone", "valueOf",
                        "values", "ordinal", "name", "of", "get", "put", "add", "remove",
                        "stream", "toList", "contains", "size", "isEmpty", "markDirty",
                        "run", "test", "apply", "accept", "getOrDefault", "putAll",
                        "addAll", "read", "write", "build", "create", "register"}

    counter = [0]
    for path, tree in parsed.items():

        def walk(node):
            if node.type == "method_invocation":
                target = node.child_by_field_name("object")
                name_node = node.child_by_field_name("name")
                args = node.child_by_field_name("arguments")
                if (target is not None and name_node is not None and args is not None
                        and target.type == "identifier"):
                    owner = target.text.decode()
                    name = name_node.text.decode()
                    if owner in methods and owner[0].isupper():
                        arity = len(args.named_children)
                        counter[0] += 1
                        if (owner, name) in varargs or name in allowed_anywhere:
                            pass
                        elif name not in methods[owner]:
                            check("call", False,
                                  f"{path} calls {owner}.{name}, which is not declared in {owner}")
                        elif arity not in methods[owner][name]:
                            check("call", False,
                                  f"{path} calls {owner}.{name} with {arity} args, "
                                  f"declared with {sorted(methods[owner][name])}")
            for child in node.children:
                walk(child)

        walk(tree.root_node)
    print(f"  {counter[0]} internal calls checked against {len(methods)} classes")



def check_void_assignments():
    """Catches `int x = Thing.doThing();` where doThing returns nothing.

    Arity checks cannot see this, and with no compiler it is exactly the kind of
    mistake that survives a careful read.
    """
    try:
        import tree_sitter_java as tsj
        from tree_sitter import Language, Parser
    except ImportError:
        return
    parser = Parser(Language(tsj.language()))

    voids = {}
    for path in java_files():
        with open(path, "rb") as fh:
            tree = parser.parse(fh.read())

        def collect(node, owner):
            current = owner
            if node.type in ("class_declaration", "enum_declaration", "record_declaration"):
                for child in node.children:
                    if child.type == "identifier":
                        current = child.text.decode()
                        break
            if node.type == "method_declaration" and current:
                name_node = node.child_by_field_name("name")
                ret = node.child_by_field_name("type")
                if name_node is not None and ret is not None and ret.text.decode() == "void":
                    voids.setdefault(current, set()).add(name_node.text.decode())
            for child in node.children:
                collect(child, current)

        collect(tree.root_node, None)

    found = [0]
    for path in java_files():
        with open(path, "rb") as fh:
            tree = parser.parse(fh.read())

        def report(value):
            target = value.child_by_field_name("object")
            name_node = value.child_by_field_name("name")
            if target is None or name_node is None or target.type != "identifier":
                return
            owner = target.text.decode()
            name = name_node.text.decode()
            if owner in voids and name in voids[owner]:
                found[0] += 1
                check("return", False,
                      f"{path} assigns from {owner}.{name}, which returns void")

        def walk(node):
            # `int x = Foo.bar();`  ->  variable_declarator carries the value
            if node.type == "variable_declarator":
                value = node.child_by_field_name("value")
                if value is not None and value.type == "method_invocation":
                    report(value)
            # `x = Foo.bar();`  ->  assignment_expression carries it on the right
            if node.type == "assignment_expression":
                value = node.child_by_field_name("right")
                if value is not None and value.type == "method_invocation":
                    report(value)
            for child in node.children:
                walk(child)

        walk(tree.root_node)
    print(f"  {found[0]} bad assignments from void methods")


# ---------------------------------------------------------------- 7. lore ids
def check_lore():
    ids = set()
    for path in resource_files("data/uncanny/lore"):
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        for record in data.get("records", []):
            check("lore", "id" in record and "pages" in record, f"{rel(path)} record is incomplete")
            check("lore unique", record["id"] not in ids, f"duplicate lore id {record['id']}")
            ids.add(record["id"])
            check("lore stage", 1 <= record.get("stage", 1) <= 9, f"{record['id']} has a bad stage")
    print(f"  {len(ids)} lore documents checked")



# ------------------------------------------- 9. block names that are not blocks
#
# `Blocks.BREAD` compiles into a red squiggle and nothing else. These are the names
# that look like blocks and are not, which is the easiest mistake of this kind to
# make when writing generation code from memory.
ITEM_ONLY = {
    "BREAD", "APPLE", "PAPER", "BOOK", "WRITTEN_BOOK", "STICK", "MAP", "CLOCK",
    "NAME_TAG", "STRING", "BONE", "FEATHER", "COAL", "CHARCOAL", "DIAMOND",
    "IRON_INGOT", "GOLD_INGOT", "WHEAT", "SUGAR", "BOWL", "BUCKET", "COMPASS",
    "FLINT", "LEATHER", "BRICK", "CLAY_BALL", "SLIME_BALL", "EGG", "SADDLE",
    "MUSIC_DISC_13", "GUNPOWDER", "SNOWBALL", "WATER_BUCKET", "LAVA_BUCKET",
}


def check_block_names():
    bad = []
    for path in java_files():
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        for match in re.finditer(r"\bBlocks\.([A-Z_0-9]+)", text):
            name = match.group(1)
            if name in ITEM_ONLY:
                bad.append(f"{rel(path)} uses Blocks.{name}, which is an item")
    check("block names", not bad, "; ".join(bad))
    print(f"  block name check across {len(ITEM_ONLY)} known item-only names")

def rel(path):
    return os.path.relpath(path, ROOT)


def main():
    print("uncanny project checks")
    check_syntax()
    classes, declared = collect_classes()
    print(f"  {len(declared)} classes found")
    check_imports(classes)
    check_registry(classes)
    check_textures()
    sources = {}
    for path in java_files():
        with open(path, encoding="utf-8") as fh:
            sources[rel(path)] = fh.read()
    check_dimensions(sources)
    check_static_calls()
    check_void_assignments()
    check_json()
    check_lore()
    check_block_names()

    # fabric.mod.json entrypoints must exist.
    with open(os.path.join(RES, "fabric.mod.json"), encoding="utf-8") as fh:
        mod = json.load(fh)
    for entrypoints in mod.get("entrypoints", {}).values():
        for entrypoint in entrypoints:
            fqcn = entrypoint
            check("entrypoint", fqcn in classes, f"fabric.mod.json names {fqcn}, which does not exist")

    print()
    for warning in warnings:
        print("WARN  " + warning)
    if failures:
        print(f"FAIL  {len(failures)} problem(s) in {checks_run} checks:")
        for failure in failures:
            print("  - " + failure)
        return 1
    print(f"OK    {checks_run} checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
