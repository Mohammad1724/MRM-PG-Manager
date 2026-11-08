"""Regression checks for inherited members and missing coroutine imports."""

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "kt-imports.py"
spec = importlib.util.spec_from_file_location("kt_imports", SCRIPT)
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


class CoroutineScopeImportTests(unittest.TestCase):
    def check(self, source):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "Example.kt"
            path.write_text(source, encoding="utf-8")
            return validator.check(path, {"coroutineScope": {"kotlinx.coroutines"}}, {})

    def test_inherited_member_access_is_not_an_import(self):
        source = "package example\nclass Node { fun animate() { this.coroutineScope.launch {} } }"
        self.assertEqual([], self.check(source))

    def test_unqualified_function_still_requires_an_import(self):
        source = "package example\nfun work() { coroutineScope {} }"
        problems = self.check(source)
        self.assertEqual(["coroutineScope"], [problem[0] for problem in problems])


if __name__ == "__main__":
    unittest.main()
