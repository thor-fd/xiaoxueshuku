# 小程序版 —— 小学书库（原生微信小程序）

后端与 H5 共用同一套接口（`/api/h5/meta`、`/api/h5/lessons`、`/api/generate/copybook/excel`），本目录只有 UI 层。

## 本地跑起来

1. 启动后端：`pdf-server/run.sh`（本机 8787）
2. 微信开发者工具 → 导入项目 → 选择 `miniprogram/` 目录
   - AppID 用测试号即可（`project.config.json` 里是 `touristappid`，正式发布时替换）
   - `setting.urlCheck` 已默认关闭合法域名校验，导入即可连本机服务
3. 编译运行：选课时 → 生成字帖 PDF → 预览打开

**真机调试注意**：手机连不到你 Mac 的 localhost，把 `utils/config.js` 的
`DEV_BASE` 改成 Mac 的局域网 IP（如 `http://192.168.x.x:8787`），并保证手机与 Mac 同一 WiFi。

## 环境

`utils/config.js` 按 `envVersion` 自动切换：

| 版本 | 地址 |
|---|---|
| 开发版（开发者工具/真机调试） | `http://localhost:8787` |
| 体验版 / 正式版 | `https://你的域名`（**上线前必须替换**） |

## 发布前清单

1. 注册小程序账号（个人主体即可），把 AppID 填进 `project.config.json`
2. 类目：**工具 > 效率**（避开教育培训类目）
3. 小程序后台「开发管理-开发设置-服务器域名」：
   - request 合法域名 + downloadFile 合法域名：`https://你的域名`
   - 域名要求：HTTPS + 已备案（部署方案里已覆盖）
4. 替换 `utils/config.js` 的 `PROD_BASE`
5. 微信认证（可选，个人号不认证也能发布工具类）
6. 上传代码 → 提交审核（备注里写明"免费工具，内容为教材生字表练习纸生成"）

## 已知限制 / 说明

- PDF 无法像浏览器那样直接"下载到手机"，交互是 `downloadFile → openDocument` 预览，
  预览页右上角可**转发/保存**（典型路径：转发到"文件传输助手" → 电脑打印）
- 以后加会员付费：iOS 端禁止虚拟支付，小程序里只放"去 H5 开通"引导（与整体方案一致）
- 页面多起来后可迁移 uni-app（后端零改动），当前单页原生维护成本最低
