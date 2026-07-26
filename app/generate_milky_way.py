import random
import math
import os

random.seed(1337)

def get_spine_x(y):
    # Galactic axis line from top (360, 0) to bottom (450, 1376)
    progress = y / 1376.0
    base_x = 350.0 + progress * 110.0
    # Soft wobble curve
    wobble = 25.0 * math.sin(progress * math.pi)
    return base_x + wobble

def hex_alpha(val_float):
    i = int(max(0, min(255, val_float * 255)))
    return f"{i:02X}"

lines = []
lines.append('<?xml version="1.0" encoding="utf-8"?>')
lines.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
lines.append('    android:width="768dp"')
lines.append('    android:height="1376dp"')
lines.append('    android:viewportWidth="768"')
lines.append('    android:viewportHeight="1376">')

# 1. Base Deep Cosmic Space Gradient
lines.append("""
    <!-- Base Deep Space Sky Gradient -->
    <path android:pathData="M0,0h768v1376h-768z">
        <gradient
            android:startX="384"
            android:startY="0"
            android:endX="384"
            android:endY="1376"
            android:type="linear"
            android:startColor="#FF010106"
            android:centerColor="#FF070C20"
            android:endColor="#FF020107" />
    </path>
""")

# 2. Outer Deep Blue/Indigo Cosmic Gas
lines.append("""
    <!-- Broad Cosmic Nebula Outer Glow -->
    <path android:pathData="M140,0 L580,0 L680,1376 L180,1376 Z">
        <gradient
            android:startX="350"
            android:startY="100"
            android:endX="460"
            android:endY="1250"
            android:type="linear"
            android:startColor="#281A2B56"
            android:centerColor="#4C3A2466"
            android:endColor="#28121B42" />
    </path>
""")

# 3. Magenta/Violet Galactic Bulge Cloud
lines.append("""
    <!-- Central Galactic Bulge Magenta Glow -->
    <path android:pathData="M 100,640 C 160,400 600,400 660,640 C 600,880 160,880 100,640 Z">
        <gradient
            android:centerX="380"
            android:centerY="640"
            android:gradientRadius="300"
            android:type="radial"
            android:startColor="#809A387E"
            android:centerColor="#40582068"
            android:endColor="#00000000" />
    </path>
""")

# 4. Golden / Amber Warm Dust Glow Layers
lines.append("""
    <!-- Bright Golden Core Outer Cloud -->
    <path android:pathData="M 180,640 C 220,460 540,460 580,640 C 540,820 220,820 180,640 Z">
        <gradient
            android:centerX="380"
            android:centerY="635"
            android:gradientRadius="230"
            android:type="radial"
            android:startColor="#C0FFA23D"
            android:centerColor="#68FFC05C"
            android:endColor="#00000000" />
    </path>

    <!-- Hot Golden Core Center -->
    <path android:pathData="M 240,635 C 270,500 490,500 520,635 C 490,770 270,770 240,635 Z">
        <gradient
            android:centerX="380"
            android:centerY="630"
            android:gradientRadius="150"
            android:type="radial"
            android:startColor="#E6FFE5B0"
            android:centerColor="#90FFCC75"
            android:endColor="#00000000" />
    </path>

    <!-- White-Hot Galactic Center Nucleus -->
    <path android:pathData="M 300,630 C 320,550 440,560 460,630 C 440,710 320,710 300,630 Z">
        <gradient
            android:centerX="380"
            android:centerY="630"
            android:gradientRadius="90"
            android:type="radial"
            android:startColor="#FFFFFFFF"
            android:centerColor="#D4FFF2C6"
            android:endColor="#00FFFFFF" />
    </path>
""")

# 5. Upper and Lower Spine Nebulae
lines.append("""
    <!-- Upper Spine Golden Glow -->
    <path android:pathData="M 270,300 C 310,180 430,180 470,300 C 430,420 310,420 270,300 Z">
        <gradient
            android:centerX="370"
            android:centerY="290"
            android:gradientRadius="140"
            android:type="radial"
            android:startColor="#70FFA852"
            android:endColor="#00000000" />
    </path>

    <!-- Lower Spine Amber Glow -->
    <path android:pathData="M 310,960 C 350,830 490,830 530,960 C 490,1090 350,1090 310,960 Z">
        <gradient
            android:centerX="420"
            android:centerY="960"
            android:gradientRadius="150"
            android:type="radial"
            android:startColor="#60FF9933"
            android:endColor="#00000000" />
    </path>
""")

# 6. Dark Interstellar Dust Lanes
lines.append("""
    <!-- Dark Interstellar Rift Lanes -->
    <path
        android:fillColor="#E8030208"
        android:pathData="M 340,380 Q 380,480 360,580 T 410,780 Q 440,900 420,1080 L 455,1080 Q 475,900 435,780 T 425,580 Q 435,480 380,380 Z" />

    <path
        android:fillColor="#C005030B"
        android:pathData="M 310,480 Q 350,560 330,640 T 380,780 Q 400,880 390,1000 L 415,1000 Q 420,880 400,780 T 360,640 Q 370,560 335,480 Z" />

    <path
        android:fillColor="#A0020105"
        android:pathData="M 370,540 Q 410,620 390,720 L 420,720 Q 435,620 395,540 Z" />
""")

# 7. Dense Star Field Generation
lines.append("    <!-- Star Field Layer -->")

star_colors = [
    ("FFFFFF", 0.45), # Crisp white
    ("FFE8C2", 0.25), # Warm gold star
    ("DCEEFF", 0.20), # Cool ice blue star
    ("FAD2FF", 0.10)  # Magenta tinted star
]

total_stars = 750

for i in range(total_stars):
    # 65% concentrated along the galactic spine, 35% scattered everywhere
    if random.random() < 0.65:
        y = random.uniform(0, 1376)
        spine_x = get_spine_x(y)
        # Distance from spine follow Gaussian spread
        offset_x = random.gauss(0, 75.0)
        x = max(0, min(768, spine_x + offset_x))
    else:
        x = random.uniform(0, 768)
        y = random.uniform(0, 1376)

    # Determine star color
    r_color = random.random()
    cum = 0
    color_hex = "FFFFFF"
    for c_hex, prob in star_colors:
        cum += prob
        if r_color <= cum:
            color_hex = c_hex
            break

    # Determine star size
    r_size = random.random()
    if r_size < 0.70:
        # Faint tiny star
        radius = random.uniform(0.4, 0.9)
        alpha = random.uniform(0.35, 0.75)
        lines.append(f'    <path android:fillColor="#{hex_alpha(alpha)}{color_hex}" android:pathData="M{x:.1f},{y:.1f} a{radius:.1f},{radius:.1f} 0 1,0 {radius*2:.1f},0 a{radius:.1f},{radius:.1f} 0 1,0 -{radius*2:.1f},0Z" />')
    elif r_size < 0.94:
        # Medium star
        radius = random.uniform(1.0, 1.6)
        alpha = random.uniform(0.70, 0.95)
        lines.append(f'    <path android:fillColor="#{hex_alpha(alpha)}{color_hex}" android:pathData="M{x:.1f},{y:.1f} a{radius:.1f},{radius:.1f} 0 1,0 {radius*2:.1f},0 a{radius:.1f},{radius:.1f} 0 1,0 -{radius*2:.1f},0Z" />')
    else:
        # Bright major star with soft glow ring!
        radius = random.uniform(1.8, 2.8)
        alpha = random.uniform(0.85, 1.0)
        glow_r = radius * 2.5
        lines.append(f'    <path android:fillColor="#25{color_hex}" android:pathData="M{x-glow_r+radius:.1f},{y:.1f} a{glow_r:.1f},{glow_r:.1f} 0 1,0 {glow_r*2:.1f},0 a{glow_r:.1f},{glow_r:.1f} 0 1,0 -{glow_r*2:.1f},0Z" />')
        lines.append(f'    <path android:fillColor="#{hex_alpha(alpha)}{color_hex}" android:pathData="M{x:.1f},{y:.1f} a{radius:.1f},{radius:.1f} 0 1,0 {radius*2:.1f},0 a{radius:.1f},{radius:.1f} 0 1,0 -{radius*2:.1f},0Z" />')

# 8. Special 4-Point Lens Flare Stars
flare_stars = [
    (380, 630, 12.0, "FFFFFF"), # Core Nucleus Flare
    (365, 590, 8.0, "FFE2A3"),
    (410, 680, 9.0, "DCEEFF"),
    (350, 310, 10.0, "FFC870"),
    (425, 950, 8.5, "FFFFFF"),
    (220, 200, 7.0, "E2EEFF"),
    (560, 1150, 7.5, "FFF2C6"),
    (610, 420, 6.5, "FFFFFF"),
    (180, 920, 6.0, "D8E6FF")
]

lines.append("    <!-- Lens Flare Stars -->")
for fx, fy, fsize, fcolor in flare_stars:
    d = f"M {fx:.1f},{fy-fsize:.1f} Q {fx:.1f},{fy:.1f} {fx+fsize:.1f},{fy:.1f} Q {fx:.1f},{fy:.1f} {fx:.1f},{fy+fsize:.1f} Q {fx:.1f},{fy:.1f} {fx-fsize:.1f},{fy:.1f} Q {fx:.1f},{fy:.1f} {fx:.1f},{fy-fsize:.1f} Z"
    d_outer = f"M {fx:.1f},{fy-fsize*2.2:.1f} Q {fx:.1f},{fy:.1f} {fx+fsize*2.2:.1f},{fy:.1f} Q {fx:.1f},{fy:.1f} {fx:.1f},{fy+fsize*2.2:.1f} Q {fx:.1f},{fy:.1f} {fx-fsize*2.2:.1f},{fy:.1f} Q {fx:.1f},{fy:.1f} {fx:.1f},{fy-fsize*2.2:.1f} Z"
    lines.append(f'    <path android:fillColor="#30{fcolor}" android:pathData="{d_outer}" />')
    lines.append(f'    <path android:fillColor="#EE{fcolor}" android:pathData="{d}" />')

lines.append("</vector>")

out_path = "app/src/main/res/drawable/bg_library_svg.xml"
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w") as f:
    f.write("\n".join(lines))

print(f"Successfully generated vector at {out_path}")
