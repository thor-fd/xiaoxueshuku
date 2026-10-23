#!/bin/bash
# 生字表数据热更新：改完 Excel → 同步到服务器 → 调 reload 接口，全程无需重启
# 用法:
#   ./reload.sh           本地模式：直接调用本机 8787 的 reload 接口（本地开发用）
#   ./reload.sh remote    远程模式：rsync data/ 到服务器 → 服务器本机调 reload
#
# token 默认从 src/main/resources/application.yml 读取，也可用环境变量覆盖:
#   RELOAD_TOKEN=xxxx ./reload.sh remote
set -e
cd "$(dirname "$0")"

MODE=${1:-local}
TOKEN=${RELOAD_TOKEN:-$(grep -E '^[[:space:]]*token:' src/main/resources/application.yml | head -1 | sed 's/.*token:[[:space:]]*//' | tr -d '[:space:]')}
SERVER=${SERVER:-"root@你的服务器IP"}   # remote 模式用
KEY="$HOME/Downloads/key1.pem"          # SSH 私钥，与 deploy.sh 相同
RSYNC_SSH="ssh -i $KEY -o StrictHostKeyChecking=accept-new"

if [ -z "$TOKEN" ]; then
  echo "❌ 未读取到 admin.token，请检查 src/main/resources/application.yml"
  exit 1
fi

case "$MODE" in
  local)
    echo "== 本地热更新 =="
    curl -sf -X POST -H "X-Admin-Token: $TOKEN" http://localhost:8787/api/admin/reload
    echo
    echo "✅ 数据已热更新（无需重启）"
    ;;
  remote)
    echo "== 同步 data/ 到服务器 =="
    rsync -avz --exclude ".DS_Store" -e "$RSYNC_SSH" data/ "$SERVER:/tmp/pdf-data/"
    echo "== sudo 移入安装目录并热更新 =="
    $RSYNC_SSH "$SERVER" "sudo rsync -a /tmp/pdf-data/ /opt/pdf-server/data/ && \
      curl -sf -X POST -H 'X-Admin-Token: $TOKEN' http://127.0.0.1:8787/api/admin/reload && \
      rm -rf /tmp/pdf-data"
    echo
    echo "✅ 服务器数据已热更新（无需重启）"
    ;;
  *)
    echo "用法: ./reload.sh [local|remote]"
    exit 1
    ;;
esac
