"""demo_video.sh가 남긴 녹화 조각을 잇고 captions.tsv의 말·장면 이름을 자막으로 입힌다.

사용: python3 burn_captions.py <녹화 폴더> <결과 mp4>
"""

import re
import subprocess
import sys
from pathlib import Path

FONT = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"


def main(folder: Path, out: Path) -> None:
    segments = sorted(folder.glob("seg*.mp4"), key=lambda p: int(re.sub(r"\D", "", p.stem)))
    if not segments:
        sys.exit("녹화 조각이 없음")
    listing = folder / "segments.txt"
    listing.write_text("".join(f"file '{p.resolve()}'\n" for p in segments), encoding="utf-8")
    joined = folder / "joined.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy", str(joined)], check=True)

    height = int(subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=height", "-of", "csv=p=0", str(joined)],
        check=True, capture_output=True, text=True).stdout.strip())
    # 에뮬레이터 상태 표시줄(24/1440)과 작업 표시줄(60/1440)은 검게 덮고, 장면 이름은 아래 띠 왼쪽에 쓴다
    top, bottom = round(height * 24 / 1440), round(height * 60 / 1440)
    say_size, scene_size = round(height * 0.042), round(height * 0.024)
    filters = [
        f"drawbox=x=0:y=0:w=iw:h={top}:color=black:t=fill",
        f"drawbox=x=0:y=ih-{bottom}:w=iw:h={bottom}:color=black:t=fill",
    ]
    texts = folder / "captions"
    texts.mkdir(exist_ok=True)
    for i, line in enumerate((folder / "captions.tsv").read_text(encoding="utf-8").splitlines()):
        start, end, kind, text = line.split("\t", 3)
        # 글은 파일로 넘겨 따옴표·쌍점을 이스케이프하지 않는다
        path = texts / f"{i}.txt"
        path.write_text(text, encoding="utf-8")
        when = f"enable='between(t,{start},{end})'"
        if kind == "say":
            filters.append(
                f"drawtext=fontfile={FONT}:textfile={path}:fontsize={say_size}:fontcolor=white:"
                f"box=1:boxcolor=black@0.65:boxborderw={say_size // 2}:x=(w-text_w)/2:y=h-{bottom}-text_h-{say_size * 2}:{when}"
            )
        else:
            filters.append(
                f"drawtext=fontfile={FONT}:textfile={path}:fontsize={scene_size}:fontcolor=0xFF8A3D:"
                f"x={scene_size * 2}:y=h-{bottom}+({bottom}-text_h)/2:{when}"
            )
    script = folder / "filters.txt"
    script.write_text(",\n".join(filters) or "null", encoding="utf-8")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", str(joined), "-filter_complex_script", str(script),
         "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(out)],
        check=True,
    )
    print("만듦:", out)


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
