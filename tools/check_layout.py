"""界面布局自检。

用法: python tools/check_layout.py .

校验 SuperIOPortScreen 里的槽位坐标是否全部落在 GUI 背景贴图范围内。
曾经两次因为这个失误把「槽位被面板切掉」的 bug 发布出去，所以做成脚本常驻。

退出码 0 表示通过，1 表示有越界。
"""
import os
import sys

from PIL import Image

# ---- 与 SuperIOPortScreen.java 中的常量保持一致 ----
PANEL_WIDTH = 176
PANEL_HEIGHT = 226

CELL_COLUMNS = 2
INPUT_X, OUTPUT_X, CELL_Y = 18, 122, 80

UPGRADE_X, UPGRADE_Y, UPGRADE_COLUMNS = 8, 34, 4

PLAYER_X, PLAYER_Y, PLAYER_COLUMNS = 8, 146, 9
HOTBAR_Y = 206

INFO_X, INFO_Y = 8, 20
TITLE_X, TITLE_Y = 8, 6

SLOT_SIZE = 16
MARGIN = 3          # 与贴图外框保持的距离

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."


def slot_rect(x, y):
    """槽位在贴图上的实际绘制范围（内容区左上角 -> 含凹陷边框）。"""
    return (x - 1, y - 1, x + SLOT_SIZE, y + SLOT_SIZE)


def main():
    png = os.path.join(ROOT, "src", "main", "resources", "assets",
                       "neoeco_io", "textures", "guis", "super_io_port.png")

    ok = True

    # 1) 贴图必须存在，尺寸必须与代码常量一致
    if not os.path.exists(png):
        print("  [x] 找不到 GUI 贴图: %s" % png)
        return 1
    img = Image.open(png)
    print("  贴图尺寸 = %s   (代码常量 %dx%d)" % (img.size, PANEL_WIDTH, PANEL_HEIGHT))
    if img.size != (PANEL_WIDTH, PANEL_HEIGHT):
        print("  [x] 贴图尺寸与代码常量不一致！")
        ok = False

    w, h = PANEL_WIDTH, PANEL_HEIGHT

    groups = {
        "升级槽 2x%d" % UPGRADE_COLUMNS: [
            (UPGRADE_X + c * 18, UPGRADE_Y + r * 18)
            for r in range(2) for c in range(UPGRADE_COLUMNS)
        ],
        "输入元件槽 2x3": [
            (INPUT_X + c * 18, CELL_Y + r * 18)
            for r in range(3) for c in range(CELL_COLUMNS)
        ],
        "输出元件槽 2x3": [
            (OUTPUT_X + c * 18, CELL_Y + r * 18)
            for r in range(3) for c in range(CELL_COLUMNS)
        ],
        "玩家背包 3x9": [
            (PLAYER_X + c * 18, PLAYER_Y + r * 18)
            for r in range(3) for c in range(PLAYER_COLUMNS)
        ],
        "快捷栏 1x9": [
            (PLAYER_X + c * 18, HOTBAR_Y) for c in range(PLAYER_COLUMNS)
        ],
    }

    # 2) 每个槽位都必须完整落在面板内
    print()
    occupied = []
    for name, pts in groups.items():
        boxes = [slot_rect(x, y) for (x, y) in pts]
        x0 = min(b[0] for b in boxes)
        y0 = min(b[1] for b in boxes)
        x1 = max(b[2] for b in boxes)
        y1 = max(b[3] for b in boxes)
        inside = (x0 >= MARGIN and y0 >= MARGIN and x1 <= w - 1 - MARGIN and y1 <= h - 1 - MARGIN)
        flag = "OK" if inside else "*** 越界 ***"
        print("  %-14s x %3d..%3d   y %3d..%3d   %s" % (name, x0, x1, y0, y1, flag))
        if not inside:
            ok = False
        occupied.extend(boxes)

    # 3) 槽位之间不得重叠
    #
    # 注意：相邻槽位的边框会「共边」1 像素（a.x1 == b.x0），这不是重叠。
    # 因此只有真正交叠（交集面积 > 0）才算问题。
    print()
    overlaps = []
    for i in range(len(occupied)):
        for j in range(i + 1, len(occupied)):
            a, b = occupied[i], occupied[j]
            if a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]:
                overlaps.append((a, b))
    if overlaps:
        print("  [x] 槽位之间有 %d 处真实重叠，前 8 个：" % len(overlaps))
        for a, b in overlaps[:8]:
            print("        %s  <->  %s" % (a, b))
        ok = False
    else:
        print("  槽位无重叠  OK")

    # 4) 文字不要压在槽位上
    print()
    text_boxes = {
        "标题": (TITLE_X, TITLE_Y, TITLE_X + 100, TITLE_Y + 9),
        "速度信息": (INFO_X, INFO_Y, INFO_X + 160, INFO_Y + 9),
    }
    for name, tb in text_boxes.items():
        hit = 0
        for a in occupied:
            if a[0] < tb[2] and tb[0] < a[2] and a[1] < tb[3] and tb[1] < a[3]:
                hit += 1
        flag = "OK" if hit == 0 else "*** 压住 %d 个槽位 ***" % hit
        print("  %-10s y=%d..%d  %s" % (name, tb[1], tb[3], flag))
        if hit:
            ok = False

    print()
    if ok:
        print("  ==== 布局自检通过 ====")
        return 0
    print("  ==== 布局自检失败 ====")
    return 1


if __name__ == "__main__":
    sys.exit(main())
