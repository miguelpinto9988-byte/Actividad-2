"""Validate the catalog and auditable family grouping; no network required."""
import json
from pathlib import Path
root = Path(__file__).resolve().parents[1]
data = json.loads((root / 'app/src/main/assets/exercises.json').read_text())
audit = json.loads((root / 'tools/catalog_audit.json').read_text())
assert len(data) == len(audit) == 100
assert len({e['id'] for e in data}) == len({e['family'] for e in data}) == 100
source_ids = [source for family in audit.values() for source in family]
assert len(source_ids) == len(set(source_ids)), 'A source variant belongs to two families'
for exercise in data:
    assert exercise['family'] in audit
    assert exercise['level'] in (1, 2, 3)
    assert exercise['unit'] in ('reps', 'seconds')
    assert exercise['instructions'] and exercise['equipment'] and exercise['muscles']
    assert exercise['source'].endswith(exercise['family'] + '.json')
print(f'Validated: 100 families, {len(source_ids)} distinct source records; no variant counted twice.')
