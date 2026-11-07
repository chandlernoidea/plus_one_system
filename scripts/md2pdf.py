#!/usr/bin/env python3
"""把使用说明书从 Markdown 转成带样式的 HTML，再用 Chromium 打印成 PDF。

不依赖 pandoc / markdown 库，只实现这份文档用到的语法子集：
标题、表格、代码块、引用、有序/无序列表、分隔线、图片、链接、粗体、行内代码。

用法：
    python3 scripts/md2pdf.py docs/solo/使用说明书-美甲师.md
产出：
    docs/solo/使用说明书-美甲师.html
    docs/solo/使用说明书-美甲师.pdf
"""

import html
import os
import re
import subprocess
import sys

CSS = """
@page { size: A4; margin: 16mm 14mm 18mm 14mm; }
* { box-sizing: border-box; }
body {
  font-family: "Source Han Sans CN", "Noto Sans CJK SC", "PingFang SC", sans-serif;
  font-size: 11pt; line-height: 1.75; color: #1c1a17; margin: 0;
}
h1 { font-size: 22pt; margin: 0 0 4mm; letter-spacing: 0.01em; }
h2 { font-size: 15pt; margin: 9mm 0 3mm; padding-bottom: 2mm;
     border-bottom: 1px solid #ddd6c8; page-break-after: avoid; }
h3 { font-size: 12.5pt; margin: 6mm 0 2mm; page-break-after: avoid; }
p { margin: 0 0 2.5mm; }
a { color: #8a3324; text-decoration: none; }
blockquote {
  margin: 3mm 0; padding: 2mm 0 2mm 4mm; border-left: 3px solid #c8b89a;
  color: #4a443c; background: #faf7f1;
}
blockquote p:last-child { margin-bottom: 0; }
code {
  font-family: "JetBrains Mono", "DejaVu Sans Mono", monospace;
  font-size: 0.88em; background: #f2eee6; padding: 0.4mm 1.2mm; border-radius: 1mm;
}
pre {
  background: #f7f4ee; border: 1px solid #e4dccd; border-radius: 2mm;
  padding: 3mm 4mm; overflow-x: auto; page-break-inside: avoid;
}
pre code { background: none; padding: 0; font-size: 9pt; line-height: 1.6; }
table {
  width: 100%; border-collapse: collapse; margin: 3mm 0 4mm;
  font-size: 10pt; page-break-inside: avoid;
}
th, td { border: 1px solid #e0d8c8; padding: 1.6mm 2.4mm; text-align: left; vertical-align: top; }
th { background: #f5f1e8; font-weight: 600; }
hr { border: 0; border-top: 1px solid #ddd6c8; margin: 7mm 0; }
img { max-height: 112mm; max-width: 52%; border: 1px solid #e4dccd; border-radius: 2mm; margin: 2mm 0; }
ul, ol { margin: 0 0 3mm; padding-left: 6mm; }
li { margin-bottom: 1mm; }
.toc-note { color: #6b6459; font-size: 10pt; }
h2, h3 { page-break-after: avoid; }
"""


def inline(text):
    """行内标记：先转义，再处理 code / 粗体 / 链接 / 图片。"""
    codes = []

    def stash(m):
        codes.append(html.escape(m.group(1)))
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", stash, text)
    text = html.escape(text)
    text = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", r'<img alt="\1" src="\2">', text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    for i, code in enumerate(codes):
        text = text.replace(f"\x00{i}\x00", f"<code>{code}</code>")
    return text


def convert(md):
    lines = md.split("\n")
    out = []
    i = 0
    list_open = None  # 'ul' / 'ol' / None

    def close_list():
        nonlocal list_open
        if list_open:
            out.append(f"</{list_open}>")
            list_open = None

    while i < len(lines):
        line = lines[i]

        # 代码块
        if line.startswith("```"):
            close_list()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            out.append("<pre><code>" + html.escape("\n".join(buf)) + "</code></pre>")
            continue

        # 分隔线
        if re.match(r"^\s*---+\s*$", line):
            close_list()
            out.append("<hr>")
            i += 1
            continue

        # 标题
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_list()
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2).strip())}</h{level}>")
            i += 1
            continue

        # 表格
        if line.strip().startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            close_list()
            header = [c.strip() for c in line.strip().strip("|").split("|")]
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            out.append("<table><thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in header) + "</tr></thead><tbody>")
            for row in rows:
                cells = row + [""] * (len(header) - len(row))
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in cells[: len(header)]) + "</tr>")
            out.append("</tbody></table>")
            continue

        # 引用
        if line.startswith(">"):
            close_list()
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(lines[i].lstrip(">").strip())
                i += 1
            out.append("<blockquote>" + "".join(f"<p>{inline(b)}</p>" for b in buf if b) + "</blockquote>")
            continue

        # 列表
        m_ul = re.match(r"^(\s*)[-*]\s+(.*)$", line)
        m_ol = re.match(r"^(\s*)(\d+)\.\s+(.*)$", line)
        if m_ul or m_ol:
            want = "ul" if m_ul else "ol"
            if list_open != want:
                close_list()
                out.append(f"<{want}>")
                list_open = want
            out.append(f"<li>{inline((m_ul or m_ol).group(2 if m_ul else 3))}</li>")
            i += 1
            continue

        # 空行
        if not line.strip():
            close_list()
            i += 1
            continue

        # 普通段落
        close_list()
        buf = [line.strip()]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(
            r"^(#{1,6}\s|>|\||```|\s*[-*]\s|\s*\d+\.\s|---+\s*$)", lines[i]
        ):
            buf.append(lines[i].strip())
            i += 1
        out.append(f"<p>{inline(' '.join(buf))}</p>")

    close_list()
    return "\n".join(out)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    src = sys.argv[1]
    base = os.path.splitext(src)[0]
    md = open(src, encoding="utf-8").read()
    # 去掉一级标题下的手写目录里可能带的锚点转义问题；保留原文
    html_doc = f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(os.path.basename(base))}</title>
<style>{CSS}</style>
</head>
<body>
{convert(md)}
</body>
</html>
"""
    out_html = base + ".html"
    out_pdf = base + ".pdf"
    open(out_html, "w", encoding="utf-8").write(html_doc)
    print(f"HTML: {out_html}")

    subprocess.run(
        [
            "chromium", "--headless=new", "--no-sandbox", "--disable-gpu",
            "--no-pdf-header-footer", "--run-all-compositor-stages-before-draw",
            "--virtual-time-budget=8000",
            f"--print-to-pdf={out_pdf}", "file://" + os.path.abspath(out_html),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    size = os.path.getsize(out_pdf) / 1024
    print(f"PDF : {out_pdf}  ({size:.0f} KB)")


if __name__ == "__main__":
    main()
