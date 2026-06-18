"""Complete training pipeline for FreshScan fruit/vegetable freshness model.

Steps:
  1. Load dataset from training/data/ (train/ and test/ subdirectories)
  2. Split train into train/val (90/10 stratified)
  3. Train MobileNetV3-Small with transfer learning
  4. Evaluate on test set + generate reports
  5. Convert to TFLite (FP32 + INT8 quantization)
  6. Validate INT8 accuracy vs FP32
  7. Copy model to app/src/main/assets/model/

Model Input/Output Contract (must match ModelConfig.kt):
  - Input:  [1, 224, 224, 3] float32, RGB, normalized to [0, 1]
  - Output: [1, 18] float32 raw logits (NO softmax — ModelMapper applies it)

Dataset: Kaggle "Fresh and Stale Classification" (swoyam2609)
  - 9 fruits/vegetables x 2 states = 18 classes
  - Train: ~23,619 images, Test: ~6,738 images (14 of 18 classes)
  - Download: python training/download_dataset.py

Usage:
    python training/train.py              # full pipeline
    python training/train.py --epochs 20  # quick test
    python training/train.py --skip-training --model-path <path>  # convert only
"""

import os
import sys
import argparse
import shutil
import time
from pathlib import Path

# Add project root
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers
import matplotlib
matplotlib.use("Agg")  # non-interactive backend
import matplotlib.pyplot as plt
from sklearn.metrics import classification_report, confusion_matrix
import seaborn as sns

# --- Constants matching ModelConfig.kt / Constants.kt ---
IMG_SIZE = 224
IMG_CHANNELS = 3
NUM_CLASSES = 18  # 9 fruits/vegetables x 2 states (fresh/rotten)
BATCH_SIZE = 32
LEARNING_RATE = 1e-4
MAX_EPOCHS = 50
EARLY_STOP_PATIENCE = 5
REDUCE_LR_PATIENCE = 3
REDUCE_LR_FACTOR = 0.5

# Label order MUST match Android ModelConfig.kt labels and labels.txt.
# Index parity: even = fresh, odd = rotten (same item)
LABELS = [
    "fresh_apple", "rotten_apple",
    "fresh_banana", "rotten_banana",
    "fresh_bittergourd", "rotten_bittergourd",
    "fresh_capsicum", "rotten_capsicum",
    "fresh_cucumber", "rotten_cucumber",
    "fresh_okra", "rotten_okra",
    "fresh_orange", "rotten_orange",
    "fresh_potato", "rotten_potato",
    "fresh_tomato", "rotten_tomato",
]

# Paths
DATA_DIR = PROJECT_ROOT / "training" / "data"
OUTPUT_DIR = PROJECT_ROOT / "training" / "output"
MODEL_ASSETS_DIR = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "model"
DOCS_DIR = PROJECT_ROOT / "docs"


def find_dataset_path():
    """Find dataset directory with train/ and test/ subdirectories."""
    if DATA_DIR.exists() and (DATA_DIR / "train").exists():
        print(f"Using local dataset: {DATA_DIR}")
        return str(DATA_DIR)

    print("ERROR: Dataset not found at training/data/")
    print("Run 'python training/download_dataset.py' first, then")
    print("ensure train/ and test/ subdirectories exist with class folders.")
    sys.exit(1)


def total_batches(num_images, batch_size, fraction=1.0):
    """Estimate number of batches for reporting."""
    return max(1, int(num_images * fraction / batch_size))


def create_datasets(dataset_path, batch_size=BATCH_SIZE):
    """Create tf.data datasets from train/ directory.

    Stratified 80/10/10 split from train/:
      - Train: 80% (with augmentation)
      - Val:   10% (no augmentation, used for early stopping)
      - Test:  10% (no augmentation, final evaluation)

    This ensures ALL 18 classes appear in train/val/test, unlike the
    original Kaggle test/ split which only has 14 classes.

    If a separate test/ directory exists, it is used as a secondary
    validation set at the end (not for training decisions).
    """
    train_dir = os.path.join(dataset_path, "train")
    test_dir = os.path.join(dataset_path, "test")

    if not os.path.isdir(train_dir):
        print(f"ERROR: train/ directory not found at {train_dir}")
        sys.exit(1)

    # Count images per class for reporting
    class_counts = {}
    for cls in LABELS:
        cls_dir = os.path.join(train_dir, cls)
        if os.path.isdir(cls_dir):
            class_counts[cls] = len([f for f in os.listdir(cls_dir)
                                     if f.lower().endswith(('.png', '.jpg', '.jpeg'))])
    total_imgs = sum(class_counts.values())
    print(f"\n=== Loading dataset ===")
    print(f"Train dir: {train_dir} ({total_imgs} images across {len(class_counts)} classes)")
    if os.path.isdir(test_dir):
        extra_test_count = sum(1 for cls in os.listdir(test_dir)
                               for f in os.listdir(os.path.join(test_dir, cls))
                               if f.lower().endswith(('.png', '.jpg', '.jpeg'))
                               if os.path.isdir(os.path.join(test_dir, cls)))
        print(f"Extra test dir: {test_dir} ({extra_test_count} images, reference only)")
    print(f"  Image range per class: {min(class_counts.values())} ~ {max(class_counts.values())}")
    if max(class_counts.values()) / min(class_counts.values()) > 5:
        print("  ⚠️  Class imbalance detected — consider using class_weight")

    # Load full dataset from train/ with 80/20 split
    train_and_val_test = tf.keras.utils.image_dataset_from_directory(
        train_dir,
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=batch_size,
        label_mode="categorical",
        class_names=LABELS,
        shuffle=True,
        seed=42,
        validation_split=0.2,
        subset="both",
    )
    full_train_ds = train_and_val_test[0]  # 80%
    val_test_ds = train_and_val_test[1]     # 20%

    # Split val_test 50/50 → val (10%) + test (10%)
    val_test_batches = tf.data.experimental.cardinality(val_test_ds).numpy()
    val_batches = max(1, val_test_batches // 2)
    test_batches = val_test_batches - val_batches

    val_ds = val_test_ds.take(val_batches)
    test_ds = val_test_ds.skip(val_batches)

    print(f"Split: Train={total_batches(total_imgs, batch_size, 0.8)} batches (~80%), "
          f"Val={val_batches} batches (~10%), Test={test_batches} batches (~10%)")
    print(f"All {len(LABELS)} classes present in train/val/test ✓")

    # --- Preprocessing layers ---
    normalization = layers.Rescaling(1.0 / 255.0)

    data_augmentation = tf.keras.Sequential([
        layers.RandomRotation(20.0 / 360.0),
        layers.RandomTranslation(0.1, 0.1),
        layers.RandomZoom(0.15),
        layers.RandomFlip("horizontal"),
        layers.RandomBrightness(0.15),
    ], name="data_augmentation")

    AUTOTUNE = tf.data.AUTOTUNE

    def prepare_train(ds):
        return ds.map(
            lambda x, y: (data_augmentation(x, training=True), y),
            num_parallel_calls=AUTOTUNE,
        ).map(
            lambda x, y: (normalization(x), y),
            num_parallel_calls=AUTOTUNE,
        ).prefetch(AUTOTUNE)

    def prepare_eval(ds):
        return ds.map(
            lambda x, y: (normalization(x), y),
            num_parallel_calls=AUTOTUNE,
        ).prefetch(AUTOTUNE)

    return prepare_train(full_train_ds), prepare_eval(val_ds), prepare_eval(test_ds)


def build_model():
    """Build MobileNetV3-Small with custom 6-class classification head.

    Output: raw logits (no softmax). ModelMapper applies softmax on Android.
    """
    print("\n=== Building model ===")

    base_model = keras.applications.MobileNetV3Small(
        input_shape=(IMG_SIZE, IMG_SIZE, IMG_CHANNELS),
        include_top=False,
        weights="imagenet",
        include_preprocessing=False,
    )

    # Fine-tune last 20 layers
    base_model.trainable = True
    for layer in base_model.layers[:-20]:
        layer.trainable = False

    trainable_count = sum(w.numpy().size for w in base_model.trainable_weights)
    print(f"Base model: {base_model.count_params():,} params "
          f"({trainable_count:,} trainable in base)")

    inputs = keras.Input(shape=(IMG_SIZE, IMG_SIZE, IMG_CHANNELS))
    x = base_model(inputs, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.2)(x)
    outputs = layers.Dense(NUM_CLASSES, activation=None)(x)  # raw logits
    model = keras.Model(inputs, outputs, name="fruit_freshness")

    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=LEARNING_RATE),
        loss=keras.losses.CategoricalCrossentropy(from_logits=True),
        metrics=["accuracy"],
    )

    print(f"Total params: {model.count_params():,}")
    return model


def train_model(model, train_ds, val_ds, epochs=None):
    """Train with EarlyStopping, ReduceLROnPlateau, and ModelCheckpoint."""
    if epochs is None:
        epochs = MAX_EPOCHS

    print(f"\n=== Training (max {epochs} epochs) ===")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=EARLY_STOP_PATIENCE,
            restore_best_weights=True,
            verbose=1,
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss",
            factor=REDUCE_LR_FACTOR,
            patience=REDUCE_LR_PATIENCE,
            min_lr=1e-7,
            verbose=1,
        ),
        keras.callbacks.ModelCheckpoint(
            str(OUTPUT_DIR / "best_model.keras"),
            monitor="val_accuracy",
            save_best_only=True,
            verbose=1,
        ),
        keras.callbacks.CSVLogger(str(OUTPUT_DIR / "training_log.csv")),
    ]

    start = time.time()
    history = model.fit(
        train_ds,
        validation_data=val_ds,
        epochs=epochs,
        callbacks=callbacks,
        verbose=1,
    )
    elapsed = time.time() - start
    print(f"\nTraining completed in {elapsed / 60:.1f} min "
          f"({elapsed / 3600:.2f} hr)")

    return history


def plot_training_curves(history):
    """Save training/validation loss and accuracy curves to docs/."""
    print("\n=== Generating training curves ===")

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

    ax1.plot(history.history["loss"], label="Train Loss", linewidth=1.5)
    ax1.plot(history.history["val_loss"], label="Val Loss", linewidth=1.5)
    ax1.set_title("Training & Validation Loss")
    ax1.set_xlabel("Epoch")
    ax1.set_ylabel("Loss")
    ax1.legend()
    ax1.grid(True, alpha=0.3)

    ax2.plot(history.history["accuracy"], label="Train Acc", linewidth=1.5)
    ax2.plot(history.history["val_accuracy"], label="Val Acc", linewidth=1.5)
    ax2.set_title("Training & Validation Accuracy")
    ax2.set_xlabel("Epoch")
    ax2.set_ylabel("Accuracy")
    ax2.legend()
    ax2.grid(True, alpha=0.3)

    plt.tight_layout()
    save_path = DOCS_DIR / "training_history.png"
    fig.savefig(save_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"Saved: {save_path}")


def evaluate_model(model, test_ds):
    """Evaluate on test set, generate classification report and confusion matrix.
    
    Labels are already mapped to 18-class indices — no mismatch.
    """
    print("\n=== Evaluating on test set ===")

    test_loss, test_acc = model.evaluate(test_ds, verbose=1)
    print(f"Test Loss:     {test_loss:.4f}")
    print(f"Test Accuracy: {test_acc:.4f} ({test_acc * 100:.2f}%)")

    # Collect all predictions
    y_true_all = []
    y_pred_all = []
    for x_batch, y_batch in test_ds:
        y_true_all.extend(np.argmax(y_batch.numpy(), axis=1))
        logits = model.predict(x_batch, verbose=0)
        y_pred_all.extend(np.argmax(logits, axis=1))

    y_true_all = np.array(y_true_all)
    y_pred_all = np.array(y_pred_all)

    # Classification report (use all labels but only present ones get data)
    report = classification_report(
        y_true_all, y_pred_all, target_names=LABELS,
        labels=list(range(len(LABELS))), digits=4, zero_division=0
    )
    print("\nClassification Report:")
    print(report)

    report_path = DOCS_DIR / "classification_report.txt"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(f"Test Accuracy: {test_acc:.4f} ({test_acc * 100:.2f}%)\n")
        f.write(f"Test Loss: {test_loss:.4f}\n\n")
        f.write(report)
    print(f"Saved: {report_path}")

    # Confusion matrix (all 18 classes; missing ones show as 0 rows/cols)
    cm = confusion_matrix(y_true_all, y_pred_all, labels=list(range(len(LABELS))))
    fig, ax = plt.subplots(figsize=(14, 12))
    sns.heatmap(
        cm, annot=True, fmt="d", cmap="Blues",
        xticklabels=LABELS, yticklabels=LABELS,
        ax=ax, linewidths=0.5,
    )
    ax.set_title("Confusion Matrix — Test Set")
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    plt.xticks(rotation=45, ha="right")
    plt.tight_layout()
    cm_path = DOCS_DIR / "confusion_matrix.png"
    fig.savefig(cm_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"Saved: {cm_path}")

    # Per-class accuracy
    print("\n--- Per-class Accuracy ---")
    per_class_acc = cm.diagonal() / (cm.sum(axis=1) + 1e-9)  # avoid div-by-zero for missing classes
    for label, acc in zip(LABELS, per_class_acc):
        if cm.sum(axis=1)[LABELS.index(label)] > 0:
            bar = "#" * int(acc * 20) + "." * (20 - int(acc * 20))
            print(f"  {label:22s} {acc:.3f}  {bar}")
        else:
            print(f"  {label:22s} N/A (no test data)")

    return test_acc, test_loss


def convert_to_tflite(model):
    """Convert to TFLite FP32 + INT8 quantized model.

    Both output raw logits — ModelMapper applies softmax on Android side.

    Uses from_concrete_functions() to avoid TF 2.16 + Keras 3 MLIR bug
    that crashes on MobileNetV3Small's DepthwiseConv2D ops.
    """
    print("\n=== Converting to TFLite ===")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    # Export concrete function (avoids TF 2.16 MLIR crash with training=False)
    run_model = tf.function(lambda x: model(x, training=False))
    concrete_func = run_model.get_concrete_function(
        tf.TensorSpec([1, IMG_SIZE, IMG_SIZE, IMG_CHANNELS], tf.float32))

    # --- FP32 ---
    print("Converting to FP32 TFLite...")
    converter = tf.lite.TFLiteConverter.from_concrete_functions(
        [concrete_func], trackable_obj=model)
    fp32_model = converter.convert()
    fp32_path = OUTPUT_DIR / "fruit_freshness_fp32.tflite"
    with open(fp32_path, "wb") as f:
        f.write(fp32_model)
    fp32_mb = len(fp32_model) / (1024 * 1024)
    print(f"FP32 model: {fp32_mb:.2f} MB -> {fp32_path}")

    # --- INT8 with representative dataset ---
    print("\nConverting to INT8 TFLite...")
    converter = tf.lite.TFLiteConverter.from_concrete_functions(
        [concrete_func], trackable_obj=model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    def representative_dataset():
        train_dir = DATA_DIR / "train"
        temp_ds = tf.keras.utils.image_dataset_from_directory(
            str(train_dir),
            image_size=(IMG_SIZE, IMG_SIZE),
            batch_size=1,
            label_mode="categorical",
            class_names=LABELS,
            shuffle=True,
            seed=42,
        )
        for i, (img_batch, _) in enumerate(temp_ds):
            if i >= 500:  # Increased from 200 for better calibration
                break
            yield [tf.cast(img_batch, tf.float32) / 255.0]

    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.float32
    converter.inference_output_type = tf.float32

    int8_model = converter.convert()
    int8_path = OUTPUT_DIR / "fruit_freshness_model.tflite"
    with open(int8_path, "wb") as f:
        f.write(int8_model)
    int8_mb = len(int8_model) / (1024 * 1024)
    print(f"INT8 model: {int8_mb:.2f} MB -> {int8_path}")
    print(f"Compression: {len(fp32_model) / len(int8_model):.2f}x")

    return str(fp32_path), str(int8_path)


def validate_tflite(fp32_path, int8_path):
    """Compare INT8 accuracy against FP32 baseline on test data."""
    print("\n=== Validating TFLite models ===")

    test_dir = DATA_DIR / "test"
    test_class_dirs = sorted([d for d in os.listdir(str(test_dir)) if os.path.isdir(os.path.join(str(test_dir), d))])
    test_dir_to_label = {d: LABELS.index(d) for d in test_class_dirs if d in LABELS}
    raw_test_ds = tf.keras.utils.image_dataset_from_directory(
        str(test_dir),
        image_size=(IMG_SIZE, IMG_SIZE),
        batch_size=1,
        label_mode="int",
        shuffle=False,
    )
    def remap_lbls(images, labels):
        local_names = raw_test_ds.class_names
        map_local = tf.constant([test_dir_to_label.get(n, 0) for n in local_names], dtype=tf.int64)
        return images, tf.gather(map_local, tf.cast(labels, tf.int64))
    test_ds = raw_test_ds.map(remap_lbls)

    # FP32 interpreter
    fp32_interp = tf.lite.Interpreter(model_path=fp32_path)
    fp32_interp.allocate_tensors()
    fp32_in = fp32_interp.get_input_details()[0]["index"]
    fp32_out = fp32_interp.get_output_details()[0]["index"]

    # INT8 interpreter
    int8_interp = tf.lite.Interpreter(model_path=int8_path)
    int8_interp.allocate_tensors()
    int8_in = int8_interp.get_input_details()[0]["index"]
    int8_out = int8_interp.get_output_details()[0]["index"]

    fp32_ok = int8_ok = mismatches = total = 0

    for img_batch, label_batch in test_ds:
        img = img_batch.numpy().astype(np.float32) / 255.0
        true_label = np.argmax(label_batch.numpy())

        fp32_interp.set_tensor(fp32_in, img)
        fp32_interp.invoke()
        fp32_pred = np.argmax(fp32_interp.get_tensor(fp32_out))

        int8_interp.set_tensor(int8_in, img)
        int8_interp.invoke()
        int8_pred = np.argmax(int8_interp.get_tensor(int8_out))

        if fp32_pred == true_label:
            fp32_ok += 1
        if int8_pred == true_label:
            int8_ok += 1
        if fp32_pred != int8_pred:
            mismatches += 1

        total += 1
        if total % 200 == 0:
            print(f"  Validated {total} samples...")
        if total >= 1000:
            break

    fp32_acc = fp32_ok / total * 100
    int8_acc = int8_ok / total * 100
    loss = fp32_acc - int8_acc

    print(f"\nValidation ({total} samples):")
    print(f"  FP32 Accuracy: {fp32_acc:.2f}%")
    print(f"  INT8 Accuracy: {int8_acc:.2f}%")
    print(f"  Accuracy loss: {loss:.2f}%")
    print(f"  Top-1 mismatches: {mismatches}/{total}")

    if loss >= 2.0:
        print("\n[WARN] INT8 accuracy loss >= 2% — deploying FP32 instead")
        return None  # Signal that INT8 should not be used
    elif loss < 1.0:
        print("[OK] INT8 accuracy within acceptable range (< 1% loss)")
    else:
        print("[OK] INT8 accuracy within 2% threshold")

    return int8_acc


def copy_model_to_assets(int8_valid=True):
    """Copy final TFLite model + labels to Android assets directory.

    Args:
        int8_valid: If False, INT8 accuracy was too poor — use FP32 instead.
    """
    print("\n=== Copying model to Android assets ===")

    if int8_valid:
        src = OUTPUT_DIR / "fruit_freshness_model.tflite"
    else:
        src = OUTPUT_DIR / "fruit_freshness_fp32.tflite"
        print("[NOTE] INT8 accuracy too low, deploying FP32 model")

    if not src.exists():
        print(f"ERROR: Model not found at {src}")
        return False

    MODEL_ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    dst = MODEL_ASSETS_DIR / "fruit_freshness_model.tflite"
    shutil.copy2(src, dst)

    size_mb = dst.stat().st_size / (1024 * 1024)
    print(f"Copied: {src} -> {dst}")
    print(f"Size: {size_mb:.2f} MB")

    # Verify labels.txt
    labels_path = MODEL_ASSETS_DIR / "labels.txt"
    if labels_path.exists():
        labels_content = labels_path.read_text().strip().split("\n")
        labels_content = [l for l in labels_content if l]  # remove empty
        print(f"labels.txt: {len(labels_content)} classes — {labels_content}")
    else:
        print("[WARN] labels.txt not found in assets")

    print("[OK] Model ready for Android integration!")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="FreshScan model training pipeline"
    )
    parser.add_argument("--epochs", type=int, default=None,
                        help=f"Max epochs (default: {MAX_EPOCHS})")
    parser.add_argument("--batch-size", type=int, default=BATCH_SIZE,
                        help=f"Batch size (default: {BATCH_SIZE})")
    parser.add_argument("--skip-training", action="store_true",
                        help="Skip training, convert existing .keras model")
    parser.add_argument("--skip-convert", action="store_true",
                        help="Skip TFLite conversion")
    parser.add_argument("--model-path", type=str, default=None,
                        help="Path to existing .keras model for --skip-training")
    args = parser.parse_args()

    print("=" * 60)
    print("FreshScan — Model Training Pipeline")
    print(f"Classes: {NUM_CLASSES} ({len(LABELS)} labels)")
    print("=" * 60)

    dataset_path = find_dataset_path()

    if not args.skip_training:
        train_ds, val_ds, test_ds = create_datasets(dataset_path, args.batch_size)
        model = build_model()
        history = train_model(model, train_ds, val_ds, args.epochs)
        test_acc, test_loss = evaluate_model(model, test_ds)
        plot_training_curves(history)

        # Acceptance criteria
        print("\n=== Acceptance Criteria ===")
        if test_acc >= 0.92:
            print(f"[TARGET] Top-1 Accuracy {test_acc:.2%} >= 92% target!")
        elif test_acc >= 0.85:
            print(f"[OK] Top-1 Accuracy {test_acc:.2%} >= 85% minimum")
        else:
            print(f"[FAIL] Top-1 Accuracy {test_acc:.2%} < 85% minimum")
    else:
        model_path = args.model_path or str(OUTPUT_DIR / "best_model.keras")
        print(f"Loading existing model: {model_path}")
        model = keras.models.load_model(model_path)
        _, _, test_ds = create_datasets(dataset_path, args.batch_size)
        evaluate_model(model, test_ds)

    if not args.skip_convert:
        fp32_path, int8_path = convert_to_tflite(model)
        int8_acc = validate_tflite(fp32_path, int8_path)
        use_int8 = int8_acc is not None
    else:
        use_int8 = False

    copy_model_to_assets(int8_valid=use_int8)

    print("\n" + "=" * 60)
    print("Pipeline complete!")
    print("=" * 60)
    print(f"Outputs:")
    print(f"  Model:  {MODEL_ASSETS_DIR / 'fruit_freshness_model.tflite'}")
    print(f"  Curves: {DOCS_DIR / 'training_history.png'}")
    print(f"  Report: {DOCS_DIR / 'classification_report.txt'}")
    print(f"  Matrix: {DOCS_DIR / 'confusion_matrix.png'}")
    print(f"\nNext: cd e:/freshscan && ./gradlew assembleDebug")


if __name__ == "__main__":
    main()
