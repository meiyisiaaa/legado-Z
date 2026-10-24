#!/usr/bin/env python3
"""Public generated RSS fixtures: byte ranges, slow transfer, redirect and offline control.

Run on the host; emulator tests use http://10.0.2.2:8765. No packages required.
GET /__control?case=NAME&offline=0&drop=1&reset=1 arms one interrupted transfer.
After the interruption the case stays offline (503) until offline=0 is requested.
"""

import argparse
import http.client
import http.server
import json
import mimetypes
import pathlib
import re
import socket
import threading
import time
import urllib.error
import urllib.parse
import urllib.request


def byte_range(value, size):
    if not value:
        return 0, size - 1
    match = re.fullmatch(r"bytes=(\d*)-(\d*)", value)
    if not match or not any(match.groups()):
        raise ValueError("invalid range")
    first, last = match.groups()
    start = int(first) if first else max(0, size - int(last))
    end = min(int(last), size - 1) if first and last else size - 1
    if start > end or start >= size:
        raise ValueError("unsatisfiable range")
    return start, end


class FixtureServer(http.server.ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, root):
        self.root = root.resolve()
        self.cases = {}
        self.lock = threading.Lock()
        super().__init__(address, FixtureHandler)

    def state(self, name):
        return self.cases.setdefault(name, dict(offline=False, drop=False, drops=0,
            requests=0, redirects=0, nonzero_ranges=0, bytes=0))


class FixtureHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_HEAD(self):
        self.do_GET()

    def reply(self, status, content, content_type="application/json", **headers):
        data = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        for key, value in headers.items():
            self.send_header(key, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(data)

    def do_GET(self):
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query)
        case = query.get("case", ["default"])[0]
        path = urllib.parse.unquote(parsed.path)
        with self.server.lock:
            if path == "/__control" and query.get("reset") == ["1"]:
                self.server.cases.pop(case, None)
            state = self.server.state(case)
            if path in ("/__control", "/__stats"):
                if path == "/__control":
                    for key in ("offline", "drop"):
                        if key in query:
                            state[key] = query[key] == ["1"]
                snapshot = dict(state)
            else:
                snapshot = None
                state["requests"] += 1
                offline = state["offline"]
        if snapshot is not None:
            return self.reply(200, json.dumps(snapshot))
        if offline:
            return self.reply(503, "controlled offline", "text/plain")
        if path.startswith("/redirect/"):
            with self.server.lock:
                state["redirects"] += 1
            location = "/slow/" + path.removeprefix("/redirect/")
            if parsed.query:
                location += "?" + parsed.query
            return self.reply(302, "", Location=location)
        slow = path.startswith(("/slow/", "/flaky/"))
        relative = re.sub(r"^/(?:slow|flaky)/", "/", path).lstrip("/")
        target = (self.server.root / relative).resolve()
        if not target.is_relative_to(self.server.root) or not target.is_file():
            return self.reply(404, "fixture missing", "text/plain")
        size = target.stat().st_size
        try:
            start, end = byte_range(self.headers.get("Range"), size)
        except ValueError:
            return self.reply(416, "invalid range", "text/plain", **{"Content-Range": f"bytes */{size}"})
        length = end - start + 1
        with self.server.lock:
            if start > 0:
                state["nonzero_ranges"] += 1
            # Skip the production 1 KiB MIME probe; interrupt the actual download.
            drop = state["drop"] and length > 4096 and self.command != "HEAD"
            if drop:
                state["drop"] = False
        self.send_response(206 if self.headers.get("Range") else 200)
        self.send_header("Content-Type", mimetypes.guess_type(target.name)[0] or "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(length))
        self.send_header("Cache-Control", "no-store")
        if self.headers.get("Range"):
            self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        if self.command == "HEAD":
            return
        delay = min(250, max(0, int(query.get("delay_ms", ["50" if slow else "0"])[0]))) / 1000
        sent = 0
        try:
            with target.open("rb") as source:
                source.seek(start)
                while sent < length:
                    block = source.read(min(1024 if slow else 65536, length - sent))
                    if not block:
                        break
                    self.wfile.write(block)
                    self.wfile.flush()
                    sent += len(block)
                    with self.server.lock:
                        state["bytes"] += len(block)
                    if drop and sent >= 32768:
                        with self.server.lock:
                            state["drops"] += 1
                            state["offline"] = True
                        self.close_connection = True
                        self.connection.shutdown(socket.SHUT_RDWR)
                        return
                    if delay:
                        time.sleep(delay)
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass  # Clients intentionally stop MIME probes and paused downloads.

    def log_message(self, format, *args):
        print(format % args, flush=True)


def self_test(root):
    assert byte_range("bytes=4-9", 20) == (4, 9)
    assert byte_range("bytes=-3", 20) == (17, 19)
    server = FixtureServer(("127.0.0.1", 0), root)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    base = f"http://127.0.0.1:{server.server_port}"
    try:
        request = urllib.request.Request(base + "/sample.mp4", headers={"Range": "bytes=4-35"})
        with urllib.request.urlopen(request) as response:
            assert response.status == 206 and len(response.read()) == 32
        with urllib.request.urlopen(base + "/redirect/sample.mp4?case=self-test&delay_ms=0") as response:
            assert response.read() == (root / "sample.mp4").read_bytes()
        urllib.request.urlopen(base + "/__control?case=self-test&offline=1").close()
        try:
            urllib.request.urlopen(base + "/sample.mp4?case=self-test")
            raise AssertionError("offline case returned success")
        except urllib.error.HTTPError as error:
            assert error.code == 503
        urllib.request.urlopen(base + "/__control?case=self-test&offline=0").close()
        with urllib.request.urlopen(base + "/__stats?case=self-test") as response:
            assert json.load(response)["redirects"] == 1
        urllib.request.urlopen(base + "/__control?case=self-test&drop=1").close()
        try:
            with urllib.request.urlopen(base + "/flaky/sample.mp4?case=self-test&delay_ms=0") as response:
                response.read()
            raise AssertionError("armed transfer was not interrupted")
        except http.client.IncompleteRead:
            pass
        with urllib.request.urlopen(base + "/__stats?case=self-test") as response:
            state = json.load(response)
            assert state["drops"] == 1 and state["offline"]
        urllib.request.urlopen(base + "/__control?case=self-test&offline=0").close()
        request = urllib.request.Request(base + "/sample.mp4?case=self-test", headers={"Range": "bytes=32768-"})
        with urllib.request.urlopen(request) as response:
            assert response.read() == (root / "sample.mp4").read_bytes()[32768:]
        print("Fixture self-test passed: range, redirect, offline, interrupted transfer and recovery", flush=True)
    finally:
        server.shutdown()
        server.server_close()
        worker.join()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--root", type=pathlib.Path,
        default=pathlib.Path(__file__).resolve().parents[2] / "app/src/androidTest/assets/rss-media-fixture")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test(args.root)
    else:
        with FixtureServer((args.bind, args.port), args.root) as server:
            print(f"Serving public generated fixtures at http://{args.bind}:{args.port}", flush=True)
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                pass
