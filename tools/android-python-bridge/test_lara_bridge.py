import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from lara_bridge import load_or_create_token, make_server

class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.home = Path(self.temp.name)
        self.token, created = load_or_create_token(self.home)
        self.assertTrue(created)
        self.server = make_server(0, self.token)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        self.temp.cleanup()

    def request(self, path, method="GET", payload=None, token=None):
        data = None if payload is None else json.dumps(payload).encode()
        headers = {}
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        if data is not None:
            headers["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=2) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read())

    def test_health_requires_token_and_returns_version(self):
        self.assertEqual(self.request("/v1/health")[0], 401)
        status, body = self.request("/v1/health", token=self.token)
        self.assertEqual(status, 200)
        self.assertTrue(body["ok"])
        self.assertEqual(body["service"], "lara-python-bridge")

    def test_echo_task_round_trip(self):
        status, body = self.request("/v1/tasks/echo", "POST",
            {"task": "echo", "text": "Automate erreicht Python"}, self.token)
        self.assertEqual(status, 200)
        self.assertEqual(body["result"], "Automate erreicht Python")

    def test_unknown_task_is_rejected(self):
        status, body = self.request("/v1/tasks/echo", "POST",
            {"task": "run_python", "text": "print(1)"}, self.token)
        self.assertEqual(status, 400)
        self.assertEqual(body["error"], "unsupported_task")

    def test_unknown_path_is_rejected(self):
        status, body = self.request("/v1/admin", token=self.token)
        self.assertEqual(status, 404)
        self.assertEqual(body["error"], "not_found")

    def test_server_binds_to_loopback_only(self):
        self.assertEqual(self.server.server_address[0], "127.0.0.1")

    def test_token_is_persisted(self):
        token2, created2 = load_or_create_token(self.home)
        self.assertFalse(created2)
        self.assertEqual(token2, self.token)

if __name__ == "__main__":
    unittest.main(verbosity=2)
