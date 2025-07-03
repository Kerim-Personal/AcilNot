package com.codenzi.acilnot

import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout

// Parola göster/gizle ikonunun durumunu temsil eden sabitler
// Normal göz ikonu, parolanın şu anda gizli olduğunu (tıklanırsa gösterileceğini) ifade eder.
const val SHOW_PASSWORD_ICON_TAG = "show_password_icon_tag"
// Gözün üstünde çizgi olan ikon, parolanın şu anda görünür olduğunu (tıklanırsa gizleneceğini) ifade eder.
const val HIDE_PASSWORD_ICON_TAG = "hide_password_icon_tag"

/**
 * Bir EditText içindeki parolanın görünürlüğünü açar veya kapatır ve buna uygun ikonu ayarlar.
 *
 * @param editText Parolanın görünürlüğünü değiştireceğimiz EditText.
 * @param textInputLayout EditText'i içeren TextInputLayout. İkon yönetimi için kullanılır.
 */
fun togglePasswordVisibility(editText: EditText, textInputLayout: TextInputLayout) {
    val selection = editText.selectionEnd // İmlecin mevcut konumunu sakla

    if (editText.transformationMethod is PasswordTransformationMethod) {
        // Eğer parola şu anda gizliyse (yani PasswordTransformationMethod kullanılıyorsa)
        // Parolayı görünür yap:
        editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
        // İkonu "gizle" durumuna getir (gözün üstünde çizgi):
        textInputLayout.setEndIconDrawable(R.drawable.ic_visibility_off)
        textInputLayout.tag = HIDE_PASSWORD_ICON_TAG
    } else {
        // Eğer parola şu anda görünürse (yani HideReturnsTransformationMethod kullanılıyorsa)
        // Parolayı gizle:
        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        // İkonu "göster" durumuna getir (normal göz):
        textInputLayout.setEndIconDrawable(R.drawable.ic_visibility)
        textInputLayout.tag = SHOW_PASSWORD_ICON_TAG
    }
    // İmleci eski konumuna geri getir
    editText.setSelection(selection)
}

/**
 * TextInputLayout'daki göster/gizle ikonunun ilk durumunu ayarlar.
 * Varsayılan olarak parola gizlidir ve normal göz ikonu gösterilir.
 */
fun setupPasswordVisibilityToggle(textInputLayout: TextInputLayout, editText: EditText) {
    // İkonun tıklama olayını dinle
    textInputLayout.setEndIconOnClickListener {
        togglePasswordVisibility(editText, textInputLayout)
    }

    // İlk yüklemede parolayı gizli tut ve normal göz ikonunu göster
    editText.transformationMethod = PasswordTransformationMethod.getInstance()
    textInputLayout.setEndIconDrawable(R.drawable.ic_visibility)
    textInputLayout.tag = SHOW_PASSWORD_ICON_TAG
}