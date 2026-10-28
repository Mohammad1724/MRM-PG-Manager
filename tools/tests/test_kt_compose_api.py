"""Regression checks for Compose UI test member and extension imports."""

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "kt-compose-api.py"
spec = importlib.util.spec_from_file_location("kt_compose_api", SCRIPT)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class ComposeTestImportChecks(unittest.TestCase):
    def check_source(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Example.kt"
            path.write_text(source, encoding="utf-8")
            return (
                validator.missing_imports(path, validator.index_symbols()),
                validator.forbidden_imports(path),
            )

    def test_provider_members_need_no_top_level_import(self):
        missing, forbidden = self.check_source("""
            fun example(rule: Rule, matcher: Matcher) {
                rule.onNode(matcher)
                rule.onAllNodes(matcher)
            }
        """)
        self.assertEqual([], missing)
        self.assertEqual([], forbidden)

    def test_importing_provider_members_is_rejected(self):
        missing, forbidden = self.check_source("""
            import androidx.compose.ui.test.onNode
            import androidx.compose.ui.test.onAllNodes
        """)
        self.assertEqual([], missing)
        self.assertEqual(["onAllNodes", "onNode"], forbidden)

    def test_actual_extensions_still_require_their_imports(self):
        missing, forbidden = self.check_source("""
            fun example(rule: Rule) { rule.onNodeWithTag("tag") }
        """)
        self.assertEqual(["onNodeWithTag"], missing)
        self.assertEqual([], forbidden)


if __name__ == "__main__":
    unittest.main()
