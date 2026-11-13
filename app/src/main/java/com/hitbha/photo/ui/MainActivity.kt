package com.hitbha.photo.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.hitbha.photo.R
import com.hitbha.photo.databinding.ActivityMainBinding
import com.hitbha.photo.ui.editor.EditorActivity
import com.hitbha.photo.ui.batch.BatchProcessingActivity
import com.hitbha.photo.ui.cut.CutInsideImageActivity
import com.hitbha.photo.ui.passport.PassportSizeActivity
import com.hitbha.photo.ui.stitch.ImageStitchActivity
import com.hitbha.photo.ui.history.HistoryActivity
import com.hitbha.photo.utils.AdManager
import com.hitbha.photo.utils.LocaleUtils
import com.hitbha.photo.utils.StatePersistence
import com.hitbha.photo.utils.ThemeManager

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleUtils.applyLocale(newBase))
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        setTheme(ThemeManager.getThemeStyle(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ThemeManager.setupEdgeToEdge(this)
        updateStatusBarStyle()
        setupWindowInsets()
        setSupportActionBar(binding.toolbar)

        AdManager.loadInterstitial(this)
		AdManager.loadRewardedInterstitial(this)
		setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        StatePersistence.clearPassportState(this)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.menu_night_mode)?.isChecked = ThemeManager.isNightMode(this)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_night_mode -> {
                val newState = !item.isChecked
                item.isChecked = newState
                ThemeManager.setNightMode(this, newState)
                ThemeManager.applyNightMode(this)
                recreate()
                true
            }
            R.id.menu_theme -> {
                showThemeDialog()
                true
            }
            R.id.menu_language -> {
                showLanguageDialog()
                true
            }
            R.id.menu_support -> {
                Toast.makeText(this, R.string.contact_support, Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_privacy -> {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/photo-resize/home"))
                startActivity(intent)
                true
            }
            R.id.menu_share -> {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text) + packageName)
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_app)))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemeDialog() {
        val themes = arrayOf(
            "White", "Light Green", "Red", "Green", "Light Blue", "Yellow",
            "Pink", "Brown", "Orange", "Purple", "Dark Blue"
        )
        val themeKeys = arrayOf(
            ThemeManager.THEME_WHITE, ThemeManager.THEME_LIGHT_GREEN, ThemeManager.THEME_RED, ThemeManager.THEME_GREEN,
            ThemeManager.THEME_LIGHT_BLUE, ThemeManager.THEME_YELLOW, ThemeManager.THEME_PINK,
            ThemeManager.THEME_BROWN, ThemeManager.THEME_ORANGE, ThemeManager.THEME_PURPLE,
            ThemeManager.THEME_DARK_BLUE
        )
        val current = ThemeManager.getThemeColor(this)
        val selectedIndex = themeKeys.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_theme)
            .setSingleChoiceItems(themes, selectedIndex) { dialog, which ->
                ThemeManager.setThemeColor(this, themeKeys[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            "Arabic", "Bengali", "Chinese", "English", "French", "German", "Gujarati",
            "Hindi", "Indonesian", "Italian", "Japanese", "Korean", "Marathi",
            "Portuguese", "Russian", "Spanish", "Tamil", "Telugu", "Turkish", "Urdu"
        )
        val languageCodes = arrayOf(
            "ar", "bn", "zh", "en", "fr", "de", "gu",
            "hi", "id", "it", "ja", "ko", "mr",
            "pt", "ru", "es", "ta", "te", "tr", "ur"
        )
        val currentLang = prefs.getString("app_language", "en")
        val selectedIndex = languageCodes.indexOf(currentLang).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_language)
            .setSingleChoiceItems(languages, selectedIndex) { dialog, which ->
                val newLang = languageCodes[which]
                prefs.edit().putString("app_language", newLang).apply()
                dialog.dismiss()
                
                val appLocale = LocaleListCompat.forLanguageTags(newLang)
                AppCompatDelegate.setApplicationLocales(appLocale)
                recreate()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupClickListeners() {
        binding.cardSingleEdit.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, EditorActivity::class.java))
        }
        binding.cardCutInside.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, CutInsideImageActivity::class.java))
        }
        binding.cardPassport.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, PassportSizeActivity::class.java))
        }
        binding.cardStitch.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, ImageStitchActivity::class.java))
        }
        binding.cardBatchEdit.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, BatchProcessingActivity::class.java))
        }
        binding.cardScan.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, com.hitbha.photo.ui.scan.DocumentScannerActivity::class.java))
        }
        binding.btnHistory.setOnClickListener { view ->
            performHapticClick(view)
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun performHapticClick(view: View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    }

    private fun updateStatusBarStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isNightMode = ThemeManager.isNightMode(this)
            WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = !isNightMode
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = systemBars.top)
            val navBarBottom = systemBars.bottom
            if (navBarBottom > 0) {
                binding.btnHistory.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.spacing_xl) + navBarBottom
                }
            }
            binding.scrollContent.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }
}
