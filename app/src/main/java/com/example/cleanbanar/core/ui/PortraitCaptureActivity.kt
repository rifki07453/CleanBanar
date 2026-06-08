package com.example.cleanbanar.core.ui

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.example.cleanbanar.R

/**
 * Activity khusus untuk ZXing Scanner agar terkunci di mode Portrait (Vertikal)
 */
class PortraitCaptureActivity : CaptureActivity() {

    protected override fun initializeContent(): DecoratedBarcodeView {
        setContentView(R.layout.activity_portrait_capture)
        
        // Temukan tombol back dan set listener untuk keluar
        findViewById<android.view.View>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
        
        return findViewById(R.id.zxing_barcode_scanner)
    }
}
