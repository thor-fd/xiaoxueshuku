#!/bin/bash
# 服务器初始化（在新购的 Ubuntu 22.04/24.04 上以 root 执行一次）
# 用法: bash setup-server.sh example.com
set -e
DOMAIN=${1:?用法: bash setup-server.sh 你的域名}

echo "== 1. 基础包 =="
apt update && apt install -y nginx curl unzip certbot python3-certbot-nginx

echo "== 2. JDK 25（清华 TUNA 镜像，国内快；自动识别架构取最新版）=="
if [ "$(uname -m)" = "aarch64" ]; then ARCH=aarch64; else ARCH=x64; fi
BASE="https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jdk/${ARCH}/linux"
TARBALL=$(curl -s "$BASE/" | grep -oE 'OpenJDK25U-jdk_'"$ARCH"'_linux_hotspot_[^"]+\.tar\.gz' | sort -u | tail -1)
echo "下载 $BASE/$TARBALL"
mkdir -p /opt/jdk25 && curl -sL "$BASE/$TARBALL" -o /tmp/jdk.tgz
tar xzf /tmp/jdk.tgz -C /opt/jdk25 --strip-components=1 && rm /tmp/jdk.tgz
/opt/jdk25/bin/java -version

echo "== 3. 运行用户与目录 =="
id -u pdfsrv &>/dev/null || useradd -r -s /usr/sbin/nologin pdfsrv
mkdir -p /opt/pdf-server/data
chown -R pdfsrv:pdfsrv /opt/pdf-server

echo "== 4. systemd 服务 =="
cp pdf-server.service /etc/systemd/system/
systemctl daemon-reload
systemctl enable pdf-server   # 先 enable 不 start，等 jar 上传后 deploy.sh 会启动

echo "== 5. nginx =="
cp nginx-pdf.conf /etc/nginx/conf.d/pdf.conf
sed -i "s/example.com/$DOMAIN/g" /etc/nginx/conf.d/pdf.conf
nginx -t && systemctl reload nginx

echo "== 6. 后续步骤 =="
echo "① 本地执行 deploy.sh 上传 jar 并启动，之后用 http://服务器IP 访问（备案前调试用）"
echo "② 备案通过且域名解析生效后，服务器上执行（免费证书，自动配置 HTTPS 并续期）:"
echo "   certbot --nginx -d $DOMAIN"
echo
echo "完成。"
