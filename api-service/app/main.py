from fastapi import FastAPI, HTTPException
from app.api.video import router as video_router, ensure_ray_connected, RAY_NAMESPACE
import ray
import os
# FastAPI 是一个“Web 服务框架”
# 它的作用：帮你处理 HTTP 请求（比如 POST / GET）

app =  FastAPI()

# 统一所有视频相关接口前缀：/video
app.include_router(video_router, prefix="/video", tags=["video"])

@app.get("/health")
def heal_check() :
    return {"status" : "ok"}

@app.get("/healthz")
def healthz():
    try:
        ensure_ray_connected()
        actor = ray.get_actor("video_worker", namespace=RAY_NAMESPACE)
        pong = ray.get(actor.ping.remote())
    except Exception as e:
        raise HTTPException(status_code=503, detail={"ok": False, "error": repr(e)})

    out = {"ok": True, "ray_connected": True, "actor_ok": True, "actor": str(actor), "actor_ping": str(pong)}
    try:
        out["ray_address"] = os.getenv("RAY_ADDRESS")
        out["ray_namespace"] = RAY_NAMESPACE
        out["cluster_resources"] = ray.cluster_resources()
        out["available_resources"] = ray.available_resources()
    except Exception as e:
        out["resources_error"] = repr(e)
    return out

@app.on_event("startup")
def _startup():
    # 不要让失败把服务启动打崩；healthz 会反映未就绪
    try:
        ensure_ray_connected()
    except Exception as e:
        print(f"[api] ray startup connect failed: {e!r}", flush=True)