# SPDX-License-Identifier: GPL-3.0-only
"""Guarded integration hooks. Updating upstream must pass these exact anchors."""
import hashlib
import json
class PatchContext:
    def __init__(self, root, here):
        self.root, self.here, self.anchors = root, here, []
    def replace(self, path, old, new):
        file = self.root / path
        text = file.read_text()
        if text.count(old) != 1:
            raise RuntimeError(f'Pinned upstream hook changed: {path}: {old!r}')
        file.write_text(text.replace(old, new))
        self.anchors.append({'path': path, 'anchor_sha256': hashlib.sha256(old.encode()).hexdigest()})
    def report(self, lock):
        self.root.joinpath('TECLADO-BR-PATCHES.json').write_text(json.dumps({
            'upstream': lock['commit'], 'version': lock['trial_version'],
            'modules': ['layout', 'suggestions', 'typing', 'assistant'],
            'guarded_anchors': self.anchors,
        }, ensure_ascii=False, indent=2)+'\n')

