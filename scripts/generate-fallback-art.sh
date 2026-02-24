#!/bin/bash
# Generate fallback art for each mana color using local Stable Diffusion

SD_URL="${SD_URL:-http://127.0.0.1:7860}"
OUTPUT_DIR="../rider-plugin/src/main/resources/art/fallback"

mkdir -p "$OUTPUT_DIR"

# Common style elements
STYLE="traditional fantasy oil painting, masterwork illustration, painted with confident brushstrokes, rich color depth, sharp defined edges, clear focal point, crisp details, professional fantasy book cover art, Magic the Gathering card art quality, by Donato Giancola, Terese Nielsen, Todd Lockwood"
NEGATIVE="photograph, photo, photorealistic, 3d render, CGI, anime, cartoon, text, watermark, blurry, ugly, deformed, modern, plain background"

generate_art() {
    local name=$1
    local prompt=$2
    local output_file=$3

    echo "Generating $name..."

    response=$(curl -s -X POST "$SD_URL/sdapi/v1/txt2img" \
        -H "Content-Type: application/json" \
        -d "{
            \"prompt\": \"$prompt, $STYLE\",
            \"negative_prompt\": \"$NEGATIVE\",
            \"steps\": 40,
            \"cfg_scale\": 6.5,
            \"width\": 768,
            \"height\": 768,
            \"sampler_name\": \"DPM++ 2M Karras\",
            \"enable_hr\": true,
            \"hr_scale\": 1.5,
            \"hr_upscaler\": \"Latent\",
            \"hr_second_pass_steps\": 15,
            \"denoising_strength\": 0.4
        }")

    # Extract base64 image and save
    echo "$response" | jq -r '.images[0]' | base64 -d > "$OUTPUT_DIR/$output_file"
    echo "Saved $OUTPUT_DIR/$output_file"
}

# Blue - Scholar
generate_art "Blue Scholar" \
    "detailed fantasy portrait of a wise scholar mage in flowing blue robes, holding ancient tome with glowing arcane runes, mystical library background with floating books, deep blue and silver color scheme, ethereal mist, moonlit atmosphere, cool magical glow" \
    "blue_scholar.png"

# White - Knight
generate_art "White Knight" \
    "detailed fantasy portrait of a noble paladin knight in gleaming white and gold plate armor, radiant holy aura, wielding blessed sword, marble temple background with divine light rays through stained glass, warm white cream and gold color scheme, celestial radiance, dawn lighting" \
    "white_knight.png"

# Green - Elf
generate_art "Green Elf" \
    "detailed fantasy portrait of a wise ancient elf druid with pointed ears, wearing nature-woven robes with leaves and vines, standing in primordial ancient forest with massive trees, forest green brown and emerald color scheme, dappled sunlight, wild untamed primal energy" \
    "green_elf.png"

# Black - Vampire
generate_art "Black Vampire" \
    "detailed fantasy portrait of an elegant vampire lord with pale skin and crimson eyes, wearing dark ornate noble attire, cursed gothic castle background with full moon, dark purple sickly green and black color scheme, ominous shadows, eerie necrotic atmosphere" \
    "black_vampire.png"

# Red - Goblin
generate_art "Red Goblin" \
    "detailed fantasy portrait of a fierce goblin pyromancer with wild eyes and sharp teeth, surrounded by swirling flames and fire magic, volcanic mountain fortress background with lava, fiery red orange and crimson color scheme, blazing explosive energy, intense dramatic lighting" \
    "red_goblin.png"

echo "Done! Generated 5 fallback images in $OUTPUT_DIR"
