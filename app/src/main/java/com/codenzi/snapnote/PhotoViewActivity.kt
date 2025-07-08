package com.codenzi.snapnote

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import coil.load
import com.codenzi.snapnote.databinding.ActivityPhotoViewBinding

class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUriString = intent.getStringExtra("IMAGE_URI")
        if (imageUriString != null) {
            // Hata mesajındaki öneriye uyarak .toUri() fonksiyonunu kullanıyoruz.
            val imageUri = imageUriString.toUri()
            binding.ivFullscreenPhoto.load(imageUri) {
                crossfade(true)
                error(R.drawable.ic_image_24)
            }
        }

        binding.btnClosePhotoView.setOnClickListener {
            finish()
        }

        binding.ivFullscreenPhoto.setOnClickListener{
            finish()
        }
    }
}