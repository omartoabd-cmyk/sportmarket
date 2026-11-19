"""Run with python -m unittest discover -s scripts -p test_*.py."""
import importlib.util
from pathlib import Path
import struct
import unittest
from unittest.mock import patch
import zlib

spec = importlib.util.spec_from_file_location("seed_demo", Path(__file__).with_name("seed-demo.py"))
demo = importlib.util.module_from_spec(spec)
spec.loader.exec_module(demo)


class DemoTests(unittest.TestCase):
    def test_illustrations_are_valid_small_pngs(self):
        for category in ("Soccer", "Basketball", "Tennis", "Fitness", "Hockey", "Baseball", "Other"):
            image = demo.illustration(category)
            self.assertTrue(image.startswith(b"\x89PNG\r\n\x1a\n"))
            self.assertLess(len(image), 5 * 1024 * 1024)
            cursor, compressed = 8, bytearray()
            while cursor < len(image):
                length = struct.unpack(">I", image[cursor:cursor + 4])[0]
                kind = image[cursor + 4:cursor + 8]
                data = image[cursor + 8:cursor + 8 + length]
                crc = struct.unpack(">I", image[cursor + 8 + length:cursor + 12 + length])[0]
                self.assertEqual(crc, zlib.crc32(kind + data))
                if kind == b"IDAT":
                    compressed.extend(data)
                cursor += length + 12
            self.assertEqual(len(zlib.decompress(compressed)), 480 * (640 * 3 + 1))

    def test_nonempty_target_is_not_modified(self):
        with patch.object(demo.urllib.request, "urlopen") as fetch:
            response = fetch.return_value.__enter__.return_value
            response.read.return_value = b'{"totalElements": 3}'
            with self.assertRaisesRegex(RuntimeError, "Refusing"):
                demo.seed("http://localhost:8082")
            self.assertEqual(fetch.call_count, 1)
            self.assertEqual(fetch.call_args.args[0].get_method(), "GET")


if __name__ == "__main__":
    unittest.main()
