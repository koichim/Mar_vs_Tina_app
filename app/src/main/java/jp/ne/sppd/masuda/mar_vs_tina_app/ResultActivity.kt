package jp.ne.sppd.masuda.mar_vs_tina_app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import jp.ne.sppd.masuda.mar_vs_tina_app.databinding.ActivityResultBinding
import jp.ne.sppd.masuda.mar_vs_tina_app.ml.AutoModel072629368F16067259368
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt


class ResultActivity : AppCompatActivity(), Runnable {
    private lateinit var binding: ActivityResultBinding
    private val BATCH_SIZE = 1 //バッチサイズ
    private val INPUT_PIXELS = 3 //入力ピクセル
    private val IMAGE_SIZE = 480

    private val imageBuffer = IntArray(IMAGE_SIZE * IMAGE_SIZE)
    private lateinit var bitmap: Bitmap
    private var path: String? = null
    private val handler : Handler = Handler(Looper.getMainLooper())

    // ラベルデータ
    private var labels: List<String>? = null

    // 入力
    private var inBitmap: Bitmap? = null
    private var inCanvas: Canvas? = null
    private val inBitmapSrc: Rect = Rect()
    private val inBitmapDst: Rect = Rect(0, 0, IMAGE_SIZE, IMAGE_SIZE)
    private var inBuffer: ByteBuffer? = null

    override fun onDestroy() {
        super.onDestroy()
        // 渡された画像ファイルを削除する
        val file = File(path!!)
        file.delete()
    }
    override fun onPause() {
        super.onPause()
        if (intent.hasExtra("exit")) {
            moveTaskToBack(true)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //setContentView(R.layout.activity_result)

        path = intent.getStringExtra("imagePath")
        val exif = ExifInterface(path.toString())
        val orientation: Int =
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        bitmap = BitmapFactory.decodeFile(path)
        val copiedBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val viewBitmap = rotateImage(copiedBitmap, orientation)

        val viewFace = binding.viewImage //findViewById<ImageView>(R.id.view_picture)
        viewFace.setImageBitmap(viewBitmap)

        // 入力の初期化
        inBitmap = Bitmap.createBitmap(
            IMAGE_SIZE, IMAGE_SIZE, Bitmap.Config.ARGB_8888
        )
        inCanvas = Canvas(inBitmap!!)

        val numBytesPerChannel = 4
        inBuffer = ByteBuffer.allocateDirect(
            BATCH_SIZE * IMAGE_SIZE * IMAGE_SIZE * INPUT_PIXELS * numBytesPerChannel
        )
        inBuffer!!.order(ByteOrder.nativeOrder())

        // AI用のラベルデータ読込み
        labels = FileUtil.loadLabels(this, "Labels.txt")

        val resultView = binding.resultText //findViewById<TextView>(R.id.result_text)
        resultView.text = "考え中・・・"

        //handler.post(this)
        handler.postDelayed(this, 100)
    }

    override fun run() {
        //時間のかかる処理実行します。

        // AI呼び出し
        val resultList = predict(bitmap)

        // 結果表示
        val resultView = binding.resultText //findViewById<TextView>(R.id.result_text)
        val percent = (resultList[0].second * 10000).roundToInt() /100
        resultView.text = percent.toString() + "% " + resultList[0].first
    }

    private fun predict(bitmap: Bitmap): List<Pair<String, Float>> {
        // 入力画像の生成
        //inBitmapSrc.set(0, 0, bitmap.width, bitmap.height)
        //inCanvas!!.drawBitmap(bitmap, inBitmapSrc, inBitmapDst, null)
        inCanvas!!.drawBitmap(bitmap, null, inBitmapDst, null)

        // 入力バッファの生成（画像）
        bmpToInBuffer(inBitmap)

        // 出力バッファの生成
        var out: Array<FloatArray> = arrayOf(FloatArray(199))
        val outputMap = mapOf(
            0 to out
        )

        val model = AutoModel072629368F16067259368.newInstance(this)

        // Creates inputs for reference.
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 480, 480, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(inBuffer!!)

        // Runs model inference and gets result.
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer

        // Releases model resources if no longer used.
        model.close()

        /*
        val model = Model.newInstance(this)

        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
        inputFeature0.loadBuffer(inBuffer!!)

        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
         */

        // 結果取得
        val results = TensorLabel(labels!!, outputFeature0!!)
        val floatMap = results.mapWithFloatValue;
        val resultList = floatMap.toList().sortedByDescending { it.second }.toList()

        // 結果の取得
        //return resultList[0].first
        return resultList
    }
    private fun rotateImage(bitmap: Bitmap, orientation : Int): Bitmap? {
        val degree = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90.0
            ExifInterface.ORIENTATION_ROTATE_180 -> 180.0
            ExifInterface.ORIENTATION_ROTATE_270 -> 270.0
            else -> 0.0
        }
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedImg
    }


    private fun bmpToInBuffer(bitmap: Bitmap?) {
        inBuffer!!.rewind()
        bitmap!!.getPixels(
            imageBuffer, 0, bitmap.width,
            0, 0, bitmap.width, bitmap.height
        )
        var pixel = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val pixelValue = imageBuffer[pixel++]
                inBuffer!!.putFloat((pixelValue shr 16 and 0xFF) / 255.0f)
                inBuffer!!.putFloat((pixelValue shr 8 and 0xFF) / 255.0f)
                inBuffer!!.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }
    }
}