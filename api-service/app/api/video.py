import os
import uuid
from fastapi import APIRouter, UploadFile, File, HTTPException
import ray
import threading
import time
from typing import Dict
import hashlib
import json

_job_lock = threading.Lock()
_job_refs: Dict[str, "ray.ObjectRef"] = {}
_job_errors: Dict[str, str] = {}
_job_done_ts: Dict[str, float] = {}

_ai_lock = threading.Lock()
_ai_refs: Dict[str, "ray.ObjectRef"] = {}
_ai_errors: Dict[str, str] = {}

# ✅ GenAI: dedupe cache (in-memory, TTL)
_ai_dedupe: Dict[str, Dict[str, object]] = {}  # key -> {"task_id":..., "request_id":..., "ts":...}


GENAI_INFLIGHT_STUCK_SEC = int(os.getenv("GENAI_INFLIGHT_STUCK_SEC", "120"))

GENAI_DEDUPE_TTL_SEC = int(os.getenv("GENAI_DEDUPE_TTL_SEC", "5"))  # 10 min default, 5 sec for test
GENAI_SCHEMA_VERSION = os.getenv("GENAI_SCHEMA_VERSION", "mock_v1")   # key 的“版本盐”

GENAI_MAX_INFLIGHT = int(os.getenv("GENAI_MAX_INFLIGHT", "1"))  # 默认 2，和你 worker replicas=2 对齐， 1 for test

GENAI_RETRY_AFTER_MS = int(os.getenv("GENAI_RETRY_AFTER_MS", "500"))  # ✅ 新增：429 建议重试间隔

router = APIRouter()

UPLOAD_DIR = "/data/uploads"
PROCESSED_DIR = "/data/processed"
os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(PROCESSED_DIR, exist_ok=True)

RAY_NAMESPACE = os.getenv("RAY_NAMESPACE", "video")
# ✅ API Pod（独立服务）固定走 Ray Client
RAY_ADDRESS = os.getenv("RAY_ADDRESS", "ray://ray-head:10001")

_ray_lock = threading.Lock()
_ray_inited = False

def _norm_prompt(prompt: str) -> str:
    return " ".join((prompt or "").strip().split())

def _genai_dedupe_key(prompt: str) -> str:
    payload = {
        "schema": GENAI_SCHEMA_VERSION,
        "backend": "mock",              # 未来你可以换成 model_name/version
        "prompt": _norm_prompt(prompt),
        # "params": {...}               # 未来加 temperature/top_p 等
    }
    raw = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()

def _purge_ai_dedupe(now: float) -> None:
    # ✅简单清理：只删过期 key，不动 _ai_refs（避免误伤正在跑的 task）
    expired = []
    for k, rec in _ai_dedupe.items():
        ts = float(rec.get("ts", 0.0))
        if now - ts > GENAI_DEDUPE_TTL_SEC:
            expired.append(k)
    for k in expired:
        _ai_dedupe.pop(k, None)

def _count_inflight_ai_locked() -> int:
    # 必须在 _ai_lock 内调用
    n = 0
    for task_id, ref in list(_ai_refs.items()):
        try:
            ready, _ = ray.wait([ref], timeout=0)
            if ready:
                # 任务已完成：可以清理，避免内存无限增长
                _ai_refs.pop(task_id, None)
                continue
            n += 1
        except Exception:
            # ref 已坏/连接异常：也清理，避免卡死在 inflight
            _ai_refs.pop(task_id, None)
    return n

def _active_task_ids_locked(limit: int = 5) -> list[str]:
    """
    ✅ 只做内存快照：返回部分 task_id，供 429 响应提示调用方继续轮询。
    注意：必须在 _ai_lock 内调用；不要在这里做 ray.wait/ray.get，避免锁内阻塞。
    """
    return list(_ai_refs.keys())[:max(0, limit)]

def ensure_ray_connected() -> None:
    """
    Step 1: make Ray Client connection 'truthful'.
    If ray looks initialized, still probe a lightweight RPC.
    If probe fails, shutdown and reconnect once.
    """
    global _ray_inited
    with _ray_lock:
        # Case A: we think it's initialized -> do a lightweight RPC probe
        if _ray_inited and ray.is_initialized():
            try:
                _ = ray.cluster_resources() # lightweight RPC; validates session
                return
            except Exception:
                # session may be stale / cleaned up server-side
                try:
                    ray.shutdown()
                except Exception:
                    pass
                _ray_inited = False
        # Case B: not initialized (or probe failed) -> init
        # 2) 正常 init（必要时重建连接）
        # ✅ [FIX] init 阶段兜底 allow_multiple “脏状态”
        try:
            ray.init(
                address=RAY_ADDRESS,
                namespace=RAY_NAMESPACE,
                ignore_reinit_error=True,
            )
        except ValueError as e:
            msg = str(e)
            if "allow_multiple=True" in msg:
                # 关键：清理 client 残留状态后重试一次
                try:
                    ray.shutdown()
                except Exception:
                    pass
                _ray_inited = False
                ray.init(
                    address=RAY_ADDRESS,
                    namespace=RAY_NAMESPACE,
                    ignore_reinit_error=True,
                )
            else:
                raise

        _ray_inited = True
        print(f"[api] ray connected: {RAY_ADDRESS} ns={RAY_NAMESPACE}", flush=True)
        return

async def save_file(upload: UploadFile, dst: str):
    with open(dst, "wb") as f:
        while True:
            chunk = await upload.read(1024 * 1024)
            if not chunk:
                break
            f.write(chunk)


@router.post("/upload")
async def upload_video(file: UploadFile = File(...)):
    video_id = str(uuid.uuid4())
    input_path = f"{UPLOAD_DIR}/{video_id}.mp4"
    output_path = f"{PROCESSED_DIR}/{video_id}.mp4"

    await save_file(file, input_path)

    # ✅ 请求路径里确保 Ray 可用（失败就 503，不让服务 crash）
    try:
        ensure_ray_connected()
    except Exception as e:
        raise HTTPException(status_code=503, detail=f"Ray not ready: {e!r}")

    try:
        worker = ray.get_actor("video_worker", namespace=RAY_NAMESPACE)
    except Exception as e:
        # 关键：actor 不存在属于“未就绪”，不要 500
        raise HTTPException(status_code=503, detail=f"Ray actor not ready: {e!r}")

    try:
        ref = worker.process.remote(input_path, output_path)
        with _job_lock:
            _job_refs[video_id] = ref
            _job_errors.pop(video_id, None)
            _job_done_ts.pop(video_id, None)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Submit failed: {e!r}")

    return {"video_id": video_id, "status": "processing"}

@router.get("/status/{video_id}")
def status(video_id: str):
    # 如果没见过这个 job（API 重启或非法 id），保守处理：看文件是否最终存在
    output_path = f"{PROCESSED_DIR}/{video_id}.mp4"

    # 1) 优先走 Ray 任务状态（正确语义）
    with _job_lock:
        ref = _job_refs.get(video_id)
        err = _job_errors.get(video_id)

    if err:
        return {"video_id": video_id, "status": "failed", "error": err}

    if ref is not None:
        try:
            # 确保 ray client 可用（避免 status 调用时 session stale）
            ensure_ray_connected()
            ready, _ = ray.wait([ref], timeout=0)
            if not ready:
                return {"video_id": video_id, "status": "processing"}

            # 任务完成：ray.get 成功才算 done；异常则 failed
            try:
                _ = ray.get(ref)  # worker 返回 output_path；我们不依赖其内容
            except Exception as e:
                with _job_lock:
                    _job_errors[video_id] = repr(e)
                return {"video_id": video_id, "status": "failed", "error": repr(e)}

            required = [
                f"{PROCESSED_DIR}/{video_id}.mp4",
                f"{PROCESSED_DIR}/{video_id}_360p.mp4",
                f"{PROCESSED_DIR}/{video_id}_720p.mp4",
            ]
            missing = [p for p in required if not os.path.exists(p)]
            if missing:
                # 任务已经结束但产物缺失：这是“明确失败”，否则会误导为 done
                return {"video_id": video_id, "status": "failed", "error": "outputs_missing_after_job_finished", "missing": missing}

            with _job_lock:
                _job_done_ts[video_id] = time.time()
            return {"video_id": video_id, "status": "done"}

        except Exception as e:
            # status 本身不能把服务打挂，返回 processing 并带上提示
            return {"video_id": video_id, "status": "processing", "detail": f"status_check_error: {e!r}"}

    # 2) fallback：API 重启后没有 ref 的情况——用“产物齐全”判断，避免误报 done
    required = [
        f"{PROCESSED_DIR}/{video_id}.mp4",
        f"{PROCESSED_DIR}/{video_id}_360p.mp4",
        f"{PROCESSED_DIR}/{video_id}_720p.mp4",
    ]
    missing = [p for p in required if not os.path.exists(p)]
    if missing:
        return {"video_id": video_id, "status": "processing", "detail": f"missing_outputs={missing}"}
    return {"video_id": video_id, "status": "done"}


@router.post("/genai/run")
def genai_run(prompt: str):
    now = time.time()
    key = _genai_dedupe_key(prompt)

    # 0) TTL purge + dedupe hit
    with _ai_lock:
        _purge_ai_dedupe(now)
        rec = _ai_dedupe.get(key)

        if rec:
            task_id = str(rec["task_id"])

            # ✅ 如果该 task 已失败：evict 后走 miss
            if task_id in _ai_errors:
                _ai_dedupe.pop(key, None)
                rec = None
            else:
                request_id = str(rec["request_id"])
                artifact = f"{PROCESSED_DIR}/{task_id}_llm.json"
                if os.path.exists(artifact):
                    return {
                        "task_id": task_id,
                        "request_id": request_id,
                        "status": "done",
                        "artifact": artifact,
                        "deduped": True,
                        "dedupe_key_prefix": key[:12],
                        "ttl_sec": GENAI_DEDUPE_TTL_SEC,
                    }
                return {
                    "task_id": task_id,
                    "request_id": request_id,
                    "status": "processing",
                    "deduped": True,
                    "dedupe_key_prefix": key[:12],
                    "ttl_sec": GENAI_DEDUPE_TTL_SEC,
                }

        # =========================
        # ✅ Step3A: Backpressure 检查（只做“锁内快照”，不要引入新阻塞点）
        # =========================

        # 先确保 Ray 连接（你原本就做了；保留）
        try:
            ensure_ray_connected()
        except Exception as e:
            # 这里还没写入 _ai_dedupe 占位，无需回滚占位
            raise HTTPException(status_code=503, detail=f"Ray not ready: {e!r}")

        # ✅ 统计 inflight（你原逻辑：会在内部清理已完成 ref）
        inflight = _count_inflight_ai_locked()

        # ✅ 新增：快照 active task ids（最多返回 5 个）
        active_task_ids = _active_task_ids_locked(limit=5)

        if inflight >= GENAI_MAX_INFLIGHT:
            # =========================
            # ✅ Step3B: 429 返回 retry_after_ms + active_task_ids
            # =========================
            raise HTTPException(
                status_code=429,
                detail={
                    "error": "genai_backpressure",
                    "inflight": inflight,
                    "max_inflight": GENAI_MAX_INFLIGHT,
                    "retry_after_ms": GENAI_RETRY_AFTER_MS,          # ✅ 新增
                    "active_task_id": active_task_ids[0] if active_task_ids else None,  # ✅ 新增（单个更易用）
                    "active_task_ids": active_task_ids,             # ✅ 新增（批量更完整）
                },
            )

        # ✅ miss：通过 backpressure 后再创建占位，避免占位被 429 卡住
        task_id = str(uuid.uuid4())
        request_id = str(uuid.uuid4())
        _ai_dedupe[key] = {"task_id": task_id, "request_id": request_id, "ts": now}

    # 1) 确保 Ray ready（锁外；保留你的原逻辑）
    try:
        ensure_ray_connected()
    except Exception as e:
        with _ai_lock:
            _ai_dedupe.pop(key, None)
        raise HTTPException(status_code=503, detail=f"Ray not ready: {e!r}")

    # 2) get actor
    try:
        worker = ray.get_actor("video_worker", namespace=RAY_NAMESPACE)
    except Exception as e:
        with _ai_lock:
            _ai_dedupe.pop(key, None)
        raise HTTPException(status_code=503, detail=f"Ray actor not ready: {e!r}")

    # 3) submit
    try:
        ref = worker.llm_run.remote(task_id, prompt, request_id)
        with _ai_lock:
            _ai_refs[task_id] = ref
            _ai_errors.pop(task_id, None)
    except Exception as e:
        with _ai_lock:
            _ai_dedupe.pop(key, None)
        raise HTTPException(status_code=500, detail=f"Submit failed: {e!r}")

    return {
        "task_id": task_id,
        "request_id": request_id,
        "status": "processing",
        "deduped": False,
        "dedupe_key_prefix": key[:12],
        "ttl_sec": GENAI_DEDUPE_TTL_SEC,
    }

@router.get("/genai/status/{task_id}")
def genai_status(task_id: str):
    with _ai_lock:
        ref = _ai_refs.get(task_id)
        err = _ai_errors.get(task_id)

    if err:
        return {"task_id": task_id, "status": "failed", "error": err}

    if ref is None:
        # ✅ fallback：如果 Ray ref 丢了（API 重启/内存丢失），但 artifact 已存在，就直接 done
        artifact = os.path.join(PROCESSED_DIR, f"{task_id}_llm.json")
        if os.path.exists(artifact):
            try:
                d = json.load(open(artifact, "r", encoding="utf-8"))
                return {
                    "task_id": task_id,
                    "status": "done",
                    "artifact": artifact,
                    "request_id": d.get("request_id"),
                }
            except Exception:
                return {"task_id": task_id, "status": "done", "artifact": artifact}
        return {"task_id": task_id, "status": "unknown"}

    try:
        ensure_ray_connected()
        ready, _ = ray.wait([ref], timeout=0)
        if not ready:
            return {"task_id": task_id, "status": "processing"}

        try:
            out_path = ray.get(ref)
        except Exception as e:
            with _ai_lock:
                _ai_errors[task_id] = repr(e)
            return {"task_id": task_id, "status": "failed", "error": repr(e)}

        # ✅严格语义：产物文件必须存在
        if not os.path.exists(out_path):
            return {"task_id": task_id, "status": "failed", "error": "missing_llm_artifact", "path": out_path}
        # ✅ [NEW] read request_id from artifact (source of truth)
        request_id = None
        try:
            import json
            with open(out_path, "r", encoding="utf-8") as f:
                request_id = json.load(f).get("request_id")
        except Exception:
            # 不影响主流程；最多少一个字段
            request_id = None

        return {"task_id": task_id, "status": "done", "artifact": out_path, "request_id": request_id}

    except Exception as e:
        return {"task_id": task_id, "status": "processing", "detail": f"status_check_error: {e!r}"}

@router.get("/genai/debug/{task_id}")
def genai_debug(task_id: str):
    artifact = os.path.join(PROCESSED_DIR, f"{task_id}_llm.json")
    out = {
        "task_id": task_id,
        "artifact_exists": os.path.exists(artifact),
        "artifact": artifact,
        "in_memory_ref": False,
        "in_memory_error": None,
        "file_size": None,
        "mtime": None,
        "json": None,
        "json_read_error": None,
    }

    with _ai_lock:
        out["in_memory_ref"] = task_id in _ai_refs
        out["in_memory_error"] = _ai_errors.get(task_id)

    if not os.path.exists(artifact):
        return out

    try:
        out["file_size"] = os.path.getsize(artifact)
        out["mtime"] = os.path.getmtime(artifact)
        with open(artifact, "r", encoding="utf-8") as f:
            out["json"] = json.load(f)
    except Exception as e:
        out["json_read_error"] = repr(e)

    return out