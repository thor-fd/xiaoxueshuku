#!/bin/bash
# 本地打包并发布到服务器（在本地 Mac 上执行）
# 前置：服务器已跑过 setup-server.sh；本地密钥 ~/Downloads/key1.pem
# 说明：腾讯云 Ubuntu 默认用 ubuntu 用户登录（root 禁止 SSH），
#       /opt/pdf-server 属于 pdfsrv，所以先传 /tmp 再远程 sudo 移入
# 用法: bash deploy.sh [user@server-ip]   不带参数则用下面 SERVER 默认值
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../pdf-server"   # 进入 pdf-server 目录（pom.xml 和 data/ 都在这里）

if [ ! -f pom.xml ]; then
  echo "❌ 找不到 pom.xml —— deploy.sh 必须在【本地 Mac】上运行（它负责本机打包再上传），"
  echo "   不要在服务器上执行；服务器上只需跑过 setup-server.sh 即可。"
  exit 1
fi

SERVER=${1:-"ubuntu@你的服务器IP"}   # TODO: 腾讯云控制台查公网 IP 后替换，或保留用参数传入
DIR=/opt/pdf-server

# SSH 密钥（腾讯云密钥对的私钥文件）
KEY="$HOME/Downloads/key1.pem"
if [ ! -f "$KEY" ]; then
  echo "❌ 找不到密钥文件: $KEY"
  exit 1
fi
chmod 600 "$KEY"   # 权限过宽 OpenSSH 会拒绝使用
SSH="ssh -i $KEY -o StrictHostKeyChecking=accept-new"

echo "== 1. 本地打包 =="
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/openjdk-25/Contents/Home"
mvn -DskipTests -q package
JAR=target/pdf-server-0.1.0.jar
ls -lh "$JAR"

echo "== 2. 上传 jar 与数据到 /tmp =="
scp -i "$KEY" -o StrictHostKeyChecking=accept-new "$JAR" "$SERVER:/tmp/pdf-server.new.jar"
rsync -avz --exclude ".DS_Store" -e "ssh -i $KEY -o StrictHostKeyChecking=accept-new" \
  data/ "$SERVER:/tmp/pdf-server-data/"

echo "== 3. sudo 安装到 $DIR 并重启 =="
$SSH "$SERVER" "sudo mkdir -p $DIR/data && \
  sudo rsync -a --exclude '.DS_Store' /tmp/pdf-server-data/ $DIR/data/ && \
  [ -f $DIR/pdf-server.jar ] && sudo cp $DIR/pdf-server.jar $DIR/pdf-server.jar.bak; \
  sudo mv /tmp/pdf-server.new.jar $DIR/pdf-server.jar && \
  sudo chown -R pdfsrv:pdfsrv $DIR && \
  sudo systemctl restart pdf-server && sleep 4 && \
  curl -sf http://127.0.0.1:8787/api/h5/meta && echo && echo '✅ 发布成功' && \
  rm -rf /tmp/pdf-server-data /tmp/pdf-server.new.jar"

echo "回滚方法（如需）: ssh -i $KEY $SERVER 'sudo cp $DIR/pdf-server.jar.bak $DIR/pdf-server.jar && sudo systemctl restart pdf-server'"
