# PDF 模板设计指南（OpenHTMLtoPDF 方案）

> 核心思路：**模板 = HTML 布局（Freemarker .ftl 文件）+ 数据模型（参数）**。
> 加一个新模板 ≈ 写一个新 FTL 文件，成本极低，所以先做 3 个模板验证市场，火了再铺。

---

## 一、打印品设计原则（和网页设计完全不同）

1. **A4 + 安全边距**：家用打印机物理边距约 5-8mm，页面 margin 设 `12mm 14mm` 保证任何打印机都不切内容
2. **黑白友好**：家庭多为黑白打印。所有颜色必须灰阶可辨——线条用黑/中灰，描红用浅灰但别太浅
3. **线宽下限 0.5pt**：低于 0.3pt 的细线激光打印会丢失
4. **信息密度低**：打印品是给孩子写的，留白要足，别按屏幕排版思路塞内容
5. **每页独立成题**：家长可能只打某一页（比如只打第一课），每页自带标题、姓名栏、日期栏

---

## 二、工程结构

```
src/main/resources/
├── templates/
│   ├── copybook_tian.ftl      # 田字格字帖
│   ├── copybook_mi.ftl        # 米字格字帖
│   ├── oral_math.ftl          # 口算题卡
│   └── partials/
│       ├── header.ftl         # 页眉（标题+姓名日期栏）
│       └── footer.ftl         # 页脚（免费版水印）
├── fonts/
│   └── LXGWWenKai-Regular.ttf # 霞鹜文楷（OFL 可商用）
└── static/
    └── grids/                 # 田字格/米字格底图（SVG 或 PNG）
```

数据流：`参数 → Freemarker 渲染 HTML 字符串 → OpenHTMLtoPDF 转 PDF → 参数哈希做 key 存 COS`

Maven 依赖：

```xml
<dependency>
    <groupId>io.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.0.10</version>
</dependency>
<dependency>
    <groupId>io.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-svg-support</artifactId>
    <version>1.0.10</version>
</dependency>
```

（原仓库 danfickle/openhtmltopdf 已归档，1.0.10 可直接用，也有社区维护分支，用法一致。）

---

## 三、字帖模板（核心难点：田字格怎么画）

### 3.1 田字格的实现方式选择

| 方案 | 说明 | 结论 |
|---|---|---|
| 嵌套 2×2 表格画虚线十字 | 纯 border 实现 | ❌ 生字没法叠在十字线上方（CSS 2.1 无文字叠层） |
| **SVG/PNG 做 cell 背景图** | 田字格底图 + 生字作为文字居中叠上 | ✅ 推荐。背景会完整印进 PDF（PDF 是成品，不存在浏览器"不打印背景"问题） |
| Canvas/Java2D 逐格绘制 | PDFBox 直接画 | 备选，控制力最强但写模板慢，后期做特殊版式再上 |

田字格底图 SVG（米字格加两条对角线即可）：

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
  <rect x="0.75" y="0.75" width="98.5" height="98.5" fill="none" stroke="#333333" stroke-width="1.5"/>
  <line x1="50" y1="1.5" x2="50" y2="98.5" stroke="#BBBBBB" stroke-width="1" stroke-dasharray="5,4"/>
  <line x1="1.5" y1="50" x2="98.5" y2="50" stroke="#BBBBBB" stroke-width="1" stroke-dasharray="5,4"/>
</svg>
```

以 data URI 内联进 CSS（`#` 要转义成 `%23`），或放 static 目录引用。

### 3.2 版式规格（A4 竖版）

- 格子 17mm 见方：内容宽 182mm ÷ 17mm ≈ 10 格/行
- 每行一个字：**描红 ×3 + 空白 ×7**（一年级可改 描4写6）
- 每行上方可选拼音行（6mm 高）
- 每页 8-9 个字，页首标题 + 姓名日期栏
- 字号：17mm ≈ 48pt，字用 **40pt**（格内留空隙），描红色 `#DCDCDC`，另提供"描红加深 `#C8C8C8`"选项（部分喷墨机浅灰打不出来）

### 3.3 完整模板示例（copybook_tian.ftl 简化版）

```html
<html>
<head>
<meta charset="utf-8"/>
<style>
  @page {
    size: A4 portrait;
    margin: 12mm 14mm;
    @bottom-center {
      content: "第 " counter(page) " 页 · 由「XX打印」生成";
      font-size: 8pt; color: #999999;
    }
  }
  body { font-family: "LXGW WenKai"; margin: 0; }
  .title { font-size: 16pt; text-align: center; margin: 0 0 4mm 0; }
  .meta  { font-size: 10pt; color: #666666; text-align: right; margin-bottom: 3mm; }
  table.grid { border-collapse: collapse; margin: 0 auto; }
  td.py {
    height: 6mm; font-size: 11pt; text-align: center;
    vertical-align: bottom; color: #444444;
  }
  td.cell {
    width: 17mm; height: 17mm;
    background-image: url("${gridBg}");   /* 田字格 data URI，服务端注入 */
    background-repeat: no-repeat;
    text-align: center; vertical-align: middle;
    font-size: 40pt; color: #222222;
  }
  td.trace { color: ${traceColor}; }      /* 描红 */
</style>
</head>
<body>
  <div class="title">一年级上册 · 识字表（一）</div>
  <div class="meta">姓名：__________　　日期：__________</div>

  <#list units as unit>
  <#list unit.words as w>
  <tr><#t>
  </#list><#t>
  </#list>
  <table class="grid">
    <#list words as w>
    <tr>
      <#if withPinyin><#list 1..10 as i><td class="py"><#if i==1>${w.pinyin}</#if></td></#list></#if>
      <tr>
        <td class="cell trace">第1描</td>
      </tr>
    </tr>
    </#list>
  </table>
</body>
</html>
```

> 注：上面 `<#list>` 内层结构示意，实际生成逻辑为——每字一行：`<td class="py">拼音</td>` 一行 + `<td class="cell trace">字</td>` ×3 + `<td class="cell"></td>` ×7。把这段写进 FTL 循环即可，别复制注释行。

### 3.4 生字居中微调

CJK 字形基线偏低，若视觉不居中，用 `padding-top` 微调（1-2mm 级别），不同字体不同，上线前打印实测一次。

---

## 四、口算题卡模板

### 4.1 版式规格

- 4 列 × 8 行 = 32 题/页（低年级 24 题更宽松）
- 正文 14pt 思源黑体（正文别用楷体，数字辨识度差）
- 答题空 16mm 横线，加"用时：____分____秒"栏（口算计时是家长刚需）
- 答案页用 `page-break-before: always` 独立成页，可参数化关闭

### 4.2 关键片段

```css
table.math { width: 100%; border-collapse: collapse; }
table.math td {
  font-family: "Source Han Sans"; font-size: 14pt;
  padding: 4mm 2mm; white-space: nowrap;
}
.blank { display: inline-block; width: 16mm; border-bottom: 0.5pt solid #333333; }
```

```html
<table class="math">
  <#list rows as row>
  <tr>
    <#list row as eq>
    <td>${eq.a} ${eq.op} ${eq.b} = <span class="blank"></span></td>
    </#list>
  </tr>
  </#list>
</table>

<div style="page-break-before: always; font-size:12pt">
  参考答案
  <table><!-- 4列小字排版答案 --></table>
</div>
```

算式不存题库，由**规则引擎随机生成**（如"20以内进位加法"：`a+b≤20 且 (a%10)+(b%10)≥10`），固定 seed → 同参数结果可复现 → 可缓存。

---

## 五、OpenHTMLtoPDF 落地要点

### 5.1 字体注册（一次加载，全局复用）

```java
PdfRendererBuilder builder = new PdfRendererBuilder();
builder.useFont(new File("fonts/LXGWWenKai-Regular.ttf"), "LXGW WenKai");
builder.useFont(new File("fonts/SourceHanSansSC-Regular.otf"), "Source Han Sans");
builder.withHtmlContent(html, baseUri);   // baseUri 指向 static 目录，解析相对路径图片
builder.toStream(out);
builder.run();
```

- 只支持 TTF（霞鹜文楷有 .ttf 版本，注意下载 TTF 不是 OTF 版——思源黑体 OTF 需转 TTF 或用其 TTF 发行版）
- 字体名必须和 CSS `font-family` 完全一致
- 自动子集化，单份 PDF 体积没问题

### 5.2 CSS 能力边界（Flying Saucer 内核 = CSS 2.1 子集）

| 能用 | 不能用 |
|---|---|
| table（主力排版工具） | flexbox |
| float、inline-block | grid |
| position: relative/absolute | box-shadow |
| @page + margin boxes（页脚计数器） | 渐变、transform |
| page-break-before/after/inside | CSS 变量、伪元素 ::before 内容复杂用法 |

**心态：把 2005 年的 CSS 能力当天花板，全用表格排版，反而稳定。**

### 5.3 分页控制

- 行不跨页：每行高度远小于页高，天然不跨页
- 答案页/新单元另起一页：外面包 `div { page-break-before: always; }`
- 30 天口算计划 = 循环 30 个页单元，每页页眉带"第 N 天"

---

## 六、打印适配清单（上线前实测一遍）

| 检查项 | 标准 |
|---|---|
| 边距 | 家用喷墨/激光各打一台，四边无裁切 |
| 描红灰度 | 喷墨机打出来肉眼可见但铅笔能覆盖；提供加深选项兜底 |
| 虚线十字 | 打印后虚线连续不糊 |
| 黑白打印 | 彩色激光打黑白后，层级仍清晰 |
| 字体 | 生字全部渲染（抽查生僻字，缺字形会显示空白框） |
| 答案页 | 参数关闭后页数正确 |

---

## 七、参数模型与缓存

```
请求参数 → 规范化 JSON → MD5 → COS key（pdf/{md5}.pdf）
   ├─ 命中：直接返回签名 URL
   └─ 未命中：Freemarker 渲染 → 生成 → 上传 → 返回
```

字帖参数：`{grade, volume, unit, chars[], gridType, pattern, withPinyin, traceColor, title}`
口算参数：`{knowledgePoint, count, cols, seed, withAnswer, day}`

热门参数组合（如"一年级上册识字表一·默认版式"）天然高命中，COS+CDN 后边际成本趋近于零。

---

## 八、起步建议

1. **先做 3 个模板**：田字格字帖、米字格字帖、20 以内口算卡——覆盖两个品类的最小验证面
2. 每个模板自己**打印实测 3 遍**再上线（不同打印机）
3. 模板即 FTL 文件，小红书测出哪个资料最火，就为它加变体（这是这个模式迭代成本最低的环节）
4. 米字格/四线三格/控笔等新模板复用同一套 header/footer partial，半天一个
