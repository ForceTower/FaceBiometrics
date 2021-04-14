package dev.forcetower.fullfacepoc.detection

import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dev.forcetower.fullfacepoc.widget.FaceContourGraphic
import dev.forcetower.fullfacepoc.widget.GraphicOverlay
import timber.log.Timber
import java.io.IOException

class FaceContourDetectionProcessor(private val view: GraphicOverlay) : ImageAnalysis.Analyzer {
    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)

    @ExperimentalGetImage
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        mediaImage?.let {
            detectInImage(InputImage.fromMediaImage(it, image.imageInfo.rotationDegrees))
                .addOnSuccessListener { results ->
                    onSuccess(
                        results,
                        view,
                        it.cropRect
                    )
                    image.close()
                }
                .addOnFailureListener { exception ->
                    onFailure(exception)
                    image.close()
                }
        }
    }

    private fun detectInImage(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

    fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Timber.e(e, "Exception thrown while trying to close Face Detector")
        }
    }

    private fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay,
        rect: Rect
    ) {
        Timber.d("Success. Detected ${results.size} faces")
        graphicOverlay.clear()
        results.forEach {
            val faceGraphic = FaceContourGraphic(graphicOverlay, it, rect)
            graphicOverlay.add(faceGraphic)
        }
        graphicOverlay.postInvalidate()
    }

    private fun onFailure(e: Exception) {
        Timber.e(e, "Face Detector failed.")
    }

}