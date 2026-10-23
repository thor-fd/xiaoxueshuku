<#ftl encoding="UTF-8">
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<style>
@page {
  size: A4 portrait;
  margin: 12mm 14mm;
  @bottom-center {
    content: "由 小学书库 生成 · 第 " counter(page) " 页";
    font-family: "${fontFamily}";
    font-size: 8pt;
    color: #999999;
  }
}
body { font-family: "${fontFamily}"; margin: 0; }
.title { font-size: 16pt; text-align: center; margin: 0 0 2.5mm 0; }
.meta { font-size: 10.5pt; color: #555555; text-align: right; margin: 0 0 4mm 0; }
table.math { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.math td { font-size: 14pt; padding: 10mm 2mm; white-space: nowrap; }
.blank { display: inline-block; width: 15mm; border-bottom: 0.6pt solid #333333; }
.ansTitle { font-size: 13pt; margin: 0 0 3mm 0; }
table.answers { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.answers td { font-size: 11pt; padding: 2.4mm 2mm; color: #333333; }
</style>
</head>
<body>
<div class="title">${title}</div>
<div class="meta">姓名：__________　日期：__________　用时：____分____秒　对：____题</div>
<table class="math">
  <#list rows as row>
  <tr><#list row as eq><td>${eq["question"]}<span class="blank"></span></td></#list></tr>
  </#list>
</table>
<#if withAnswer>
<div style="page-break-before: always;"></div>
<div class="ansTitle">参考答案</div>
<table class="answers">
  <#list answerRows as row>
  <tr><#list row as eq><td>${eq["text"]}</td></#list></tr>
  </#list>
</table>
</#if>
</body>
</html>
