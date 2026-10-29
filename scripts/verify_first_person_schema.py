#!/usr/bin/env python3
"""Development-only schema verification; no dependencies added to the mod."""
import copy
import json
from pathlib import Path
from jsonschema import Draft202012Validator
from referencing import Registry, Resource


def patch(document, edits):
    for edit in edits:
        parts = edit['field'].split('.')
        parent = document
        for part in parts[:-1]:
            parent = parent[int(part)] if isinstance(parent, list) else parent[part]
        key = int(parts[-1]) if isinstance(parent, list) else parts[-1]
        if edit.get('remove'):
            del parent[key]
        else:
            parent[key] = copy.deepcopy(edit['value'])
    return document


def main():
    root = Path(__file__).resolve().parents[1]
    registry = Registry()
    schemas = {}
    for path in (root / 'schemas/armament').rglob('*.schema.json'):
        schema = json.loads(path.read_text())
        Draft202012Validator.check_schema(schema)
        registry = registry.with_resource(schema['$id'], Resource.from_contents(schema))
        schemas[schema['$id']] = schema
    validator = Draft202012Validator(schemas['urn:bobincraft:armament:first-person:1'], registry=registry)
    profile = json.loads((root / 'src/main/resources/assets/bobin_craft/armament/debug/first_person_fixture.profile.json').read_text())
    cases = json.loads((root / 'src/test/resources/armament/first-person-cases.json').read_text())
    for case in cases:
        errors = list(validator.iter_errors(patch(copy.deepcopy(profile), case['patches'])))
        assert (not errors) == case['structural_valid'], case['name'] + ': ' + '; '.join(e.message for e in errors)
    for kind, schema_id in [('rig', 'urn:bobincraft:armament:rig:1'), ('sockets', 'urn:bobincraft:armament:sockets:1')]:
        data = json.loads((root / 'src/main/resources/assets/bobin_craft/armament/debug' / f'first_person_fixture.{kind}.json').read_text())
        Draft202012Validator(schemas[schema_id], registry=registry).validate(data)
    assets = root / 'src/main/resources/assets/bobin_craft/armament/debug'
    for kind, schema_id in [('profile', 'urn:bobincraft:armament:first-person:1'), ('rig', 'urn:bobincraft:armament:rig:1'), ('sockets', 'urn:bobincraft:armament:sockets:1')]:
        Draft202012Validator(schemas[schema_id], registry=registry).validate(json.loads((assets / f'first_person_fixture_slim.{kind}.json').read_text()))
    hands = json.loads((assets / 'first_person_hands.appearance.json').read_text())
    hand_validator = Draft202012Validator(schemas['urn:bobincraft:armament:player-hands:1'], registry=registry)
    hand_validator.validate(hands)
    bad_hands = []
    bad = copy.deepcopy(hands);bad['scale'] = .5;bad_hands.append(bad)
    bad = copy.deepcopy(hands);bad['schema_version'] = 2;bad_hands.append(bad)
    bad = copy.deepcopy(hands);del bad['profiles']['slim'];bad_hands.append(bad)
    bad = copy.deepcopy(hands);bad['profiles']['default'] = 'https://example.invalid/hands.glb';bad_hands.append(bad)
    bad = copy.deepcopy(hands);bad['materials']['sleeve_left'] = 'Bad Material';bad_hands.append(bad)
    for bad in bad_hands:
        assert list(hand_validator.iter_errors(bad)), bad
    print(f'PASS: first-person profile schema, {len(cases)} structural cases; Steve/Alex sidecars; appearance: 1 positive + {len(bad_hands)} negative')


if __name__ == '__main__':
    main()
