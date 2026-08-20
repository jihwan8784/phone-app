package com.ledger.mobile.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

class StatementOcr {
    fun read(context: Context, uri: Uri, onText: (String) -> Unit, onError: (Exception) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                .process(image)
                .addOnSuccessListener { result -> onText(result.text) }
                .addOnFailureListener { error -> onError(error) }
        } catch (error: Exception) {
            onError(error)
        }
    }
}
