#!/usr/bin/env python3
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def required(name):
    value = os.environ.get(name, "")
    if not value:
        raise RuntimeError(f"required environment variable {name} is missing")
    return value


AUTH_TOKEN = required("PROVIDER_AUTH_TOKEN")
MODEL = required("PROVIDER_MODEL")
DELAY_SECONDS = int(required("PROVIDER_DELAY_MS")) / 1000
INPUT_TOKENS = int(required("PROVIDER_INPUT_TOKENS"))
OUTPUT_TOKENS = int(required("PROVIDER_OUTPUT_TOKENS"))
MAX_OUTPUT_TOKENS = int(required("PROVIDER_MAX_OUTPUT_TOKENS"))
ANSWER_PARTS = ("Use a small ", "boundary example.")
MAX_REQUEST_BYTES = 131072

lock = threading.Lock()
stats = {
    "requests": 0,
    "completed": 0,
    "rejected": 0,
    "failed": 0,
    "active": 0,
    "maxConcurrent": 0,
}


def snapshot():
    with lock:
        return dict(stats)


def reject():
    with lock:
        stats["rejected"] += 1


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, _format, *_args):
        return

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "UP"})
            return
        if self.path == "/stats":
            if not self.authorized():
                reject()
                self.send_json(401, {"error": "unauthorized"})
                return
            self.send_json(200, snapshot())
            return
        self.send_json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/v1/responses":
            reject()
            self.send_json(404, {"error": "not_found"})
            return
        if not self.authorized():
            reject()
            self.send_json(401, {"error": "unauthorized"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > MAX_REQUEST_BYTES:
                raise ValueError("invalid request length")
            payload = json.loads(self.rfile.read(length))
            self.validate_payload(payload)
        except (ValueError, TypeError, json.JSONDecodeError):
            reject()
            self.send_json(400, {"error": "invalid_request"})
            return

        with lock:
            stats["requests"] += 1
            stats["active"] += 1
            stats["maxConcurrent"] = max(stats["maxConcurrent"], stats["active"])

        try:
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "close")
            self.end_headers()
            time.sleep(DELAY_SECONDS)
            for part in ANSWER_PARTS:
                event = {"type": "response.output_text.delta", "delta": part}
                self.write_event(event)
            completed = {
                "type": "response.completed",
                "response": {
                    "model": MODEL,
                    "usage": {
                        "input_tokens": INPUT_TOKENS,
                        "output_tokens": OUTPUT_TOKENS,
                    },
                },
            }
            self.write_event(completed)
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            with lock:
                stats["completed"] += 1
        except (BrokenPipeError, ConnectionResetError):
            with lock:
                stats["failed"] += 1
        finally:
            with lock:
                stats["active"] -= 1
            self.close_connection = True

    def authorized(self):
        return self.headers.get("Authorization") == f"Bearer {AUTH_TOKEN}"

    def validate_payload(self, payload):
        if not isinstance(payload, dict):
            raise ValueError("payload must be an object")
        if payload.get("model") != MODEL:
            raise ValueError("unexpected model")
        if payload.get("store") is not False or payload.get("stream") is not True:
            raise ValueError("unsafe response options")
        if payload.get("max_output_tokens") != MAX_OUTPUT_TOKENS:
            raise ValueError("unexpected output bound")
        if not isinstance(payload.get("instructions"), str) or not payload["instructions"].strip():
            raise ValueError("instructions are missing")
        inputs = payload.get("input")
        if not isinstance(inputs, list) or len(inputs) < 2:
            raise ValueError("bounded context and question are required")
        for item in inputs:
            if not isinstance(item, dict):
                raise ValueError("input item must be an object")
            if item.get("role") not in ("user", "assistant"):
                raise ValueError("unexpected input role")
            if not isinstance(item.get("content"), str) or not item["content"].strip():
                raise ValueError("input content is missing")

    def write_event(self, event):
        body = json.dumps(event, separators=(",", ":")).encode("utf-8")
        self.wfile.write(b"data: " + body + b"\n\n")
        self.wfile.flush()

    def send_json(self, status, payload):
        body = json.dumps(payload, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True


server = ThreadingHTTPServer(("0.0.0.0", 8080), Handler)
server.daemon_threads = True
server.serve_forever()
