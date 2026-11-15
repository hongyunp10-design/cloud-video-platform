
import json
import subprocess
import time
import uuid
from pathlib import Path
from urllib.request import Request, urlopen

API = "http://127.0.0.1:8000"
JOB = "http://job-service:8080"
PROCESSED_DIR = Path("/data/processed")


def request_json(method, url, body=None, headers=None):
    request = Request(
        url,
        data=body,
        headers=headers or {},
        method=method,
    )
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    # 1. Verify the Ray dependency before submitting work.
    health = request_json("GET", f"{API}/healthz")
    assert health["ok"] and health["actor_ok"], health
    print("[PASS] API and Ray Actor health", flush=True)

    # 2. Generate a new, valid MP4 inside the API Pod.
    video_file = Path(f"/tmp/e2e-{uuid.uuid4()}.mp4")

    try:
        subprocess.run(
            [
                "ffmpeg", "-y", "-loglevel", "error",
                "-f", "lavfi",
                "-i", "testsrc2=size=640x360:rate=24",
                "-t", "2",
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                str(video_file),
            ],
            check=True,
        )

        # 3. Stage video through the real Python API.
        boundary = uuid.uuid4().hex
        prefix = (
            f"--{boundary}\r\n"
            'Content-Disposition: form-data; name="file"; '
            'filename="e2e.mp4"\r\n'
            "Content-Type: video/mp4\r\n\r\n"
        ).encode()

        body = (
            prefix
            + video_file.read_bytes()
            + f"\r\n--{boundary}--\r\n".encode()
        )

        staged = request_json(
            "POST",
            f"{API}/video/stage",
            body,
            {"Content-Type": f"multipart/form-data; boundary={boundary}"},
        )
        video_id = staged["video_id"]
        print(f"[PASS] Stage video: {video_id}", flush=True)

        # 4. Create a persistent Java MEDIA task.
        payload = json.dumps({
            "name": "automated-k8s-e2e",
            "videoId": video_id,
        }).encode()

        created = request_json(
            "POST",
            f"{JOB}/api/media-tasks",
            payload,
            {"Content-Type": "application/json"},
        )
        task_id = created["id"]
        print(f"[PASS] Create Java task: {task_id}", flush=True)

        # 5. Wait for Java Worker and Ray processing.
        deadline = time.monotonic() + 150
        previous_status = None

        while time.monotonic() < deadline:
            result = request_json(
                "GET", f"{JOB}/api/tasks/{task_id}"
            )["data"]

            status = result["status"]

            if status != previous_status:
                print(f"[TASK] {task_id}: {status}", flush=True)
                previous_status = status

            if status == "SUCCESS":
                break

            if status in ("FAILED", "DEAD"):
                raise RuntimeError(
                    f"Task failed: {result.get('lastErrorMessage')}"
                )

            time.sleep(2)
        else:
            raise TimeoutError(f"Task {task_id} did not succeed")

        # 6. Verify the actual Ray result and persisted files.
        video_status = request_json(
            "GET", f"{API}/video/status/{video_id}"
        )
        assert video_status["status"] == "done", video_status

        manifest_path = PROCESSED_DIR / f"{video_id}_manifest.json"
        manifest = json.loads(manifest_path.read_text())

        assert manifest["video_id"] == video_id
        assert manifest["status"] == "done"
        assert not manifest["errors"]

        outputs = {
            item["profile"]: item
            for item in manifest["outputs"]
        }

        for profile in ("360p", "480p", "720p"):
            assert profile in outputs, f"Missing profile: {profile}"

            output = outputs[profile]
            path = Path(output["path"])

            assert path.is_file(), f"Missing file: {path}"
            assert path.stat().st_size > 1024
            assert path.stat().st_size == output["size_bytes"]

            print(f"[PASS] {profile}: {path.name}", flush=True)

        print(
            f"\nE2E PASSED: task={task_id}, video={video_id}",
            flush=True,
        )

    finally:
        video_file.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
