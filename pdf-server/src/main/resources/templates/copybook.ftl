<#ftl encoding="UTF-8">
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<style>
@page {
  size: A4 portrait;
  margin: 10mm 14mm;
  @bottom-center {
    content: "由 小学书库 生成 · 第 " counter(page) " 页";
    font-family: "${fontFamily}";
    font-size: 8pt;
    color: #999999;
  }
}
body { font-family: "${fontFamily}"; margin: 0; }
.title { font-size: 16pt; text-align: center; margin: 0 0 2mm 0; }
.meta { font-size: 10.5pt; color: #555555; text-align: right; margin: 0 0 2mm 0; }
.secLabel { font-size: 12pt; color: #333333; margin: 2mm 0 1mm 0; }
table.grid { border-collapse: collapse; margin: 0 auto; }
td.py {
  height: 5mm; width: 17mm;
  font-size: 11pt; text-align: center; vertical-align: bottom;
  color: #444444;
}
td.cell {
  width: 17mm; height: 17mm;
  background-image: url("${gridBg}");
  background-repeat: no-repeat;
  background-size: 17mm 17mm;
  text-align: center; vertical-align: middle;
  font-size: 38pt; line-height: 1; color: #222222;
}
/* CJK 字形在 em 框内偏低，用相对定位整体上移（调这个数值）
   注意：position: relative 只能配合"逐页渲染+合并"架构使用——
   openhtmltopdf 的多页文档 + 行内相对定位会崩溃，单页渲染没有这个问题；
   vertical-align 的长度值不被支持，会被静默忽略 */
span.glyph { position: relative; top: -3.5mm; }
/* 拼音字：四线三格里字母主体应落在中格，字号小、整体下移 */
span.glyph-py { font-size: 14pt; position: relative; top: 1.2mm; font-weight: 500; }
td.trace { color: ${traceColor}; }
td.spacer { height: 1.2mm; font-size: 1pt; }
</style>
</head>
<body>
<#if title??>
<div class="title">${title}</div>
<div class="meta">姓名：____________　　日期：____________</div>
</#if>
<#list sections as sec>
<div class="secLabel">${sec["label"]}</div>
<table class="grid" cellspacing="0" cellpadding="0">
  <#list sec["words"] as w>
  <#if withPinyin && w["type"] == "hanzi">
  <tr><#list 1..sec["total"] as i><td class="py">${w["pinyin"]}</td></#list></tr>
  </#if>
  <tr>
    <#list 1..sec["trace"] as i><td class="cell trace" style="background-image:url(${w["bg"]})"><span class="glyph<#if w["type"] == "pinyin">-py</#if>">${w["char"]}</span></td></#list>
    <#list 1..sec["blank"] as i><td class="cell" style="background-image:url(${w["bg"]})"></td></#list>
  </tr>
  <#if w_has_next>
  <tr><#list 1..sec["total"] as i><td class="spacer"></td></#list></tr>
  </#if>
  </#list>
</table>
</#list>
</body>
</html>
