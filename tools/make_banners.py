"""Renders the banner artwork of the bot.

Run:  python3 make_banners.py <output-dir> [en|fa]

English banners go to src/main/resources/ui, Persian ones to src/main/resources/ui/fa.
Persian needs the Vazirmatn font installed (https://github.com/rastikerdar/vazirmatn).
"""
import asyncio
import random
import sys
from pathlib import Path

from PIL import Image
from playwright.async_api import async_playwright

INK = "#070C16"
NAVY = "#0E1828"
SLATE = "#1B2638"
CRIMSON = "#B4202E"
BRASS = "#C8A25A"
FOG = "#E4E7EC"

W = 1280

# Everything that depends on the language of the words on the banners.
LANGUAGES = {
    "en": {
        "font": "TeX Gyre Heros Cn",
        "rtl": False,
        "wordmark": "DECEPTION",
        "tagline": "A SOCIAL DEDUCTION<br>MURDER MYSTERY",
        "tape": "CRIME SCENE &#160;&#160;&#160; DO NOT CROSS &#160;&#160;&#160; ",
        "titles": {
            "home": ("DECEPTION", "MURDER IN HONG KONG"),
            "rooms": ("GAME ROOMS", "Take a seat. The case is waiting."),
            "briefing": ("HOW TO PLAY", "Field manual for new detectives"),
            "roles": ("GAME ROLES", "One of you is lying"),
            "records": ("RECORDS", "Statistics, leaderboard and profile"),
            "case-open": ("CASE OPEN", "The investigation begins"),
            "case-solved": ("CASE SOLVED", "The investigators win"),
            "case-cold": ("COLD CASE", "The murderer escapes"),
        },
        "stamps": {"solved": "SOLVED", "unsolved": "UNSOLVED"},
    },
    "fa": {
        "font": "Vazirmatn",
        "rtl": True,
        "wordmark": "فریب",
        "tagline": "یک معمای جنایی<br>برای بازی گروهی",
        "tape": "صحنه‌ی جرم &#160;&#160;&#160;&#160; عبور ممنوع &#160;&#160;&#160;&#160; ",
        "titles": {
            "home": ("فریب", "قتل در هنگ‌کنگ"),
            "rooms": ("اتاق‌های بازی", "بنشینید. پرونده منتظر است."),
            "briefing": ("راهنمای بازی", "دفترچه‌ی کارآگاه‌های تازه‌کار"),
            "roles": ("نقش‌ها", "یکی از شما دروغ می‌گوید"),
            "records": ("کارنامه", "آمار، جدول برترین‌ها و پروفایل"),
            "case-open": ("پرونده باز شد", "تحقیق آغاز می‌شود"),
            "case-solved": ("پرونده حل شد", "کارآگاه‌ها برنده شدند"),
            "case-cold": ("پرونده‌ی بی‌جواب", "قاتل فرار کرد"),
        },
        "stamps": {"solved": "حل شد", "unsolved": "حل نشد"},
    },
}
L = LANGUAGES["en"]


def sp(pixels):
    """Letter spacing. Persian letters are joined, so spacing them out would break the words."""
    return 0 if L["rtl"] else pixels


def skyline(seed, height, base_y, min_h, max_h, fill, window_chance, win_colors):
    """One layer of towers as SVG, with a few lit windows."""
    rnd = random.Random(seed)
    parts = []
    x = -20
    while x < W + 20:
        w = rnd.randint(34, 96)
        h = rnd.randint(min_h, max_h)
        top = base_y - h
        shape = rnd.random()
        if shape < 0.16:      # slanted roof
            cut = rnd.randint(14, 34)
            parts.append(f'<polygon points="{x},{base_y} {x},{top + cut} {x + w},{top} {x + w},{base_y}" fill="{fill}"/>')
        elif shape < 0.30:    # stepped crown with antenna
            parts.append(f'<rect x="{x}" y="{top}" width="{w}" height="{h}" fill="{fill}"/>')
            parts.append(f'<rect x="{x + w * 0.25:.0f}" y="{top - 16}" width="{w * 0.5:.0f}" height="16" fill="{fill}"/>')
            parts.append(f'<rect x="{x + w / 2 - 1:.0f}" y="{top - 52}" width="2" height="36" fill="{fill}"/>')
        else:
            parts.append(f'<rect x="{x}" y="{top}" width="{w}" height="{h}" fill="{fill}"/>')
        # windows
        for wy in range(top + 14, base_y - 8, 13):
            for wx in range(x + 7, x + w - 7, 11):
                if rnd.random() < window_chance:
                    color = rnd.choice(win_colors)
                    op = rnd.uniform(0.35, 0.95)
                    parts.append(f'<rect x="{wx}" y="{wy}" width="4" height="6" fill="{color}" opacity="{op:.2f}"/>')
        x += w + rnd.randint(-4, 10)
    return "".join(parts)


def landmark(x, base_y, fill):
    """A tall angular tower: the one silhouette people recognise a harbour city by."""
    top = base_y - 330
    return (
        f'<polygon points="{x},{base_y} {x},{top + 120} {x + 46},{top + 40} {x + 46},{top + 120} '
        f'{x + 92},{top + 80} {x + 92},{base_y}" fill="{fill}"/>'
        f'<rect x="{x + 20}" y="{top - 30}" width="2" height="78" fill="{fill}"/>'
        f'<rect x="{x + 34}" y="{top - 6}" width="2" height="52" fill="{fill}"/>'
        f'<line x1="{x}" y1="{top + 120}" x2="{x + 92}" y2="{top + 200}" stroke="{NAVY}" stroke-width="2"/>'
        f'<line x1="{x + 92}" y1="{top + 200}" x2="{x}" y2="{top + 280}" stroke="{NAVY}" stroke-width="2"/>'
    )


def rain(seed, height, count):
    rnd = random.Random(seed)
    lines = []
    for _ in range(count):
        x = rnd.randint(-80, W + 80)
        y = rnd.randint(-40, height)
        length = rnd.randint(18, 54)
        op = rnd.uniform(0.05, 0.22)
        lines.append(f'<line x1="{x}" y1="{y}" x2="{x - length * 0.28:.0f}" y2="{y + length}" '
                     f'stroke="{FOG}" stroke-width="1" opacity="{op:.2f}"/>')
    return "".join(lines)


def water(seed, top, height):
    rnd = random.Random(seed)
    parts = [f'<rect x="0" y="{top}" width="{W}" height="{height - top}" fill="{INK}"/>']
    for _ in range(170):
        y = rnd.randint(top + 4, height - 4)
        x = rnd.randint(0, W)
        length = rnd.randint(8, 60)
        color = rnd.choice([BRASS, BRASS, CRIMSON, FOG])
        op = rnd.uniform(0.05, 0.30) * (1 - (y - top) / (height - top) * 0.6)
        parts.append(f'<rect x="{x}" y="{y}" width="{length}" height="1.5" fill="{color}" opacity="{op:.2f}"/>')
    return "".join(parts)


def emblem(size):
    """Brand mark: a magnifying glass holding one drop."""
    return f'''
    <svg width="{size}" height="{size}" viewBox="0 0 100 100">
      <circle cx="42" cy="42" r="30" fill="none" stroke="{BRASS}" stroke-width="5"/>
      <line x1="64" y1="64" x2="92" y2="92" stroke="{BRASS}" stroke-width="9" stroke-linecap="round"/>
      <path d="M42 22 C52 36 56 42 56 49 A14 14 0 0 1 28 49 C28 42 32 36 42 22 Z" fill="{CRIMSON}"/>
    </svg>'''


def art_rooms():
    cells = "".join(
        f'<rect x="{40 + i * 62}" y="150" width="50" height="62" rx="4" fill="none" stroke="{BRASS}" stroke-width="2.5"/>'
        f'<circle cx="{65 + i * 62}" cy="181" r="5" fill="{BRASS}" opacity="0.75"/>'
        for i in range(4))
    return f'''<svg width="330" height="300" viewBox="0 0 330 300">
      <g transform="rotate(-7 165 150)">
        <path d="M30 70 L250 70 L300 105 L300 235 L30 235 Z" fill="{SLATE}" stroke="{BRASS}" stroke-width="3"/>
        <circle cx="268" cy="104" r="9" fill="{INK}" stroke="{BRASS}" stroke-width="2.5"/>
        <rect x="40" y="94" width="120" height="8" fill="{BRASS}" opacity="0.8"/>
        <rect x="40" y="112" width="74" height="5" fill="{FOG}" opacity="0.35"/>
        {cells}
      </g>
      <path d="M268 100 C300 40 330 60 322 12" fill="none" stroke="{CRIMSON}" stroke-width="3"/>
    </svg>'''


def art_briefing():
    lines = "".join(f'<rect x="88" y="{98 + i * 22}" width="{[150, 132, 158, 96, 140, 118][i]}" height="5" fill="{INK}" opacity="0.55"/>'
                    for i in range(6))
    return f'''<svg width="330" height="300" viewBox="0 0 330 300">
      <g transform="rotate(5 165 150)">
        <path d="M34 62 L134 62 L152 82 L296 82 L296 262 L34 262 Z" fill="{SLATE}" stroke="{BRASS}" stroke-width="3"/>
        <rect x="66" y="54" width="204" height="190" fill="{FOG}" opacity="0.92"/>
        <rect x="88" y="72" width="92" height="9" fill="{CRIMSON}"/>
        {lines}
        <path d="M250 40 L250 96 A10 10 0 0 1 230 96 L230 52" fill="none" stroke="{BRASS}" stroke-width="4" stroke-linecap="round"/>
      </g>
    </svg>'''


def art_roles():
    def bust(x, color, lift=0):
        return (f'<g transform="translate({x} {lift})">'
                f'<rect x="0" y="60" width="92" height="170" rx="6" fill="{SLATE}" stroke="{color}" stroke-width="3"/>'
                f'<circle cx="46" cy="118" r="22" fill="{color}"/>'
                f'<path d="M12 196 C12 160 80 160 80 196 L80 206 L12 206 Z" fill="{color}"/>'
                f'<rect x="14" y="74" width="40" height="5" fill="{color}" opacity="0.7"/></g>')
    return f'''<svg width="330" height="300" viewBox="0 0 330 300">
      <g transform="rotate(-6 80 150)">{bust(14, BRASS)}</g>
      <g transform="rotate(6 250 150)">{bust(224, BRASS)}</g>
      {bust(119, CRIMSON, -14)}
    </svg>'''


def art_records():
    arcs = "".join(
        f'<path d="M{165 - r} 190 A{r} {r * 1.18:.0f} 0 0 1 {165 + r} 190 L{165 + r} {190 + r * 0.5:.0f}" '
        f'fill="none" stroke="{BRASS if i != 2 else CRIMSON}" stroke-width="5" stroke-linecap="round" opacity="{0.95 - i * 0.07:.2f}"/>'
        for i, r in enumerate([18, 38, 58, 78, 98]))
    return f'''<svg width="330" height="300" viewBox="0 0 330 300">
      {arcs}
      <path d="M147 190 L147 250" stroke="{BRASS}" stroke-width="5" stroke-linecap="round"/>
      <path d="M165 176 L165 262" stroke="{BRASS}" stroke-width="5" stroke-linecap="round"/>
    </svg>'''


def stamp_fit(text):
    """Latin stamps are stretched to a fixed width. Joined Persian letters must not be stretched."""
    if L["rtl"]:
        return ""
    return f'textLength="{300 if len(text) > 6 else 250}" lengthAdjust="spacingAndGlyphs"'


def stamp(text, color):
    return f'''<svg width="430" height="300" viewBox="0 0 430 300">
      <g transform="rotate(-9 215 150)" opacity="0.94">
        <rect x="24" y="92" width="382" height="116" rx="10" fill="none" stroke="{color}" stroke-width="7"/>
        <rect x="36" y="104" width="358" height="92" rx="5" fill="none" stroke="{color}" stroke-width="2"/>
        <text x="215" y="176" text-anchor="middle" font-family="{L['font']}" font-weight="700"
              font-size="{66 if L['rtl'] else 78}" {stamp_fit(text)} fill="{color}">{text}</text>
      </g>
    </svg>'''


def tape():
    words = L["tape"] * 8
    return f'''<div style="position:absolute; left:-80px; right:-80px; top:92px; height:74px; background:{BRASS};
        transform:rotate({5 if L["rtl"] else -5}deg); box-shadow:0 14px 40px rgba(0,0,0,.6); overflow:hidden; white-space:nowrap;
        font:700 40px/74px '{L["font"]}'; letter-spacing:{sp(6)}px; color:{INK};">{words}</div>'''


def banners():
    """name: (height, title, subtitle, artwork, hero?) in the current language."""
    titles = L["titles"]
    layout = {
        "home": (640, "", True),
        "rooms": (460, art_rooms(), False),
        "briefing": (460, art_briefing(), False),
        "roles": (460, art_roles(), False),
        "records": (460, art_records(), False),
        "case-open": (560, tape(), False),
        "case-solved": (560, stamp(L["stamps"]["solved"], BRASS), False),
        "case-cold": (560, stamp(L["stamps"]["unsolved"], CRIMSON), False),
    }
    return {name: (height, titles[name][0], titles[name][1], art, hero)
            for name, (height, art, hero) in layout.items()}


def title_size(title):
    if L["rtl"]:
        return 96 if len(title) <= 11 else 84
    return 104 if len(title) <= 11 else 92


def page(name, height, title, subtitle, art, hero):
    # In a right-to-left language the words sit on the right and the artwork on the left.
    near_side, far_side = ("right", "left") if L["rtl"] else ("left", "right")
    direction = "rtl" if L["rtl"] else "ltr"
    mirror = "transform:scaleX(-1);" if L["rtl"] else ""
    seed = sum(ord(c) for c in name)
    horizon = int(height * (0.80 if hero else 0.84))
    far = skyline(seed + 1, height, horizon, 90, 270 if hero else 190, "#1C2C49", 0.06, [BRASS, FOG])
    near = skyline(seed + 2, height, horizon, 50, 190 if hero else 130, "#0A111E", 0.11, [BRASS, BRASS, BRASS, CRIMSON])
    tower = landmark(1010, horizon, "#22355A") if hero else ""

    if hero:
        text_block = f'''
        <div style="position:absolute; {near_side}:84px; top:104px; direction:{direction};">
          <div style="display:flex; align-items:center; gap:22px;">
            {emblem(92)}
            <div style="width:3px; height:76px; background:{CRIMSON};"></div>
            <div style="font:400 23px/1.25 '{L["font"]}'; letter-spacing:{sp(5)}px; color:{BRASS};">
              {L["tagline"]}</div>
          </div>
          <div style="margin-top:34px; font:700 156px/0.9 '{L["font"]}'; letter-spacing:{sp(10)}px; color:{FOG};
               text-shadow:0 6px 40px rgba(0,0,0,.65);">{title}</div>
          <div style="margin-top:26px; display:flex; align-items:center; gap:20px;">
            <div style="width:96px; height:4px; background:{CRIMSON};"></div>
            <div style="font:700 34px/1 '{L["font"]}'; letter-spacing:{sp(13)}px; color:{BRASS};">{subtitle}</div>
          </div>
        </div>'''
        art_block = ""
    else:
        stamp_like = name.startswith("case-") and name != "case-open"
        text_block = f'''
        <div style="position:absolute; {near_side}:72px; top:54px; display:flex; align-items:center; gap:14px; direction:{direction};">
          {emblem(44)}
          <div style="font:700 22px/1 '{L["font"]}'; letter-spacing:{sp(7)}px; color:{BRASS};">{L["wordmark"]}</div>
        </div>
        <div style="position:absolute; {near_side}:72px; bottom:{int(height * 0.26)}px; direction:{direction};">
          <div style="font:700 {title_size(title)}px/0.92 '{L["font"]}'; letter-spacing:{sp(6)}px; color:{FOG};
               text-shadow:0 6px 34px rgba(0,0,0,.7);">{title}</div>
          <div style="margin-top:20px; display:flex; align-items:center; gap:16px;">
            <div style="width:64px; height:4px; background:{CRIMSON};"></div>
            <div style="font:400 30px/1 '{L["font"]}'; letter-spacing:{sp(3)}px; color:{BRASS};">{subtitle}</div>
          </div>
        </div>'''
        if name == "case-open":
            art_block = art
        else:
            right = 40 if stamp_like else 70
            top = 70 if stamp_like else 62
            art_block = f'<div style="position:absolute; {far_side}:{right}px; top:{top}px;">{art}</div>'

    return f'''<!doctype html><html><head><meta charset="utf-8"><style>
      html,body{{margin:0;}}
      body{{width:{W}px;height:{height}px;position:relative;overflow:hidden;background:{INK};}}
    </style></head><body>
      <svg width="{W}" height="{height}" style="position:absolute;inset:0;{mirror}">
        <defs>
          <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stop-color="{INK}"/><stop offset="0.7" stop-color="{NAVY}"/>
            <stop offset="1" stop-color="#1A2030"/>
          </linearGradient>
          <radialGradient id="haze" cx="0.72" cy="1" r="0.75">
            <stop offset="0" stop-color="{CRIMSON}" stop-opacity="0.72"/>
            <stop offset="0.45" stop-color="{CRIMSON}" stop-opacity="0.22"/>
            <stop offset="1" stop-color="{CRIMSON}" stop-opacity="0"/>
          </radialGradient>
          <linearGradient id="shade" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0" stop-color="{INK}" stop-opacity="0.88"/>
            <stop offset="0.5" stop-color="{INK}" stop-opacity="0.38"/>
            <stop offset="1" stop-color="{INK}" stop-opacity="0"/>
          </linearGradient>
        </defs>
        <rect width="{W}" height="{horizon}" fill="url(#sky)"/>
        <rect width="{W}" height="{horizon}" fill="url(#haze)"/>
        {far}{tower}{near}
        {water(seed + 3, horizon, height)}
        <rect width="{W}" height="{height}" fill="url(#shade)"/>
        {rain(seed + 4, height, 260 if hero else 170)}
      </svg>
      {art_block}
      {text_block}
    </body></html>'''


async def main(out_dir, language):
    global L
    L = LANGUAGES[language]
    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)
    async with async_playwright() as pw:
        browser = await pw.chromium.launch()
        for name, (height, title, subtitle, art, hero) in banners().items():
            ctx = await browser.new_context(viewport={"width": W, "height": height})
            tab = await ctx.new_page()
            await tab.set_content(page(name, height, title, subtitle, art, hero))
            await tab.wait_for_timeout(250)
            png = out / f"banner-{name}.png"
            await tab.screenshot(path=str(png))
            Image.open(png).convert("RGB").save(out / f"banner-{name}.jpg", quality=90, optimize=True)
            png.unlink()
            await ctx.close()
        await browser.close()


if __name__ == "__main__":
    asyncio.run(main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else "en"))
