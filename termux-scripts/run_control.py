#!/usr/bin/env python3
# Termux 侧运行控制服务：通过 tmux 启停 autoglm，并提供状态查询
import json
import subprocess
from http.server import BaseHTTPRequestHandler, HTTPServer

HOST = "127.0.0.1"
PORT = 18080
SESSION = "autoglm"


def run_cmd(cmd: str) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, shell=True, capture_output=True, text=True)


class Handler(BaseHTTPRequestHandler):
    def _ok(self, obj: dict):
        data = json.dumps(obj).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path == "/agent/start":
            # 在 tmux 中启动 autoglm，若已存在则直接报告 running
            status = run_cmd(f"tmux has-session -t {SESSION}")
            if status.returncode != 0:
                r = run_cmd(f"tmux new -d -s {SESSION} 'autoglm'")
                success = r.returncode == 0
            else:
                success = True
            self._ok({"success": success})
        elif self.path == "/agent/stop":
            r = run_cmd(f"tmux kill-session -t {SESSION}")
            self._ok({"success": r.returncode == 0})
        else:
            self.send_response(404)
            self.end_headers()

    def do_GET(self):
        if self.path == "/agent/status":
            r = run_cmd("tmux ls")
            running = SESSION in r.stdout
            self._ok({"running": running, "raw": r.stdout})
        else:
            self.send_response(404)
            self.end_headers()


def main():
    server = HTTPServer((HOST, PORT), Handler)
    print(f"Run control server on http://{HOST}:{PORT}")
    server.serve_forever()


if __name__ == "__main__":
    main()
