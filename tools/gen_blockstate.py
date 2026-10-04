"""生成 super_io_port 的方块状态 JSON。

AE2 的 full() 方向策略会让方块同时拥有 facing 与 spin 属性，
并为「绕朝向轴旋转」使用自定义属性 `ae2:z`（不是原版 y）。
本脚本按 AE2 原版 io_port 的规律生成全部变体。

用法: python gen_blockstate.py <项目根目录>
"""
import json
import os
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "neoeco_io",
                   "blockstates", "super_io_port.json")

# facing -> (x 旋转, y 旋转)
XY = {
    "down": (90, 0),
    "up": (270, 0),
    "north": (0, 0),
    "south": (0, 180),
    "east": (0, 90),
    "west": (0, 270),
}


def z_rotation(facing, spin):
    """与 AE2 原版 io_port.json 完全一致的 z 值规律。"""
    if facing in ("down", "north"):
        return (90 * spin) % 360
    if facing in ("up", "south"):
        return (360 - 90 * spin) % 360
    # east / west
    return (270 - 90 * spin) % 360


def main():
    variants = {}
    for facing, (x, y) in XY.items():
        for powered in (False, True):
            model = "neoeco_io:block/super_io_port_powered" if powered else "neoeco_io:block/super_io_port"
            for spin in range(4):
                key = "facing=%s,powered=%s,spin=%d" % (facing, str(powered).lower(), spin)
                entry = {"model": model}
                if x:
                    entry["x"] = x
                if y:
                    entry["y"] = y
                z = z_rotation(facing, spin)
                if z:
                    entry["ae2:z"] = z
                variants[key] = entry

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"variants": variants}, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("已写入 %s（%d 个变体）" % (OUT, len(variants)))


if __name__ == "__main__":
    main()
