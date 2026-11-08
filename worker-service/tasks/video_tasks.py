import os
import subprocess
import time

import boto3
import ray
import json  # ✅新增：写输出 json
import socket
import urllib.request
import urllib.error

UPLOAD_DIR = "/data/uploads"
PROCESSED_DIR = "/data/processed"
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(PROCESSED_DIR, exist_ok=True)


def _env_bool(name: str, default: str = "false") -> bool:
    return os.getenv(name, default).lower() == "true"


def _get_s3_cfg():
    """Read S3-related config lazily (inside worker), to keep Ray actor serialization safe
    and to avoid relying on module import order.
    """
    enabled = _env_bool("ENABLE_S3_UPLOAD", "false")
    if not enabled:
        return {"enabled": False}

    bucket = os.getenv("AWS_S3_BUCKET")
    if not bucket:
        # ✅ Fail fast with an actionable error (prevents silent 'upload skipped' behavior)
        raise RuntimeError("ENABLE_S3_UPLOAD=true but AWS_S3_BUCKET is empty")

    region = os.getenv("AWS_REGION", "us-east-1")
    prefix = (os.getenv("AWS_S3_PREFIX", "processed") or "processed").strip("/")

    upload_extra = _env_bool("UPLOAD_EXTRA_PROFILES", "true")

    return {
        "enabled": True,
        "bucket": bucket,
        "region": region,
        "prefix": prefix,
        "upload_extra": upload_extra,
    }


def upload_with_verify(s3, bucket: str, key: str, filename: str, retries: int = 2) -> None:
    """Deterministic S3 upload with verify + bounded retry."""
    last_err = None
    for attempt in range(retries + 1):
        try:
            s3.upload_file(filename, bucket, key)
            s3.head_object(Bucket=bucket, Key=key)  # strong existence check
            return
        except Exception as e:
            last_err = e
            time.sleep(1 + attempt)

    raise RuntimeError(
        f"S3 upload/verify failed after retries: bucket={bucket} key={key} err={last_err!r}"
    )

# ✅ [GENAI_FIX] keep it tiny + deterministic
def assert_file_ready(path: str, stage: str, min_bytes: int = 10) -> None:
    if not os.path.exists(path):
        raise RuntimeError(f"{stage}: output missing: {path}")
    sz = os.path.getsize(path)
    if sz < min_bytes:
        raise RuntimeError(f"{stage}: output too small ({sz} bytes): {path}")


def log_event(event: str, **fields) -> None:
    """Structured log line (JSON) to stdout for kubectl logs."""
    rec = {
        "ts": time.time(),
        "event": event,
        "hostname": socket.gethostname(),
        **fields,
    }
    print(json.dumps(rec, ensure_ascii=False), flush=True)

def write_manifest_atomic(path: str, payload: dict) -> None:
    """Write JSON manifest atomically (tmp -> os.replace)."""
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False)
    os.replace(tmp, path)

#
# ====== Multi-profile transcoding (MINIMAL) ======
# MAIN_PROFILE 决定 output_path 这个“主文件”的分辨率（必须生成，用于你现有闭环 done 判断）
MAIN_PROFILE = os.getenv("MAIN_PROFILE", "480p")

# VIDEO_PROFILES 决定额外要生成哪些档位（逗号分隔）
VIDEO_PROFILES = [p.strip() for p in os.getenv("VIDEO_PROFILES", "480p").split(",") if p.strip()]

# 每个 profile 对应的 ffmpeg scale
PROFILE_VF = {
    "360p": "scale=640:360",
    "480p": "scale=640:480",  # ✅ 和你现有逻辑保持一致
    "720p": "scale=1280:720",
}

# ✅ [STABILITY] ffmpeg/x264 默认会开很多线程（你日志里看到 threads=12），会造成 CPU/内存峰值过高，
# 进而触发 cgroup OOM killer（你已证实 oom_kill>0），导致 ffmpeg 被 SIGKILL -> rc=-9。
# 这里用环境变量把线程数和 preset “限住”，保证稳定；需要性能时再逐步放开。
FFMPEG_THREADS = os.getenv("FFMPEG_THREADS", "1")      # 建议先 1；稳定后可尝试 2
FFMPEG_PRESET = os.getenv("FFMPEG_PRESET", "ultrafast")  # 建议先 ultrafast；稳定后可改 veryfast/fast
# ================================================

LLM_MAX_RETRIES = int(os.getenv("LLM_MAX_RETRIES", "2"))  # ✅ 默认重试 2 次
LLM_RETRY_BACKOFF_SEC = float(os.getenv("LLM_RETRY_BACKOFF_SEC", "0.2"))  # ✅ 基础 backoff

# ====== [GENAI_STEP3] Provider gateway (minimal) ======
LLM_PROVIDER = os.getenv("LLM_PROVIDER", "mock")  # mock | http_echo
LLM_TIMEOUT_MS = int(os.getenv("LLM_TIMEOUT_MS", "1500"))  # provider 调用超时
LLM_HTTP_URL = os.getenv("LLM_HTTP_URL", "")  # provider=http_echo 时必填
# ==============================================

def _llm_call(provider: str, prompt: str, request_id: str, timeout_ms: int) -> dict:
    """
    Minimal provider gateway:
    - mock: 本地返回
    - http_echo: POST JSON 到 LLM_HTTP_URL（后续可替换为 LiteLLM/Bedrock/Vertex）
    Return: {"text": "..."}  (统一输出格式)
    """
    provider = (provider or "mock").strip()

    if provider == "mock":
        return {"text": f"[mock] {prompt}"}

    if provider == "http_echo":
        if not LLM_HTTP_URL:
            raise RuntimeError("provider=http_echo but LLM_HTTP_URL is empty")

        body = json.dumps(
            {"prompt": prompt, "request_id": request_id},
            ensure_ascii=False
        ).encode("utf-8")

        req = urllib.request.Request(
            LLM_HTTP_URL,
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        # urllib timeout 是秒
        timeout_sec = max(0.001, timeout_ms / 1000.0)
        with urllib.request.urlopen(req, timeout=timeout_sec) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            # 约定 provider 返回 {"text": "..."}，否则 fallback 用 raw
            try:
                d = json.loads(raw)
                if isinstance(d, dict) and "text" in d:
                    return {"text": str(d["text"])}
            except Exception:
                pass
            return {"text": raw}

    raise RuntimeError(f"unknown provider: {provider}")

def _ffmpeg_cmd(input_path: str, vf: str, out_path: str) -> list[str]:
    """Build a stable ffmpeg command with bounded resource usage."""
    return [
        "ffmpeg",
        "-y",
        "-nostdin",  # ✅ 避免某些环境下 ffmpeg 等待 stdin 导致卡住
        "-i",
        input_path,
        "-vf",
        vf,
        # ✅ [STABILITY] 显式使用 libx264 + preset + threads，避免默认线程数过高
        "-c:v",
        "libx264",
        "-preset",
        str(FFMPEG_PRESET),
        "-threads",
        str(FFMPEG_THREADS),
        # 音频这里保持 re-encode（对不同输入更稳）。如果你确认永远是 AAC，可改为 -c:a copy 降低 CPU。
        "-c:a",
        "aac",
        out_path,
    ]


@ray.remote
class VideoWorker:

    def process(self, input_path: str, output_path: str, request_id: str) -> str:
        # Minimal observability: per-job manifest + structured logs (stdout)
        t0 = time.time()
        video_id = os.path.splitext(os.path.basename(output_path))[0]
        manifest_path = os.path.join(PROCESSED_DIR, f"{video_id}_manifest.json")

        payload = {
            "video_id": video_id,
            "request_id": request_id,
            "status": "processing",
            "started_ts": t0,
            "finished_ts": None,
            "latency_ms": None,
            "hostname": socket.gethostname(),
            "module_file": __file__,
            "main_profile": MAIN_PROFILE,
            "video_profiles": VIDEO_PROFILES,
            "outputs": [],          # [{"profile": "480p", "path": "...", "size_bytes": 123}]
            "s3_uploads": [],       # [{"key": "...", "ok": True}]
            "errors": [],           # [repr(e), ...]
        }

        # write initial manifest early (helps post-mortem if pod dies mid-job)
        write_manifest_atomic(manifest_path, payload)
        log_event("video_job_start", video_id=video_id, request_id=request_id, input_path=input_path, output_path=output_path)

        try:
            # 1) Generate main output (must exist; preserves your current done semantics)
            vf_main = PROFILE_VF.get(MAIN_PROFILE, PROFILE_VF["480p"])
            cmd_main = _ffmpeg_cmd(input_path=input_path, vf=vf_main, out_path=output_path)
            subprocess.run(cmd_main, check=True)
            assert_file_ready(output_path, stage="ffmpeg_main", min_bytes=1024)

            payload["outputs"].append({
                "profile": MAIN_PROFILE,
                "path": output_path,
                "size_bytes": os.path.getsize(output_path),
            })
            write_manifest_atomic(manifest_path, payload)
            log_event("ffmpeg_main_done", video_id=video_id, request_id=request_id, path=output_path, profile=MAIN_PROFILE)

            # 2) Extra profiles: {vid}_{profile}.mp4
            base, ext = os.path.splitext(output_path)
            extra_outputs: list[str] = []
            for p in VIDEO_PROFILES:
                if p == MAIN_PROFILE:
                    continue
                vf = PROFILE_VF.get(p)
                if not vf:
                    log_event("ffmpeg_profile_skip_unknown", video_id=video_id, request_id=request_id, profile=p)
                    continue

                out_p = f"{base}_{p}{ext}"
                cmd_extra = _ffmpeg_cmd(input_path=input_path, vf=vf, out_path=out_p)
                subprocess.run(cmd_extra, check=True)
                assert_file_ready(out_p, stage=f"ffmpeg_{p}", min_bytes=1024)

                extra_outputs.append(out_p)
                payload["outputs"].append({
                    "profile": p,
                    "path": out_p,
                    "size_bytes": os.path.getsize(out_p),
                })
                write_manifest_atomic(manifest_path, payload)
                log_event("ffmpeg_extra_done", video_id=video_id, request_id=request_id, path=out_p, profile=p)

            # 3) S3 upload (optional): upload main; extra based on flag
            cfg = _get_s3_cfg()
            if cfg.get("enabled"):
                s3 = boto3.client("s3", region_name=cfg["region"])

                key_main = f"{cfg['prefix']}/{os.path.basename(output_path)}"
                log_event("s3_upload_start", video_id=video_id, request_id=request_id, key=key_main)
                upload_with_verify(s3, cfg["bucket"], key_main, output_path, retries=2)
                payload["s3_uploads"].append({"key": key_main, "ok": True})
                write_manifest_atomic(manifest_path, payload)
                log_event("s3_upload_done", video_id=video_id, request_id=request_id, key=key_main)

                if cfg.get("upload_extra"):
                    for out_p in extra_outputs:
                        k = f"{cfg['prefix']}/{os.path.basename(out_p)}"
                        log_event("s3_upload_start", video_id=video_id, request_id=request_id, key=k)
                        upload_with_verify(s3, cfg["bucket"], k, out_p, retries=2)
                        payload["s3_uploads"].append({"key": k, "ok": True})
                        write_manifest_atomic(manifest_path, payload)
                        log_event("s3_upload_done", video_id=video_id, request_id=request_id, key=k)

            payload["status"] = "done"
            payload["finished_ts"] = time.time()
            payload["latency_ms"] = int((payload["finished_ts"] - t0) * 1000)
            write_manifest_atomic(manifest_path, payload)
            log_event("video_job_done", video_id=video_id, request_id=request_id, latency_ms=payload["latency_ms"])

            return output_path

        except Exception as e:
            payload["status"] = "failed"
            payload["finished_ts"] = time.time()
            payload["latency_ms"] = int((payload["finished_ts"] - t0) * 1000)
            payload["errors"].append(repr(e))
            write_manifest_atomic(manifest_path, payload)
            log_event("video_job_failed", video_id=video_id, request_id=request_id, error=repr(e), latency_ms=payload["latency_ms"])
            raise
    def ping(self) -> str:
        # Step 5: minimal liveness probe for actor execution
        return "pong"

    # ===========================
    # [GENAI_STEP1] LLM job (mock) - minimal, deterministic, debuggable
    # Rationale:
    # - Gives you a GenAI "platform" lane without adding external dependencies.
    # - Reuses your existing S3 upload+verify for production-like reliability.
    # - Output is a JSON artifact -> easy to audit, replay, and troubleshoot.
    # ===========================

    # ✅ [GENAI_STEP2] add request_id + artifact schema + latency + retry
    def llm_run(self, task_id: str, prompt: str, request_id: str) -> str:
        """
        Produce /data/processed/<task_id>_llm.json
        Optionally upload to S3 if ENABLE_S3_UPLOAD=true
        """
        t0 = time.time()

        # ✅ _get_s3_cfg() returns dict
        cfg = _get_s3_cfg()

        out_path = os.path.join(PROCESSED_DIR, f"{task_id}_llm.json")
        payload = {
            "task_id": task_id,
            "request_id": request_id,
            "backend": "mock",
            "prompt": prompt,
            "status": "processing",
            "result": None,
            "error": None,
            "ts": t0,  # ✅ use start time
            "hostname": socket.gethostname(),
            "module_file": __file__,
            "attempts": 0,
            "errors": [],
            "provider": LLM_PROVIDER,          # ✅ new
            "timeout_ms": LLM_TIMEOUT_MS,      # ✅ new
            "error_type": None,                # ✅ new: timeout|upstream_4xx|upstream_5xx|internal
            "upstream_status_code": None,      # ✅ [NEW] 记录上游 HTTP code（尤其 429/5xx）
            "token_usage": {"prompt": 0, "completion": 0, "total": 0},  # ✅ placeholder
        }

        errors = []
        attempt = 0

        # total tries = max_retries + 1
        for attempt in range(1, LLM_MAX_RETRIES + 2):
            try:
                # ✅ 保留你的 fail_once 测试钩子（只用于验证重试）
                if "fail_once" in (prompt or "") and attempt == 1:
                    raise RuntimeError("simulated transient error (fail_once)")

                # ✅ [GENAI_STEP3] provider gateway call
                out = _llm_call(
                    provider=LLM_PROVIDER,
                    prompt=prompt,
                    request_id=request_id,
                    timeout_ms=LLM_TIMEOUT_MS,
                )
                payload["result"] = out
                payload["status"] = "done"
                payload["error_type"] = None
                break

            except Exception as e:
                err = repr(e)
                errors.append(err)
                payload["status"] = "processing"

                # ✅ [NEW] 默认：内部错误（保守）
                et = "internal"
                upstream_code = None

                # ✅ [NEW] 1) timeout 识别：优先类型，其次 URLError.reason，其次字符串兜底
                try:
                    import socket as _sock
                    if isinstance(e, _sock.timeout):
                        et = "timeout"
                except Exception:
                    pass

                if isinstance(e, urllib.error.URLError):
                    # URLError 经常把 socket.timeout 放在 reason 里
                    r = getattr(e, "reason", None)
                    try:
                        import socket as _sock
                        if isinstance(r, _sock.timeout):
                            et = "timeout"
                    except Exception:
                        pass
                    # 字符串兜底（避免漏判）
                    if "timed out" in str(e).lower():
                        et = "timeout"

                # ✅ [NEW] 2) HTTPError：记录 code，并分类 4xx/5xx
                if isinstance(e, urllib.error.HTTPError):
                    upstream_code = int(getattr(e, "code", 0) or 0)
                    if 400 <= upstream_code < 500:
                        et = "upstream_4xx"
                    elif upstream_code >= 500:
                        et = "upstream_5xx"

                payload["error_type"] = et
                payload["upstream_status_code"] = upstream_code

                # ✅ [NEW] 3) 最小重试 gate：除了 429，其它 4xx 直接停止重试（平台工程关键语义）
                no_retry = (et == "upstream_4xx" and upstream_code not in (429,))
                if no_retry:
                    # 直接结束循环：attempts/errors 会反映“失败原因 + 没有盲目重试”
                    break

                # ✅ 仍沿用你现有 backoff（小步，不改策略）
                time.sleep(min(1.0, LLM_RETRY_BACKOFF_SEC * attempt))

        payload["attempts"] = attempt
        payload["errors"] = errors

        if payload["status"] != "done":
            payload["status"] = "failed"
            payload["error"] = errors[-1] if errors else "unknown"

        payload["latency_ms"] = int((time.time() - t0) * 1000)

        # ✅ write artifact (fix indentation)
        with open(out_path, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False)

        assert_file_ready(out_path, stage="llm_run", min_bytes=10)

        # ✅ upload artifact to S3 (still using cfg, no top-level S3 globals)
        if cfg.get("enabled"):
            s3 = boto3.client("s3", region_name=cfg["region"])
            key = f"{cfg['prefix']}/{os.path.basename(out_path)}"
            print(f"[s3] uploading llm artifact: {key}", flush=True)
            upload_with_verify(s3, cfg["bucket"], key, out_path, retries=2)

        # ✅ fail task if failed (API status should show failed)
        if payload["status"] != "done":
            raise RuntimeError(f"llm_run failed: {payload.get('error')}")

        return out_path


    def info(self):
        # Helpful for debugging via /healthz; keep it simple.
        cfg = {"enabled": False}
        try:
            cfg = _get_s3_cfg()
        except Exception as e:
            cfg = {"enabled": False, "cfg_error": repr(e)}

        return {
            "hostname": os.uname().nodename,
            "main_profile": MAIN_PROFILE,
            "video_profiles": VIDEO_PROFILES,
            "enable_s3_upload": bool(cfg.get("enabled")),
            "upload_extra_profiles": bool(cfg.get("upload_extra", False)),
            "ffmpeg_threads": FFMPEG_THREADS,
            "ffmpeg_preset": FFMPEG_PRESET,
            "s3_prefix": cfg.get("prefix"),
            "aws_region": cfg.get("region"),
            "s3_bucket_set": bool(cfg.get("bucket")),
            "module_file": __file__,
        }
