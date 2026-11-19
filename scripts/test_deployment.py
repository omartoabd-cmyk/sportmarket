"""Check URL adaptation without running Java or exposing real credentials."""
import os
from pathlib import Path
import shutil
import subprocess
import unittest

ENTRYPOINT = Path(__file__).resolve().parents[1] / "deploy" / "entrypoint.sh"
SHELL = shutil.which("sh")
if not SHELL and Path("C:/Program Files/Git/bin/bash.exe").exists():
    SHELL = "C:/Program Files/Git/bin/bash.exe"


@unittest.skipUnless(SHELL, "POSIX shell required")
class DeploymentTests(unittest.TestCase):
    def adapt(self, **variables):
        script = ENTRYPOINT.read_text(encoding="utf-8").split("exec java", 1)[0]
        script += '\nprintf "%s" "$DB_URL"\n'
        environment = {key: value for key, value in os.environ.items()
                       if key not in ("DATABASE_URL", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET")}
        environment.update(variables)
        return subprocess.run([SHELL, "-c", script], env=environment,
                              capture_output=True, text=True, timeout=10)

    def test_render_url_removes_credentials(self):
        result = self.adapt(DATABASE_URL="postgresql://demo:dummy-password@private-db:5432/sportmarket")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "jdbc:postgresql://private-db:5432/sportmarket")

    def test_explicit_jdbc_url_is_preserved(self):
        result = self.adapt(DB_URL="jdbc:postgresql://custom:5432/demo", DATABASE_URL="postgresql://ignored:5432/demo")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout, "jdbc:postgresql://custom:5432/demo")

    def test_invalid_scheme_fails(self):
        result = self.adapt(DATABASE_URL="https://not-a-database.example.test")
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("not-a-database", result.stderr)


if __name__ == "__main__":
    unittest.main()
