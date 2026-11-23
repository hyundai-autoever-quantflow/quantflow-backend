#!/usr/bin/env python3
"""Run all tests with disposable PostgreSQL/Redis and a local fake Toss server."""
import base64
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
import uuid


def docker(*args):
    return subprocess.check_output(["docker", *args], text=True).strip()


def main():
    repo = Path(__file__).resolve().parent.parent
    names = []
    try:
        suffix = uuid.uuid4().hex[:8]
        pg = "quantflow-toss-test-pg-" + suffix
        redis = "quantflow-toss-test-redis-" + suffix
        docker("run", "--detach", "--rm", "--name", pg, "--tmpfs", "/var/lib/postgresql/data",
               "-e", "POSTGRES_DB=quantflow_toss_test", "-e", "POSTGRES_PASSWORD=isolated_test_only",
               "-p", "127.0.0.1::5432", "postgres:16")
        names.append(pg)
        docker("run", "--detach", "--rm", "--name", redis, "--tmpfs", "/data",
               "-p", "127.0.0.1::6379", "redis:7.4", "redis-server", "--requirepass", "isolated_redis_test_only")
        names.append(redis)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            pg_ready = subprocess.run(["docker", "exec", pg, "pg_isready", "-U", "postgres"], capture_output=True)
            redis_ready = subprocess.run(["docker", "exec", "-e", "REDISCLI_AUTH=isolated_redis_test_only",
                                          redis, "redis-cli", "ping"], capture_output=True)
            if pg_ready.returncode == redis_ready.returncode == 0:
                break
            time.sleep(0.2)
        else:
            raise RuntimeError("Test PostgreSQL/Redis did not become ready within 30s")
        pg_port = docker("port", pg, "5432/tcp").rsplit(":", 1)[-1]
        redis_port = docker("port", redis, "6379/tcp").rsplit(":", 1)[-1]
        env = os.environ.copy()
        env.update({
            "SPRING_CONFIG_IMPORT": "",  # Never import real credentials from .env.
            "DB_URL": "jdbc:postgresql://127.0.0.1:" + pg_port + "/quantflow_toss_test",
            "DB_USERNAME": "postgres", "DB_PASSWORD": "isolated_test_only",
            "REDIS_HOST": "127.0.0.1", "REDIS_PORT": redis_port, "REDIS_PASSWORD": "isolated_redis_test_only",
            "GOOGLE_WEB_CLIENT_ID": "isolated-test-client",
            "JWT_SECRET_BASE64": base64.b64encode(secrets.token_bytes(32)).decode(),
            "BROKER_ENCRYPTION_KEY_BASE64": base64.b64encode(secrets.token_bytes(32)).decode(),
            "RUN_TOSS_INTEGRATION_TESTS": "true",
            "OPENAI_API_KEY": "sk-isolated-test-not-real",
            "OPENAI_MODEL": "gpt-6-astra",
            "NAVER_CLIENT_ID": "isolated-test-naver-id",
            "NAVER_CLIENT_SECRET": "isolated-test-naver-secret",
        })
        wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
        return subprocess.call([wrapper, "test", "--rerun-tasks", *sys.argv[1:]], cwd=repo, env=env)
    finally:
        for name in reversed(names):
            subprocess.run(["docker", "stop", name], stdout=subprocess.DEVNULL, check=False)
        print("Removed isolated test containers.", flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
