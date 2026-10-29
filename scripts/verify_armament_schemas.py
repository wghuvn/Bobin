#!/usr/bin/env python3
"""Validate the published schemas against the same corpus as the Java validators.

Development check only. Uses the verifier's installed jsonschema package; does not
add a mod dependency, fetch schemas, or change the project. Semantic relationships
marked structural_valid=true are deliberately checked by Java, not JSON Schema.
"""
import json
from pathlib import Path

from jsonschema import Draft202012Validator
from referencing import Registry, Resource


def main():
    root = Path(__file__).resolve().parents[1]
    schemas = {
        path.name.removesuffix(".schema.json"): json.loads(path.read_text())
        for path in (root / "schemas/armament").glob("*.schema.json")
    }
    assert len(schemas) == 6, "expected all six v1 schemas"
    registry = Registry()
    for schema in schemas.values():
        Draft202012Validator.check_schema(schema)
        registry = registry.with_resource(schema["$id"], Resource.from_contents(schema))
    cases = json.loads((root / "src/test/resources/armament/metadata-cases.json").read_text())
    assert cases, "the schema check must execute cases"
    for case in cases:
        validator = Draft202012Validator(schemas[case["schema"]], registry=registry)
        errors = list(validator.iter_errors(case["document"]))
        actual = not errors
        assert actual == case["structural_valid"], (
            f"{case['name']}: expected structural_valid={case['structural_valid']}; "
            + "; ".join(error.message for error in errors)
        )
    print(f"PASS: {len(schemas)} Draft 2020-12 schemas; {len(cases)} structural cases")


if __name__ == "__main__":
    main()
