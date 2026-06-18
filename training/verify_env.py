"""Verify the training environment is properly set up."""
import tensorflow as tf
import numpy as np
import matplotlib
import sklearn
import seaborn

print(f"TensorFlow {tf.__version__}")
print(f"NumPy {np.__version__}")
print(f"Matplotlib {matplotlib.__version__}")
print(f"scikit-learn {sklearn.__version__}")
print(f"seaborn {seaborn.__version__}")

# Check GPU
gpus = tf.config.list_physical_devices('GPU')
if gpus:
    print(f"✅ GPU available: {gpus}")
else:
    print("⚠️ No GPU detected — training will run on CPU (slower)")

# Quick model test
base = tf.keras.applications.MobileNetV3Small(
    input_shape=(224, 224, 3),
    include_top=False,
    weights='imagenet'
)
print(f"✅ MobileNetV3-Small loaded: {base.count_params():,} params")
print("✅ Environment OK!")
