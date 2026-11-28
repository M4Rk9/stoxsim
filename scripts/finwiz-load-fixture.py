#!/usr/bin/env python3
"""Local CI fixture with fixed latency and no provider credentials or network calls."""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200 if self.path == "/healthz" else 404)
        self.end_headers()

    def do_POST(self):
        size = int(self.headers.get("Content-Length", 0))
        if self.path != "/v1beta/models/ci-finwiz-fixture:generateContent" or not 0 < size <= 65536:
            self.send_error(400)
            return
        self.rfile.read(size)
        time.sleep(0.15)
        answer = json.dumps({
            "candidates": [{"finishReason": "STOP", "content": {"parts": [
                {"text": "Cash flow tracks money entering and leaving the business."},
            ]}}],
            "usageMetadata": {"totalTokenCount": 128},
        }).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(answer)))
        self.end_headers()
        self.wfile.write(answer)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer.request_queue_size = 32
    with ThreadingHTTPServer(("127.0.0.1", 18081), Handler) as server:
        server.serve_forever()
