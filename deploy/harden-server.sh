#!/bin/bash
# 服务器安全整改（公安备案评估项落地）：在服务器上以 ubuntu 用户执行
# 用法: scp 上传后 bash harden-server.sh（脚本内部自行 sudo）
# 内容:
#   1. nginx 日志补全字段（源端口/目标地址/目标端口）+ 独立访问日志
#   2. logrotate 留存延长到 ≥180 天（网安法第二十一条：不少于六个月）
#   3. journald 持久化 + 保留 6 个月 + 磁盘上限
#   4. /api/admin/ 限本机访问（nginx 层）
#   5. admin token 换随机强令牌（systemd 注入，不再用 jar 内默认值）
# 幂等：重复执行安全。
set -euo pipefail

echo "== 1. nginx 日志格式与 /api/admin/ 限制 =="
sudo python3 - <<'PYEOF'
import re, pathlib
p = pathlib.Path('/etc/nginx/conf.d/pdf.conf')
src = p.read_text()

if 'log_format detailed' not in src:
    fmt = ('log_format detailed \'$remote_addr - $remote_user [$time_local] '
           '"$request" $status $body_bytes_sent "$http_referer" "$http_user_agent" '
           'src_port=$remote_port dst=$server_addr:$server_port rt=$request_time\';\n\n')
    src = fmt + src  # conf.d 在 http 上下文中，文件顶部定义即可

if 'pdf-access.log' not in src:
    # 每个 server 块内加独立访问日志（首个 listen 行后）
    src = re.sub(r'(\n(\s*)listen [^;]+;\n)',
                 lambda m: m.group(1) + m.group(2) + 'access_log /var/log/nginx/pdf-access.log detailed;\n',
                 src, count=2)

if 'location ^~ /api/admin/' not in src:
    admin_block = ('    # 管理接口仅限本机调用（运维经 SSH: ssh -L 8787:127.0.0.1:8787 ...）\n'
                   '    location ^~ /api/admin/ {\n'
                   '        allow 127.0.0.1;\n'
                   '        deny all;\n'
                   '        proxy_pass http://127.0.0.1:8787;\n'
                   '        proxy_set_header Host $host;\n'
                   '        proxy_set_header X-Real-IP $remote_addr;\n'
                   '    }\n\n')
    src = src.replace('    location / {', admin_block + '    location / {')

p.write_text(src)
print('pdf.conf 已更新')
PYEOF
sudo nginx -t
sudo systemctl reload nginx

echo "== 2. logrotate 延长留存到 ≥180 天 =="
LR=/etc/logrotate.d/nginx
if grep -q "rotate" "$LR"; then
  sudo sed -i -E 's/^(\s*)rotate [0-9]+/\1rotate 26/; s/^(\s*)(daily|weekly|monthly)\s*$/\1weekly/' "$LR"
  echo "logrotate: 每周轮转保留 26 份（约 182 天）"
fi

echo "== 3. journald 持久化 + 6 个月留存 =="
sudo mkdir -p /etc/systemd/journald.conf.d
sudo tee /etc/systemd/journald.conf.d/90-retention.conf >/dev/null <<'EOF'
[Journal]
Storage=persistent
MaxRetentionSec=6month
SystemMaxUse=500M
EOF
sudo systemctl restart systemd-journald

echo "== 4. admin token 随机化（systemd 环境变量注入）=="
TOKEN=$(openssl rand -hex 24)
sudo mkdir -p /etc/systemd/system/pdf-server.service.d
sudo tee /etc/systemd/system/pdf-server.service.d/90-admin-token.conf >/dev/null <<EOF
[Service]
Environment=ADMIN_TOKEN=$TOKEN
EOF
sudo chmod 640 /etc/systemd/system/pdf-server.service.d/90-admin-token.conf
sudo systemctl daemon-reload
sudo systemctl restart pdf-server
# 服务器上留存一份，仅 root 可读，便于运维查看
echo "$TOKEN" | sudo tee /opt/pdf-server/ADMIN_TOKEN.txt >/dev/null
sudo chmod 600 /opt/pdf-server/ADMIN_TOKEN.txt

echo "== 5. 验证 =="
sleep 4
systemctl is-active nginx systemd-journald pdf-server
curl -sf -o /dev/null -w "https_page:%{http_code}\n" --resolve xiaoxueshuku.online:443:127.0.0.1 https://xiaoxueshuku.online/
curl -sf -o /dev/null -w "meta:%{http_code}\n" http://127.0.0.1:8787/api/h5/meta
OLD=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8787/api/admin/reload -H "X-Admin-Token: change-me-please")
NEW=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:8787/api/admin/reload -H "X-Admin-Token: $TOKEN")
echo "admin接口: 旧token=$OLD(期望403) 新token=$NEW(期望200)"
curl -s "http://127.0.0.1/api/h5/meta" -H "Host: xiaoxueshuku.online" >/dev/null
sleep 1
echo "-- 新日志字段示例 --"
sudo tail -1 /var/log/nginx/pdf-access.log

echo
echo "✅ 整改完成。新 admin token 已存于服务器 /opt/pdf-server/ADMIN_TOKEN.txt（查看: ssh ... 'sudo cat ...'）"
echo "   今后热更新数据: rsync data/ 后 → ssh 服务器 'curl -X POST -H \"X-Admin-Token: \$(sudo cat /opt/pdf-server/ADMIN_TOKEN.txt)\" http://127.0.0.1:8787/api/admin/reload'"
