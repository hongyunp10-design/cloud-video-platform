FROM python:3.11-slim

WORKDIR /app

# 系统依赖：ffmpeg（你做视频处理必须有）
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
  && rm -rf /var/lib/apt/lists/*

# Python 依赖
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# ✅ 复制 tasks 代码到镜像
COPY tasks /app/tasks

# ✅ 关键：让 Ray 的子进程无论 cwd 在哪里，都能 import 到 /app 下的 tasks
ENV PYTHONPATH=/app

# 默认命令由 k8s yaml 决定（ray start --head / --address）
