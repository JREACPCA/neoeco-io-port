"""生成 Neo ECO IO Port 的全部贴图资源。

用法: python gen_textures.py <项目根目录>
"""
import os
import sys

from PIL import Image, ImageDraw

# ---------------------------------------------------------------- 调色板
DARK = (28, 32, 38, 255)        # 最暗
BASE = (58, 66, 76, 255)        # 主体金属
LIGHT = (96, 108, 122, 255)     # 高光
PANEL = (16, 20, 24, 255)       # 屏幕底
CYAN = (72, 216, 232, 255)      # 主强调色
CYAN_D = (26, 118, 138, 255)    # 强调色暗部
AMBER = (236, 178, 78, 255)     # 输出侧标记


def new_tex(size=16, color=BASE):
    return Image.new("RGBA", (size, size), color)


def bevel(img, x0, y0, x1, y1, light=LIGHT, dark=DARK):
    """给矩形区域加上 1 像素立体高光/阴影。"""
    d = ImageDraw.Draw(img)
    d.line([(x0, y0), (x1, y0)], fill=light)
    d.line([(x0, y0), (x0, y1)], fill=light)
    d.line([(x0, y1), (x1, y1)], fill=dark)
    d.line([(x1, y0), (x1, y1)], fill=dark)


def plate(fill=BASE, light=LIGHT, dark=DARK):
    """16x16 通用金属板，带 1 像素外框立体感。"""
    img = new_tex(color=fill)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=dark)
    d.line([(1, 1), (14, 1)], fill=light)
    d.line([(1, 1), (1, 14)], fill=light)
    # 四角铆钉
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=dark if (x + y) % 2 else light)
    return img


def tex_front():
    """正面：中央屏幕 + 箭头指示 + 状态灯。"""
    img = plate()
    d = ImageDraw.Draw(img)

    # 屏幕凹陷
    d.rectangle([3, 3, 12, 12], fill=PANEL, outline=DARK)
    d.line([(3, 3), (12, 3)], fill=DARK)

    # 水平流动的箭头（左 → 右），示意搬运
    d.line([(4, 8), (11, 8)], fill=CYAN_D)
    d.point((11, 7), fill=CYAN)
    d.point((11, 9), fill=CYAN)
    d.point((10, 8), fill=CYAN)

    # 上下两条数据槽
    d.line([(5, 5), (10, 5)], fill=CYAN_D)
    d.line([(5, 11), (10, 11)], fill=CYAN_D)

    # 顶部状态灯
    d.rectangle([6, 1, 9, 1], fill=CYAN)
    return img


def tex_front_powered():
    """通电正面：指示灯更亮。"""
    img = tex_front()
    d = ImageDraw.Draw(img)
    d.rectangle([6, 1, 9, 1], fill=(190, 255, 255, 255))
    d.rectangle([3, 3, 12, 12], outline=CYAN_D)
    return img


def tex_top():
    """顶面：散热格栅 + 强调色灯带。"""
    img = plate()
    d = ImageDraw.Draw(img)
    d.rectangle([2, 2, 13, 13], fill=(46, 53, 62, 255), outline=DARK)
    for y in range(4, 13, 3):
        d.line([(3, y), (12, y)], fill=DARK)
        d.line([(3, y + 1), (12, y + 1)], fill=(70, 80, 92, 255))
    d.point((3, 3), fill=CYAN)
    d.point((12, 3), fill=CYAN)
    d.point((3, 12), fill=CYAN)
    d.point((12, 12), fill=CYAN)
    return img


def tex_side():
    """侧面：纵向散热槽 + 输出侧琥珀标记。"""
    img = plate()
    d = ImageDraw.Draw(img)
    for x in range(4, 12, 3):
        d.line([(x, 3), (x, 12)], fill=DARK)
        d.line([(x + 1, 3), (x + 1, 12)], fill=(78, 88, 100, 255))
    d.point((8, 14), fill=AMBER)
    return img


def tex_back():
    """背面：纯金属板 + 中央通风口。"""
    img = plate()
    d = ImageDraw.Draw(img)
    d.rectangle([5, 5, 10, 10], outline=DARK)
    d.point((7, 7), fill=DARK)
    d.point((8, 8), fill=DARK)
    return img


def tex_bottom():
    img = plate()
    d = ImageDraw.Draw(img)
    d.rectangle([6, 6, 9, 9], outline=DARK)
    return img


def tex_item():
    """物品栏图标：略带等距感的本体缩略图。"""
    img = plate()
    d = ImageDraw.Draw(img)
    # 顶面（亮）
    d.polygon([(2, 6), (8, 2), (14, 6), (8, 10)], fill=(86, 98, 112, 255))
    # 正面（暗）
    d.polygon([(2, 6), (8, 10), (8, 15), (2, 11)], fill=(52, 60, 70, 255))
    # 右侧面
    d.polygon([(14, 6), (8, 10), (8, 15), (14, 11)], fill=(40, 47, 56, 255))
    # 发光屏幕
    d.polygon([(4, 7), (8, 9), (8, 13), (4, 11)], outline=CYAN)
    d.line([(9, 9), (12, 7)], fill=CYAN)
    d.point((8, 2), fill=CYAN)
    return img


def tex_gui(w=200, h=210):
    """容器界面背景。

    布局必须与 SuperIOPortScreen 里的坐标常量逐一对齐
    （那里的坐标是槽位内容区左上角，这里绘制的是 x-1..x+16 的整格）。

      y=7    标题文字起点 (8, 7)
      y=22   升级槽 ×7             x = 27 + i*18        （到 x=27+6*18+16=151）
      y=44   分区线
      y=56   「速度」信息文字 (8, 32)
      y=52   输入元件槽 2×3        x = 30 + col*18, y = 52 + row*18
      y=52   输出元件槽 2×3        x = 140 + col*18
      y=118  分区线
      y=130  玩家背包 3×9          x = 10 + col*18, y = 130 + row*18
      y=188  快捷栏 1×9            x = 10 + col*18
    """
    img = Image.new("RGBA", (w, h), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)

    # 外框
    d.rectangle([0, 0, w - 1, h - 1], fill=(198, 198, 198, 255))
    d.rectangle([0, 0, w - 1, h - 1], outline=(85, 85, 85, 255))
    d.line([(1, 1), (w - 2, 1)], fill=(255, 255, 255, 255))
    d.line([(1, 1), (1, h - 2)], fill=(255, 255, 255, 255))
    d.line([(1, h - 2), (w - 2, h - 2)], fill=(60, 60, 60, 255))
    d.line([(w - 2, 1), (w - 2, h - 2)], fill=(60, 60, 60, 255))
    d.rectangle([3, 3, w - 4, h - 4], outline=(150, 150, 150, 255))

    def slot(cx, cy):
        """在槽位内容区左上角 (cx, cy) 绘制 16x16 凹陷槽。"""
        d.rectangle([cx - 1, cy - 1, cx + 16, cy + 16], fill=(139, 139, 139, 255))
        d.line([(cx - 1, cy - 1), (cx + 16, cy - 1)], fill=(55, 55, 55, 255))
        d.line([(cx - 1, cy - 1), (cx - 1, cy + 16)], fill=(55, 55, 55, 255))
        d.line([(cx - 1, cy + 16), (cx + 16, cy + 16)], fill=(255, 255, 255, 255))
        d.line([(cx + 16, cy - 1), (cx + 16, cy + 16)], fill=(255, 255, 255, 255))

    def divider(y):
        """横向分隔线，用于区分功能分区。"""
        d.line([(5, y), (w - 6, y)], fill=(150, 150, 150, 255))
        d.line([(5, y + 1), (w - 6, y + 1)], fill=(255, 255, 255, 255))

    # 标题下划线
    d.line([(5, 20), (w - 6, 20)], fill=(150, 150, 150, 255))

    # 升级卡槽：顶部一行 7 格
    for i in range(7):
        slot(27 + i * 18, 22)

    # 分区：升级区 / 搬运区
    divider(44)

    # 输入元件槽 2 列 × 3 行
    for row in range(3):
        for col in range(2):
            slot(30 + col * 18, 52 + row * 18)

    # 输出元件槽 2 列 × 3 行
    for row in range(3):
        for col in range(2):
            slot(140 + col * 18, 52 + row * 18)

    # 中间箭头指示（输入 → 输出）
    d.polygon([(94, 60), (112, 70), (94, 80)], fill=(120, 120, 120, 255))
    d.polygon([(98, 63), (109, 70), (98, 77)], fill=(90, 90, 90, 255))

    # 分区：机器区 / 玩家背包
    divider(118)

    # 玩家背包 3 行 × 9 列
    for row in range(3):
        for col in range(9):
            slot(10 + col * 18, 130 + row * 18)

    # 快捷栏 1 行 × 9 列
    for col in range(9):
        slot(10 + col * 18, 188)

    return img


def tex_logo(size=128):
    """模组 logo。"""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = 8
    d.rectangle([pad, pad, size - pad, size - pad], fill=BASE, outline=DARK, width=3)
    d.rectangle([pad + 6, pad + 6, size - pad - 6, size - pad - 6], fill=PANEL)
    # 大箭头
    cy = size // 2
    d.line([(28, cy), (76, cy)], fill=CYAN_D, width=6)
    d.polygon([(74, cy - 16), (100, cy), (74, cy + 16)], fill=CYAN)
    # 顶部指示灯
    d.rectangle([30, 18, 98, 26], fill=CYAN_D)
    d.rectangle([34, 20, 94, 24], fill=CYAN)
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("  " + os.path.relpath(path, ROOT))


ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "neoeco_io")

if __name__ == "__main__":
    print("生成方块贴图:")
    save(tex_front(), os.path.join(ASSETS, "textures", "block", "super_io_port_front.png"))
    save(tex_front_powered(), os.path.join(ASSETS, "textures", "block", "super_io_port_front_powered.png"))
    save(tex_top(), os.path.join(ASSETS, "textures", "block", "super_io_port_top.png"))
    save(tex_side(), os.path.join(ASSETS, "textures", "block", "super_io_port_side.png"))
    save(tex_back(), os.path.join(ASSETS, "textures", "block", "super_io_port_back.png"))
    save(tex_bottom(), os.path.join(ASSETS, "textures", "block", "super_io_port_bottom.png"))

    print("生成物品贴图:")
    save(tex_item(), os.path.join(ASSETS, "textures", "item", "super_io_port.png"))

    print("生成界面贴图:")
    save(tex_gui(), os.path.join(ASSETS, "textures", "guis", "super_io_port.png"))

    print("生成 logo:")
    save(tex_logo(), os.path.join(ASSETS, "neoeco_io.png"))

    print("完成。")
