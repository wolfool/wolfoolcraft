# -*- coding: utf-8 -*-
"""
제작대 가구 모델 생성기 — CraftEngine 용

CraftEngine 가구는 바닐라 아이템 모델(JSON) 을 그대로 쓴다. Simmer 처럼 bbmodel 이
아니라서, 여기서는 큐브 목록을 vanilla model JSON 으로 바로 뽑는다.

만드는 것
  out/workbench.json          모델
  out/workbench.png           64x64 텍스처 아틀라스

모양: 나무 작업대. 다리 넷 + 상판 + 아래 선반 + 상판 위 청사진 + 망치.
"""
import io, json, os

from PIL import Image, ImageDraw

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "out")
os.makedirs(OUT, exist_ok=True)
RES = 64

# ---------------------------------------------------------------- 텍스처
img = Image.new("RGBA", (RES, RES), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# 아틀라스는 16x16 타일 4x4 칸으로 나눠 쓴다. (tx, ty) = 타일 좌표
TILE = 16


def tile_box(tx, ty):
    return (tx * TILE, ty * TILE, tx * TILE + TILE - 1, ty * TILE + TILE - 1)


def noise(tx, ty, base, spots, seed):
    """나뭇결처럼 보이도록 점을 흩뿌린다. random 대신 고정 수열이라 매번 같은 그림이 나온다."""
    x0, y0, x1, y1 = tile_box(tx, ty)
    d.rectangle([x0, y0, x1, y1], fill=base)
    v = seed
    for _ in range(spots):
        v = (v * 1103515245 + 12345) & 0x7FFFFFFF
        px = x0 + (v >> 7) % TILE
        py = y0 + (v >> 15) % TILE
        c = ((v >> 3) & 1)
        shade = tuple(max(0, min(255, ch + (10 if c else -12))) for ch in base[:3]) + (255,)
        d.point((px, py), fill=shade)


# (0,0) 짙은 나무 - 다리와 테두리
noise(0, 0, (0x6B, 0x47, 0x2A, 255), 70, 7)
# (1,0) 밝은 나무 - 상판
noise(1, 0, (0xB1, 0x83, 0x4F, 255), 70, 21)
for i in range(TILE):  # 판자 이음매
    d.point((TILE + i, 5), fill=(0x93, 0x6B, 0x3F, 255))
    d.point((TILE + i, 11), fill=(0x93, 0x6B, 0x3F, 255))
# (2,0) 청사진
x0, y0, x1, y1 = tile_box(2, 0)
d.rectangle([x0, y0, x1, y1], fill=(0x3B, 0x6E, 0xC4, 255))
d.rectangle([x0 + 2, y0 + 2, x1 - 2, y1 - 2], outline=(0xD8, 0xE4, 0xF7, 255))
d.rectangle([x0 + 5, y0 + 5, x1 - 5, y1 - 5], outline=(0xD8, 0xE4, 0xF7, 255))
d.line([x0 + 8, y0 + 5, x0 + 8, y1 - 5], fill=(0xD8, 0xE4, 0xF7, 255))
# (3,0) 금속 - 망치 머리
noise(3, 0, (0x8A, 0x91, 0x99, 255), 40, 33)
# (0,1) 손잡이 나무
noise(0, 1, (0x8C, 0x5B, 0x33, 255), 40, 51)

img.save(os.path.join(OUT, "workbench.png"))

# ---------------------------------------------------------------- 모델
FACES = ("north", "east", "south", "west", "up", "down")
# 재질 이름 -> 아틀라스 UV (0~16 단위. 바닐라 모델 UV 는 텍스처 크기와 무관하게 0~16)
UV = {
    "dark":      (0, 0, 4, 4),
    "top":       (4, 0, 8, 4),
    "blueprint": (8, 0, 12, 4),
    "metal":     (12, 0, 16, 4),
    "handle":    (0, 4, 4, 8),
}

elements = []


def cube(frm, to, mat, faces=None, rotation=None):
    """큐브 하나. faces 를 주면 그 면만 다른 재질로 덮는다."""
    e = {"from": list(frm), "to": list(to), "faces": {}}
    for f in FACES:
        m = (faces or {}).get(f, mat)
        e["faces"][f] = {"uv": list(UV[m]), "texture": "#0"}
    if rotation:
        e["rotation"] = rotation
    elements.append(e)


# 다리 4개
for lx, lz in ((1, 1), (12, 1), (1, 12), (12, 12)):
    cube((lx, 0, lz), (lx + 3, 10, lz + 3), "dark")

# 아래 선반
cube((2, 4, 2), (14, 5, 14), "dark")

# 상판 (조금 넓게 빼서 테이블처럼)
cube((0, 10, 0), (16, 12, 16), "dark", faces={"up": "top"})

# 상판 위 청사진
cube((3, 12, 4), (12, 12.2, 13), "blueprint")

# 망치: 손잡이 + 머리 (살짝 눕혀 놓은 모양)
cube((10, 12.2, 3), (15, 13, 4), "handle")
cube((13.5, 12.2, 2), (15.5, 14.2, 5), "metal")

model = {
    "credit": "wolfoolcraft/make_workbench.py",
    "texture_size": [RES, RES],
    "textures": {"0": "wolfoolcraft:item/workbench", "particle": "wolfoolcraft:item/workbench"},
    "elements": elements,
    "display": {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
        "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        # 가구는 이 fixed 변환으로 그려진다. 배율을 1 로 둬야
        # '가구 yml 의 scale = 블록 단위 크기' 가 되어 높이 계산이 단순해진다.
        # 여기에 0.5 같은 값을 넣으면 실제 크기가 그만큼 줄어서,
        # translation 을 크기의 절반으로 맞춰도 물건이 공중에 뜬다.
        "fixed": {"scale": [1, 1, 1]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                                  "scale": [0.4, 0.4, 0.4]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
    },
}

path = os.path.join(OUT, "workbench.json")
io.open(path, "w", encoding="utf-8").write(json.dumps(model, ensure_ascii=False, indent=2))
print("모델 %d큐브 -> %s" % (len(elements), path))
print("텍스처 %dx%d -> %s" % (RES, RES, os.path.join(OUT, "workbench.png")))
