# pdf-server —— 打印资料 PDF 生成服务

技术栈：Spring Boot 4 · JDK 25 · Freemarker · OpenHTMLtoPDF + PDFBox · 霞鹜文楷/汇迹正楷（免费可商用）

## 运行

```bash
./run.sh          # 首次/启动（打包并前台运行）
./restart.sh      # 日常重启：停旧 → 重新打包 → 后台启动 → 健康检查（日志: logs/app.log）
./restart.sh fast # 跳过打包快速重启（jar 没变时用）
# 服务端口 8787：
# http://localhost:8787/                    H5 首页（选学科/年级/课时 → 生成下载 PDF）
# http://localhost:8787/api/demo/copybook   整本字帖演示
# http://localhost:8787/api/demo/oral-math  口算题卡演示
```

## H5 页面

`src/main/resources/static/index.html`（单文件，无前端依赖）。流程：选学科 → 年级册 → 课时 → 生成并下载。
对应接口：

- `GET /api/h5/meta`    学科 → 年级册目录
- `GET /api/h5/lessons?subject=&volume=` 课时列表，返回 `[{"unit":"第一单元·识字","lesson":"识字3 口耳目"}]`（H5 用 optgroup 分组，小程序显示「单元 · 课时」）
- `GET /api/generate/copybook/excel?subject=&volume=&lesson=&font=&gridType=&withPinyin=&traceDark=` 生成下载（attachment，lesson 传课时名）

注册/付费后置；当前学科下拉中数学、英语为占位（置灰），放入对应 data/ 目录即自动启用。

## 生字表数据（字帖内容来源）

**每个学科一个文件**（文件名 = 学科名），一个文件含所有年级，放入 `pdf-server/data/`：

```
data/语文.xlsx    （或同名 .csv，UTF-8 英文逗号）
data/数学.xlsx
data/英语.xlsx
```

列约定（第 1 个 sheet，首行表头）：

| 第 1 列 | 第 2 列 | 第 3 列 | 第 4 列 | 第 5 列 |
|---|---|---|---|---|
| 年级（如「一年级上」，同文件可多年级） | 单元名称（如「第一单元·识字」，可空，拼进 PDF 标题） | 课时信息（原样出现在 PDF 标题） | 一类字（会写，描 3 写 7） | 二类字（会认，描 2 写 8） |

- 单元格写「天地人」或「天、地、人」均可，程序逐字解析
- 拼音自动标注（pinyin4j，带声调，多音字取常用音）
- PDF 标题 = `前缀 · 单元 · 课时`（前缀来自接口参数 titlePrefix，单元/前缀为空自动省略）
- 当前 `data/语文.csv` 是**格式样例**，用真实教材数据覆盖

**更新流程（无需重启）**：改 Excel → `./reload.sh`（本地热更新）或 `./reload.sh remote`（rsync 到服务器并热更新）。
接口为 `POST /api/admin/reload`，请求头 `X-Admin-Token` 需与 `application.yml` 的 `admin.token` 一致（部署后改成随机值）；加载失败自动保留旧数据继续服务。

## 字体配置

请求参数 `font`（H5 页字体下拉 / 接口参数）：`WENKAI` 霞鹜文楷（默认）| `HUIJI` 汇迹正楷。
定义在 `CopybookFont` 枚举，新增字体 = 放 TTF 进 `resources/fonts/` + 枚举加一行。均为免费可商用字体；汇迹正楷授权以发布页最新说明为准。

## 接口

### 字帖（Excel 数据源）

```
GET /api/generate/copybook/excel?lesson=&titlePrefix=&gridType=TIAN&withPinyin=true&traceDark=false

  lesson      可选，课时模糊匹配（如「口耳目」）；缺省=整本所有课时
  titlePrefix 可选，标题前缀（如「一年级上册」→ 标题变成「一年级上册 · 识字3 口耳目」）
  gridType    TIAN 田字格 | MI 米字格 | FANG 方格
  withPinyin  是否带拼音行，默认 true
  traceDark   描红加深（浅灰打不出来时用），默认 false
```

注意：URL 里的中文参数必须 URL 编码，curl 用 `-G --data-urlencode "lesson=口耳目"`。

### 字帖（直接传字）

```
POST /api/generate/copybook
{ "title": "自定义标题", "gridType": "TIAN", "withPinyin": true,
  "traceDark": false, "traceCount": 3, "blankCount": 7,
  "words": [ { "char": "春", "pinyin": "chūn" } ] }
```

### 口算题卡

```
POST /api/generate/oral-math
{ "knowledgePoint": "ADD20CARRY", "count": 32, "cols": 4,
  "seed": 42, "withAnswer": true, "day": 1 }
  knowledgePoint: ADD10 | SUB10 | ADD20CARRY | SUB20BORROW | MUL99
  seed 固定可复现同一份题（后续做 PDF 缓存的基础）
```

## 渲染架构（重要，改代码前看）

**逐页渲染 + PDFBox 合并**：openhtmltopdf 的分页器有多个渲染缺陷
（跨页长表格行被裁剪、页间分页符失效、行内相对定位跨页崩溃），
因此由 `CopybookService` 手动分页（首页 9 字、后续页 10 字），
每页渲染成单页 PDF，`PdfRenderService.renderMerged` 用 PDFBox 合并。

其他经验：
- 生字用行内 `vertical-align` 抬升基线实现居中；**不要用 position: relative**
- 格线是 Java2D 生成的 PNG data URI 背景（`GridImageService`），
  生字作为文字叠加；不要用嵌套表格画田字格（CSS 2.1 无法文字叠线）
- 字体只用霞鹜文楷（OFL）。若要求教育规范字形可换「霞鹜文楷 GB」版，TTF 替换即可
- 模板只能用 CSS 2.1（无 flex/grid），排版用 table

## 加一个新模板的步骤

1. `templates/` 新建 `.ftl`（每页一个数据模型，复用 @page/页脚写法）
2. 对应 Service 组装 List<页模型>（容量控制在单页内）
3. Controller 调 `renderer.renderMerged(...)`

## 环境说明（本机）

- Maven 3.9.16：`~/dev-tools/apache-maven-3.9.16`（已加入 PATH；brew 也装了一份）
- JDK 25（Temurin arm64）：`~/Library/Java/JavaVirtualMachines/openjdk-25`，构建需 `JAVA_HOME` 指向它（`run.sh` 已处理；系统默认 java 是 21）
- Maven 本地仓库在 `~/Desktop/repository`（`~/.m2/settings.xml` 指定，内含公司残留配置，建议清理）

## 下一步

1. 真实生字表 Excel 入库（替换样例 CSV）
2. PDF 按参数哈希缓存（seed 已可复现）→ COS + CDN
3. 会员/免费额度（个体户 + 微信支付，iOS 引导 H5）
4. 小程序端（uni-app）+ H5 电脑打印页
