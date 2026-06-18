package com.example.freshscan.data.inference.model

import com.example.freshscan.util.Constants

/**
 * TFLite model hyperparameters and label definitions.
 *
 * All values must match the training configuration exactly.
 * See docs/04-模型训练说明.md for the training pipeline.
 */
class ModelConfig {
    /** Input tensor shape: height */
    val inputHeight: Int = Constants.MODEL_INPUT_SIZE

    /** Input tensor shape: width */
    val inputWidth: Int = Constants.MODEL_INPUT_SIZE

    /** Input tensor shape: channels (RGB = 3) */
    val inputChannels: Int = Constants.MODEL_INPUT_CHANNELS

    /** Normalization mean (subtract before feeding to model). */
    val normalizationMean: Float = 0.0f

    /** Normalization std (divide by after mean subtraction). */
    val normalizationStd: Float = Constants.NORMALIZATION_STD

    /** Number of output classes (9 fruits/vegetables x 2 states = 18). */
    val numClasses: Int = 18

    /**
     * Label names in model output order (index 0-17).
     *
     * MUST match the training labels.txt exactly.
     * Index parities: even = fresh, odd = rotten.
     */
    val labels: List<String> = listOf(
        "fresh_apple", "rotten_apple",
        "fresh_banana", "rotten_banana",
        "fresh_bittergourd", "rotten_bittergourd",
        "fresh_capsicum", "rotten_capsicum",
        "fresh_cucumber", "rotten_cucumber",
        "fresh_okra", "rotten_okra",
        "fresh_orange", "rotten_orange",
        "fresh_potato", "rotten_potato",
        "fresh_tomato", "rotten_tomato"
    )

    /** ByteBuffer capacity: H × W × C × sizeof(float32) */
    val byteBufferCapacity: Int = inputHeight * inputWidth * inputChannels * 4
}
