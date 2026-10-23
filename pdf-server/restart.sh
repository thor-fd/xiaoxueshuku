#!/bin/bash
# 重启 pdf-server：停旧进程 → 重新打包（模板/代码改动生效）→ 启动 → 健康检查
# 用法: ./restart.sh        正常重启（重新打包）
#       ./restart.sh fast   跳过打包，直接重启（jar 没变时用）
set -e
cd "$(dirname "$0")"

export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/openjdk-25/Contents/Home"
JAVA="$JAVA_HOME/bin/java"
JAR=target/pdf-server-0.1.0.jar
LOG=logs/app.log
PORT=8787
mkdir -p logs

echo "== 1. 停止旧进程 =="
pkill -f "pdf-server-0.1.0.jar" 2>/dev/null && echo "已停止" || echo "没有在运行的进程"
sleep 1

if [ "$1" != "fast" ]; then
  echo "== 2. 重新打包 =="
  "$HOME/dev-tools/apache-maven-3.9.16/bin/mvn" -DskipTests -q package
  echo "打包完成"
else
  echo "== 2. 跳过打包（fast 模式）=="
fi

echo "== 3. 启动 =="
nohup "$JAVA" -jar "$JAR" >> "$LOG" 2>&1 &

echo "== 4. 健康检查 =="
for i in $(seq 1 15); do
  if curl -sf -o /dev/null "http://localhost:$PORT/api/h5/meta"; then
    echo "✅ 服务已就绪: http://localhost:$PORT/  (日志: $LOG)"
    exit 0
  fi
  sleep 2
done

echo "❌ 启动失败，最近日志："
tail -20 "$LOG"
exit 1
