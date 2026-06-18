"""Download the Fresh and Stale Classification dataset from Kaggle using kagglehub.

Dataset: swoyam2609/fresh-and-stale-classification
Content: 9 fruits/vegetables x 2 states (fresh/stale) = 18 classes
Train: ~23,619 images, Test: ~6,738 images

Usage:
    python training/download_dataset.py

Note: Dataset directories are automatically renamed to standard
fresh_X/rotten_X format after download.
"""

import os
import sys
import shutil

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import kagglehub

DATASET_PATH = "swoyam2609/fresh-and-stale-classification"
LOCAL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")

# Directory rename map: dataset uses nonstandard names, fix to fresh_X/rotten_X
RENAME_MAP = {
    "freshapples": "fresh_apple", "rottenapples": "rotten_apple",
    "freshbanana": "fresh_banana", "rottenbanana": "rotten_banana",
    "freshbittergroud": "fresh_bittergourd", "rottenbittergroud": "rotten_bittergourd",
    "freshcapsicum": "fresh_capsicum", "rottencapsicum": "rotten_capsicum",
    "freshcucumber": "fresh_cucumber", "rottencucumber": "rotten_cucumber",
    "freshokra": "fresh_okra", "rottenokra": "rotten_okra",
    "freshoranges": "fresh_orange", "rottenoranges": "rotten_orange",
    "freshpotato": "fresh_potato", "rottenpotato": "rotten_potato",
    "freshtomato": "fresh_tomato", "rottentomato": "rotten_tomato",
    # Test set typos
    "freshpatato": "fresh_potato", "freshtamto": "fresh_tomato",
    "rottenpatato": "rotten_potato", "rottentamto": "rotten_tomato",
}

def main():
    print(f"Downloading dataset: {DATASET_PATH}")
    print(f"Target directory: {LOCAL_DIR}")
    print()

    # kagglehub downloads to its cache directory
    cache_path = kagglehub.dataset_download(DATASET_PATH)
    print(f"\nDataset downloaded to cache: {cache_path}")

    # Copy to local training/data/ for convenience
    if os.path.exists(LOCAL_DIR):
        print(f"Removing existing data directory: {LOCAL_DIR}")
        shutil.rmtree(LOCAL_DIR)

    shutil.copytree(cache_path, LOCAL_DIR)
    print(f"Copied to: {LOCAL_DIR}")

    # Rename directories to standard format
    for split in ["Train", "Test"]:
        split_dir = os.path.join(LOCAL_DIR, split)
        if not os.path.exists(split_dir):
            continue
        # Move from Train/Test to train/test
        std_dir = os.path.join(LOCAL_DIR, split.lower())
        os.rename(split_dir, std_dir)
        
        for old_name in os.listdir(std_dir):
            old_path = os.path.join(std_dir, old_name)
            if not os.path.isdir(old_path):
                continue
            new_name = RENAME_MAP.get(old_name, old_name)
            if new_name != old_name:
                new_path = os.path.join(std_dir, new_name)
                os.rename(old_path, new_path)
        print(f"Renamed {split} directories to standard format")

    # Inspect structure
    print("\n--- Dataset structure ---")
    for root, dirs, files in os.walk(LOCAL_DIR):
        depth = root[len(LOCAL_DIR):].count(os.sep)
        if depth <= 3:
            indent = "  " * depth
            print(f"{indent}{os.path.basename(root)}/ ({len(files)} files)")
            if depth >= 3:
                continue

    # Count images per class
    print("\n--- Images per class ---")
    class_counts = {}
    for root, dirs, files in os.walk(LOCAL_DIR):
        jpg_files = [f for f in files if f.lower().endswith(('.jpg', '.jpeg', '.png'))]
        if jpg_files and root != LOCAL_DIR:
            class_name = os.path.basename(root)
            # Include parent folder name for disambiguation if needed
            parent = os.path.basename(os.path.dirname(root))
            if parent in ("train", "test", "val", "validation"):
                key = f"{parent}/{class_name}"
            else:
                key = class_name
            class_counts[key] = len(jpg_files)

    for cls, count in sorted(class_counts.items()):
        print(f"  {cls}: {count} images")

    total = sum(class_counts.values())
    print(f"\n  Total: {total} images")

    if total < 1000:
        print("\n⚠️  WARNING: Very few images found. Dataset structure may be unexpected.")
        print("   Inspecting full directory tree...")
        for root, dirs, files in os.walk(LOCAL_DIR):
            print(f"  {root} ({len(files)} files)")

    print("\n[OK] Download complete!")


if __name__ == "__main__":
    main()
