"""Regression checks for Kotlin declaration indexing used by the import validator."""

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "kt-import-paths.py"
spec = importlib.util.spec_from_file_location("kt_import_paths", SCRIPT)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class ImportPathDeclarationTests(unittest.TestCase):
    def test_suspend_function_is_indexed_under_its_name(self):
        match = validator.DECL_RE.search("internal suspend fun readEvents(): Unit = Unit")
        self.assertIsNotNone(match)
        self.assertEqual("readEvents", match.group(1))

    def test_normal_function_and_class_are_still_indexed(self):
        text = "fun ordinary() = Unit\ninternal class Example"
        self.assertEqual(["ordinary", "Example"], validator.DECL_RE.findall(text))


if __name__ == "__main__":
    unittest.main()
